package com.zyntasolutions.zyntapos.security.prefs

import com.zyntasolutions.zyntapos.domain.port.SecureStorageKeys

// ZENTA-FINAL-AUDIT MERGED-F1 | MERGED-F3 (2026-02-22)
/**
 * Key registry for [SecurePreferences] — delegates to [SecureStorageKeys] (domain port).
 *
 * ## MERGED-F3 (2026-02-22) — Delegation to domain layer
 * [SecureStorageKeys] (in `:shared:domain:port`) is now the **single source of truth**
 * for all encrypted-preference key strings. This object delegates every constant so that
 * existing `:shared:security` call sites (e.g. [JwtManager]) continue to compile
 * unchanged while the canonical definitions live in the domain layer — accessible to
 * `:shared:data` without a direct `:shared:security` dependency.
 *
 * ## Migration note
 * Keys were unified from two previously divergent sets:
 * - `:shared:data` used dotted keys  (`"auth.access_token"`, …)
 * - `:shared:security` used bare keys (`"access_token"`, …)
 * The dotted-namespace variant was chosen as canonical (see [SecureStorageKeys] for rationale).
 * See [com.zyntasolutions.zyntapos.data.local.db.SecurePreferencesKeyMigration] for the
 * one-time upgrade migration.
 *
 * @see com.zyntasolutions.zyntapos.domain.port.SecureStorageKeys for key definitions.
 * @since Sprint 8 / ZENTA-FINAL-AUDIT MERGED-F1 | MERGED-F3 (2026-02-22)
 */
@Suppress("unused") // Properties are consumed by JwtManager and SecurePreferences actuals
object SecurePreferencesKeys {

    // ── Authentication tokens ──────────────────────────────────────────────────

    /** @see SecureStorageKeys.KEY_ACCESS_TOKEN */
    val KEY_ACCESS_TOKEN: String  get() = SecureStorageKeys.KEY_ACCESS_TOKEN

    /** @see SecureStorageKeys.KEY_REFRESH_TOKEN */
    val KEY_REFRESH_TOKEN: String get() = SecureStorageKeys.KEY_REFRESH_TOKEN

    /** @see SecureStorageKeys.KEY_TOKEN_EXPIRY */
    val KEY_TOKEN_EXPIRY: String  get() = SecureStorageKeys.KEY_TOKEN_EXPIRY

    /** @see SecureStorageKeys.KEY_USER_ID */
    val KEY_USER_ID: String       get() = SecureStorageKeys.KEY_USER_ID

    // ── Device identity ────────────────────────────────────────────────────────

    /** @see SecureStorageKeys.KEY_DEVICE_ID */
    val KEY_DEVICE_ID: String     get() = SecureStorageKeys.KEY_DEVICE_ID

    // ── Sync state ─────────────────────────────────────────────────────────────

    /** @see SecureStorageKeys.KEY_LAST_SYNC_TS */
    val KEY_LAST_SYNC_TS: String  get() = SecureStorageKeys.KEY_LAST_SYNC_TS

    // ── RS256 public key cache ─────────────────────────────────────────────────

    /** @see SecureStorageKeys.KEY_RS256_PUBLIC_KEY */
    val KEY_RS256_PUBLIC_KEY: String get() = SecureStorageKeys.KEY_RS256_PUBLIC_KEY
}
