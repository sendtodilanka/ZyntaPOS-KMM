package com.zyntasolutions.zyntapos.domain.repository

import com.zyntasolutions.zyntapos.domain.model.LogLevel
import com.zyntasolutions.zyntapos.domain.model.OperationalLog

/**
 * Repository for Tier 2 operational / diagnostic logs.
 *
 * Operational logs complement the append-only [AuditRepository] by capturing
 * low-level runtime events (coroutine lifecycle, network retries, DB queries, etc.).
 * Unlike audit entries, operational logs ARE subject to automated deletion by
 * [com.zyntasolutions.zyntapos.data.job.LogRetentionJob] according to the
 * retention policy defined on [LogLevel].
 *
 * Implemented by `:shared:data` ([OperationalLogRepositoryImpl]) backed by the
 * `operational_logs` SQLDelight table.
 *
 * ### Retention policy (enforced by [LogRetentionJob])
 * - [LogLevel.VERBOSE] / [LogLevel.DEBUG]: 3 days (debug builds only)
 * - [LogLevel.INFO]: 14 days
 * - [LogLevel.WARN]: 30 days
 * - [LogLevel.ERROR] / [LogLevel.FATAL]: 90 days
 */
interface OperationalLogRepository {

    // ── Write ─────────────────────────────────────────────────────────────────

    /**
     * Inserts a fully constructed [OperationalLog] entry.
     */
    suspend fun insert(log: OperationalLog)

    /**
     * Convenience overload: builds and inserts a log entry from its constituent fields.
     * The [createdAt] timestamp defaults to the current system clock.
     */
    suspend fun insert(
        level: LogLevel,
        tag: String,
        message: String,
        stackTrace: String? = null,
        threadName: String? = null,
        sessionId: String? = null,
        metadata: String? = null,
    )

    // ── Read ──────────────────────────────────────────────────────────────────

    /**
     * Returns a page of log entries matching the given filters, ordered by
     * [OperationalLog.createdAt] descending (most recent first).
     *
     * @param level         Optional level filter; null = all levels.
     * @param tag           Optional tag filter; null = all tags.
     * @param fromEpochMillis Start of date range (inclusive). Defaults to [Long.MIN_VALUE].
     * @param toEpochMillis   End of date range (inclusive). Defaults to [Long.MAX_VALUE].
     * @param pageSize        Maximum rows to return. Defaults to 100.
     * @param offset          Rows to skip (for pagination). Defaults to 0.
     */
    suspend fun getPage(
        level: LogLevel? = null,
        tag: String? = null,
        fromEpochMillis: Long = Long.MIN_VALUE,
        toEpochMillis: Long = Long.MAX_VALUE,
        pageSize: Long = 100L,
        offset: Long = 0L,
    ): List<OperationalLog>

    /**
     * Returns the total number of entries in the operational log table.
     */
    suspend fun count(): Long

    // ── Retention (called by LogRetentionJob) ─────────────────────────────────

    /**
     * Deletes all entries of the given [level] created before [olderThanEpochMillis].
     */
    suspend fun purgeByLevelOlderThan(level: LogLevel, olderThanEpochMillis: Long)

    /**
     * Deletes ALL entries (regardless of level) created before [olderThanEpochMillis].
     * Use sparingly; prefer [purgeByLevelOlderThan] for graduated retention.
     */
    suspend fun purgeAllOlderThan(olderThanEpochMillis: Long)

    /**
     * Deletes all [LogLevel.VERBOSE] and [LogLevel.DEBUG] entries older than the given threshold.
     * Corresponds to the `deleteVerboseDebugOlderThan` SQLDelight query.
     */
    suspend fun purgeVerboseDebugOlderThan(olderThanEpochMillis: Long)
}
