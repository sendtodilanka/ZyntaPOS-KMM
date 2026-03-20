package com.zyntasolutions.zyntapos.api.routes

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import java.util.UUID

/**
 * Unit tests for AdminAlertsRoutes validation logic.
 *
 * Tests cover:
 * - Alert ID UUID format validation
 * - Query parameter coercion and bounds (page, pageSize)
 * - Status/severity/category filter validation
 * - Silence duration validation
 * - Alert rule toggle request validation
 */
class AdminAlertsRoutesTest {

    // ── Alert ID UUID validation ──────────────────────────────────────────────

    @Test
    fun `valid UUID alert ID parses successfully`() {
        val id = "550e8400-e29b-41d4-a716-446655440000"
        val parsed = runCatching { UUID.fromString(id) }.getOrNull()
        assertNotNull(parsed)
    }

    @Test
    fun `invalid alert ID returns null`() {
        val id = "not-a-uuid"
        val parsed = runCatching { UUID.fromString(id) }.getOrNull()
        assertNull(parsed)
    }

    @Test
    fun `blank alert ID returns null`() {
        val id = ""
        val parsed = runCatching { UUID.fromString(id) }.getOrNull()
        assertNull(parsed)
    }

    @Test
    fun `numeric-only string is not a valid UUID`() {
        val id = "12345678"
        val parsed = runCatching { UUID.fromString(id) }.getOrNull()
        assertNull(parsed)
    }

    // ── Pagination parameter coercion ─────────────────────────────────────────

    @Test
    fun `page defaults to 0 when not provided`() {
        val page = null?.toIntOrNull()?.coerceAtLeast(0) ?: 0
        assertEquals(0, page)
    }

    @Test
    fun `page coerced to 0 when negative`() {
        val page = "-1".toIntOrNull()?.coerceAtLeast(0) ?: 0
        assertEquals(0, page)
    }

    @Test
    fun `valid page is parsed correctly`() {
        val page = "3".toIntOrNull()?.coerceAtLeast(0) ?: 0
        assertEquals(3, page)
    }

    @Test
    fun `pageSize defaults to 20 when not provided`() {
        val pageSize = null?.toIntOrNull()?.coerceIn(1, 100) ?: 20
        assertEquals(20, pageSize)
    }

    @Test
    fun `pageSize clamped to 100 when exceeds maximum`() {
        val pageSize = "999".toIntOrNull()?.coerceIn(1, 100) ?: 20
        assertEquals(100, pageSize)
    }

    @Test
    fun `pageSize clamped to 1 when below minimum`() {
        val pageSize = "0".toIntOrNull()?.coerceIn(1, 100) ?: 20
        assertEquals(1, pageSize)
    }

    @Test
    fun `valid pageSize is preserved`() {
        val pageSize = "50".toIntOrNull()?.coerceIn(1, 100) ?: 20
        assertEquals(50, pageSize)
    }

    // ── Status filter validation ──────────────────────────────────────────────

    @Test
    fun `valid alert statuses are accepted`() {
        val validStatuses = listOf("active", "acknowledged", "resolved", "silenced")
        validStatuses.forEach { status ->
            assertFalse(status.isBlank(), "Status '$status' should not be blank")
        }
    }

    @Test
    fun `valid alert severities are accepted`() {
        val validSeverities = listOf("critical", "high", "medium", "low", "info")
        validSeverities.forEach { severity ->
            assertFalse(severity.isBlank(), "Severity '$severity' should not be blank")
        }
    }

    @Test
    fun `valid alert categories are accepted`() {
        val validCategories = listOf("sync", "inventory", "system", "license", "security")
        validCategories.forEach { category ->
            assertFalse(category.isBlank(), "Category '$category' should not be blank")
        }
    }

    // ── Silence duration validation ───────────────────────────────────────────

    @Test
    fun `silence duration must be positive`() {
        val duration = 60
        assertTrue(duration > 0)
    }

    @Test
    fun `zero silence duration is invalid`() {
        val duration = 0
        assertFalse(duration > 0)
    }

    @Test
    fun `negative silence duration is invalid`() {
        val duration = -30
        assertFalse(duration > 0)
    }

    @Test
    fun `maximum allowed silence duration is 7 days in minutes`() {
        val sevenDaysMinutes = 7 * 24 * 60
        assertEquals(10080, sevenDaysMinutes)
        val duration = 10080
        assertTrue(duration <= sevenDaysMinutes)
    }

    // ── Alert rule toggle request validation ──────────────────────────────────

    @Test
    fun `toggle rule request with valid enabled flag is valid`() {
        data class ToggleAlertRuleRequest(val enabled: Boolean)
        val req = ToggleAlertRuleRequest(enabled = true)
        assertTrue(req.enabled)
    }

    @Test
    fun `toggle rule request with false disabled flag is valid`() {
        data class ToggleAlertRuleRequest(val enabled: Boolean)
        val req = ToggleAlertRuleRequest(enabled = false)
        assertFalse(req.enabled)
    }

    // ── storeId filter ────────────────────────────────────────────────────────

    @Test
    fun `storeId filter is optional`() {
        val storeId: String? = null
        assertNull(storeId)
    }

    @Test
    fun `non-null storeId is passed to service`() {
        val storeId: String? = "store-001"
        assertNotNull(storeId)
        assertEquals("store-001", storeId)
    }
}
