package com.zyntasolutions.zyntapos.security.crypto

// CANARY:ZyntaPOS-security-db-key-a1b2c3d4

/**
 * Cross-platform secure storage for the SQLCipher database encryption key.
 *
 * On first launch a cryptographically random 256-bit (32-byte) key is generated via
 * [java.security.SecureRandom] and persisted in the platform's secure key storage.
 * On subsequent launches the same key is retrieved and returned, ensuring the same
 * encrypted database can be opened across restarts.
 *
 * ## Platform implementations
 * - **Android:** Envelope encryption — a random Data-Encryption Key (DEK) is wrapped by a
 *   non-extractable Key-Encryption Key (KEK) stored in the Android Keystore. The wrapped
 *   DEK is stored in SharedPreferences. This matches the pattern used by [DatabaseKeyProvider]
 *   in `:shared:data` but exposes a cleaner, security-module-owned API.
 * - **Desktop (JVM):** The 32-byte key is stored directly in a PKCS12 KeyStore at
 *   `~/.zyntapos/.db_keystore.p12` protected with a machine-fingerprint derived password.
 *
 * ## Usage
 * ```kotlin
 * val key: ByteArray = DatabaseKeyManager().getOrCreateKey()  // 32 bytes
 * // Pass to SQLCipher driver factory for PRAGMA key initialisation
 * ```
 */
expect class DatabaseKeyManager() {

    /**
     * Returns the 32-byte AES-256 database key, generating and persisting it if
     * this is the first call on this device installation.
     *
     * @return 32-byte raw key material ready for SQLCipher `PRAGMA key = "x'<hex>'"`.
     * @throws SecurityException if the key cannot be generated or retrieved from secure storage.
     */
    fun getOrCreateKey(): ByteArray

    /**
     * Returns `true` if a key has already been persisted for this installation.
     * Useful for detecting first-launch vs warm-start scenarios.
     */
    fun hasPersistedKey(): Boolean
}
