package com.zyntasolutions.zyntapos.data.remoteconfig

import co.touchlab.kermit.Logger
import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import com.google.firebase.remoteconfig.FirebaseRemoteConfigSettings
import com.zyntasolutions.zyntapos.core.config.RemoteConfigKeys
import com.zyntasolutions.zyntapos.core.config.RemoteConfigProvider
import com.zyntasolutions.zyntapos.core.config.RemoteEdition
import kotlinx.coroutines.CompletableDeferred

/**
 * Android implementation of [RemoteConfigService] using Firebase Remote Config SDK.
 *
 * Firebase Remote Config SDK is Android-only (TODO-011 rule #1: Firebase SDK must only
 * be used in androidMain — never in commonMain).
 *
 * Default values are registered in-code for every [RemoteConfigKeys] constant so the app
 * works correctly before the first successful fetch (offline + first launch).
 *
 * Fetch interval: 3600 s (1 hour, Firebase recommended minimum).
 */
actual class RemoteConfigService : RemoteConfigProvider {

    private val log = Logger.withTag("RemoteConfigService")
    private val remoteConfig: FirebaseRemoteConfig = FirebaseRemoteConfig.getInstance()

    init {
        val settings = FirebaseRemoteConfigSettings.Builder()
            .setMinimumFetchIntervalInSeconds(3600L)
            .build()
        remoteConfig.setConfigSettingsAsync(settings)

        // In-code defaults — used before first successful fetch and when a key is absent.
        remoteConfig.setDefaultsAsync(
            mapOf(
                RemoteConfigKeys.EDITION_KEY          to RemoteEdition.STARTER.name,
                RemoteConfigKeys.MIN_APP_VERSION      to "1.0.0",
                RemoteConfigKeys.MULTI_STORE_ENABLED  to false,
                RemoteConfigKeys.ACCOUNTING_ENABLED   to false,
                RemoteConfigKeys.STAFF_MODULE_ENABLED to false,
                RemoteConfigKeys.DIAGNOSTIC_ENABLED   to false,
                RemoteConfigKeys.MAINTENANCE_MODE     to false,
            )
        )
    }

    actual override suspend fun fetchAndActivate(): Boolean {
        val deferred = CompletableDeferred<Boolean>()
        remoteConfig.fetchAndActivate()
            .addOnSuccessListener { activated ->
                log.d { "RemoteConfig fetchAndActivate: activated=$activated" }
                deferred.complete(activated)
            }
            .addOnFailureListener { e ->
                log.w { "RemoteConfig fetchAndActivate failed: ${e.message}" }
                deferred.complete(false)
            }
        return deferred.await()
    }

    actual override fun getString(key: String, defaultValue: String): String =
        remoteConfig.getString(key).ifBlank { defaultValue }

    actual override fun getBoolean(key: String, defaultValue: Boolean): Boolean =
        try {
            remoteConfig.getBoolean(key)
        } catch (e: Exception) {
            defaultValue
        }

    actual override fun getLong(key: String, defaultValue: Long): Long =
        try {
            remoteConfig.getLong(key)
        } catch (e: Exception) {
            defaultValue
        }

    actual override fun getEdition(): RemoteEdition {
        val raw = remoteConfig.getString(RemoteConfigKeys.EDITION_KEY)
        return runCatching { RemoteEdition.valueOf(raw.uppercase()) }
            .getOrElse {
                log.w { "RemoteConfig: unknown edition value '$raw', defaulting to STARTER" }
                RemoteEdition.STARTER
            }
    }
}
