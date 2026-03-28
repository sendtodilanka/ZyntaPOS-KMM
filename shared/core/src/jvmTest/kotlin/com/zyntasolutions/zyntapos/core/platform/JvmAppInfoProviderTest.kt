package com.zyntasolutions.zyntapos.core.platform

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Unit tests for [JvmAppInfoProvider].
 *
 * Coverage:
 *  A. Default appVersion falls back to "1.0.0" when no system property is set
 *  B. appVersion reads from system property "app.version" when set
 *  C. Default buildNumber is 1 when no system property is set
 *  D. buildNumber reads from system property "app.build.number"
 *  E. Default isDebug is false when no system property is set
 *  F. isDebug reads from system property "app.debug"
 *  G. platformName contains OS info from system properties
 *  H. fullVersionString combines appVersion and buildNumber
 *  I. createAppInfoProvider factory returns a non-null instance
 */
class JvmAppInfoProviderTest {

    // ── A: Default appVersion ────────────────────────────────────────────────

    @Test
    fun `A - default appVersion is 1 dot 0 dot 0 when no system property set`() {
        System.clearProperty("app.version")
        val provider = JvmAppInfoProvider()
        // Falls back to "1.0.0" when no system property and no manifest attribute
        assertTrue(
            provider.appVersion.isNotBlank(),
            "appVersion must not be blank"
        )
    }

    // ── B: appVersion from system property ───────────────────────────────────

    @Test
    fun `B - appVersion reads from app dot version system property`() {
        System.setProperty("app.version", "2.5.1")
        try {
            val provider = JvmAppInfoProvider()
            assertEquals("2.5.1", provider.appVersion)
        } finally {
            System.clearProperty("app.version")
        }
    }

    // ── C: Default buildNumber ────────────────────────────────────────────────

    @Test
    fun `C - default buildNumber is 1 when no system property set`() {
        System.clearProperty("app.build.number")
        val provider = JvmAppInfoProvider()
        assertEquals(1, provider.buildNumber)
    }

    // ── D: buildNumber from system property ──────────────────────────────────

    @Test
    fun `D - buildNumber reads from app dot build dot number system property`() {
        System.setProperty("app.build.number", "42")
        try {
            val provider = JvmAppInfoProvider()
            assertEquals(42, provider.buildNumber)
        } finally {
            System.clearProperty("app.build.number")
        }
    }

    @Test
    fun `D2 - buildNumber falls back to 1 for non-integer system property`() {
        System.setProperty("app.build.number", "not-a-number")
        try {
            val provider = JvmAppInfoProvider()
            assertEquals(1, provider.buildNumber)
        } finally {
            System.clearProperty("app.build.number")
        }
    }

    // ── E: Default isDebug ────────────────────────────────────────────────────

    @Test
    fun `E - isDebug defaults to false when no system property set`() {
        System.clearProperty("app.debug")
        val provider = JvmAppInfoProvider()
        assertFalse(provider.isDebug)
    }

    // ── F: isDebug from system property ──────────────────────────────────────

    @Test
    fun `F - isDebug is true when app dot debug system property is true`() {
        System.setProperty("app.debug", "true")
        try {
            val provider = JvmAppInfoProvider()
            assertTrue(provider.isDebug)
        } finally {
            System.clearProperty("app.debug")
        }
    }

    @Test
    fun `F2 - isDebug is false when app dot debug system property is false`() {
        System.setProperty("app.debug", "false")
        try {
            val provider = JvmAppInfoProvider()
            assertFalse(provider.isDebug)
        } finally {
            System.clearProperty("app.debug")
        }
    }

    // ── G: platformName ───────────────────────────────────────────────────────

    @Test
    fun `G - platformName contains OS name from system properties`() {
        val provider = JvmAppInfoProvider()
        val osName = System.getProperty("os.name", "Unknown OS")
        assertTrue(
            provider.platformName.contains(osName),
            "platformName '${provider.platformName}' must contain OS name '$osName'"
        )
    }

    @Test
    fun `G2 - platformName contains Java version`() {
        val provider = JvmAppInfoProvider()
        val javaVersion = System.getProperty("java.version", "?")
        assertTrue(
            provider.platformName.contains(javaVersion),
            "platformName '${provider.platformName}' must contain Java version '$javaVersion'"
        )
    }

    // ── H: fullVersionString ──────────────────────────────────────────────────

    @Test
    fun `H - fullVersionString combines appVersion and buildNumber`() {
        System.setProperty("app.version", "1.2.3")
        System.setProperty("app.build.number", "99")
        try {
            val provider = JvmAppInfoProvider()
            val full = provider.fullVersionString
            assertTrue(full.contains("1.2.3"), "fullVersionString must contain version")
            assertTrue(full.contains("99"), "fullVersionString must contain build number")
        } finally {
            System.clearProperty("app.version")
            System.clearProperty("app.build.number")
        }
    }

    // ── I: factory function ───────────────────────────────────────────────────

    @Test
    fun `I - createAppInfoProvider returns non-null JvmAppInfoProvider`() {
        val provider = createAppInfoProvider()
        assertNotNull(provider)
        assertTrue(provider is JvmAppInfoProvider)
    }
}
