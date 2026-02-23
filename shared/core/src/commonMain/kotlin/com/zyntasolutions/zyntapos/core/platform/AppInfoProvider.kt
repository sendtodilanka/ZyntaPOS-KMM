package com.zyntasolutions.zyntapos.core.platform

// ─────────────────────────────────────────────────────────────────────────────
// AppInfoProvider — Platform-specific application build information
//
// Provides app version, build date, and platform name for the About screen
// and diagnostics. Resolved via expect/actual:
//   Android  → reads from BuildConfig + Build.VERSION
//   Desktop  → reads from system properties / manifest
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Provides platform-specific application metadata.
 *
 * Implemented via `expect/actual` so each platform can read its own
 * build configuration (Android [BuildConfig], JVM system properties, etc.).
 */
interface AppInfoProvider {

    /** Application version string, e.g. "1.0.0". */
    val appVersion: String

    /** Build number (integer, auto-incremented per release). */
    val buildNumber: Int

    /** Build date string, e.g. "2026-02-23". */
    val buildDate: String

    /** Human-readable platform identifier, e.g. "Android 14" or "macOS (Java 17)". */
    val platformName: String

    /** Whether this is a debug build. */
    val isDebug: Boolean

    /** Full version display string, e.g. "1.0.0 (build 1)". */
    val fullVersionString: String
        get() = "$appVersion (build $buildNumber)"
}

/**
 * Returns the platform-specific [AppInfoProvider] implementation.
 *
 * - **Android:** Reads version from app's `PackageInfo`.
 * - **Desktop:** Reads from JVM system properties and manifest.
 */
expect fun createAppInfoProvider(): AppInfoProvider
