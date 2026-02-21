package com.zyntasolutions.zyntapos.security.prefs

// ZENTA-FINAL-AUDIT MERGED-F1 | MERGED-D3 (2026-02-21)
/**
 * Canonical secure preferences contract for ZyntaPOS.
 *
 * Platform actuals:
 *  - **Android** — `AndroidEncryptedSharedPreferences` (AES-256-GCM via EncryptedSharedPreferences)
 *  - **Desktop (JVM)** — `DesktopAesSecurePreferences` (AES-256-GCM encrypted Properties file)
 *
 * Decision: ADR-003 (see docs/adr/) — `data.local.security.SecurePreferences` (interface) deleted
 * 2026-02-21 in favour of this canonical `expect class`. All `:shared:data` consumers updated to
 * import `security.prefs.SecurePreferences` directly. Adapter classes
 * (`AndroidEncryptedSecurePreferences`, `DesktopAesSecurePreferences`) removed from `:shared:data`.
 *
 * All values are encrypted at rest. The storage is suitable for:
 * - JWT access and refresh tokens
 * - Session user identifiers
 * - Small application secrets
 *
 * Key strings are defined in [SecurePreferencesKeys] — **always use those constants**
 * instead of raw string literals to prevent key-drift between modules.
 *
 * Implements [TokenStorage] so [com.zyntasolutions.zyntapos.security.auth.JwtManager]
 * can accept it without a platform-specific reference in commonMain.
 *
 * ## Thread safety
 * Both implementations are thread-safe via synchronised read/write blocks.
 *
 * @see SecurePreferencesKeys for canonical key constants.
 */
expect class SecurePreferences() : TokenStorage {

    /** Stores [value] under [key], replacing any existing value. */
    override fun put(key: String, value: String)

    /** Retrieves the decrypted value for [key], or `null` if the key does not exist. */
    override fun get(key: String): String?

    /** Removes the entry for [key]. No-op if the key does not exist. */
    override fun remove(key: String)

    /** Removes all entries from secure storage. */
    fun clear()

    /**
     * Returns `true` if a value is stored under [key].
     *
     * Migrated from `data.local.security.SecurePreferences` (ADR-003) to preserve
     * the contract used by [SecurePreferencesKeyMigration] and auth session checks.
     */
    fun contains(key: String): Boolean

    companion object
}
