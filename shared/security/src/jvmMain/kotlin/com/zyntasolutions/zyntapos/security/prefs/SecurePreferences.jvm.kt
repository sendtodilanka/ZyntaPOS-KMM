package com.zyntasolutions.zyntapos.security.prefs

import com.zyntasolutions.zyntapos.core.logger.ZyntaLogger
import com.zyntasolutions.zyntapos.domain.port.SecureStoragePort
import com.zyntasolutions.zyntapos.security.crypto.EncryptedData
import com.zyntasolutions.zyntapos.security.crypto.EncryptionManager
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.Base64
import java.util.Properties

// ZENTA-FINAL-AUDIT MERGED-F1 | MERGED-F3 (2026-02-22)
private const val TAG = "SecurePreferences"
private const val PREFS_FILE = ".zyntapos/secure_prefs.enc"
private const val SEPARATOR = ":"

/**
 * Desktop (JVM) actual: Properties file encrypted per-value via [EncryptionManager].
 *
 * Each value is encrypted with AES-256-GCM and serialised as three Base64 segments:
 * `<iv>:<ciphertext>:<tag>`. The Properties file itself is stored in plain-text
 * (the key names are visible) but all values are encrypted. For fully encrypted
 * key-value storage use a PKCS12-backed solution in a future security hardening sprint.
 *
 * File location: `~/.zyntapos/secure_prefs.enc`
 *
 * Implements both [TokenStorage] (for [JwtManager]) and [SecureStoragePort] (for
 * `:shared:data` injection via Koin — MERGED-F3 2026-02-22).
 *
 * Key strings are sourced exclusively from [SecurePreferencesKeys] / [com.zyntasolutions.zyntapos.domain.port.SecureStorageKeys].
 * @see SecurePreferencesKeys
 */
actual class SecurePreferences actual constructor() : TokenStorage, SecureStoragePort {

    private val prefsFile: File = File(System.getProperty("user.home"), PREFS_FILE)
    private val encryption = EncryptionManager()

    @Synchronized
    private fun loadProps(): Properties {
        val props = Properties()
        if (prefsFile.exists()) {
            FileInputStream(prefsFile).use { props.load(it) }
        }
        return props
    }

    @Synchronized
    private fun saveProps(props: Properties) {
        prefsFile.parentFile?.mkdirs()
        FileOutputStream(prefsFile).use { props.store(it, "ZyntaPOS Secure Preferences") }
    }

    actual override fun put(key: String, value: String) {
        val encrypted = encryption.encrypt(value)
        val encoded = "${encoded(encrypted.iv)}$SEPARATOR${encoded(encrypted.ciphertext)}$SEPARATOR${encoded(encrypted.tag)}"
        val props = loadProps()
        props[key] = encoded
        saveProps(props)
        ZyntaLogger.d(TAG, "put key=$key")
    }

    actual override fun get(key: String): String? {
        val props = loadProps()
        val encoded = props.getProperty(key) ?: return null
        return try {
            val parts = encoded.split(SEPARATOR)
            require(parts.size == 3) { "Malformed encrypted value for key=$key" }
            val data = EncryptedData(
                iv = decoded(parts[0]),
                ciphertext = decoded(parts[1]),
                tag = decoded(parts[2]),
            )
            encryption.decrypt(data)
        } catch (e: Exception) {
            ZyntaLogger.e(TAG, "Failed to decrypt key=$key — removing corrupt entry", e)
            remove(key)
            null
        }
    }

    actual override fun remove(key: String) {
        val props = loadProps()
        props.remove(key)
        saveProps(props)
    }

    actual override fun clear() {
        val props = loadProps()
        props.clear()
        saveProps(props)
    }

    /** Returns `true` if [key] is present in the encrypted properties file. */
    actual override fun contains(key: String): Boolean = loadProps().containsKey(key)

    private fun encoded(bytes: ByteArray): String = Base64.getEncoder().encodeToString(bytes)
    private fun decoded(s: String): ByteArray = Base64.getDecoder().decode(s)

    actual companion object
}
