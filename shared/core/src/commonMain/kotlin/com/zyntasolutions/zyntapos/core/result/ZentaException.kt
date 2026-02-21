package com.zyntasolutions.zyntapos.core.result

/**
 * Root of the ZyntaPOS exception hierarchy.
 *
 * All domain-level errors extend [ZentaException] so callers can handle them uniformly
 * via `Result.Error(exception)` without catching raw [Throwable]s.
 *
 * @param message Human-readable description of the error.
 * @param cause   Optional underlying throwable (logged but not surfaced to UI).
 */
sealed class ZentaException(
    override val message: String,
    override val cause: Throwable? = null,
) : Exception(message, cause)

// ── Network ───────────────────────────────────────────────────────────────────

/**
 * Thrown when a network call fails.
 *
 * @param statusCode HTTP status code if available (null for connectivity failures).
 */
class NetworkException(
    message: String,
    val statusCode: Int? = null,
    cause: Throwable? = null,
) : ZentaException(message, cause) {
    val isClientError: Boolean get() = statusCode != null && statusCode in 400..499
    val isServerError: Boolean get() = statusCode != null && statusCode in 500..599
}

// ── Database ──────────────────────────────────────────────────────────────────

/**
 * Thrown when a local SQLite / SQLDelight operation fails.
 *
 * @param operation A short label of the failing DB operation (e.g., "INSERT products").
 */
class DatabaseException(
    message: String,
    val operation: String = "",
    cause: Throwable? = null,
) : ZentaException(message, cause)

// ── Authentication ────────────────────────────────────────────────────────────

/**
 * Thrown when login, token refresh, or session validation fails.
 *
 * @param reason Categorises the auth failure for programmatic handling.
 */
class AuthException(
    message: String,
    val reason: AuthFailureReason = AuthFailureReason.INVALID_CREDENTIALS,
    cause: Throwable? = null,
) : ZentaException(message, cause)

/** Describes why an [AuthException] was raised. */
enum class AuthFailureReason {
    /** Supplied email/password or PIN was incorrect. */
    INVALID_CREDENTIALS,
    /** JWT or session has expired; a refresh or re-login is required. */
    SESSION_EXPIRED,
    /** Account has been deactivated by an administrator. */
    ACCOUNT_DISABLED,
    /** Network unavailable and no cached session exists for offline auth. */
    OFFLINE_NO_CACHE,
    /** Too many failed attempts; account temporarily locked. */
    TOO_MANY_ATTEMPTS,
}

// ── Validation ────────────────────────────────────────────────────────────────

/**
 * Thrown when domain validation rules are violated.
 *
 * @param field    The name of the invalid field (used to map errors back to form fields).
 * @param rule     Short code identifying the violated rule (e.g., "REQUIRED", "BARCODE_DUPLICATE").
 */
class ValidationException(
    message: String,
    val field: String = "",
    val rule: String = "",
    cause: Throwable? = null,
) : ZentaException(message, cause)

// ── HAL (Hardware Abstraction Layer) ─────────────────────────────────────────

/**
 * Thrown when a POS peripheral (printer, scanner, cash drawer) interaction fails.
 *
 * @param device Short label of the failing device (e.g., "thermal_printer", "barcode_scanner").
 */
class HalException(
    message: String,
    val device: String = "",
    cause: Throwable? = null,
) : ZentaException(message, cause)

// ── Sync ──────────────────────────────────────────────────────────────────────

/**
 * Thrown when the offline-first sync engine encounters an unrecoverable error.
 *
 * @param operationId ID of the sync queue entry that failed (if applicable).
 * @param retryCount  How many times the operation has already been retried.
 */
class SyncException(
    message: String,
    val operationId: String? = null,
    val retryCount: Int = 0,
    cause: Throwable? = null,
) : ZentaException(message, cause) {
    val isMaxRetriesReached: Boolean get() = retryCount >= MAX_RETRIES

    companion object {
        const val MAX_RETRIES = 5
    }
}
