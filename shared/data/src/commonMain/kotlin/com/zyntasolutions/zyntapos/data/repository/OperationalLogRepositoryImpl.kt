package com.zyntasolutions.zyntapos.data.repository

import com.zyntasolutions.zyntapos.db.ZyntaDatabase
import com.zyntasolutions.zyntapos.domain.model.LogLevel
import com.zyntasolutions.zyntapos.domain.model.OperationalLog
import com.zyntasolutions.zyntapos.domain.repository.OperationalLogRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.time.Clock

/**
 * Concrete implementation of [OperationalLogRepository] backed by the SQLDelight
 * `operational_logs` table defined in `operational_logs.sq`.
 *
 * ## Storage model
 * Operational log entries are stored in `operational_logs` using an INTEGER PRIMARY KEY
 * AUTOINCREMENT — optimised for high-frequency writes from the [KermitSqliteAdapter].
 * Entries ARE subject to deletion by [com.zyntasolutions.zyntapos.data.job.LogRetentionJob].
 *
 * ## Thread-safety
 * All DB operations run on [Dispatchers.IO].
 *
 * @param db  Encrypted [ZyntaDatabase] singleton, provided by Koin.
 */
class OperationalLogRepositoryImpl(
    private val db: ZyntaDatabase,
) : OperationalLogRepository {

    private val q get() = db.operational_logsQueries

    // ── Write ─────────────────────────────────────────────────────────────────

    override suspend fun insert(log: OperationalLog): Unit = withContext(Dispatchers.IO) {
        q.insertLog(
            level = log.level.name,
            tag = log.tag,
            message = log.message,
            stack_trace = log.stackTrace,
            thread_name = log.threadName,
            session_id = log.sessionId,
            metadata = log.metadata,
            created_at = log.createdAt,
        )
    }

    override suspend fun insert(
        level: LogLevel,
        tag: String,
        message: String,
        stackTrace: String?,
        threadName: String?,
        sessionId: String?,
        metadata: String?,
    ): Unit = withContext(Dispatchers.IO) {
        q.insertLog(
            level = level.name,
            tag = tag,
            message = message,
            stack_trace = stackTrace,
            thread_name = threadName,
            session_id = sessionId,
            metadata = metadata,
            created_at = Clock.System.now().toEpochMilliseconds(),
        )
    }

    // ── Read ──────────────────────────────────────────────────────────────────

    override suspend fun getPage(
        level: LogLevel?,
        tag: String?,
        fromEpochMillis: Long,
        toEpochMillis: Long,
        pageSize: Long,
        offset: Long,
    ): List<OperationalLog> = withContext(Dispatchers.IO) {
        q.getLogsPage(
            level = level?.name,
            tag = tag,
            from = fromEpochMillis,
            to = toEpochMillis,
            pageSize = pageSize,
            offset = offset,
        ).executeAsList().map { row -> row.toDomain() }
    }

    override suspend fun count(): Long = withContext(Dispatchers.IO) {
        q.countLogs().executeAsOne()
    }

    // ── Retention ─────────────────────────────────────────────────────────────

    override suspend fun purgeByLevelOlderThan(
        level: LogLevel,
        olderThanEpochMillis: Long,
    ): Unit = withContext(Dispatchers.IO) {
        q.deleteLevelOlderThan(level = level.name, created_at = olderThanEpochMillis)
    }

    override suspend fun purgeAllOlderThan(olderThanEpochMillis: Long): Unit = withContext(Dispatchers.IO) {
        q.deleteLogsOlderThan(created_at = olderThanEpochMillis)
    }

    override suspend fun purgeVerboseDebugOlderThan(olderThanEpochMillis: Long): Unit = withContext(Dispatchers.IO) {
        q.deleteVerboseDebugOlderThan(created_at = olderThanEpochMillis)
    }

    // ── Mapper ────────────────────────────────────────────────────────────────

    private fun com.zyntasolutions.zyntapos.db.Operational_logs.toDomain(): OperationalLog =
        OperationalLog(
            id = id,
            level = runCatching { LogLevel.valueOf(level) }.getOrDefault(LogLevel.INFO),
            tag = tag,
            message = message,
            stackTrace = stack_trace,
            threadName = thread_name,
            sessionId = session_id,
            metadata = metadata,
            createdAt = created_at,
        )
}
