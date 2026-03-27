package com.zyntasolutions.zyntapos.core.config

/**
 * Abstraction for runtime feature flags sourced from Firebase Remote Config.
 *
 * Feature modules depend on `:shared:core` and inject this interface via Koin.
 * The platform-specific [com.zyntasolutions.zyntapos.data.remoteconfig.RemoteConfigService]
 * in `:shared:data` implements it.
 *
 * This separation keeps feature modules free of `:shared:data` dependencies
 * while allowing runtime configuration reads from any ViewModel.
 *
 * TODO-011 Phase 2: edition feature flags — runtime overrides without an app update.
 *
 * Default values (when Remote Config is not available or key is absent) are
 * always the most restrictive / safest option:
 * - `getString` → ""
 * - `getBoolean` → false
 * - `getLong` → 0
 * - `getEdition` → [RemoteEdition.STARTER]
 */
interface RemoteConfigProvider {

    /**
     * Fetch latest values from Firebase Remote Config and activate them.
     * Returns true if new values were fetched and activated; false if defaults are used.
     * Should be called once at app startup (e.g. in Application.onCreate or main.kt).
     */
    suspend fun fetchAndActivate(): Boolean

    /** Read a string config value. Returns [defaultValue] when key is absent. */
    fun getString(key: String, defaultValue: String = ""): String

    /** Read a boolean config value. Returns [defaultValue] when key is absent. */
    fun getBoolean(key: String, defaultValue: Boolean = false): Boolean

    /** Read a long config value. Returns [defaultValue] when key is absent. */
    fun getLong(key: String, defaultValue: Long = 0L): Long

    /**
     * Resolve the activated app edition from the [EDITION_KEY] config flag.
     * Falls back to [RemoteEdition.STARTER] on parse error or missing value.
     */
    fun getEdition(): RemoteEdition
}

/**
 * App edition as reported by Firebase Remote Config.
 *
 * Maps to the [com.zyntasolutions.zyntapos.domain.model.Edition] domain model
 * once the value is validated and persisted by the license layer.
 * Remote Config is the *override channel* — the license server is the source of truth.
 */
enum class RemoteEdition { STARTER, PROFESSIONAL, ENTERPRISE }

/**
 * Standard Remote Config key names for ZyntaPOS feature flags.
 * Keep keys in sync with the Firebase console parameter definitions.
 */
object RemoteConfigKeys {
    /** Current app edition: "STARTER" | "PROFESSIONAL" | "ENTERPRISE" */
    const val EDITION_KEY = "app_edition"

    /** Minimum supported app version — force-update gate (semver string). */
    const val MIN_APP_VERSION = "min_app_version"

    /** Whether the multi-store module is enabled for this license. */
    const val MULTI_STORE_ENABLED = "feature_multi_store"

    /** Whether the accounting / e-invoice module is enabled. */
    const val ACCOUNTING_ENABLED = "feature_accounting"

    /** Whether the staff / HR module is enabled. */
    const val STAFF_MODULE_ENABLED = "feature_staff"

    /** Whether the diagnostic remote-access module is enabled (ENTERPRISE only). */
    const val DIAGNOSTIC_ENABLED = "feature_diagnostic"

    /** Maintenance mode flag — show maintenance screen when true. */
    const val MAINTENANCE_MODE = "maintenance_mode"
}
