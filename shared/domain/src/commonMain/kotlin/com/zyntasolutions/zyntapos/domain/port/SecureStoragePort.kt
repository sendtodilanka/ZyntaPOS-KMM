package com.zyntasolutions.zyntapos.domain.port

/**
 * ZyntaPOS — Domain Port: Encrypted Secure Storage
 *
 * Defines the contract that `:shared:data` uses to read/write encrypted
 * credentials and session tokens without holding a compile-time dependency
 * on `:shared:security`'s `expect class SecurePreferences`.
 *
 * ## Ports & Adapters (Hexagonal Architecture)
 * ```
 * :shared:domain (this interface)
 *       ▲
 *       │ implements
 * :shared:security
 *   SecurePreferences (expect/actual)
 *     Android → EncryptedSharedPreferences (AES-256-GCM, Android Keystore)
 *     Desktop → AES-256-GCM encrypted Properties file (JCE PKCS12)
 * ```
 *
 * ## Why this port exists (MERGED-F3 — 2026-02-22)
 * MERGED-F3 removed the direct `:shared:data → :shared:security` compile dependency
 * to enforce Clean Architecture module boundaries. As a result all five `:shared:data`
 * source files that previously imported `SecurePreferences` directly now import this
 * interface instead, keeping the data layer decoupled from the security platform detail.
 *
 * ## Methods
 * Mirrors the full `SecurePreferences` contract so that no data-layer call site
 * requires a cast or instanceof check:
 * - [put] / [get] / [remove] — basic CRUD (same signatures as [TokenStorage])
 * - [clear] — wipe all entries on logout
 * - [contains] — key-existence check (used by [SecurePreferencesKeyMigration])
 *
 * @see com.zyntasolutions.zyntapos.domain.port.SecureStorageKeys for canonical key constants.
 * @see com.zyntasolutions.zyntapos.security.prefs.SecurePreferences for platform implementation.
 */
interface SecureStoragePort {

    /** Stores [value] under [key], replacing any existing entry. */
    fun put(key: String, value: String)

    /** Returns the decrypted value for [key], or `null` if absent. */
    fun get(key: String): String?

    /** Removes the entry for [key]. No-op if the key does not exist. */
    fun remove(key: String)

    /** Removes all entries from secure storage (called on logout). */
    fun clear()

    /**
     * Returns `true` if an entry exists for [key].
     * Used by [com.zyntasolutions.zyntapos.data.local.db.SecurePreferencesKeyMigration]
     * to avoid overwriting a canonical key that is already populated.
     */
    fun contains(key: String): Boolean
}
