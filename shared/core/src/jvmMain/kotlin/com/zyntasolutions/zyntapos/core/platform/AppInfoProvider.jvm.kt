package com.zyntasolutions.zyntapos.core.platform

// ─────────────────────────────────────────────────────────────────────────────
// Desktop (JVM) actual — AppInfoProvider
//
// Reads app version from the JAR manifest or falls back to system properties.
// Build date is read from a system property set in the Gradle build script.
// Platform name combines OS and JVM version for diagnostics.
// ─────────────────────────────────────────────────────────────────────────────

/**
 * JVM Desktop [AppInfoProvider] implementation.
 *
 * Reads build metadata from:
 * - JAR manifest `Implementation-Version` header
 * - System property `app.version` (set via Gradle `-D` flag)
 * - System property `app.build.date`
 * - OS and JVM runtime info from system properties
 */
class JvmAppInfoProvider : AppInfoProvider {

    override val appVersion: String =
        System.getProperty("app.version")
            ?: JvmAppInfoProvider::class.java.`package`?.implementationVersion
            ?: "1.0.0"

    override val buildDate: String =
        System.getProperty("app.build.date") ?: "2026-02-23"

    override val platformName: String = buildString {
        val osName = System.getProperty("os.name", "Unknown OS")
        val osArch = System.getProperty("os.arch", "")
        val javaVersion = System.getProperty("java.version", "?")
        append("$osName $osArch (Java $javaVersion)")
    }

    override val isDebug: Boolean =
        System.getProperty("app.debug")?.toBoolean() ?: false
}

actual fun createAppInfoProvider(): AppInfoProvider = JvmAppInfoProvider()
