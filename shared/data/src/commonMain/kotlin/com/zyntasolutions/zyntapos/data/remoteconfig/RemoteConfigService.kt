package com.zyntasolutions.zyntapos.data.remoteconfig

import com.zyntasolutions.zyntapos.core.config.RemoteConfigProvider
import com.zyntasolutions.zyntapos.core.config.RemoteEdition

/**
 * Cross-platform Firebase Remote Config service (TODO-011 Phase 2).
 *
 * Implements [RemoteConfigProvider] from `:shared:core` so feature modules can
 * depend on the interface without pulling in `:shared:data`.
 *
 * Platform implementations:
 * - **Android:** Firebase Remote Config SDK — live fetches from Firebase console.
 * - **Desktop JVM:** No-op stub — returns all defaults (Firebase RC has no JVM SDK).
 *
 * Architecture constraint (TODO-011 rule #1): Firebase SDK must only be used in
 * `androidMain` — never in `commonMain`.
 */
expect class RemoteConfigService : RemoteConfigProvider {

    override suspend fun fetchAndActivate(): Boolean

    override fun getString(key: String, defaultValue: String): String

    override fun getBoolean(key: String, defaultValue: Boolean): Boolean

    override fun getLong(key: String, defaultValue: Long): Long

    override fun getEdition(): RemoteEdition
}
