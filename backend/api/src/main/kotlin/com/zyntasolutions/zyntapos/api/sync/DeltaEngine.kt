package com.zyntasolutions.zyntapos.api.sync

import com.zyntasolutions.zyntapos.api.models.PullResponse
import com.zyntasolutions.zyntapos.api.repository.SyncCursorRepository
import com.zyntasolutions.zyntapos.api.repository.SyncOperationRepository

/**
 * Computes cursor-based delta operations for pull requests.
 *
 * Uses [server_seq] (monotonic BIGSERIAL) as the cursor — never timestamps,
 * so gaps and clock skew cannot cause missed operations.
 */
class DeltaEngine(
    private val syncOpRepo: SyncOperationRepository,
    private val cursorRepo: SyncCursorRepository,
    private val metrics: SyncMetrics,
) {
    companion object {
        const val DEFAULT_LIMIT = 50
        const val MAX_LIMIT = 200
    }

    /**
     * Compute delta operations for a pull request.
     *
     * @param storeId  Store to pull from (JWT claim enforced by caller)
     * @param deviceId Requesting device — cursor is persisted per device
     * @param since    Last server_seq the device received (0 for first sync)
     * @param limit    Max operations to return (clamped to [MAX_LIMIT])
     */
    suspend fun computeDelta(
        storeId: String,
        deviceId: String,
        since: Long,
        limit: Int = DEFAULT_LIMIT,
    ): PullResponse {
        val t0 = java.time.Instant.now().toEpochMilli()
        val effectiveLimit = limit.coerceIn(1, MAX_LIMIT)

        // Fetch one extra to determine hasMore without an extra COUNT
        val rows = syncOpRepo.findAfterSeq(
            storeId  = storeId,
            afterSeq = since,
            limit    = effectiveLimit + 1,
        )

        val hasMore    = rows.size > effectiveLimit
        val page       = if (hasMore) rows.dropLast(1) else rows
        val newCursor  = page.lastOrNull()?.serverSeq ?: since

        // Persist cursor so admin panel can show "last pull" per device
        cursorRepo.upsert(storeId, deviceId, newCursor)

        metrics.recordPullDuration(java.time.Instant.now().toEpochMilli() - t0)

        return PullResponse(
            operations        = page,
            serverTimestamp   = java.time.Instant.now().toEpochMilli(),
            serverVectorClock = newCursor,
            hasMore           = hasMore,
        )
    }
}
