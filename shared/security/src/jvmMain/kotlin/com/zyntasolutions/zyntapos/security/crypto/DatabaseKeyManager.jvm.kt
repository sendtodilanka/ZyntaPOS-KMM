package com.zyntasolutions.zyntapos.security.crypto

import com.zyntasolutions.zyntapos.core.logger.ZentaLogger
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
 * The KeyStore file is located at `~/.zentapos/.db_keystore.p12` and protected
 * with a machine-fingerprint derived password (SHA-256 of `user.name|os.name|os.arch`).
 * The 32-byte key is directly extractable on the JVM (`secretKey.encoded` returns raw bytes).
 */
actual class DatabaseKeyManager actual constructor() {

    private val keystoreFile: File = File(
        System.getProperty("user.home"),
        ".zentapos/.db_keystore.p12",
    )

    private val keystorePassword: CharArray by lazy {
        val fingerprint = "${System.getProperty("user.name")}|" +
            "${System.getProperty("os.name")}|" +
            System.getProperty("os.arch")
        val digest = java.security.MessageDigest.getInstance("SHA-256")
        digest.digest(fingerprint.encodeToByteArray())
            .joinToString("") { "%02x".format(it) }
            .toCharArray()
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
            ZentaLogger.d(TAG) { "DEK loaded from PKCS12 store" }
            return raw
        }

        // First launch — generate and persist a fresh 256-bit DEK
        val generator = KeyGenerator.getInstance("AES")
        generator.init(256, SecureRandom())
        val secretKey: SecretKey = generator.generateKey()
        ks.setEntry(KEY_ALIAS, KeyStore.SecretKeyEntry(secretKey), protection)
        saveKeyStore(ks)
        ZentaLogger.d(TAG) { "DEK generated and stored in PKCS12 on first launch" }
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
