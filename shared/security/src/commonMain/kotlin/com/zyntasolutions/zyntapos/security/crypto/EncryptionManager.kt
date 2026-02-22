package com.zyntasolutions.zyntapos.security.crypto

/**
 * Holds the raw output of an AES-256-GCM encryption operation.
 *
 * @property ciphertext Encrypted bytes (payload without tag — JCE returns them separately).
 * @property iv         12-byte initialisation vector (random, unique per encryption call).
 * @property tag        16-byte GCM authentication tag (ensures integrity + authenticity).
 */
data class EncryptedData(
    val ciphertext: ByteArray,
    val iv: ByteArray,
    val tag: ByteArray,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is EncryptedData) return false
        return ciphertext.contentEquals(other.ciphertext) &&
            iv.contentEquals(other.iv) &&
            tag.contentEquals(other.tag)
    }

    override fun hashCode(): Int {
        var result = ciphertext.contentHashCode()
        result = 31 * result + iv.contentHashCode()
        result = 31 * result + tag.contentHashCode()
        return result
    }
}

/**
 * Cross-platform AES-256-GCM encryption/decryption facade.
 *
 * ## Platform implementations
 * - **Android:** AES-256-GCM via [Android Keystore][android.security.keystore.AndroidKeyStoreProvider]
 *   and [javax.crypto.Cipher]. The key is non-extractable, hardware-backed where available.
 * - **Desktop (JVM):** AES-256-GCM via [javax.crypto.Cipher] (JCE provider) with a key loaded
 *   from the PKCS12 KeyStore located at `~/.zentapos/.zyntapos.p12`.
 *
 * Both actuals use a fresh 12-byte random IV per call and a 128-bit GCM tag.
 *
 * ## Thread safety
 * Instances **must not** be shared across threads. Obtain a new instance or synchronise
 * externally. Each [encrypt]/[decrypt] call creates its own [javax.crypto.Cipher] instance.
 *
 * ## Usage
 * ```kotlin
 * val manager = EncryptionManager()
 * val encrypted = manager.encrypt("sensitive-value")
 * val plaintext = manager.decrypt(encrypted)   // == "sensitive-value"
 * ```
 *
 * @param keyAlias  Platform-specific key alias used to retrieve the secret key from the keystore.
 *                  Defaults to the global ZyntaPOS encryption key alias.
 */
expect class EncryptionManager(keyAlias: String = DEFAULT_KEY_ALIAS) {

    /** Encrypts [plaintext] using AES-256-GCM and returns the [EncryptedData] tuple. */
    fun encrypt(plaintext: String): EncryptedData

    /** Decrypts [data] and returns the original plaintext string. */
    fun decrypt(data: EncryptedData): String

    companion object {
        /**
         * Default key alias used for general-purpose symmetric encryption.
         * Concrete value is defined in each platform actual:
         *   Android → "zyntapos_enc_key_v1" (Android Keystore alias)
         *   Desktop → "zyntapos_enc_key_v1" (PKCS12 entry alias)
         *
         * NOTE: `const val` is NOT permitted in `expect class` companions (no initializer
         * can be provided on the expect side). The actuals declare `actual const val`.
         */
        val DEFAULT_KEY_ALIAS: String
    }
}
