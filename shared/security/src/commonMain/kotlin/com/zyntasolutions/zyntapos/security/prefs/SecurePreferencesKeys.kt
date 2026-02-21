package com.zyntasolutions.zyntapos.security.prefs

// ZENTA-FINAL-AUDIT MERGED-F1
/**
 * Canonical key registry for [SecurePreferences] across all ZyntaPOS modules.
 *
 * This is the **single source of truth** for all encrypted-preference key strings.
 * Every key follows the dotted-namespace convention `<domain>.<field>` to avoid
 * collisions and to make log/debug output self-documenting.
 *
 * ## Migration note
 * Keys were unified from two previously divergent sets:
 * - `:shared:data` used dotted keys  (`"auth.access_token"`, …)
 * - `:shared:security` used bare keys (`"access_token"`, …)
 *
 * The dotted-namespace variant was chosen as canonical because it is namespaced,
 * human-readable in debug logs, and already used by the `:shared:data` sprint code.
 * See [com.zyntasolutions.zyntapos.data.local.db.SecurePreferencesKeyMigration] for the
 * one-time upgrade migration that rewrites any old bare-key entries.
 *
 * @since Sprint 8 / ZENTA-FINAL-AUDIT MERGED-F1
 */
object SecurePreferencesKeys {

    // ── Authentication tokens ──────────────────────────────────────────────────

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

    // ── Device identity ────────────────────────────────────────────────────────

    /**
     * Stable device/installation identifier generated on first launch.
     * Used for multi-device session tracking and audit-log attribution.
     */
    const val KEY_DEVICE_ID: String     = "auth.device_id"

    // ── Sync state ─────────────────────────────────────────────────────────────

    /**
     * ISO-8601 timestamp of the last successful sync with the remote server.
     * The [com.zyntasolutions.zyntapos.data.sync.SyncEngine] reads and writes
     * this key to implement incremental delta-pull.
     */
    const val KEY_LAST_SYNC_TS: String  = "sync.last_timestamp"
}
