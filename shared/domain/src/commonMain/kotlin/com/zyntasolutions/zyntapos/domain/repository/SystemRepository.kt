package com.zyntasolutions.zyntapos.domain.repository

import com.zyntasolutions.zyntapos.core.result.Result
import com.zyntasolutions.zyntapos.domain.model.DatabaseStats
import com.zyntasolutions.zyntapos.domain.model.PurgeResult
import com.zyntasolutions.zyntapos.domain.model.SystemHealth

/**
 * Contract for system health and database administration operations.
 */
interface SystemRepository {

    /** Returns a snapshot of system health metrics. */
    suspend fun getSystemHealth(): Result<SystemHealth>

    /** Returns per-table row counts and database file size. */
    suspend fun getDatabaseStats(): Result<DatabaseStats>

    /**
     * Runs VACUUM on the SQLite database to reclaim free pages and compact the file.
     * This operation can be slow on large databases.
     *
     * @return [PurgeResult] with bytes freed and duration.
     */
    suspend fun vacuumDatabase(): Result<PurgeResult>

    /**
     * Purges soft-deleted records older than [olderThanMillis] from all tables.
     *
     * @return [PurgeResult] with number of rows deleted.
     */
    suspend fun purgeExpiredData(olderThanMillis: Long): Result<PurgeResult>
}
