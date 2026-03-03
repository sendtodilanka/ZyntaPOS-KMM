package com.zyntasolutions.zyntapos.domain.model

/**
 * A single diagnostic / system-health log entry (Tier 2 logging).
 *
 * Operational logs are stored in the `operational_logs` SQLite table and are
 * subject to automated retention (ERROR/FATAL: 90 days; WARN: 30 days;
 * INFO: 14 days; DEBUG/VERBOSE: 3 days). They complement the append-only
 * business audit trail ([AuditEntry]) by capturing low-level runtime events
 * that are useful for debugging and performance monitoring.
 *
 * Unlike [AuditEntry], operational logs MAY be deleted by the retention job.
 * They do not form a hash chain and are not subject to tamper detection.
 *
 * @property id         Auto-increment integer PK (not UUID — optimised for high-frequency writes).
 * @property level      Log severity level.
 * @property tag        Module, class, or subsystem name (e.g., "SyncEngine", "PosViewModel").
 * @property message    Human-readable log message.
 * @property stackTrace Exception stack trace (null for non-error levels).
 * @property threadName Coroutine or thread context at the time of logging.
 * @property sessionId  Current authenticated user session identifier (null if unauthenticated).
 * @property metadata   Optional JSON blob with runtime context (memory, CPU, battery, etc.).
 * @property createdAt  Unix epoch milliseconds — used for indexed date-range queries.
 */
data class OperationalLog(
    val id: Long,
    val level: LogLevel,
    val tag: String,
    val message: String,
    val stackTrace: String?,
    val threadName: String?,
    val sessionId: String?,
    val metadata: String?,
    val createdAt: Long,
)

/**
 * Severity levels for [OperationalLog] entries, ordered from lowest to highest.
 *
 * Retention policy is keyed on [LogLevel]:
 * - [VERBOSE] / [DEBUG]: 3 days (debug builds only; never stored in release builds).
 * - [INFO]: 14 days.
 * - [WARN]: 30 days.
 * - [ERROR] / [FATAL]: 90 days.
 */
enum class LogLevel {
    VERBOSE,
    DEBUG,
    INFO,
    WARN,
    ERROR,
    FATAL,
}
