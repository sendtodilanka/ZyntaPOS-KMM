package com.zyntasolutions.zyntapos.data.logging

import co.touchlab.kermit.LogWriter
import co.touchlab.kermit.Severity
import com.zyntasolutions.zyntapos.domain.model.LogLevel
import com.zyntasolutions.zyntapos.domain.repository.OperationalLogRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Kermit [LogWriter] bridge that persists log entries to the SQLite
 * `operational_logs` table via [OperationalLogRepository].
 *
 * ## Design
 * - [log] is synchronous (Kermit API requirement), so the actual DB write is
 *   dispatched as a **fire-and-forget** coroutine on [scope]. This means log
 *   writes never block the calling thread.
 * - All exceptions from [OperationalLogRepository.insert] are swallowed via
 *   [runCatching] — a failing logger must never crash the application.
 * - [sessionIdProvider] is a lambda resolved at call-time so it always reflects
 *   the current authenticated session (avoids capturing a stale session ID at
 *   construction time).
 *
 * ## Registration
 * Add this writer to the Kermit logger in the application entry point:
 * ```kotlin
 * Logger.setLogWriters(CommonWriter(), get<KermitSqliteAdapter>())
 * ```
 * or for DEBUG builds only (VERBOSE/DEBUG entries skipped in release):
 * ```kotlin
 * if (BuildConfig.DEBUG) Logger.addLogWriter(get<KermitSqliteAdapter>())
 * ```
 *
 * @param repository      Repository that writes to `operational_logs`.
 * @param scope           CoroutineScope for fire-and-forget DB writes.
 * @param sessionIdProvider Lambda returning the current session ID (nullable when unauthenticated).
 */
class KermitSqliteAdapter(
    private val repository: OperationalLogRepository,
    private val scope: CoroutineScope,
    private val sessionIdProvider: () -> String? = { null },
) : LogWriter() {

    override fun log(severity: Severity, message: String, tag: String, throwable: Throwable?) {
        val level = severity.toLogLevel()
        val stackTrace = throwable?.stackTraceToString()
        val sessionId = runCatching { sessionIdProvider() }.getOrNull()

        scope.launch {
            runCatching {
                repository.insert(
                    level = level,
                    tag = tag,
                    message = message,
                    stackTrace = stackTrace,
                    threadName = null,
                    sessionId = sessionId,
                    metadata = null,
                )
            }
        }
    }

    private fun Severity.toLogLevel(): LogLevel = when (this) {
        Severity.Verbose -> LogLevel.VERBOSE
        Severity.Debug   -> LogLevel.DEBUG
        Severity.Info    -> LogLevel.INFO
        Severity.Warn    -> LogLevel.WARN
        Severity.Error   -> LogLevel.ERROR
        Severity.Assert  -> LogLevel.FATAL
    }
}
