package com.zyntasolutions.zyntapos.data.sync

import co.touchlab.kermit.Logger
import com.zyntasolutions.zyntapos.core.config.AppConfig
import com.zyntasolutions.zyntapos.core.result.SyncException
import com.zyntasolutions.zyntapos.data.local.security.SecurePreferences
import com.zyntasolutions.zyntapos.data.remote.api.ApiService
import com.zyntasolutions.zyntapos.data.remote.dto.SyncOperationDto
import com.zyntasolutions.zyntapos.db.ZyntaDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
import kotlinx.datetime.Clock

/**
 * ZyntaPOS — Offline-first background sync coordinator (commonMain).
 *
 * Implements the core push/pull cycle shared across all platforms:
 * 1. Reads PENDING operations from the local outbox queue (batch of [BATCH_SIZE])
 * 2. Pushes them to `POST /api/v1/sync/push` via [ApiService]
 * 3. Marks accepted IDs as SYNCED; increments retry count for rejected IDs
 * 4. Applies server-side delta operations (pull) from [SyncResponseDto.deltaOperations]
 * 5. Persists the new [SecurePreferences.Keys.LAST_SYNC_TS]
 *
 * ## Platform scheduling (expect/actual wrapping)
 * - **Android**: [SyncWorker] (`CoroutineWorker` / WorkManager) calls [runOnce] per scheduled work.
 * - **Desktop**: [startPeriodicSync] on `CoroutineScope(IO)` with [AppConfig.SYNC_INTERVAL_MS] delay.
 *
 * ## Thread safety
 * [_isSyncing] prevents overlapping sync cycles. Each [runOnce] invocation is guarded
 * by a compare-and-set so concurrent calls (e.g. foreground trigger + background tick) are no-ops.
 *
 * @param db           Encrypted [ZyntaDatabase] singleton.
 * @param api          [ApiService] Ktor client.
 * @param prefs        [SecurePreferences] for last-sync timestamp + auth tokens.
 * @param networkMonitor Provides real-time connectivity state.
 */
class SyncEngine(
    private val db: ZyntaDatabase,
    private val api: ApiService,
    private val prefs: SecurePreferences,
    private val networkMonitor: NetworkMonitor,
) {
    private val log = Logger.withTag("SyncEngine")

    // ── State ──────────────────────────────────────────────────────────────────

    private val _isSyncing = MutableStateFlow(false)
    val isSyncing: StateFlow<Boolean> = _isSyncing.asStateFlow()

    private val _lastSyncResult = MutableStateFlow<SyncResult>(SyncResult.Idle)
    val lastSyncResult: StateFlow<SyncResult> = _lastSyncResult.asStateFlow()

    /** Current background periodic-sync job (Desktop only). */
    private var periodicJob: Job? = null

    // ── Public API ─────────────────────────────────────────────────────────────

    /**
     * Starts a continuous periodic sync loop on [scope] (Desktop platform).
     * Android scheduling is handled by WorkManager — call [runOnce] from [SyncWorker].
     *
     * No-op if a periodic job is already running.
     *
     * @param scope Coroutine scope tied to application lifetime (e.g., `applicationScope`).
     */
    fun startPeriodicSync(scope: CoroutineScope) {
        if (periodicJob?.isActive == true) {
            log.d { "Periodic sync already running — skipping start" }
            return
        }
        periodicJob = scope.launch {
            log.i { "Periodic sync started (interval: ${AppConfig.SYNC_INTERVAL_MS} ms)" }
            while (true) {
                if (networkMonitor.isConnected.value) {
                    runOnce()
                } else {
                    log.d { "No network — skipping sync cycle" }
                }
                delay(AppConfig.SYNC_INTERVAL_MS)
            }
        }
    }

    /** Stops the periodic sync loop (Desktop). */
    fun stopPeriodicSync() {
        periodicJob?.cancel()
        periodicJob = null
        log.i { "Periodic sync stopped" }
    }

    /**
     * Executes a single push → pull sync cycle.
     *
     * Safe to call from both WorkManager (Android) and periodic loop (Desktop).
     * Re-entrant guard: returns immediately if already in progress.
     */
    suspend fun runOnce() {
        if (!_isSyncing.compareAndSet(expect = false, update = true)) {
            log.d { "Sync already in progress — skipping" }
            return
        }
        try {
            supervisorScope { executeSyncCycle() }
        } finally {
            _isSyncing.value = false
        }
    }

    // ── Core Sync Cycle ────────────────────────────────────────────────────────

    private suspend fun executeSyncCycle() {
        log.d { "=== Sync cycle START ===" }
        val cycleStart = Clock.System.now().toEpochMilliseconds()

        try {
            // 1. Push pending operations
            val pushed = pushPendingOperations()

            // 2. Pull server delta
            val pulled = pullServerDelta()

            // 3. Persist new server timestamp
            prefs.put(SecurePreferences.Keys.LAST_SYNC_TS, Clock.System.now().toEpochMilliseconds().toString())

            val durationMs = Clock.System.now().toEpochMilliseconds() - cycleStart
            log.i { "=== Sync cycle DONE — pushed=$pushed pulled=$pulled duration=${durationMs}ms ===" }
            _lastSyncResult.value = SyncResult.Success(
                pushedCount = pushed,
                pulledCount = pulled,
                durationMs  = durationMs,
            )
        } catch (e: Exception) {
            log.e(e) { "Sync cycle FAILED" }
            // Reset any SYNCING rows back to PENDING so they are retried on the next cycle.
            // (Equivalent to what resetStaleSync does on app restart after a crash.)
            val futureCutoff = Clock.System.now().toEpochMilliseconds() + 1L
            db.sync_queueQueries.resetStaleSync(futureCutoff)
            _lastSyncResult.value = SyncResult.Failure(
                error = e.message ?: "Unknown sync error",
            )
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Push
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Reads up to [BATCH_SIZE] PENDING rows from [pending_operations],
     * pushes them to the server, and processes the ack response.
     *
     * @return Number of operations successfully pushed.
     */
    private suspend fun pushPendingOperations(): Int {
        val pending = db.sync_queueQueries
            .getEligibleOperations(BATCH_SIZE)
            .executeAsList()

        if (pending.isEmpty()) {
            log.d { "No eligible operations — skipping push" }
            return 0
        }

        log.d { "Pushing ${pending.size} operation(s)" }

        val dtos = pending.map { row ->
            SyncOperationDto(
                id          = row.id,
                entityType  = row.entity_type,
                entityId    = row.entity_id,
                operation   = row.operation,
                payload     = row.payload,
                createdAt   = row.created_at,
                retryCount  = row.retry_count.toInt(),
            )
        }

        // Mark all as SYNCING before network call (crash-safe — resetStaleSync recovers these)
        val now = Clock.System.now().toEpochMilliseconds()
        db.transaction {
            dtos.forEach { db.sync_queueQueries.markSyncing(now, it.id) }
        }

        val response = api.pushOperations(dtos)

        // Mark accepted as SYNCED
        db.transaction {
            response.accepted.forEach { id -> db.sync_queueQueries.markSynced(id) }
        }
        if (response.accepted.isNotEmpty()) {
            log.d { "Marked ${response.accepted.size} as SYNCED" }
        }

        // Increment retry count for rejected; markPermanentlyFailed if max retries reached
        val nowTs = Clock.System.now().toEpochMilliseconds()
        for (rejectedId in response.rejected) {
            val row = pending.firstOrNull { it.id == rejectedId } ?: continue
            val newCount = row.retry_count + 1
            if (newCount >= AppConfig.SYNC_MAX_RETRIES) {
                db.sync_queueQueries.markPermanentlyFailed(rejectedId)
                log.w { "Operation $rejectedId permanently FAILED after $newCount retries" }
            } else {
                // markFailed increments retry_count automatically
                db.sync_queueQueries.markFailed(nowTs, rejectedId)
                log.d { "Operation $rejectedId retry count=$newCount" }
            }
        }

        // Apply server-side conflict resolutions bundled with push ack
        if (response.deltaOperations.isNotEmpty()) {
            applyDeltaOperations(response.deltaOperations)
        }

        return response.accepted.size
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Pull
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Pulls server-side changes created after the stored [SecurePreferences.Keys.LAST_SYNC_TS].
     *
     * @return Number of delta operations applied locally.
     */
    private suspend fun pullServerDelta(): Int {
        val lastSyncTs = prefs.get(SecurePreferences.Keys.LAST_SYNC_TS)?.toLongOrNull() ?: 0L
        log.d { "Pulling delta since ts=$lastSyncTs" }

        val pullResponse = api.pullOperations(lastSyncTimestamp = lastSyncTs)

        if (pullResponse.operations.isEmpty()) {
            log.d { "No server-side delta — up to date" }
            return 0
        }

        log.d { "Applying ${pullResponse.operations.size} server delta operation(s)" }
        applyDeltaOperations(pullResponse.operations)
        return pullResponse.operations.size
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Delta application (both push conflicts + pull deltas)
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Applies a list of server-authoritative [SyncOperationDto]s to the local database.
     *
     * For Phase 1: Operations are applied naively (last-write-wins per entity).
     * Phase 2 will introduce CRDT-based conflict resolution.
     *
     * Currently only logs unhandled entity types — extend per entity as Phase 1
     * feature modules are completed.
     */
    private fun applyDeltaOperations(ops: List<SyncOperationDto>) {
        for (op in ops) {
            try {
                log.d { "Applying delta: ${op.operation} on ${op.entityType}(${op.entityId})" }
                // Phase 1: payload is a JSON snapshot; entities are re-seeded via their
                // respective RepositoryImpl "upsert" queries. Full dispatcher added in Sprint 7.
                // TODO Sprint 7: route by entityType → appropriate RepositoryImpl.upsertFromSync(op.payload)
            } catch (e: Exception) {
                log.e(e) { "Failed to apply delta for ${op.entityType}(${op.entityId})" }
            }
        }
    }

    companion object {
        /**
         * Maximum operations per push batch.
         * Must align with [AppConfig.SYNC_BATCH_SIZE] at runtime.
         */
        const val BATCH_SIZE = 50L
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Sync Cycle Result
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Represents the outcome of a [SyncEngine.runOnce] cycle.
 */
sealed class SyncResult {
    data object Idle : SyncResult()
    data class Success(
        val pushedCount: Int,
        val pulledCount: Int,
        val durationMs: Long,
    ) : SyncResult()
    data class Failure(val error: String) : SyncResult()
}
