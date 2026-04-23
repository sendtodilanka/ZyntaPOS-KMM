package com.zyntasolutions.zyntapos.security.crypto

import com.zyntasolutions.zyntapos.core.logger.ZyntaLogger
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.security.KeyStore
import java.security.SecureRandom
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey

private const val TAG = "DatabaseKeyManager"
private const val PKCS12_TYPE = "PKCS12"
private const val KEY_ALIAS = "zyntapos_db_dek_v1"
private const val DEK_SIZE_BYTES = 32

/**
 * Desktop (JVM) actual: 256-bit AES key stored in a PKCS12 KeyStore.
 *
 * The KeyStore file is located at `~/.zyntapos/.db_keystore.p12`. The keystore
 * password resolution prefers the platform OS credential manager (macOS
 * Keychain via `security`, Linux libsecret via `secret-tool`) and falls back
 * to a machine-fingerprint derivation (SHA-256 of
 * `user.name|os.name|os.arch`) when the keyring is unavailable — e.g. Windows
 * or stripped-down Linux environments without libsecret-tools installed.
 *
 * Resolution order (first match wins):
 *   1. `OsKeyring.retrieve()` — password previously persisted in OS vault.
 *   2. Legacy fingerprint — if the keystore file already exists (covers
 *      upgrades from pre-keyring installs).
 *   3. Fresh random password — generated on first launch, persisted via
 *      `OsKeyring.store()`. If persistence fails, fingerprint is used so
 *      the store remains recoverable on the next launch.
 *
 * The 32-byte DEK is directly extractable on the JVM (`secretKey.encoded`
 * returns raw bytes).
 */
actual class DatabaseKeyManager actual constructor() {

    private val keystoreFile: File = File(
        System.getProperty("user.home"),
        ".zyntapos/.db_keystore.p12",
    )

    private val keystorePassword: CharArray by lazy { resolveKeystorePassword() }

    private fun resolveKeystorePassword(): CharArray {
        OsKeyring.retrieve()?.let {
            ZyntaLogger.d(TAG, "Keystore password loaded from OS keyring")
            return it.toCharArray()
        }
        if (keystoreFile.exists()) {
            ZyntaLogger.d(TAG, "Keystore password derived from machine fingerprint (legacy install)")
            return fingerprintPassword()
        }
        val random = generateRandomPassword()
        if (OsKeyring.store(String(random))) {
            ZyntaLogger.d(TAG, "Keystore password generated and stored in OS keyring")
            return random
        }
        ZyntaLogger.d(TAG, "OS keyring unavailable — falling back to machine fingerprint")
        return fingerprintPassword()
    }

    private fun fingerprintPassword(): CharArray {
        val fingerprint = "${System.getProperty("user.name")}|" +
            "${System.getProperty("os.name")}|" +
            System.getProperty("os.arch")
        val digest = java.security.MessageDigest.getInstance("SHA-256")
        return digest.digest(fingerprint.encodeToByteArray())
            .joinToString("") { "%02x".format(it) }
            .toCharArray()
    }

    private fun generateRandomPassword(): CharArray {
        // 32 random bytes → 64 hex chars → 256 bits of entropy for the PKCS12 MAC/encryption key
        val bytes = ByteArray(32)
        SecureRandom().nextBytes(bytes)
        return bytes.joinToString("") { "%02x".format(it) }.toCharArray()
    }

    private fun loadKeyStore(): KeyStore {
        val ks = KeyStore.getInstance(PKCS12_TYPE)
        if (keystoreFile.exists()) {
            FileInputStream(keystoreFile).use { ks.load(it, keystorePassword) }
        } else {
            ks.load(null, keystorePassword)
        }
        return ks
    }

    private fun saveKeyStore(ks: KeyStore) {
        keystoreFile.parentFile?.mkdirs()
        FileOutputStream(keystoreFile).use { ks.store(it, keystorePassword) }
    }

    actual fun getOrCreateKey(): ByteArray {
        val ks = loadKeyStore()
        val protection = KeyStore.PasswordProtection(keystorePassword)
        val entry = ks.getEntry(KEY_ALIAS, protection)
        if (entry != null) {
            val secretKey = (entry as KeyStore.SecretKeyEntry).secretKey
            val raw = secretKey.encoded
            require(raw != null && raw.size == DEK_SIZE_BYTES) {
                "Stored DEK has unexpected size: ${raw?.size}"
            }
            ZyntaLogger.d(TAG, "DEK loaded from PKCS12 store")
            return raw
        }

        // First launch — generate and persist a fresh 256-bit DEK
        val generator = KeyGenerator.getInstance("AES")
        generator.init(256, SecureRandom())
        val secretKey: SecretKey = generator.generateKey()
        ks.setEntry(KEY_ALIAS, KeyStore.SecretKeyEntry(secretKey), protection)
        saveKeyStore(ks)
        ZyntaLogger.d(TAG, "DEK generated and stored in PKCS12 on first launch")
        return secretKey.encoded!!
    }

    actual fun hasPersistedKey(): Boolean {
        if (!keystoreFile.exists()) return false
        return try {
            loadKeyStore().containsAlias(KEY_ALIAS)
        } catch (_: Exception) {
            false
        }
    }
}
