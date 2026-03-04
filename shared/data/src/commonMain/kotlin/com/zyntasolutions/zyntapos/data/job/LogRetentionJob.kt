package com.zyntasolutions.zyntapos.data.job

import com.zyntasolutions.zyntapos.domain.model.LogLevel
import com.zyntasolutions.zyntapos.domain.repository.OperationalLogRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours

/**
 * Coroutine-based daily retention job for the `operational_logs` table.
 *
 * Enforces the following retention policy (matches `operational_logs.sq` design):
 * - [LogLevel.VERBOSE] / [LogLevel.DEBUG] : deleted after **3 days**
 * - [LogLevel.INFO]                        : deleted after **14 days**
 * - [LogLevel.WARN]                        : deleted after **30 days**
 * - [LogLevel.ERROR] / [LogLevel.FATAL]    : deleted after **90 days**
 *
 * ## Design
 * - [start] launches a long-lived coroutine that runs [runRetention] once per 24 hours.
 * - All database deletes are wrapped with [runCatching]; a failing purge never crashes
 *   the application and will simply retry on the next daily cycle.
 * - [runRetention] is `internal` so it can be called synchronously from tests.
 *
 * ## Registration
 * Bind in `dataModule` and call [start] during application initialisation:
 * ```kotlin
 * single { LogRetentionJob(repository = get(), scope = get(named("IO"))) }
 * ```
 * Then in the application entry point:
 * ```kotlin
 * get<LogRetentionJob>().start()
 * ```
 *
 * @param repository  Repository that writes/deletes `operational_logs` rows.
 * @param scope       Long-lived [CoroutineScope] (application / background IO scope).
 */
class LogRetentionJob(
    private val repository: OperationalLogRepository,
    private val scope: CoroutineScope,
) {

    /**
     * Starts the background retention loop.
     *
     * The first run is performed immediately, then repeats every 24 hours.
     * The loop is cancelled automatically when [scope] is cancelled.
     */
    fun start() {
        scope.launch {
            while (isActive) {
                runRetention()
                delay(24.hours)
            }
        }
    }

    /**
     * Executes one retention pass against the `operational_logs` table.
     *
     * Exposed as `internal` so unit tests can invoke it directly without starting
     * the 24-hour delay loop.
     */
    internal suspend fun runRetention() {
        runCatching {
            val now = Clock.System.now()

            // VERBOSE + DEBUG: keep 3 days
            repository.purgeVerboseDebugOlderThan(
                olderThanEpochMillis = (now - 3.days).toEpochMilliseconds(),
            )

            // INFO: keep 14 days
            repository.purgeByLevelOlderThan(
                level = LogLevel.INFO,
                olderThanEpochMillis = (now - 14.days).toEpochMilliseconds(),
            )

            // WARN: keep 30 days
            repository.purgeByLevelOlderThan(
                level = LogLevel.WARN,
                olderThanEpochMillis = (now - 30.days).toEpochMilliseconds(),
            )

            // ERROR + FATAL: keep 90 days
            repository.purgeByLevelOlderThan(
                level = LogLevel.ERROR,
                olderThanEpochMillis = (now - 90.days).toEpochMilliseconds(),
            )
            repository.purgeByLevelOlderThan(
                level = LogLevel.FATAL,
                olderThanEpochMillis = (now - 90.days).toEpochMilliseconds(),
            )
        }
    }
}
