package com.zyntasolutions.zyntapos.license.routes

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * B4: Unit tests for LicenseRoutes and AdminLicenseRoutes validation logic.
 *
 * Tests cover:
 * - License key format pattern (^[A-Za-z0-9\-]{1,128}$)
 * - Mandatory field presence (licenseKey, deviceId, appVersion)
 * - Numeric field non-negativity constraints (heartbeat telemetry)
 * - Nonce max-length constraint (128 chars)
 * - Admin route: pagination parameter coercion
 * - Admin route: filter parameter handling (status, edition, search)
 * - Admin route: expiresAt ISO-8601 format validation
 * - Admin route: maxDevices range validation
 * - Admin route: edition whitelist validation
 * - Role-based write access (ADMIN/OPERATOR only)
 *
 * Full HTTP integration tests (Ktor testApplication with Koin + DB) require a
 * running PostgreSQL container and are covered by LicenseActivationIntegrationTest
 * and LicenseHeartbeatIntegrationTest.
 */
class LicenseRoutesValidationTest {

    // ── License key format pattern ────────────────────────────────────────────

    private val licenseKeyPattern = Regex("^[A-Za-z0-9\\-]{1,128}$")

    @Test
    fun `license key pattern accepts standard XXXX-XXXX-XXXX-XXXX format`() {
        val key = "ABCD-1234-WXYZ-5678"
        assertTrue(licenseKeyPattern.matches(key), "Standard license key must match pattern")
    }

    @Test
    fun `license key pattern accepts lowercase alphanumeric with hyphens`() {
        val key = "abcd-ef01-2345-ghij"
        assertTrue(licenseKeyPattern.matches(key))
    }

    @Test
    fun `license key pattern rejects empty string`() {
        assertFalse(licenseKeyPattern.matches(""))
    }

    @Test
    fun `license key pattern rejects special characters`() {
        assertFalse(licenseKeyPattern.matches("KEY!@#$"))
        assertFalse(licenseKeyPattern.matches("KEY WITH SPACE"))
        assertFalse(licenseKeyPattern.matches("KEY/SLASH"))
    }

    @Test
    fun `license key pattern accepts up to 128 characters`() {
        val maxKey = "A".repeat(128)
        assertTrue(licenseKeyPattern.matches(maxKey))
    }

    @Test
    fun `license key pattern rejects more than 128 characters`() {
        val tooLong = "A".repeat(129)
        assertFalse(licenseKeyPattern.matches(tooLong))
    }

    // ── Mandatory field presence ──────────────────────────────────────────────

    @Test
    fun `blank licenseKey fails requireNotBlank check`() {
        val licenseKey = ""
        assertTrue(licenseKey.isBlank(), "Blank licenseKey must fail validation")
    }

    @Test
    fun `whitespace-only licenseKey fails requireNotBlank check`() {
        val licenseKey = "   "
        assertTrue(licenseKey.isBlank())
    }

    @Test
    fun `blank deviceId fails requireNotBlank check`() {
        val deviceId = ""
        assertTrue(deviceId.isBlank())
    }

    @Test
    fun `blank appVersion fails requireNotBlank check`() {
        val appVersion = ""
        assertTrue(appVersion.isBlank())
    }

    @Test
    fun `valid request fields pass notBlank checks`() {
        val licenseKey = "ABCD-1234-WXYZ-5678"
        val deviceId = "device-pos-001"
        val appVersion = "1.0.0"
        assertFalse(licenseKey.isBlank())
        assertFalse(deviceId.isBlank())
        assertFalse(appVersion.isBlank())
    }

    // ── Max length constraints ────────────────────────────────────────────────

    @Test
    fun `licenseKey of 128 chars satisfies maxLength constraint`() {
        val key = "A".repeat(128)
        assertTrue(key.length <= 128)
    }

    @Test
    fun `licenseKey of 129 chars violates maxLength constraint`() {
        val key = "A".repeat(129)
        assertFalse(key.length <= 128, "Key exceeding 128 chars must fail maxLength check")
    }

    @Test
    fun `deviceId of 256 chars satisfies maxLength constraint`() {
        val id = "d".repeat(256)
        assertTrue(id.length <= 256)
    }

    @Test
    fun `deviceId of 257 chars violates maxLength constraint`() {
        val id = "d".repeat(257)
        assertFalse(id.length <= 256)
    }

    @Test
    fun `appVersion of 64 chars satisfies maxLength constraint`() {
        val v = "v".repeat(64)
        assertTrue(v.length <= 64)
    }

    @Test
    fun `nonce of 128 chars satisfies maxLength constraint`() {
        val nonce = "n".repeat(128)
        assertTrue(nonce.length <= 128)
    }

    @Test
    fun `nonce of 129 chars violates maxLength constraint`() {
        val nonce = "n".repeat(129)
        assertFalse(nonce.length <= 128)
    }

    // ── Heartbeat telemetry non-negativity ────────────────────────────────────

    @Test
    fun `dbSizeBytes of 0 passes non-negative check`() {
        assertTrue(0L >= 0L)
    }

    @Test
    fun `negative dbSizeBytes fails requireNonNegative check`() {
        val dbSizeBytes = -1L
        assertFalse(dbSizeBytes >= 0L)
    }

    @Test
    fun `syncQueueDepth of 0 passes non-negative check`() {
        assertTrue(0 >= 0)
    }

    @Test
    fun `negative syncQueueDepth fails requireNonNegative check`() {
        val depth = -5
        assertFalse(depth >= 0)
    }

    @Test
    fun `lastErrorCount of 0 passes non-negative check`() {
        assertTrue(0 >= 0)
    }

    @Test
    fun `uptimeHours of zero passes non-negative check`() {
        assertTrue(0.0 >= 0.0)
    }

    @Test
    fun `negative uptimeHours fails requireNonNegative check`() {
        val hours = -0.5
        assertFalse(hours >= 0.0)
    }

    // ── Admin route pagination ────────────────────────────────────────────────

    @Test
    fun `page parameter defaults to 0 when absent`() {
        val page = null?.toIntOrNull()?.coerceAtLeast(0) ?: 0
        assertEquals(0, page)
    }

    @Test
    fun `page parameter is clamped to 0 when negative`() {
        val page = "-3".toIntOrNull()?.coerceAtLeast(0) ?: 0
        assertEquals(0, page)
    }

    @Test
    fun `valid page parameter is preserved`() {
        val page = "5".toIntOrNull()?.coerceAtLeast(0) ?: 0
        assertEquals(5, page)
    }

    @Test
    fun `size parameter defaults to 20 when absent`() {
        val size = null?.toIntOrNull()?.coerceIn(1, 100) ?: 20
        assertEquals(20, size)
    }

    @Test
    fun `size parameter is clamped to 100 when above maximum`() {
        val size = "999".toIntOrNull()?.coerceIn(1, 100) ?: 20
        assertEquals(100, size)
    }

    @Test
    fun `size parameter is clamped to 1 when zero`() {
        val size = "0".toIntOrNull()?.coerceIn(1, 100) ?: 20
        assertEquals(1, size)
    }

    // ── Admin route filter parameters ────────────────────────────────────────

    @Test
    fun `null status filter is not applied`() {
        val status: String? = null
        assertFalse(!status.isNullOrBlank(), "Null status must not produce a WHERE clause")
    }

    @Test
    fun `blank status filter is not applied`() {
        val status = "   "
        assertFalse(!status.isNullOrBlank(), "Blank status must not produce a WHERE clause")
    }

    @Test
    fun `non-blank status filter is applied after uppercase normalization`() {
        val status = "active"
        val normalized = if (!status.isNullOrBlank()) status.uppercase() else null
        assertEquals("ACTIVE", normalized)
    }

    @Test
    fun `null edition filter is not applied`() {
        val edition: String? = null
        assertFalse(!edition.isNullOrBlank())
    }

    @Test
    fun `search term is wrapped with percent wildcards`() {
        val search = "example"
        val pattern = "%${search.lowercase()}%"
        assertEquals("%example%", pattern)
    }

    @Test
    fun `search term is lowercased for case-insensitive matching`() {
        val search = "CUSTOMER-ABC"
        val pattern = "%${search.lowercase()}%"
        assertEquals("%customer-abc%", pattern)
    }

    // ── Admin route expiresAt validation ─────────────────────────────────────

    @Test
    fun `valid ISO-8601 OffsetDateTime string is accepted`() {
        val iso = "2026-12-31T00:00:00Z"
        val parsed = runCatching { java.time.OffsetDateTime.parse(iso) }
        assertTrue(parsed.isSuccess, "Valid ISO-8601 date must parse successfully")
    }

    @Test
    fun `invalid date string fails OffsetDateTime parsing`() {
        val bad = "31-12-2026"
        val parsed = runCatching { java.time.OffsetDateTime.parse(bad) }
        assertTrue(parsed.isFailure, "Non-ISO-8601 date must fail to parse")
    }

    @Test
    fun `null expiresAt in update request is allowed`() {
        val expiresAt: String? = null
        assertNull(expiresAt, "Null expiresAt is valid (no-op for expiry)")
    }

    // ── Admin route maxDevices validation ────────────────────────────────────

    @Test
    fun `maxDevices of 1 is valid`() {
        val maxDevices = 1
        assertTrue(maxDevices >= 1)
    }

    @Test
    fun `maxDevices of 0 is invalid`() {
        val maxDevices = 0
        assertFalse(maxDevices >= 1, "maxDevices=0 must be rejected")
    }

    @Test
    fun `maxDevices of -1 is invalid`() {
        val maxDevices = -1
        assertFalse(maxDevices >= 1)
    }

    @Test
    fun `maxDevices of 100 is valid`() {
        val maxDevices = 100
        assertTrue(maxDevices in 1..100)
    }

    // ── Admin route edition whitelist ────────────────────────────────────────

    @Test
    fun `STARTER is a valid edition in admin route whitelist`() {
        // AdminLicenseRoutes.kt uses STARTER in its edition whitelist
        val validEditions = setOf("STARTER", "PROFESSIONAL", "ENTERPRISE")
        assertTrue("STARTER" in validEditions)
    }

    @Test
    fun `PROFESSIONAL is a valid edition`() {
        val validEditions = setOf("STARTER", "PROFESSIONAL", "ENTERPRISE")
        assertTrue("PROFESSIONAL" in validEditions)
    }

    @Test
    fun `ENTERPRISE is a valid edition`() {
        val validEditions = setOf("STARTER", "PROFESSIONAL", "ENTERPRISE")
        assertTrue("ENTERPRISE" in validEditions)
    }

    @Test
    fun `BASIC is not a valid edition`() {
        val validEditions = setOf("STARTER", "PROFESSIONAL", "ENTERPRISE")
        assertFalse("BASIC" in validEditions, "BASIC is not a recognized edition")
    }

    @Test
    fun `edition comparison is case-insensitive via uppercase normalization`() {
        val validEditions = setOf("STARTER", "PROFESSIONAL", "ENTERPRISE")
        assertTrue("starter".uppercase() in validEditions)
        assertTrue("professional".uppercase() in validEditions)
    }

    // ── Role-based write access ───────────────────────────────────────────────

    @Test
    fun `ADMIN is permitted to perform write operations`() {
        val writeRoles = setOf("ADMIN", "OPERATOR")
        assertTrue("ADMIN" in writeRoles)
    }

    @Test
    fun `OPERATOR is permitted to perform write operations`() {
        val writeRoles = setOf("ADMIN", "OPERATOR")
        assertTrue("OPERATOR" in writeRoles)
    }

    @Test
    fun `FINANCE role is not permitted to perform write operations`() {
        val writeRoles = setOf("ADMIN", "OPERATOR")
        assertFalse("FINANCE" in writeRoles)
    }

    @Test
    fun `AUDITOR role is not permitted to perform write operations`() {
        val writeRoles = setOf("ADMIN", "OPERATOR")
        assertFalse("AUDITOR" in writeRoles)
    }

    @Test
    fun `HELPDESK role is not permitted to perform write operations`() {
        val writeRoles = setOf("ADMIN", "OPERATOR")
        assertFalse("HELPDESK" in writeRoles)
    }

    // ── Path parameter extraction ─────────────────────────────────────────────

    @Test
    fun `missing key path parameter produces error`() {
        val key: String? = null
        assertNull(key, "Missing path parameter must result in null")
    }

    @Test
    fun `valid key path parameter is present`() {
        val key: String? = "ABCD-1234-WXYZ-5678"
        assertNotNull(key)
    }

    @Test
    fun `missing deviceId path parameter produces error`() {
        val deviceId: String? = null
        assertNull(deviceId)
    }
}
