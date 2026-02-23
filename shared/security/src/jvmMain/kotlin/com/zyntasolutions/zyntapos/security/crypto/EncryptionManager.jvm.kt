package com.zyntasolutions.zyntapos.security.crypto

import com.zyntasolutions.zyntapos.core.logger.ZyntaLogger
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.security.KeyStore
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

private const val TAG = "EncryptionManager"
private const val AES_GCM_NOPADDING = "AES/GCM/NoPadding"
private const val GCM_TAG_LENGTH = 128
private const val GCM_IV_LENGTH = 12
private const val PKCS12_TYPE = "PKCS12"
private const val KEY_ENTRY_TYPE = "zyntapos_sym_key"

/**
 * Desktop (JVM) actual: AES-256-GCM encryption via JCE + PKCS12 KeyStore.
 *
 * The secret key is stored in a PKCS12 KeyStore at `~/.zyntapos/.zyntapos.p12`.
 * The keystore password is derived from a machine fingerprint (SHA-256 of
 * `user.name + os.name + os.arch`) so it survives restarts without user input,
 * while remaining machine-specific.
 *
 * A fresh 12-byte random IV is generated per [encrypt] call via [SecureRandom].
 * The 16-byte GCM authentication tag is split from the JCE output and stored
 * separately in [EncryptedData.tag].
 */
actual class EncryptionManager actual constructor(keyAlias: String) {

    private val alias: String = keyAlias
    private val keystoreFile: File = File(
        System.getProperty("user.home"),
        ".zyntapos/.zyntapos.p12",
    )
    private val keystorePassword: CharArray by lazy { deriveKeystorePassword() }

    private fun deriveKeystorePassword(): CharArray {
        val fingerprint = "${System.getProperty("user.name")}|" +
            "${System.getProperty("os.name")}|" +
            System.getProperty("os.arch")
        val digest = java.security.MessageDigest.getInstance("SHA-256")
        return digest.digest(fingerprint.encodeToByteArray())
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

    private fun getOrCreateKey(): SecretKey {
        val ks = loadKeyStore()
        ks.getEntry(alias, KeyStore.PasswordProtection(keystorePassword))
            ?.let { return (it as KeyStore.SecretKeyEntry).secretKey }

        val generator = KeyGenerator.getInstance("AES")
        generator.init(256, SecureRandom())
        val secretKey = generator.generateKey()
        ks.setEntry(
            alias,
            KeyStore.SecretKeyEntry(secretKey),
            KeyStore.PasswordProtection(keystorePassword),
        )
        saveKeyStore(ks)
        ZyntaLogger.d(TAG, "Generated new AES-256 key for alias=$alias in PKCS12 store")
        return secretKey
    }

    actual fun encrypt(plaintext: String): EncryptedData {
        val key = getOrCreateKey()
        val iv = ByteArray(GCM_IV_LENGTH).also { SecureRandom().nextBytes(it) }
        val cipher = Cipher.getInstance(AES_GCM_NOPADDING)
        cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(GCM_TAG_LENGTH, iv))
        val combined = cipher.doFinal(plaintext.encodeToByteArray())
        val tagStart = combined.size - (GCM_TAG_LENGTH / 8)
        val ciphertext = combined.copyOfRange(0, tagStart)
        val tag = combined.copyOfRange(tagStart, combined.size)
        return EncryptedData(ciphertext = ciphertext, iv = iv, tag = tag)
    }

    actual fun decrypt(data: EncryptedData): String {
        val key = getOrCreateKey()
        val cipher = Cipher.getInstance(AES_GCM_NOPADDING)
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(GCM_TAG_LENGTH, data.iv))
        val combined = data.ciphertext + data.tag
        return cipher.doFinal(combined).decodeToString()
    }

    actual companion object {
        actual const val DEFAULT_KEY_ALIAS: String = "zyntapos_enc_key_v1"
    }
}
