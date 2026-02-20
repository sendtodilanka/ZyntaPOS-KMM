package com.zyntasolutions.zyntapos.data.repository

import com.zyntasolutions.zyntapos.core.result.DatabaseException
import com.zyntasolutions.zyntapos.core.result.NetworkException
import com.zyntasolutions.zyntapos.core.result.Result
import com.zyntasolutions.zyntapos.data.local.mapper.SyncOperationMapper
import com.zyntasolutions.zyntapos.db.ZyntaDatabase
import com.zyntasolutions.zyntapos.domain.model.SyncOperation
import com.zyntasolutions.zyntapos.domain.repository.SyncRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.datetime.Clock

/**
 * Concrete implementation of [SyncRepository].
 *
 * ## Queue lifecycle
 * ```
 * [Local Write] ──► PENDING
 *                     │
 *               SyncEngine polls
 *                     │
 *             markSyncing ──► IN_FLIGHT
 *                     │
 *           ┌─────────┴──────────┐
 *       Success               Failure
 *           │                    │
 *       SYNCED          retryCount += 1
 *                               │
 *                     retryCount < MAX_RETRIES?
 *                        ┌──────┴──────┐
 *                       Yes            No
 *                        │             │
 *                     PENDING       FAILED (permanent)
 * ```
 *
 * ## Batch size
 * [getPendingOperations] uses a batch limit of [BATCH_SIZE] (default 50) to cap
 * memory usage and network payload size per sync cycle.
 *
 * ## Network layer
 * [pushToServer] and [pullFromServer] are **Phase 1 stubs** — the Ktor API client
 * (Sprint 6 Step 3.4) has not been wired yet. Both return [Result.Success] with
 * empty results so the sync queue compiles and the queue management logic can be
 * unit-tested independently of the network layer.
 *
 * Once the Ktor [ApiService] is available, inject it into this class and replace
 * the stub bodies with real HTTP calls.
 *
 * @param db Encrypted [ZyntaDatabase] singleton.
 */
class SyncRepositoryImpl(
    private val db: ZyntaDatabase,
) : SyncRepository {

    private val q get() = db.sync_queueQueries

    companion object {
        /** Maximum rows fetched per sync cycle. Keeps payload under ~500 KB. */
        const val BATCH_SIZE = 50L

        /** Operations that exceed this retry ceiling are permanently marked FAILED. */
        const val MAX_RETRIES = 5

        /** SYNCING rows left for more than 10 minutes are assumed stale (app crash). */
        const val STALE_SYNCING_THRESHOLD_MS = 10L * 60 * 1000
    }

    // ── Read ─────────────────────────────────────────────────────────

    /**
     * Returns up to [BATCH_SIZE] sync operations with status PENDING or FAILED
     * (retry_count < [MAX_RETRIES]) ordered oldest-first.
     *
     * Also resets any SYNCING rows left over from a previous crashed session
     * (stuck for > 10 minutes) back to PENDING before fetching.
     */
    override suspend fun getPendingOperations(): List<SyncOperation> = withContext(Dispatchers.IO) {
        val staleCutoff = Clock.System.now().toEpochMilliseconds() - STALE_SYNCING_THRESHOLD_MS
        // Reset stale in-flight rows so they're retried on next cycle
        q.resetStaleSync(staleCutoff)
        q.getEligibleOperations(BATCH_SIZE)
            .executeAsList()
            .map(SyncOperationMapper::toDomain)
    }

    // ── Status transitions ───────────────────────────────────────────

    /**
     * Marks all [ids] as SYNCED.
     * Intended to be called by [SyncEngine] after a successful server acknowledgement.
     */
    override suspend fun markSynced(ids: List<String>): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            db.transaction {
                ids.forEach { id -> q.markSynced(id) }
            }
        }.fold(
            onSuccess = { Result.Success(Unit) },
            onFailure = { t ->
                Result.Error(
                    DatabaseException(
                        message   = "markSynced failed for ${ids.size} ops: ${t.message}",
                        operation = "markSynced",
                        cause     = t,
                    )
                )
            },
        )
    }

    /**
     * Increments [SyncOperation.retryCount] for each failed id.
     * If the count reaches [MAX_RETRIES] the row is permanently marked FAILED.
     * Called by [SyncEngine] after a server push returns a non-2xx for individual ops.
     *
     * This is an internal helper not exposed via [SyncRepository] but used by the
     * SyncEngine which holds a reference to the impl. The engine can also call
     * [getPendingOperations] after this to confirm the queue drains correctly.
     */
    suspend fun markFailed(ids: List<String>): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val now = Clock.System.now().toEpochMilliseconds()
            db.transaction {
                ids.forEach { id ->
                    val row = q.getByEntityId(id).executeAsOneOrNull()
                    val nextCount = (row?.retry_count?.toInt() ?: 0) + 1
                    if (nextCount >= MAX_RETRIES) {
                        q.markPermanentlyFailed(id)
                    } else {
                        q.markFailed(now, id)
                    }
                }
            }
        }.fold(
            onSuccess = { Result.Success(Unit) },
            onFailure = { t ->
                Result.Error(DatabaseException(t.message ?: "markFailed error", cause = t))
            },
        )
    }

    // ── Network stubs (Phase 1 — replaced in Sprint 6 Step 3.4) ─────

    /**
     * **Phase 1 stub** — no-op push that marks all supplied ops as SYNCED locally.
     *
     * Sprint 6 Step 3.4 (Ktor ApiService) will replace this body with:
     * ```
     * val response = apiService.pushBatch(ops.map { it.toDto() })
     * if (response.isSuccessful) markSynced(ops.map { it.id })
     * else markFailed(ops.filter { it.id !in response.acknowledged }.map { it.id })
     * ```
     */
    override suspend fun pushToServer(ops: List<SyncOperation>): Result<Unit> {
        // TODO(Sprint6-Step3.4): wire Ktor ApiService here
        return if (ops.isEmpty()) Result.Success(Unit)
        else markSynced(ops.map { it.id })
    }

    /**
     * **Phase 1 stub** — returns an empty list (offline-only MVP).
     *
     * Sprint 6 Step 3.4 will replace this with:
     * ```
     * val response = apiService.pullDelta(since = lastSyncTimestamp)
     * return Result.Success(response.operations.map { it.toDomain() })
     * ```
     */
    override suspend fun pullFromServer(lastSyncTimestamp: Long): Result<List<SyncOperation>> =
        Result.Success(emptyList())

    // ── Maintenance ──────────────────────────────────────────────────

    /**
     * Removes SYNCED rows older than [olderThanMs] epoch-milliseconds.
     * Call periodically (e.g., daily) to keep the table lean.
     */
    suspend fun pruneSynced(olderThanMs: Long): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching { q.pruneSynced(olderThanMs) }
            .fold(
                onSuccess = { Result.Success(Unit) },
                onFailure = { t -> Result.Error(DatabaseException(t.message ?: "pruneSynced failed", cause = t)) },
            )
    }

    /**
     * Deduplicates PENDING operations: keeps only the **latest** op per (entity_type, entity_id).
     * Safe to call before each push cycle to reduce redundant network work.
     */
    suspend fun deduplicatePending(): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching { q.deduplicatePending() }
            .fold(
                onSuccess = { Result.Success(Unit) },
                onFailure = { t -> Result.Error(DatabaseException(t.message ?: "deduplicatePending failed", cause = t)) },
            )
    }
}
