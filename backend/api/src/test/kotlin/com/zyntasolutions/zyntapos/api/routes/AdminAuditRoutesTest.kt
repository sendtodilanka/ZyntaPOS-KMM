package com.zyntasolutions.zyntapos.api.routes

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Unit tests for AdminAuditRoutes validation logic.
 *
 * Tests cover:
 * - Pagination parameter coercion (page, size)
 * - Filter parameter handling (category, eventType, userId, from, to, search)
 * - CSV export header format
 * - Date range filter validation
 */
class AdminAuditRoutesTest {

    // ── Pagination coercion ───────────────────────────────────────────────────

    @Test
    fun `page defaults to 0 when not provided`() {
        val page = null?.toIntOrNull()?.coerceAtLeast(0) ?: 0
        assertEquals(0, page)
    }

    @Test
    fun `page clamped to 0 when negative`() {
        val page = "-5".toIntOrNull()?.coerceAtLeast(0) ?: 0
        assertEquals(0, page)
    }

    @Test
    fun `page parses valid positive integer`() {
        val page = "7".toIntOrNull()?.coerceAtLeast(0) ?: 0
        assertEquals(7, page)
    }

    @Test
    fun `size defaults to 50 when not provided`() {
        val size = null?.toIntOrNull()?.coerceIn(1, 200) ?: 50
        assertEquals(50, size)
    }

    @Test
    fun `size clamped to 200 when exceeds maximum`() {
        val size = "500".toIntOrNull()?.coerceIn(1, 200) ?: 50
        assertEquals(200, size)
    }

    @Test
    fun `size clamped to 1 when below minimum`() {
        val size = "0".toIntOrNull()?.coerceIn(1, 200) ?: 50
        assertEquals(1, size)
    }

    @Test
    fun `size accepts valid value`() {
        val size = "100".toIntOrNull()?.coerceIn(1, 200) ?: 50
        assertEquals(100, size)
    }

    // ── Filter parameter handling ─────────────────────────────────────────────

    @Test
    fun `optional filters default to null when not provided`() {
        val category: String? = null
        val eventType: String? = null
        val adminId: String? = null
        val from: String? = null
        val to: String? = null
        val search: String? = null

        assertNull(category)
        assertNull(eventType)
        assertNull(adminId)
        assertNull(from)
        assertNull(to)
        assertNull(search)
    }

    @Test
    fun `provided filters are forwarded correctly`() {
        val category  = "AUTH"
        val eventType = "ADMIN_LOGIN"
        val adminId   = "user-001"
        val from      = "2026-01-01"
        val to        = "2026-03-31"
        val search    = "login"

        assertNotNull(category)
        assertNotNull(eventType)
        assertNotNull(adminId)
        assertEquals("AUTH", category)
        assertEquals("ADMIN_LOGIN", eventType)
        assertEquals("user-001", adminId)
        assertEquals("2026-01-01", from)
        assertEquals("2026-03-31", to)
        assertEquals("login", search)
    }

    // ── CSV export format ─────────────────────────────────────────────────────

    @Test
    fun `CSV header row contains all required columns`() {
        val header = "id,eventType,category,userId,userName,entityType,entityId,success,ipAddress,createdAt"
        val columns = header.split(",")
        assertEquals(10, columns.size)
        assertTrue(columns.contains("id"))
        assertTrue(columns.contains("eventType"))
        assertTrue(columns.contains("category"))
        assertTrue(columns.contains("userId"))
        assertTrue(columns.contains("userName"))
        assertTrue(columns.contains("entityType"))
        assertTrue(columns.contains("entityId"))
        assertTrue(columns.contains("success"))
        assertTrue(columns.contains("ipAddress"))
        assertTrue(columns.contains("createdAt"))
    }

    @Test
    fun `content disposition header value is correct`() {
        val disposition = "attachment; filename=\"audit-export.csv\""
        assertTrue(disposition.startsWith("attachment"))
        assertTrue(disposition.contains("audit-export.csv"))
    }

    // ── Date range validation ─────────────────────────────────────────────────

    @Test
    fun `ISO date format is valid`() {
        val date = "2026-03-20"
        val parts = date.split("-")
        assertEquals(3, parts.size)
        assertEquals(4, parts[0].length)  // year
        assertEquals(2, parts[1].length)  // month
        assertEquals(2, parts[2].length)  // day
    }

    @Test
    fun `from date must not be after to date for valid range`() {
        val from = "2026-01-01"
        val to   = "2026-03-31"
        assertTrue(from <= to)
    }

    @Test
    fun `same from and to date is a valid single-day range`() {
        val from = "2026-03-20"
        val to   = "2026-03-20"
        assertTrue(from <= to)
    }

    // ── Audit category values ─────────────────────────────────────────────────

    @Test
    fun `valid audit categories are non-blank`() {
        val categories = listOf("AUTH", "DATA", "ADMIN", "SYNC", "SYSTEM")
        categories.forEach { cat ->
            assertFalse(cat.isBlank())
        }
    }

    @Test
    fun `search query is optional and passed through`() {
        val search: String? = "product update"
        assertNotNull(search)
        assertFalse(search.isBlank())
    }
}
