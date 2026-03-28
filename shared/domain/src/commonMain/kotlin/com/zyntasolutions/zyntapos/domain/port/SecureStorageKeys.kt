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

    // ── RS256 public key cache ─────────────────────────────────────────────

    /**
     * Standard Base64-encoded DER (SubjectPublicKeyInfo) of the RS256 public key
     * fetched from `GET /.well-known/public-key` after a successful online login.
     *
     * Used by [com.zyntasolutions.zyntapos.security.auth.JwtManager.verifyOfflineRole]
     * as the primary key source, falling back to the BuildConfig-bundled key when absent.
     *
     * Refreshed on every successful online login so key rotation on the server
     * propagates to devices without requiring an app update (ADR-008).
     */
    const val KEY_RS256_PUBLIC_KEY: String = "security.rs256_public_key"

    // ── TLS pin list (Signed Pin List — ADR-011) ───────────────────────────

    /**
     * Comma-separated SPKI SHA-256 pins fetched from `GET /.well-known/tls-pins.json`
     * and verified against the hardcoded Ed25519 signing key.
     *
     * Format: `"sha256/<base64>,sha256/<base64>"` (one or more pins).
     *
     * Updated on every successful startup fetch. Used by [ApiClient] instead of the
     * hardcoded primary pin. Falls back to [API_SPKI_PIN_BACKUP] when absent.
     */
    const val KEY_TLS_PINS: String = "security.tls_pins"

    /**
     * ISO-8601 UTC expiry timestamp of the currently stored pin list, e.g.
     * `"2026-06-01T00:00:00Z"`. Stored alongside [KEY_TLS_PINS] to allow the
     * client to detect and discard expired pin lists before the next refresh.
     */
    const val KEY_TLS_PINS_EXPIRES_AT: String = "security.tls_pins_expires_at"
}
