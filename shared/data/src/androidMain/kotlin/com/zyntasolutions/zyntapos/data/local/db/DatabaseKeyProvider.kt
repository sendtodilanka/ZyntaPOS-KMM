package com.zyntasolutions.zyntapos.data.local.db

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import com.zyntasolutions.zyntapos.core.logger.ZyntaLogger
import java.security.KeyStore
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * ZyntaPOS — DatabaseKeyProvider (androidMain actual)
 *
 * Manages the SQLCipher AES-256 database encryption key using the Android Keystore System
 * via the **envelope-encryption** pattern.
 *
 * ## Why Envelope Encryption?
 * Android Keystore is intentionally designed as a **non-extractable** key store —
 * `SecretKey.encoded` returns `null` for hardware-backed keys, making it impossible
 * to export the raw key bytes for direct use as a SQLCipher passphrase.
 *
 * Instead, we use a two-layer scheme:
 *
 * ```
 * ┌──────────────────────────────────────────────────────────┐
 * │  Layer 1 — Data Encryption Key (DEK)                     │
 * │  • 32 random bytes generated via SecureRandom            │
 * │  • Used directly as SQLCipher AES-256 raw passphrase     │
 * └──────────────────────────────────────────────────────────┘
 *            wrapped by ↓
 * ┌──────────────────────────────────────────────────────────┐
 * │  Layer 2 — Key Encryption Key (KEK)                      │
 * │  • AES-256-GCM key inside Android Keystore (TEE-backed)  │
 * │  • Encrypts the DEK → stores ciphertext in SharedPrefs   │
 * │  • Decrypts the DEK at every app launch                  │
 * └──────────────────────────────────────────────────────────┘
 * ```
 *
 * The raw DEK bytes are NEVER written to disk in plaintext; the TEE protects the KEK.
 *
 * ## Storage Layout
 * | Data | Location |
 * |------|---------|
 * | Wrapped DEK (IV + ciphertext) | `SharedPreferences("zyntapos_db_prefs")` |
 * | KEK | Android Keystore (`ZyntaPOS_KEK_v1` alias) |
 *
 * ## Key Spec
 * | Property | Value |
 * |----------|-------|
 * | KEK algorithm | AES-256 / GCM / NoPadding |
 * | GCM tag length | 128-bit |
 * | IV length | 12 bytes (GCM standard) |
 * | DEK size | 32 bytes (256-bit) |
 *
 * ## Koin Injection
 * ```kotlin
 * single { DatabaseKeyProvider(androidContext()) }
 * ```
 *
 * @param context Application [Context] required to access [SharedPreferences]
 */
actual class DatabaseKeyProvider(private val context: Context) {

    private val keyStore: KeyStore by lazy {
        KeyStore.getInstance(ANDROID_KEYSTORE).also { it.load(null) }
    }

    private val prefs by lazy {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    /**
     * Returns the 32-byte raw DEK, generating and wrapping it on first call.
     */
    actual fun getOrCreateKey(): ByteArray {
        // Ensure the KEK exists in the Keystore
        if (!keyStore.containsAlias(KEK_ALIAS)) {
            generateKek()
        }

        // Check for a persisted wrapped DEK
        val wrappedDek = prefs.getString(PREF_WRAPPED_DEK, null)
        return if (wrappedDek == null) {
            createAndPersistDek()
        } else {
            unwrapDek(wrappedDek)
        }
    }

    actual fun hasPersistedKey(): Boolean =
        keyStore.containsAlias(KEK_ALIAS) && prefs.contains(PREF_WRAPPED_DEK)

    // ─────────────────────────────────────────────────────────────────
    // DEK generation and envelope encryption
    // ─────────────────────────────────────────────────────────────────

    /**
     * Generates a fresh 32-byte DEK, wraps it with the Keystore KEK (AES/GCM),
     * persists `IV|ciphertext` as Base64 in SharedPreferences, then returns the raw DEK.
     */
    private fun createAndPersistDek(): ByteArray {
        ZyntaLogger.i(TAG, "First launch — generating and wrapping a new 256-bit DEK.")
        val rawDek = ByteArray(DEK_SIZE_BYTES).also { SecureRandom().nextBytes(it) }

        val kek = getKekFromKeystore()
        val cipher = Cipher.getInstance(KEK_CIPHER_TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, kek)
        val iv         = cipher.iv                           // 12 bytes (GCM random IV)
        val ciphertext = cipher.doFinal(rawDek)             // 32 bytes DEK + 16 bytes GCM tag

        // Store as "IV_base64:CIPHERTEXT_base64"
        val encoded = "${iv.toBase64()}:${ciphertext.toBase64()}"
        prefs.edit().putString(PREF_WRAPPED_DEK, encoded).apply()

        ZyntaLogger.i(TAG, "DEK generated and stored (wrapped, ${ciphertext.size} bytes).")
        return rawDek
    }

    /**
     * Unwraps the stored ciphertext using the Keystore KEK and returns the raw 32-byte DEK.
     */
    private fun unwrapDek(wrappedDek: String): ByteArray {
        val parts = wrappedDek.split(":")
        check(parts.size == 2) { "Malformed wrapped DEK in SharedPreferences." }

        val iv         = parts[0].fromBase64()
        val ciphertext = parts[1].fromBase64()

        val kek = getKekFromKeystore()
        val cipher = Cipher.getInstance(KEK_CIPHER_TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, kek, GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv))
        val rawDek = cipher.doFinal(ciphertext)

        check(rawDek.size == DEK_SIZE_BYTES) {
            "Decrypted DEK is ${rawDek.size} bytes — expected $DEK_SIZE_BYTES."
        }
        ZyntaLogger.d(TAG, "DEK unwrapped successfully from Keystore envelope.")
        return rawDek
    }

    // ─────────────────────────────────────────────────────────────────
    // Keystore KEK lifecycle
    // ─────────────────────────────────────────────────────────────────

    private fun generateKek() {
        ZyntaLogger.i(TAG, "Generating AES-256-GCM KEK in Android Keystore (alias=$KEK_ALIAS).")
        val keyGenerator = KeyGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_AES,
            ANDROID_KEYSTORE,
        )
        val spec = KeyGenParameterSpec.Builder(
            KEK_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
        )
            .setKeySize(KEK_SIZE_BITS)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setUserAuthenticationRequired(false)         // Headless POS device support
            .setInvalidatedByBiometricEnrollment(false)   // Stable across biometric changes
            .build()

        keyGenerator.init(spec)
        keyGenerator.generateKey()
        ZyntaLogger.i(TAG, "KEK generated in Android Keystore.")
    }

    private fun getKekFromKeystore(): SecretKey {
        val entry = keyStore.getEntry(KEK_ALIAS, null)
            ?: error("KEK alias '$KEK_ALIAS' not found in Android Keystore.")
        return (entry as KeyStore.SecretKeyEntry).secretKey
    }

    // ─────────────────────────────────────────────────────────────────
    // Base64 helpers (avoid additional dependency)
    // ─────────────────────────────────────────────────────────────────

    private fun ByteArray.toBase64(): String =
        android.util.Base64.encodeToString(this, android.util.Base64.NO_WRAP)

    private fun String.fromBase64(): ByteArray =
        android.util.Base64.decode(this, android.util.Base64.NO_WRAP)

    // ─────────────────────────────────────────────────────────────────
    // Constants
    // ─────────────────────────────────────────────────────────────────

    private companion object {
        const val TAG                     = "DatabaseKeyProvider"
        const val ANDROID_KEYSTORE        = "AndroidKeyStore"
        const val KEK_ALIAS               = "ZyntaPOS_KEK_v1"          // Key Encryption Key alias
        const val KEK_SIZE_BITS           = 256
        const val KEK_CIPHER_TRANSFORMATION = "AES/GCM/NoPadding"
        const val GCM_TAG_LENGTH_BITS     = 128
        const val DEK_SIZE_BYTES          = 32                          // 256-bit Data Encryption Key
        const val PREFS_NAME              = "zyntapos_db_prefs"
        const val PREF_WRAPPED_DEK        = "wrapped_dek_v1"
    }
}
