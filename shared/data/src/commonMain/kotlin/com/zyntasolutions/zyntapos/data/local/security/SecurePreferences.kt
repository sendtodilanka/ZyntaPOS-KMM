package com.zyntasolutions.zyntapos.data.local.security

import com.zyntasolutions.zyntapos.security.prefs.SecurePreferencesKeys

// ZENTA-FINAL-AUDIT MERGED-F1
/**
 * ZentaPOS — SecurePreferences (Data layer interface scaffold)
 *
 * Temporary interface scaffold for Sprint 6 (Step 3.3) repository implementations.
 * Concrete implementations are provided by :shared:security (Sprint 8, Step 5.1.3):
 * - Android: EncryptedSharedPreferences
 * - Desktop: AES-256-GCM encrypted Properties file
 *
 * Key strings delegate to [SecurePreferencesKeys] — the single source of truth for
 * all secure-preference keys used across ZentaPOS modules.  Do **not** define raw
 * key literals here; always reference [SecurePreferencesKeys].
 *
 * Koin bindings for this interface are registered in the platform data modules
 * (androidDataModule / desktopDataModule).
 */
interface SecurePreferences {

    /**
     * Stores [value] for [key] in encrypted persistent storage.
     *
     * @param key   A non-empty string key. Use constants from [SecurePreferencesKeys].
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

    /**
     * Key aliases that delegate to the canonical [SecurePreferencesKeys] constants.
     *
     * These aliases exist only for backwards-compatibility within the :shared:data module.
     * New code should reference [SecurePreferencesKeys] directly.
     */
    companion object Keys {
        // ZENTA-FINAL-AUDIT MERGED-F1 — all raw literals removed; delegate to canonical keys
        const val ACCESS_TOKEN    = SecurePreferencesKeys.KEY_ACCESS_TOKEN
        const val REFRESH_TOKEN   = SecurePreferencesKeys.KEY_REFRESH_TOKEN
        const val TOKEN_EXPIRY    = SecurePreferencesKeys.KEY_TOKEN_EXPIRY
        const val CURRENT_USER_ID = SecurePreferencesKeys.KEY_USER_ID
        const val LAST_SYNC_TS    = SecurePreferencesKeys.KEY_LAST_SYNC_TS
    }
}
