package com.zyntasolutions.zyntapos.core.platform

import android.os.Build

// ─────────────────────────────────────────────────────────────────────────────
// Android actual — AppInfoProvider
//
// Reads version info from the application's own context. Since shared:core
// does not have an Android Context dependency, version and build date are
// set at app startup via init(). Falls back to safe defaults if not initialised.
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Android [AppInfoProvider] implementation.
 *
 * Version and build date must be set from the Application class at startup
 * via [AndroidAppInfoProvider.init] since shared:core doesn't have Context.
 */
class AndroidAppInfoProvider : AppInfoProvider {

    override var appVersion: String = "1.0.0"
        private set

    override var buildDate: String = "2026-02-23"
        private set

    override val platformName: String
        get() = "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})"

    override var isDebug: Boolean = false
        private set

    /**
     * Initialise with values from the host application's BuildConfig.
     *
     * Call this from `Application.onCreate()`:
     * ```kotlin
     * (appInfoProvider as AndroidAppInfoProvider).init(
     *     version = BuildConfig.VERSION_NAME,
     *     buildDate = BuildConfig.BUILD_DATE,
     *     debug = BuildConfig.DEBUG,
     * )
     * ```
     */
    fun init(version: String, buildDate: String, debug: Boolean) {
        this.appVersion = version
        this.buildDate = buildDate
        this.isDebug = debug
    }
}

actual fun createAppInfoProvider(): AppInfoProvider = AndroidAppInfoProvider()
