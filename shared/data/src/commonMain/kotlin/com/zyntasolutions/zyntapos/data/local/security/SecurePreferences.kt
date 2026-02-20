package com.zyntasolutions.zyntapos.data.local.security

/**
 * ZentaPOS — SecurePreferences (Security Scaffold)
 *
 * Temporary interface scaffold for Sprint 6 (Step 3.3) repository implementations.
 * Concrete implementations will be provided by :shared:security in Sprint 8 (Step 5.1.3):
 * - Android: EncryptedSharedPreferences
 * - Desktop: AES-256-GCM encrypted Properties file
 *
 * Koin bindings for this interface are registered in the platform data modules
 * (androidDataModule / desktopDataModule).
 */
interface SecurePreferences {

    /**
     * Stores [value] for [key] in encrypted persistent storage.
     *
     * @param key   A non-empty string key (recommended: namespaced, e.g. "auth.access_token").
     * @param value The plaintext value to encrypt and persist.
     */
    fun put(key: String, value: String)

    /**
     * Retrieves the decrypted value for [key], or `null` if not present.
     *
     * @param key The storage key used in [put].
     * @return Decrypted plaintext value, or `null` if the key was never stored.
     */
    fun get(key: String): String?

    /**
     * Removes the stored value for [key].
     *
     * No-op if [key] does not exist.
     */
    fun remove(key: String)

    /**
     * Removes ALL values from secure storage.
     *
     * Use with caution — primarily intended for logout / account reset flows.
     */
    fun clear()

    /**
     * Returns `true` if [key] is currently stored.
     */
    fun contains(key: String): Boolean

    companion object Keys {
        const val ACCESS_TOKEN   = "auth.access_token"
        const val REFRESH_TOKEN  = "auth.refresh_token"
        const val TOKEN_EXPIRY   = "auth.token_expiry"
        const val CURRENT_USER_ID = "auth.user_id"
        const val LAST_SYNC_TS   = "sync.last_timestamp"
    }
}
