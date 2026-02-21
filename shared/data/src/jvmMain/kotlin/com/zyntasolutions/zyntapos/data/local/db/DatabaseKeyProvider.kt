package com.zyntasolutions.zyntapos.data.local.db

import com.zyntasolutions.zyntapos.core.logger.ZyntaLogger
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.security.KeyStore
import java.security.SecureRandom
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey

/**
 * ZyntaPOS — DatabaseKeyProvider (jvmMain actual)
 *
 * Manages the SQLCipher AES-256 database encryption key using the JCE PKCS12 KeyStore,
 * persisted as an encrypted `.p12` file in the application's private data directory.
 *
 * ## Storage Strategy
 * | Layer | Technology | Detail |
 * |-------|-----------|--------|
 * | Key Store | JCE PKCS12 | `~/.zyntapos/.db_keystore.p12` |
 * | Store Password | OS Credential Manager fallback | Derived from machine fingerprint |
 * | Key Type | AES-256 `SecretKey` | Generated via `javax.crypto.KeyGenerator` |
 *
 * ## Machine Fingerprint Password Derivation
 * The PKCS12 password is derived from a combination of machine-unique properties
 * (host name + MAC address + OS info) hashed via SHA-256. This is a defence-in-depth
 * measure — the primary security guarantee is the AES-256-CBC SQLCipher encryption of
 * the database itself, not the keystore password.
 *
 * In a future iteration (Sprint 8 — `:shared:security`), [DatabaseKeyProvider] will
 * delegate to `DatabaseKeyManager` from [securityModule] for full OS credential
 * manager integration (Windows DPAPI / macOS Keychain / libsecret on Linux).
 *
 * ## Koin Injection
 * ```kotlin
 * single { DatabaseKeyProvider(appDataDirectory()) }
 * ```
 *
 * @param appDataDir Path to the application's private data directory.
 *   The `.p12` keystore file is stored here. Directory is created if absent.
 */
actual class DatabaseKeyProvider(private val appDataDir: String) {

    private val keystoreFile: File get() = File(appDataDir, KEYSTORE_FILE_NAME)

    actual fun getOrCreateKey(): ByteArray {
        val keyStore = loadOrCreateKeyStore()

        if (!keyStore.containsAlias(KEY_ALIAS)) {
            ZyntaLogger.i(TAG, "No existing key found — generating new AES-256 DB key.")
            val key = generateAesKey()
            keyStore.setKeyEntry(
                KEY_ALIAS,
                key,
                keystorePassword(),
                null,
            )
            saveKeyStore(keyStore)
            ZyntaLogger.i(TAG, "New AES-256 key generated and stored in PKCS12 keystore.")
        }

        val entry = keyStore.getEntry(KEY_ALIAS, KeyStore.PasswordProtection(keystorePassword()))
        return (entry as KeyStore.SecretKeyEntry).secretKey.encoded
    }

    actual fun hasPersistedKey(): Boolean {
        if (!keystoreFile.exists()) return false
        return runCatching {
            loadOrCreateKeyStore().containsAlias(KEY_ALIAS)
        }.getOrDefault(false)
    }

    // ─────────────────────────────────────────────────────────────────
    // Internal helpers
    // ─────────────────────────────────────────────────────────────────

    private fun loadOrCreateKeyStore(): KeyStore {
        val ks = KeyStore.getInstance(KEYSTORE_TYPE)
        if (keystoreFile.exists()) {
            FileInputStream(keystoreFile).use { stream ->
                ks.load(stream, keystorePassword())
            }
        } else {
            File(appDataDir).mkdirs()
            ks.load(null, keystorePassword())
        }
        return ks
    }

    private fun saveKeyStore(keyStore: KeyStore) {
        FileOutputStream(keystoreFile).use { stream ->
            keyStore.store(stream, keystorePassword())
        }
    }

    private fun generateAesKey(): SecretKey {
        val generator = KeyGenerator.getInstance("AES")
        generator.init(KEY_SIZE_BITS, SecureRandom())
        return generator.generateKey()
    }

    /**
     * Derives a machine-specific password for the PKCS12 keystore from environment
     * properties. This is a defence-in-depth measure, not the primary encryption boundary.
     *
     * TODO (Sprint 8): Replace with OS Credential Manager via `DatabaseKeyManager`
     * to leverage Windows DPAPI / macOS Keychain / libsecret on Linux.
     */
    private fun keystorePassword(): CharArray {
        val fingerprint = buildString {
            append(System.getProperty("user.name") ?: "zyntapos")
            append("|")
            append(System.getProperty("os.name") ?: "desktop")
            append("|")
            append(System.getProperty("os.arch") ?: "x64")
            append("|ZENTA_POS_SALT_v1")  // Static salt prevents empty-fingerprint collapse
        }
        // SHA-256 hash → Base64 → char array
        val digest = java.security.MessageDigest.getInstance("SHA-256")
        val hash = digest.digest(fingerprint.toByteArray(Charsets.UTF_8))
        val b64 = java.util.Base64.getEncoder().encodeToString(hash)
        return b64.toCharArray()
    }

    private companion object {
        const val TAG             = "DatabaseKeyProvider"
        const val KEYSTORE_TYPE   = "PKCS12"
        const val KEYSTORE_FILE_NAME = ".db_keystore.p12"
        const val KEY_ALIAS       = "ZyntaPOS_DB_Key_v1"
        const val KEY_SIZE_BITS   = 256
    }
}
