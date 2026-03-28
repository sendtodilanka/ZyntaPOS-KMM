package com.zyntasolutions.zyntapos.data.remoteconfig

import co.touchlab.kermit.Logger
import com.zyntasolutions.zyntapos.core.config.RemoteConfigKeys
import com.zyntasolutions.zyntapos.core.config.RemoteConfigProvider
import com.zyntasolutions.zyntapos.core.config.RemoteEdition
import com.zyntasolutions.zyntapos.domain.model.ZyntaFeature
import com.zyntasolutions.zyntapos.domain.repository.FeatureRegistryRepository

/**
 * Feature flag service backed by the local [FeatureRegistryRepository].
 *
 * Implements [RemoteConfigProvider] so that feature modules depending on
 * `:shared:core` can inject this via Koin without pulling in `:shared:data`.
 *
 * **Architecture (ADR-012):** Firebase Remote Config has been removed. Feature
 * flags are now managed via the Admin Panel (`/admin/config/feature-flags`) and
 * pushed to devices through the sync engine, which populates the local
 * [FeatureRegistryRepository] (SQLite). This service reads from that local store.
 *
 * **Cache strategy:** [fetchAndActivate] populates an in-memory snapshot from
 * the local [FeatureRegistryRepository]. Subsequent [getBoolean] / [getString] /
 * [getLong] calls read from this snapshot synchronously. The snapshot is
 * refreshed every time [fetchAndActivate] is called (once per app startup).
 *
 * **Fallback:** When [fetchAndActivate] has not yet been called, all getters
 * return their supplied [defaultValue], which is always the most restrictive
 * / safest option.
 */
class RemoteConfigService(
    private val featureRegistry: FeatureRegistryRepository,
) : RemoteConfigProvider {

    private val log = Logger.withTag("RemoteConfigService")

    // In-memory snapshot populated by fetchAndActivate()
    private val boolCache  = mutableMapOf<String, Boolean>()
    private val stringCache = mutableMapOf<String, String>()

    override suspend fun fetchAndActivate(): Boolean {
        return try {
            boolCache[RemoteConfigKeys.MULTI_STORE_ENABLED]  = featureRegistry.isEnabled(ZyntaFeature.MULTISTORE)
            boolCache[RemoteConfigKeys.ACCOUNTING_ENABLED]   = featureRegistry.isEnabled(ZyntaFeature.ACCOUNTING)
            boolCache[RemoteConfigKeys.STAFF_MODULE_ENABLED] = featureRegistry.isEnabled(ZyntaFeature.STAFF_HR)
            boolCache[RemoteConfigKeys.DIAGNOSTIC_ENABLED]   = featureRegistry.isEnabled(ZyntaFeature.REMOTE_DIAGNOSTICS)
            boolCache[RemoteConfigKeys.MAINTENANCE_MODE]     = false  // Not stored in FeatureRegistry; default safe

            log.d { "RemoteConfigService: cache refreshed from FeatureRegistry" }
            true
        } catch (e: Exception) {
            log.w { "RemoteConfigService: fetchAndActivate failed — ${e.message}" }
            false
        }
    }

    override fun getString(key: String, defaultValue: String): String =
        stringCache[key] ?: defaultValue

    override fun getBoolean(key: String, defaultValue: Boolean): Boolean =
        boolCache[key] ?: defaultValue

    override fun getLong(key: String, defaultValue: Long): Long = defaultValue

    override fun getEdition(): RemoteEdition {
        // Edition is authoritative from the license server (LicenseManager / FeatureRegistry).
        // RemoteConfigService returns STARTER as a safe default; the navigation layer
        // enforces edition gating via FeatureRegistryRepository directly.
        return RemoteEdition.STARTER
    }
}
