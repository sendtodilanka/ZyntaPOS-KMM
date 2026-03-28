package com.zyntasolutions.zyntapos.core.config

/**
 * ZyntaPOS global application configuration.
 *
 * Fields fall into two categories:
 *
 * - **`const val`** — compile-time constants (never change across environments).
 *   Used for timeouts, batch sizes, pagination limits, and other fixed tuning values.
 *
 * - **`var`** — runtime-overridable fields set during app startup **before** Koin
 *   initialises. Android entry point: [ZyntaApplication.onCreate]; Desktop: `main()`.
 *   Default values are used in unit tests and are production-safe fallbacks.
 *
 * **Mutation contract:** all `var` fields are assigned exactly once, at app startup,
 * before [seal] is called and before the first Koin module is loaded. After [seal],
 * any write attempt throws [IllegalStateException] immediately.
 *
 * **Call order (both Android and Desktop):**
 * ```
 * // 1. Write config fields
 * AppConfig.BASE_URL = ...
 * AppConfig.IS_DEBUG = ...
 * // 2. Seal — no further writes allowed
 * AppConfig.seal()
 * // 3. Start Koin — modules read sealed values
 * startKoin { ... }
 * ```
 */
object AppConfig {

    @Volatile private var _sealed = false

    /**
     * Seals this configuration object, preventing any further mutation.
     *
     * Must be called after all startup assignments and **before** [startKoin].
     * Any subsequent write to a `var` field throws [IllegalStateException].
     * [seal] itself is idempotent and thread-safe.
     */
    fun seal() {
        _sealed = true
    }

    private fun requireUnsealed(field: String) {
        check(!_sealed) {
            "AppConfig.$field cannot be written after seal() — all writes must occur " +
            "before Koin initialisation. Check ZyntaApplication.onCreate() / main() call order."
        }
    }

    // ── Build ─────────────────────────────────────────────────────────────────

    /**
     * `true` in debug builds, `false` in release builds.
     *
     * - Android: assigned from `BuildConfig.DEBUG` in [ZyntaApplication.onCreate].
     * - Desktop: assigned from the `app.debug` JVM system property in `main()`.
     *
     * Controls:
     * - TLS certificate pinning (disabled when `true` to allow proxy inspection)
     * - Ktor HTTP request/response logging (enabled when `true`)
     */
    var IS_DEBUG: Boolean = false
        set(value) { requireUnsealed("IS_DEBUG"); field = value }

    // ── API ───────────────────────────────────────────────────────────────────

    /**
     * Base URL for the ZyntaPOS backend REST API.
     *
     * - Android: overridden from `BuildConfig.ZYNTA_API_BASE_URL` at startup
     *   (injected by the Secrets Gradle Plugin from `local.properties`).
     * - Desktop: overridden from the `ZYNTA_API_BASE_URL` environment variable.
     * - Default: production endpoint — safe fallback when no override is supplied.
     */
    var BASE_URL: String = "https://api.zyntapos.com"
        set(value) { requireUnsealed("BASE_URL"); field = value }

    /**
     * Base URL for the ZyntaPOS license validation service.
     *
     * - Android: overridden from `BuildConfig.ZYNTA_LICENSE_BASE_URL` at startup.
     * - Desktop: overridden from the `ZYNTA_LICENSE_BASE_URL` environment variable.
     * - Default: production endpoint — safe fallback when no override is supplied.
     */
    var LICENSE_BASE_URL: String = "https://license.zyntapos.com"
        set(value) { requireUnsealed("LICENSE_BASE_URL"); field = value }

    /** API version prefix appended to [BASE_URL] for all versioned endpoint calls. */
    const val API_VERSION: String = "v1"

    /** Fully qualified API root, e.g. `https://api.zyntapos.com/api/v1`. Computed at runtime. */
    val API_ROOT: String get() = "$BASE_URL/api/$API_VERSION"

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

    // ── Loyalty ──────────────────────────────────────────────────────────

    /**
     * How many loyalty points equal 1 currency unit for redemption.
     * Default: 100 points = 1 LKR/USD/etc.
     * Configurable at runtime via SettingsRepository.
     */
    const val LOYALTY_POINTS_PER_CURRENCY_UNIT: Int = 100

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

}
