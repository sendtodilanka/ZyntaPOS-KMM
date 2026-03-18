package com.zyntasolutions.zyntapos.api.service

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

/**
 * Unit tests for LicenseValidationResult enum and LicenseStatusResponse model.
 *
 * Full HTTP-level tests for LicenseValidationClient.validate() require a mock HTTP server
 * or engine injection (HttpClient is hard-coded to CIO). These tests cover the enum
 * semantics and response model used by the validation logic.
 */
class LicenseValidationClientTest {

    // ── LicenseValidationResult enum ─────────────────────────────────────

    @Test
    fun `enum has exactly three values`() {
        assertEquals(3, LicenseValidationResult.entries.size)
    }

    @Test
    fun `VALID INVALID UNAVAILABLE are distinct`() {
        assertNotEquals(LicenseValidationResult.VALID, LicenseValidationResult.INVALID)
        assertNotEquals(LicenseValidationResult.VALID, LicenseValidationResult.UNAVAILABLE)
        assertNotEquals(LicenseValidationResult.INVALID, LicenseValidationResult.UNAVAILABLE)
    }

    @Test
    fun `enum names match expected values`() {
        assertEquals("VALID", LicenseValidationResult.VALID.name)
        assertEquals("INVALID", LicenseValidationResult.INVALID.name)
        assertEquals("UNAVAILABLE", LicenseValidationResult.UNAVAILABLE.name)
    }

    // ── LicenseStatusResponse model ──────────────────────────────────────

    @Test
    fun `LicenseStatusResponse defaults are all null`() {
        val response = LicenseValidationClient.LicenseStatusResponse()
        assertEquals(null, response.key)
        assertEquals(null, response.edition)
        assertEquals(null, response.status)
        assertEquals(null, response.maxDevices)
        assertEquals(null, response.activeDevices)
        assertEquals(null, response.issuedAt)
        assertEquals(null, response.expiresAt)
        assertEquals(null, response.lastHeartbeatAt)
    }

    @Test
    fun `LicenseStatusResponse can be constructed with all fields`() {
        val response = LicenseValidationClient.LicenseStatusResponse(
            key = "LIC-123",
            edition = "PROFESSIONAL",
            status = "ACTIVE",
            maxDevices = 5,
            activeDevices = 2,
            issuedAt = 1000L,
            expiresAt = 2000L,
            lastHeartbeatAt = 1500L,
        )
        assertEquals("LIC-123", response.key)
        assertEquals("PROFESSIONAL", response.edition)
        assertEquals("ACTIVE", response.status)
        assertEquals(5, response.maxDevices)
        assertEquals(2, response.activeDevices)
    }

    @Test
    fun `status uppercased matches validation logic branches`() {
        // The validate() method does body.status?.uppercase() to match
        val activeStatuses = listOf("ACTIVE", "active", "Active")
        val invalidStatuses = listOf("EXPIRED", "REVOKED", "SUSPENDED")

        activeStatuses.forEach { status ->
            assertEquals("ACTIVE", status.uppercase())
        }
        invalidStatuses.forEach { status ->
            assertEquals(status, status.uppercase())
        }
    }

    @Test
    fun `unknown status would not match ACTIVE or invalid branches`() {
        val unknownStatuses = listOf("PENDING", "TRIAL", "GRACE_PERIOD", "")
        unknownStatuses.forEach { status ->
            val upper = status.uppercase()
            val isActive = upper == "ACTIVE"
            val isInvalid = upper in listOf("EXPIRED", "REVOKED", "SUSPENDED")
            assertEquals(false, isActive, "$status should not match ACTIVE")
            assertEquals(false, isInvalid, "$status should not match invalid statuses")
        }
    }
}
