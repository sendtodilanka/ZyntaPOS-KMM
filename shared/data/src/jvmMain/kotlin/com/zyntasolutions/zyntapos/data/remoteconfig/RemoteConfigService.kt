package com.zyntasolutions.zyntapos.data.remoteconfig

import co.touchlab.kermit.Logger
import com.zyntasolutions.zyntapos.core.config.RemoteConfigProvider
import com.zyntasolutions.zyntapos.core.config.RemoteEdition

/**
 * Desktop JVM stub implementation of [RemoteConfigService].
 *
 * Firebase Remote Config has no JVM Desktop SDK. On Desktop, all feature flags
 * are resolved from local defaults — the license server is the source of truth
 * for edition gating (see [com.zyntasolutions.zyntapos.domain.model.Edition]).
 *
 * [fetchAndActivate] is a no-op that always returns false.
 * All getters return their [defaultValue] parameters.
 */
actual class RemoteConfigService : RemoteConfigProvider {

    private val log = Logger.withTag("RemoteConfigService")

    actual override suspend fun fetchAndActivate(): Boolean {
        log.d { "RemoteConfig: JVM stub — fetchAndActivate is a no-op" }
        return false
    }

    actual override fun getString(key: String, defaultValue: String): String = defaultValue

    actual override fun getBoolean(key: String, defaultValue: Boolean): Boolean = defaultValue

    actual override fun getLong(key: String, defaultValue: Long): Long = defaultValue

    actual override fun getEdition(): RemoteEdition = RemoteEdition.STARTER
}
