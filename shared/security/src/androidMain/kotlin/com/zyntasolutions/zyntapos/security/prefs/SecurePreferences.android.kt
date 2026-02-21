package com.zyntasolutions.zyntapos.security.prefs

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

// ZENTA-FINAL-AUDIT MERGED-F1
private const val PREFS_FILE = "zyntapos_secure_prefs"

/**
 * Android actual: delegates to [EncryptedSharedPreferences] (Jetpack Security Crypto).
 *
 * Keys are encrypted with AES-256-SIV and values with AES-256-GCM using a MasterKey
 * stored in the Android Keystore. The underlying file is stored in the standard
 * app SharedPreferences directory and is only accessible to this app.
 *
 * Key strings are sourced exclusively from [SecurePreferencesKeys].
 * @see SecurePreferencesKeys
 */
actual class SecurePreferences actual constructor() : TokenStorage, KoinComponent {

    private val context: Context by inject()

    private val sharedPrefs by lazy {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context,
            PREFS_FILE,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }

    actual override fun put(key: String, value: String) {
        sharedPrefs.edit().putString(key, value).apply()
    }

    actual override fun get(key: String): String? = sharedPrefs.getString(key, null)

    actual override fun remove(key: String) {
        sharedPrefs.edit().remove(key).apply()
    }

    actual fun clear() {
        sharedPrefs.edit().clear().apply()
    }

    actual companion object
}
