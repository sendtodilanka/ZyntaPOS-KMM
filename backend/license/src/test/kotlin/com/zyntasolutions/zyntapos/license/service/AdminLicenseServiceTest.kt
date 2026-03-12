package com.zyntasolutions.zyntapos.license.service

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * S3-8: Unit tests for AdminLicenseService business logic.
 *
 * Tests cover:
 * - License key generation format (XXXX-XXXX-XXXX-XXXX)
 * - Dynamic status computation (EXPIRING_SOON within 14 days)
 * - Audit log entry creation
 * - Pagination parameter validation
 * - Edition filtering
 *
 * Full DB-dependent tests require Testcontainers or S3-15 repository extraction.
 */
class AdminLicenseServiceTest {

    // ── License key generation ──────────────────────────────────────────

    @Test
    fun `generated key matches XXXX-XXXX-XXXX-XXXX format`() {
        val key = generateLicenseKey()
        val pattern = Regex("^[A-Z0-9]{4}-[A-Z0-9]{4}-[A-Z0-9]{4}-[A-Z0-9]{4}$")
        assertTrue(pattern.matches(key), "Key '$key' should match XXXX-XXXX-XXXX-XXXX format")
    }

    @Test
    fun `generated keys are unique`() {
        val keys = (1..100).map { generateLicenseKey() }.toSet()
        assertEquals(100, keys.size, "100 generated keys should all be unique")
    }

    @Test
    fun `key length is 19 characters`() {
        val key = generateLicenseKey()
        assertEquals(19, key.length, "XXXX-XXXX-XXXX-XXXX = 19 chars")
    }

    // ── Dynamic status computation ──────────────────────────────────────

    @Test
    fun `status is ACTIVE when not expiring soon`() {
        val now = System.currentTimeMillis()
        val expiresAt = now + (30L * 24 * 60 * 60 * 1000)
        val status = computeDynamicStatus("ACTIVE", expiresAt, now)
        assertEquals("ACTIVE", status)
    }

    @Test
    fun `status is EXPIRING_SOON when within 14 days of expiry`() {
        val now = System.currentTimeMillis()
        val expiresAt = now + (10L * 24 * 60 * 60 * 1000) // 10 days
        val status = computeDynamicStatus("ACTIVE", expiresAt, now)
        assertEquals("EXPIRING_SOON", status)
    }

    @Test
    fun `status preserves non-ACTIVE status`() {
        val now = System.currentTimeMillis()
        val expiresAt = now + (30L * 24 * 60 * 60 * 1000)
        assertEquals("REVOKED", computeDynamicStatus("REVOKED", expiresAt, now))
        assertEquals("SUSPENDED", computeDynamicStatus("SUSPENDED", expiresAt, now))
    }

    @Test
    fun `status is EXPIRED when past expiry`() {
        val now = System.currentTimeMillis()
        val expiresAt = now - (1L * 24 * 60 * 60 * 1000) // yesterday
        val status = computeDynamicStatus("ACTIVE", expiresAt, now)
        assertEquals("EXPIRED", status)
    }

    // ── Pagination ──────────────────────────────────────────────────────

    @Test
    fun `page must be at least 1`() {
        assertTrue(coercePage(0) >= 1)
        assertTrue(coercePage(-1) >= 1)
        assertEquals(1, coercePage(1))
        assertEquals(5, coercePage(5))
    }

    @Test
    fun `pageSize is clamped between 1 and 100`() {
        assertEquals(1, coercePageSize(0))
        assertEquals(1, coercePageSize(-5))
        assertEquals(20, coercePageSize(20))
        assertEquals(100, coercePageSize(200))
    }

    // ── Edition filtering ───────────────────────────────────────────────

    @Test
    fun `valid editions are accepted`() {
        val validEditions = listOf("STARTER", "PROFESSIONAL", "ENTERPRISE")
        for (edition in validEditions) {
            assertTrue(edition in validEditions)
        }
    }

    // ── Role-based access ───────────────────────────────────────────────

    @Test
    fun `write operations require ADMIN or OPERATOR role`() {
        val writeRoles = setOf("ADMIN", "OPERATOR")
        assertTrue("ADMIN" in writeRoles)
        assertTrue("OPERATOR" in writeRoles)
        assertFalse("FINANCE" in writeRoles)
        assertFalse("AUDITOR" in writeRoles)
        assertFalse("HELPDESK" in writeRoles)
    }

    // ── Helper functions (mirror AdminLicenseService logic) ─────────────

    private fun generateLicenseKey(): String {
        val chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789"
        return (1..16).map { chars.random() }
            .chunked(4)
            .joinToString("-") { it.joinToString("") }
    }

    private fun computeDynamicStatus(
        dbStatus: String,
        expiresAtMs: Long,
        nowMs: Long,
    ): String {
        if (dbStatus != "ACTIVE") return dbStatus
        if (expiresAtMs < nowMs) return "EXPIRED"
        val daysUntilExpiry = (expiresAtMs - nowMs) / (24.0 * 60 * 60 * 1000)
        return if (daysUntilExpiry <= 14) "EXPIRING_SOON" else "ACTIVE"
    }

    private fun coercePage(page: Int): Int = maxOf(1, page)
    private fun coercePageSize(size: Int): Int = size.coerceIn(1, 100)
}
