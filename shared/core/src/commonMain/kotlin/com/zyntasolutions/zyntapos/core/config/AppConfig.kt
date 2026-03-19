package com.zyntasolutions.zyntapos.core.config

/**
 * ZyntaPOS global application constants.
 *
 * All values are compile-time constants (`const val`) so they can be used in
 * annotation arguments and inlined by the compiler.
 *
 * Override individual values at runtime via [SettingsRepository] (Sprint 6).
 * This object provides safe defaults used before settings are loaded.
 */
object AppConfig {

    // ── Build ─────────────────────────────────────────────────────────────────

    /**
     * Set to `true` during application startup in debug builds.
     * Android: read from `BuildConfig.DEBUG` in the androidApp module.
     * Desktop: set via a Gradle `buildkonfig` flag.
     * Controls verbose Ktor logging and other debug-only behavior.
     */
    var IS_DEBUG: Boolean = false

    // ── API ───────────────────────────────────────────────────────────────────

    /**
     * Base URL for the ZyntaPOS backend API.
     * Override via `local.properties` / BuildKonfig in production builds.
     */
    const val BASE_URL: String = "https://api.zyntapos.com"

    /**
     * Base URL for the ZyntaPOS license service.
     * Override via `local.properties` / BuildKonfig in production builds.
     */
    const val LICENSE_BASE_URL: String = "https://license.zyntapos.com"

    /** API version prefix appended to [BASE_URL] for all endpoint calls. */
    const val API_VERSION: String = "v1"

    /** Fully qualified API root: `https://api.zyntapos.com/api/v1` */
    const val API_ROOT: String = "$BASE_URL/api/$API_VERSION"

    // ── Database ──────────────────────────────────────────────────────────────

    /** SQLite database file name (without extension). */
    const val DB_NAME: String = "zyntapos.db"

    /**
     * Current schema version.
     * Increment on every SQLDelight schema change; triggers [DatabaseMigrations].
     */
    const val DB_VERSION: Int = 1

    // ── Sync ──────────────────────────────────────────────────────────────────

    /**
     * Interval between background sync cycles in milliseconds.
     * Default: 30 seconds.
     */
    const val SYNC_INTERVAL_MS: Long = 30_000L

    /** Maximum number of sync operations per push batch. */
    const val SYNC_BATCH_SIZE: Int = 50

    /** Maximum retry attempts for a single failed sync operation before it is marked FAILED. */
    const val SYNC_MAX_RETRIES: Int = 5

    /** Number of successful sync cycles between queue maintenance runs. */
    const val SYNC_MAINTENANCE_INTERVAL_CYCLES: Int = 10

    /** Days to retain SYNCED operations before pruning. */
    const val SYNC_QUEUE_RETENTION_DAYS: Int = 7

    /** Days to retain permanently FAILED operations before pruning. */
    const val SYNC_FAILED_RETENTION_DAYS: Int = 30

    // ── Session & Security ────────────────────────────────────────────────────

    /**
     * Inactivity timeout before the PIN lock screen is shown, in milliseconds.
     * Default: 5 minutes.
     */
    const val SESSION_TIMEOUT_MS: Long = 5 * 60 * 1_000L

    /** JWT access token validity window (used for local expiry pre-check). */
    const val TOKEN_VALIDITY_MS: Long = 30 * 60 * 1_000L

    // ── Networking ────────────────────────────────────────────────────────────

    /** HTTP connection timeout in milliseconds. */
    const val HTTP_CONNECT_TIMEOUT_MS: Long = 10_000L

    /** HTTP request timeout in milliseconds. */
    const val HTTP_REQUEST_TIMEOUT_MS: Long = 30_000L

    /** HTTP socket timeout in milliseconds. */
    const val HTTP_SOCKET_TIMEOUT_MS: Long = 30_000L

    /** Number of automatic HTTP retries on transient failures. */
    const val HTTP_MAX_RETRIES: Int = 3

    // ── Pagination ────────────────────────────────────────────────────────────

    /** Default page size for paginated queries (products, orders, customers). */
    const val PAGE_SIZE_DEFAULT: Int = 50

    /** Maximum number of items returned in a single search query. */
    const val SEARCH_RESULT_LIMIT: Int = 100

    // ── POS Business Rules ────────────────────────────────────────────────────

    /**
     * Maximum discount percentage that a Cashier role can apply without manager override.
     * Configurable via SettingsRepository at runtime.
     */
    const val MAX_CASHIER_DISCOUNT_PERCENT: Double = 20.0

    /** Minimum PIN length (digits). */
    const val PIN_MIN_LENGTH: Int = 4

    /** Maximum PIN length (digits). */
    const val PIN_MAX_LENGTH: Int = 6

    // ── Printing ──────────────────────────────────────────────────────────────

    /** Characters per line for 58 mm thermal paper. */
    const val RECEIPT_CHARS_58MM: Int = 32

    /** Characters per line for 80 mm thermal paper. */
    const val RECEIPT_CHARS_80MM: Int = 48

    // ── Currency ──────────────────────────────────────────────────────────────

    /** ISO 4217 default currency code used before settings are loaded. */
    const val DEFAULT_CURRENCY_CODE: String = "LKR"

    /** Default number of decimal places for currency display. */
    const val CURRENCY_DECIMAL_PLACES: Int = 2

    // ── IRD e-Invoice (Sri Lanka) ─────────────────────────────────────────────

    /**
     * IRD (Inland Revenue Department) API endpoint for e-invoice submission.
     * Override at runtime from BuildConfig.ZYNTA_IRD_API_ENDPOINT or SettingsRepository.
     */
    var IRD_API_ENDPOINT: String = ""

    /**
     * Absolute path to the IRD client certificate (.p12 / PKCS12) used for mTLS.
     * Override at runtime from BuildConfig.ZYNTA_IRD_CLIENT_CERTIFICATE_PATH.
     */
    var IRD_CLIENT_CERT_PATH: String = ""

    /**
     * Password for the IRD client certificate.
     * Override at runtime from BuildConfig / secure storage — never hard-code.
     */
    var IRD_CLIENT_CERT_PASSWORD: String = ""
}
