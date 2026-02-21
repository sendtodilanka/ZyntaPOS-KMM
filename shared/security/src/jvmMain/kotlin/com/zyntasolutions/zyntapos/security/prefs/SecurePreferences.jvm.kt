package com.zyntasolutions.zyntapos.security.prefs

import com.zyntasolutions.zyntapos.core.logger.ZyntaLogger
import com.zyntasolutions.zyntapos.security.crypto.EncryptedData
import com.zyntasolutions.zyntapos.security.crypto.EncryptionManager
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.Base64
import java.util.Properties

// ZENTA-FINAL-AUDIT MERGED-F1
private const val TAG = "SecurePreferences"
private const val PREFS_FILE = ".zentapos/secure_prefs.enc"
private const val SEPARATOR = ":"

/**
 * Desktop (JVM) actual: Properties file encrypted per-value via [EncryptionManager].
 *
 * Each value is encrypted with AES-256-GCM and serialised as three Base64 segments:
 * `<iv>:<ciphertext>:<tag>`. The Properties file itself is stored in plain-text
 * (the key names are visible) but all values are encrypted. For fully encrypted
 * key-value storage use a PKCS12-backed solution in a future security hardening sprint.
 *
 * File location: `~/.zentapos/secure_prefs.enc`
 *
 * Key strings are sourced exclusively from [SecurePreferencesKeys].
 * @see SecurePreferencesKeys
 */
actual class SecurePreferences actual constructor() : TokenStorage {

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

    actual fun put(key: String, value: String) {
        val encrypted = encryption.encrypt(value)
        val encoded = "${encoded(encrypted.iv)}$SEPARATOR${encoded(encrypted.ciphertext)}$SEPARATOR${encoded(encrypted.tag)}"
        val props = loadProps()
        props[key] = encoded
        saveProps(props)
        ZyntaLogger.d(TAG) { "put key=$key" }
    }

    actual fun get(key: String): String? {
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
            ZyntaLogger.e(TAG, e) { "Failed to decrypt key=$key — removing corrupt entry" }
            remove(key)
            null
        }
    }

    actual fun remove(key: String) {
        val props = loadProps()
        props.remove(key)
        saveProps(props)
    }

    actual fun clear() {
        val props = loadProps()
        props.clear()
        saveProps(props)
    }

    private fun encoded(bytes: ByteArray): String = Base64.getEncoder().encodeToString(bytes)
    private fun decoded(s: String): ByteArray = Base64.getDecoder().decode(s)

    actual companion object
}
