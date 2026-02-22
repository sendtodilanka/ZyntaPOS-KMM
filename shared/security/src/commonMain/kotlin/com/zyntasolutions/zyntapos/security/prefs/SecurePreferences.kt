package com.zyntasolutions.zyntapos.security.prefs

import com.zyntasolutions.zyntapos.domain.port.SecureStoragePort

// ZENTA-FINAL-AUDIT MERGED-F1 | MERGED-D3 (2026-02-21) | MERGED-F3 (2026-02-22)
/**
 * Canonical secure preferences contract for ZyntaPOS.
 *
 * Platform actuals:
 *  - **Android** — `AndroidEncryptedSharedPreferences` (AES-256-GCM via EncryptedSharedPreferences)
 *  - **Desktop (JVM)** — `DesktopAesSecurePreferences` (AES-256-GCM encrypted Properties file)
 *
 * Decision: ADR-003 (see docs/adr/) — `data.local.security.SecurePreferences` (interface) deleted
 * 2026-02-21 in favour of this canonical `expect class`. All `:shared:data` consumers updated to
 * import `domain.port.SecureStoragePort` directly (MERGED-F3 — 2026-02-22).
 *
 * ## MERGED-F3 (2026-02-22) — SecureStoragePort
 * `SecurePreferences` now implements [SecureStoragePort] (defined in `:shared:domain`) so that
 * `:shared:data` module can inject the port interface without holding a compile-time dependency
 * on `:shared:security`. Koin binding in [SecurityModule]:
 * ```kotlin
 * single { SecurePreferences() }
 * single<SecureStoragePort> { get<SecurePreferences>() }
 * ```
 *
 * All values are encrypted at rest. The storage is suitable for:
 * - JWT access and refresh tokens
 * - Session user identifiers
 * - Small application secrets
 *
 * Key strings are defined in [SecurePreferencesKeys] (which delegates to
 * [com.zyntasolutions.zyntapos.domain.port.SecureStorageKeys] as of MERGED-F3) —
 * **always use those constants** instead of raw string literals to prevent key-drift.
 *
 * Implements [TokenStorage] so [JwtManager] can accept it without a platform-specific
 * reference in commonMain.
 * Implements [SecureStoragePort] so `:shared:data` can inject it via the domain port.
 *
 * ## Thread safety
 * Both implementations are thread-safe via synchronised read/write blocks.
 *
 * @see SecurePreferencesKeys for canonical key constants.
 * @see com.zyntasolutions.zyntapos.domain.port.SecureStorageKeys for domain-layer key constants.
 */
expect class SecurePreferences() : TokenStorage, SecureStoragePort {

    /** Stores [value] under [key], replacing any existing value. */
    override fun put(key: String, value: String)

    /** Retrieves the decrypted value for [key], or `null` if the key does not exist. */
    override fun get(key: String): String?

    /** Removes the entry for [key]. No-op if the key does not exist. */
    override fun remove(key: String)

    /** Removes all entries from secure storage. */
    override fun clear()

    /**
     * Returns `true` if a value is stored under [key].
     *
     * Migrated from `data.local.security.SecurePreferences` (ADR-003) to preserve
     * the contract used by [SecurePreferencesKeyMigration] and auth session checks.
     */
    override fun contains(key: String): Boolean

    companion object
}
