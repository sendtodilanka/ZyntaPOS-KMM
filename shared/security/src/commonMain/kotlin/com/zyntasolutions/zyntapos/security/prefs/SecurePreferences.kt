package com.zyntasolutions.zyntapos.security.prefs

// ZENTA-FINAL-AUDIT MERGED-F1
/**
 * Cross-platform secure key-value storage for sensitive application preferences.
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
 * ## Platform implementations
 * - **Android:** [androidx.security.crypto.EncryptedSharedPreferences] backed by
 *   AES-256-GCM (values) and AES-256-SIV (keys) via Android Keystore.
 * - **Desktop (JVM):** A `java.util.Properties` file at `~/.zentapos/secure_prefs.enc`
 *   where each value is individually encrypted by `EncryptionManager`.
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

    companion object
}
