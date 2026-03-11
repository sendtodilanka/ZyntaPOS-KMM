package com.zyntasolutions.zyntapos.data.sync

import com.zyntasolutions.zyntapos.core.config.AppConfig
import com.zyntasolutions.zyntapos.core.logger.ZyntaLogger
import com.zyntasolutions.zyntapos.core.result.Result
import com.zyntasolutions.zyntapos.core.result.SyncException
import com.zyntasolutions.zyntapos.domain.model.SyncOperation
import com.zyntasolutions.zyntapos.domain.port.SecureStorageKeys
import com.zyntasolutions.zyntapos.domain.port.SecureStoragePort
import com.zyntasolutions.zyntapos.data.remote.api.ApiService
import com.zyntasolutions.zyntapos.data.remote.dto.SyncOperationDto
import com.zyntasolutions.zyntapos.data.repository.CategoryRepositoryImpl
import com.zyntasolutions.zyntapos.data.repository.CustomerRepositoryImpl
import com.zyntasolutions.zyntapos.data.repository.OrderRepositoryImpl
import com.zyntasolutions.zyntapos.data.repository.ProductRepositoryImpl
import com.zyntasolutions.zyntapos.data.repository.StockRepositoryImpl
import com.zyntasolutions.zyntapos.data.repository.SupplierRepositoryImpl
import com.zyntasolutions.zyntapos.db.ZyntaDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
import kotlin.time.Clock

/**
 * ZyntaPOS — Offline-first background sync coordinator (commonMain).
 *
 * Implements the core push/pull cycle shared across all platforms:
 * 1. Reads PENDING operations from the local outbox queue (batch of [BATCH_SIZE])
 * 2. Pushes them to `POST /api/v1/sync/push` via [ApiService]
 * 3. Marks accepted IDs as SYNCED; increments retry count for rejected IDs
 * 4. Applies server-side delta operations (pull) from [SyncResponseDto.deltaOperations]
 * 5. Persists the new [SecureStorageKeys.KEY_LAST_SYNC_TS]
 *
 * ## Platform scheduling (expect/actual wrapping)
 * - **Android**: [SyncWorker] (`CoroutineWorker` / WorkManager) calls [runOnce] per scheduled work.
 * - **Desktop**: [startPeriodicSync] on `CoroutineScope(IO)` with [AppConfig.SYNC_INTERVAL_MS] delay.
 *
 * ## Thread safety
 * [_isSyncing] prevents overlapping sync cycles. Each [runOnce] invocation is guarded
 * by a compare-and-set so concurrent calls (e.g. foreground trigger + background tick) are no-ops.
 *
 * MERGED-F3 (2026-02-22): [prefs] parameter type changed from `SecurePreferences`
 * (`:shared:security`) to [SecureStoragePort] (`:shared:domain`) so `:shared:data`
 * holds no compile-time dependency on `:shared:security`.
 *
 * @param db           Encrypted [ZyntaDatabase] singleton.
 * @param api          [ApiService] Ktor client.
 * @param prefs        [SecureStoragePort] for last-sync timestamp + auth tokens.
 * @param networkMonitor Provides real-time connectivity state.
 */
class SyncEngine(
    private val db: ZyntaDatabase,
    private val api: ApiService,
    private val prefs: SecureStoragePort,
    private val networkMonitor: NetworkMonitor,
    // Sprint 7: concrete repository impls for delta application
    private val productRepository: ProductRepositoryImpl,
    private val orderRepository: OrderRepositoryImpl,
    private val customerRepository: CustomerRepositoryImpl,
    private val categoryRepository: CategoryRepositoryImpl,
    private val supplierRepository: SupplierRepositoryImpl,
    private val stockRepository: StockRepositoryImpl,
) {
    private val log = ZyntaLogger.forModule("SyncEngine")

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
            log.d("Periodic sync already running — skipping start")
            return
        }
        periodicJob = scope.launch {
            log.i("Periodic sync started (interval: ${AppConfig.SYNC_INTERVAL_MS} ms)")
            while (true) {
                if (networkMonitor.isConnected.value) {
                    runOnce()
                } else {
                    log.d("No network — skipping sync cycle")
                }
                delay(AppConfig.SYNC_INTERVAL_MS)
            }
        }
    }

    /** Stops the periodic sync loop (Desktop). */
    fun stopPeriodicSync() {
        periodicJob?.cancel()
        periodicJob = null
        log.i("Periodic sync stopped")
    }

    /**
     * Executes a single push → pull sync cycle.
     *
     * Safe to call from both WorkManager (Android) and periodic loop (Desktop).
     * Re-entrant guard: returns immediately if already in progress.
     */
    suspend fun runOnce() {
        if (!_isSyncing.compareAndSet(expect = false, update = true)) {
            log.d("Sync already in progress — skipping")
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
        log.d("=== Sync cycle START ===")
        val cycleStart = Clock.System.now().toEpochMilliseconds()

        try {
            // 1. Push pending operations
            val pushed = pushPendingOperations()

            // 2. Pull server delta
            val pulled = pullServerDelta()

            // 3. Persist new server timestamp
            prefs.put(SecureStorageKeys.KEY_LAST_SYNC_TS, Clock.System.now().toEpochMilliseconds().toString())

            val durationMs = Clock.System.now().toEpochMilliseconds() - cycleStart
            log.i("=== Sync cycle DONE — pushed=$pushed pulled=$pulled duration=${durationMs}ms ===")
            _lastSyncResult.value = SyncResult.Success(
                pushedCount = pushed,
                pulledCount = pulled,
                durationMs  = durationMs,
            )
        } catch (e: Exception) {
            log.e("Sync cycle FAILED", throwable = e)
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
            log.d("No eligible operations — skipping push")
            return 0
        }

        log.d("Pushing ${pending.size} operation(s)")

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
            log.d("Marked ${response.accepted.size} as SYNCED")
        }

        // Increment retry count for rejected; markPermanentlyFailed if max retries reached
        val nowTs = Clock.System.now().toEpochMilliseconds()
        for (rejectedId in response.rejected) {
            val row = pending.firstOrNull { it.id == rejectedId } ?: continue
            val newCount = row.retry_count + 1
            if (newCount >= AppConfig.SYNC_MAX_RETRIES) {
                db.sync_queueQueries.markPermanentlyFailed(rejectedId)
                log.w("Operation $rejectedId permanently FAILED after $newCount retries")
            } else {
                // markFailed increments retry_count automatically
                db.sync_queueQueries.markFailed(nowTs, rejectedId)
                log.d("Operation $rejectedId retry count=$newCount")
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
     * Pulls server-side changes using cursor-based pagination (TODO-007g).
     *
     * Uses [SecureStorageKeys.KEY_LAST_SYNC_TS] as the cursor (server_seq value).
     * Loops until [SyncPullResponseDto.hasMore] is false to drain the full delta.
     *
     * @return Total number of delta operations applied locally.
     */
    private suspend fun pullServerDelta(): Int {
        var cursor = prefs.get(SecureStorageKeys.KEY_LAST_SYNC_TS)?.toLongOrNull() ?: 0L
        log.d("Pulling delta since cursor=$cursor")

        var totalPulled = 0
        var iterations = 0
        val maxIterations = 20 // Safety cap — prevents infinite loops on server bugs

        while (iterations < maxIterations) {
            iterations++
            val pullResponse = api.pullOperations(lastSyncTimestamp = cursor)

            if (pullResponse.operations.isEmpty()) {
                log.d("No server-side delta — up to date (cursor=$cursor)")
                break
            }

            log.d("Applying ${pullResponse.operations.size} delta ops (iteration=$iterations cursor=$cursor)")
            applyDeltaOperations(pullResponse.operations)
            totalPulled += pullResponse.operations.size

            // Advance cursor to the new position returned by server
            val newCursor = pullResponse.cursor
            if (newCursor <= cursor) break // Defensive: cursor did not advance, stop
            cursor = newCursor
            prefs.put(SecureStorageKeys.KEY_LAST_SYNC_TS, cursor.toString())

            if (!pullResponse.hasMore) break
        }

        if (iterations >= maxIterations) {
            log.w("Pull stopped at max iterations ($maxIterations) — possible server-side issue")
        }

        return totalPulled
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Delta application (both push conflicts + pull deltas)
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Applies a list of server-authoritative [SyncOperationDto]s to the local database.
     *
     * Routes each operation to the appropriate [RepositoryImpl.upsertFromSync] method.
     * DELETE operations call the repository's soft-delete; CREATE/UPDATE both call upsertFromSync
     * (last-write-wins, server is authoritative). Unknown entity types are logged and skipped.
     *
     * No [SyncEnqueuer.enqueue] is called here — server-originated data must not be
     * re-queued for push (would create an infinite sync loop).
     */
    private suspend fun applyDeltaOperations(ops: List<SyncOperationDto>) {
        for (op in ops) {
            try {
                log.d("Applying delta: ${op.operation} on ${op.entityType}(${op.entityId})")
                when (op.operation) {
                    "DELETE" -> {
                        val result: Result<Unit>? = when (op.entityType) {
                            SyncOperation.EntityType.PRODUCT  -> productRepository.delete(op.entityId)
                            SyncOperation.EntityType.ORDER    -> orderRepository.void(op.entityId, "server-sync")
                            SyncOperation.EntityType.CUSTOMER -> customerRepository.delete(op.entityId)
                            SyncOperation.EntityType.CATEGORY -> categoryRepository.delete(op.entityId)
                            else -> {
                                log.w("Unhandled DELETE for entityType: ${op.entityType}")
                                null
                            }
                        }
                        if (result is Result.Error) {
                            log.w("Delta DELETE failed for ${op.entityType}(${op.entityId}): ${result.exception.message}")
                        }
                    }
                    else -> { // "CREATE" | "UPDATE" — server snapshot wins
                        when (op.entityType) {
                            SyncOperation.EntityType.PRODUCT          -> productRepository.upsertFromSync(op.payload)
                            SyncOperation.EntityType.ORDER            -> orderRepository.upsertFromSync(op.payload)
                            SyncOperation.EntityType.CUSTOMER         -> customerRepository.upsertFromSync(op.payload)
                            SyncOperation.EntityType.CATEGORY         -> categoryRepository.upsertFromSync(op.payload)
                            SyncOperation.EntityType.SUPPLIER         -> supplierRepository.upsertFromSync(op.payload)
                            SyncOperation.EntityType.STOCK_ADJUSTMENT -> stockRepository.upsertFromSync(op.payload)
                            SyncOperation.EntityType.USER             -> { /* read-only from device — managed via auth */ }
                            else -> log.w("Unknown entityType for delta op: ${op.entityType}")
                        }
                    }
                }
            } catch (e: Exception) {
                log.e("Failed to apply delta for ${op.entityType}(${op.entityId})", throwable = e)
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
