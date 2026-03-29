package com.zyntasolutions.zyntapos.data.sync

import com.zyntasolutions.zyntapos.core.config.AppConfig
import com.zyntasolutions.zyntapos.core.logger.ZyntaLogger
import com.zyntasolutions.zyntapos.db.ZyntaDatabase
import kotlin.time.Clock

/**
 * Periodic maintenance for the `pending_operations` sync queue.
 *
 * Runs three operations inside a single transaction:
 * 1. **Prune SYNCED** — delete successfully synced ops older than [AppConfig.SYNC_QUEUE_RETENTION_DAYS].
 * 2. **Prune FAILED** — delete permanently failed ops older than [AppConfig.SYNC_FAILED_RETENTION_DAYS].
 * 3. **Deduplicate PENDING** — collapse write bursts by keeping only the latest PENDING op per entity.
 *
 * Called by [SyncEngine] every [AppConfig.SYNC_MAINTENANCE_INTERVAL_CYCLES] successful sync cycles.
 */
class SyncQueueMaintenance(
    private val db: ZyntaDatabase,
) {
    private val log = ZyntaLogger.forModule("SyncQueueMaint")

    /**
     * Executes all maintenance operations in a single transaction.
     *
     * @return A [MaintenanceResult] with counts of affected rows.
     */
    fun run(): MaintenanceResult {
        val now = Clock.System.now().toEpochMilliseconds()
        val syncedCutoff = now - (AppConfig.SYNC_QUEUE_RETENTION_DAYS * DAY_MS)
        val failedCutoff = now - (AppConfig.SYNC_FAILED_RETENTION_DAYS * DAY_MS)

        db.transaction {
            db.sync_queueQueries.pruneSynced(syncedCutoff)
            db.sync_queueQueries.pruneFailed(failedCutoff)
            db.sync_queueQueries.deduplicatePending()
        }

        log.d(
            "Queue maintenance complete (syncedCutoff=${AppConfig.SYNC_QUEUE_RETENTION_DAYS}d, " +
                "failedCutoff=${AppConfig.SYNC_FAILED_RETENTION_DAYS}d)",
        )
        return MaintenanceResult(syncedCutoffMs = syncedCutoff, failedCutoffMs = failedCutoff)
    }

    data class MaintenanceResult(
        val syncedCutoffMs: Long,
        val failedCutoffMs: Long,
    )

    companion object {
        private const val DAY_MS = 86_400_000L
    }
}
