package com.zyntasolutions.zyntapos.security.crypto

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import com.zyntasolutions.zyntapos.core.logger.ZyntaLogger
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

private const val TAG = "EncryptionManager"
private const val ANDROID_KEYSTORE = "AndroidKeyStore"
private const val AES_GCM_NOPADDING = "AES/GCM/NoPadding"
private const val GCM_TAG_LENGTH = 128
private const val GCM_IV_LENGTH = 12

/**
 * Android actual: AES-256-GCM encryption via Android Keystore.
 *
 * The secret key is generated once (if absent) in the Android Keystore under [keyAlias].
 * It is non-extractable and may be hardware-backed on devices with a Secure Element or TEE.
 * A fresh 12-byte IV is generated per [encrypt] call; the GCM tag is appended to the
 * ciphertext by the Android JCE provider and split out into [EncryptedData.tag].
 */
actual class EncryptionManager actual constructor(keyAlias: String) {

    private val alias: String = keyAlias

    private fun getOrCreateKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        keyStore.getKey(alias, null)?.let { return it as SecretKey }

        val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        val spec = KeyGenParameterSpec.Builder(
            alias,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .setRandomizedEncryptionRequired(true)
            .build()
        keyGenerator.init(spec)
        ZyntaLogger.d(TAG) { "Generated new AES-256-GCM key for alias=$alias" }
        return keyGenerator.generateKey()
    }

    actual fun encrypt(plaintext: String): EncryptedData {
        val key = getOrCreateKey()
        val cipher = Cipher.getInstance(AES_GCM_NOPADDING)
        cipher.init(Cipher.ENCRYPT_MODE, key)
        val iv = cipher.iv  // Android Keystore generates IV when randomizedEncryptionRequired=true
        val combined = cipher.doFinal(plaintext.encodeToByteArray())
        // Android JCE appends 16-byte tag at end of ciphertext
        val tagStart = combined.size - (GCM_TAG_LENGTH / 8)
        val ciphertext = combined.copyOfRange(0, tagStart)
        val tag = combined.copyOfRange(tagStart, combined.size)
        return EncryptedData(ciphertext = ciphertext, iv = iv, tag = tag)
    }

    actual fun decrypt(data: EncryptedData): String {
        val key = getOrCreateKey()
        val cipher = Cipher.getInstance(AES_GCM_NOPADDING)
        val spec = GCMParameterSpec(GCM_TAG_LENGTH, data.iv)
        cipher.init(Cipher.DECRYPT_MODE, key, spec)
        val combined = data.ciphertext + data.tag
        return cipher.doFinal(combined).decodeToString()
    }

    actual companion object {
        actual const val DEFAULT_KEY_ALIAS: String = "zyntapos_enc_key_v1"
    }
}
