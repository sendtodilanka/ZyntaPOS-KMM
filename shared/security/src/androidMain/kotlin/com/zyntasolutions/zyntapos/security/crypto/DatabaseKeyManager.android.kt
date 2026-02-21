package com.zyntasolutions.zyntapos.security.crypto

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.core.content.edit
import com.zyntasolutions.zyntapos.core.logger.ZentaLogger
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.security.KeyStore
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

private const val TAG = "DatabaseKeyManager"
private const val ANDROID_KEYSTORE = "AndroidKeyStore"
private const val KEK_ALIAS = "zyntapos_kek_v1"
private const val PREFS_NAME = "zyntapos_db_prefs"
private const val PREFS_KEY_WRAPPED_DEK = "wrapped_dek"
private const val PREFS_KEY_DEK_IV = "dek_iv"
private const val AES_GCM_NOPADDING = "AES/GCM/NoPadding"
private const val GCM_TAG_LENGTH = 128
private const val DEK_SIZE_BYTES = 32

/**
 * Android actual: Envelope encryption — DEK wrapped by a non-extractable KEK in Android Keystore.
 *
 * The 32-byte Data-Encryption Key (DEK) is generated with [SecureRandom] and then wrapped
 * (AES-256-GCM) by a Key-Encryption Key (KEK) stored in the Android Keystore. The wrapped DEK
 * and its IV are persisted in SharedPreferences as Base64 strings. On subsequent calls the DEK
 * is unwrapped using the KEK and returned as raw bytes for SQLCipher initialisation.
 *
 * This pattern avoids the `secretKey.encoded == null` limitation of hardware-backed Keystore keys.
 */
actual class DatabaseKeyManager actual constructor() : KoinComponent {

    private val context: Context by inject()

    private fun getOrCreateKek(): SecretKey {
        val ks = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        ks.getKey(KEK_ALIAS, null)?.let { return it as SecretKey }

        val kg = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        kg.init(
            KeyGenParameterSpec.Builder(
                KEK_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .setRandomizedEncryptionRequired(false) // we supply IV for unwrapping
                .build(),
        )
        ZentaLogger.d(TAG) { "Generated KEK in Android Keystore" }
        return kg.generateKey()
    }

    actual fun getOrCreateKey(): ByteArray {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val wrappedDek = prefs.getString(PREFS_KEY_WRAPPED_DEK, null)
        val dekIv = prefs.getString(PREFS_KEY_DEK_IV, null)

        if (wrappedDek != null && dekIv != null) {
            val kek = getOrCreateKek()
            val iv = Base64.decode(dekIv, Base64.DEFAULT)
            val cipher = Cipher.getInstance(AES_GCM_NOPADDING)
            cipher.init(Cipher.DECRYPT_MODE, kek, GCMParameterSpec(GCM_TAG_LENGTH, iv))
            val dek = cipher.doFinal(Base64.decode(wrappedDek, Base64.DEFAULT))
            require(dek.size == DEK_SIZE_BYTES) { "Decrypted DEK has unexpected size: ${dek.size}" }
            ZentaLogger.d(TAG) { "DEK unwrapped successfully" }
            return dek
        }

        // First launch — generate DEK and wrap it with the KEK
        val dek = ByteArray(DEK_SIZE_BYTES).also { SecureRandom().nextBytes(it) }
        val kek = getOrCreateKek()
        val iv = ByteArray(12).also { SecureRandom().nextBytes(it) }
        val cipher = Cipher.getInstance(AES_GCM_NOPADDING)
        cipher.init(Cipher.ENCRYPT_MODE, kek, GCMParameterSpec(GCM_TAG_LENGTH, iv))
        val wrapped = cipher.doFinal(dek)

        prefs.edit {
            putString(PREFS_KEY_WRAPPED_DEK, Base64.encodeToString(wrapped, Base64.DEFAULT))
            putString(PREFS_KEY_DEK_IV, Base64.encodeToString(iv, Base64.DEFAULT))
        }
        ZentaLogger.d(TAG) { "DEK generated and wrapped on first launch" }
        return dek
    }

    actual fun hasPersistedKey(): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.contains(PREFS_KEY_WRAPPED_DEK)
    }
}
