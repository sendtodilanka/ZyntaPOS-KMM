package com.zyntasolutions.zyntapos.domain.model

/**
 * Snapshot of system health metrics for the admin dashboard.
 *
 * @property databaseSizeBytes Current database file size in bytes.
 * @property totalMemoryBytes Total device memory in bytes.
 * @property usedMemoryBytes Used memory in bytes.
 * @property pendingSyncCount Number of records awaiting sync.
 * @property lastSyncAt Epoch millis of the most recent successful sync. Null if never synced.
 * @property appVersion Application version string (e.g., "1.0.0").
 * @property buildNumber Build number.
 * @property isOnline Whether the device is currently online.
 */
data class SystemHealth(
    val databaseSizeBytes: Long,
    val totalMemoryBytes: Long,
    val usedMemoryBytes: Long,
    val pendingSyncCount: Int,
    val lastSyncAt: Long? = null,
    val appVersion: String,
    val buildNumber: Int,
    val isOnline: Boolean,
) {
    /** Used memory as a percentage of total memory (0.0–100.0). */
    val memoryUsagePercent: Double
        get() = if (totalMemoryBytes > 0) (usedMemoryBytes * 100.0) / totalMemoryBytes else 0.0

    /** True if pending sync count is 0 and last sync was within 24 hours. */
    val isSyncHealthy: Boolean
        get() {
            if (pendingSyncCount > 0) return false
            val syncAt = lastSyncAt ?: return false
            return (System.currentTimeMillis() - syncAt) < 24 * 60 * 60 * 1000L
        }
}

/**
 * Statistics about the local database.
 *
 * @property totalRows Total number of rows across all tables.
 * @property tables Per-table breakdown.
 * @property sizeBytes Database file size in bytes.
 * @property walSizeBytes WAL file size in bytes (if applicable).
 */
data class DatabaseStats(
    val totalRows: Long,
    val tables: List<TableStats>,
    val sizeBytes: Long,
    val walSizeBytes: Long = 0L,
)

/**
 * Row count for a single database table.
 *
 * @property tableName Database table name.
 * @property rowCount Number of rows in the table.
 */
data class TableStats(
    val tableName: String,
    val rowCount: Long,
)

/**
 * Result of a database vacuum (compaction) operation.
 *
 * @property bytesFreed Number of bytes freed.
 * @property durationMs Time taken in milliseconds.
 * @property success Whether the vacuum completed without errors.
 */
data class PurgeResult(
    val bytesFreed: Long,
    val durationMs: Long,
    val success: Boolean,
)
