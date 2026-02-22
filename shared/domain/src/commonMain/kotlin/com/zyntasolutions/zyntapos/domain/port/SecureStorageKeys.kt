package com.zyntasolutions.zyntapos.domain.port

/**
 * ZyntaPOS — Domain Port: Canonical Secure-Storage Key Registry
 *
 * Single source of truth for all encrypted-preference key strings used across
 * `:shared:data` and `:shared:security`. Placing this object in `:shared:domain`
 * means both modules can import key constants without either module depending on
 * the other.
 *
 * ## Key naming convention
 * All keys follow the dotted-namespace format `<domain>.<field>` to avoid
 * collisions and produce self-documenting debug output.
 *
 * ## Relationship with SecurePreferencesKeys
 * `com.zyntasolutions.zyntapos.security.prefs.SecurePreferencesKeys` now
 * delegates every constant to this object so that existing `:shared:security`
 * call sites (e.g. [JwtManager]) continue to compile unchanged:
 * ```kotlin
 * // SecurePreferencesKeys.kt (in :shared:security)
 * object SecurePreferencesKeys {
 *     val KEY_ACCESS_TOKEN  get() = SecureStorageKeys.KEY_ACCESS_TOKEN
 *     // … etc.
 * }
 * ```
 *
 * @see SecureStoragePort for the storage contract.
 * @since MERGED-F3 / 2026-02-22
 */
object SecureStorageKeys {

    // ── Authentication tokens ──────────────────────────────────────────────

    /** JWT access token. Short-lived (e.g. 15 min). */
    const val KEY_ACCESS_TOKEN: String  = "auth.access_token"

    /** JWT refresh token. Long-lived (e.g. 30 days). */
    const val KEY_REFRESH_TOKEN: String = "auth.refresh_token"

    /**
     * Unix epoch milliseconds at which [KEY_ACCESS_TOKEN] expires.
     * Stored as a decimal string, e.g. `"1735689600000"`.
     */
    const val KEY_TOKEN_EXPIRY: String  = "auth.token_expiry"

    /** UUID of the currently authenticated user. */
    const val KEY_USER_ID: String       = "auth.user_id"

    // ── Device identity ────────────────────────────────────────────────────

    /**
     * Stable device/installation identifier generated on first launch.
     * Used for multi-device session tracking and audit-log attribution.
     */
    const val KEY_DEVICE_ID: String     = "auth.device_id"

    // ── Sync state ─────────────────────────────────────────────────────────

    /**
     * ISO-8601 / epoch-millis string of the last successful server sync.
     * Written and read by [com.zyntasolutions.zyntapos.data.sync.SyncEngine]
     * to implement incremental delta-pull.
     */
    const val KEY_LAST_SYNC_TS: String  = "sync.last_timestamp"
}
