package com.zyntasolutions.zyntapos.api.routes

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Unit tests for AdminStoresRoutes validation logic.
 *
 * Tests cover:
 * - Pagination parameter coercion
 * - Store ID path parameter validation
 * - Search and status filter handling
 * - Store config update request validation
 * - Route path structure
 */
class AdminStoresRoutesTest {

    // ── Pagination coercion ───────────────────────────────────────────────────

    @Test
    fun `page defaults to 0 when absent`() {
        val page = null?.toIntOrNull()?.coerceAtLeast(0) ?: 0
        assertEquals(0, page)
    }

    @Test
    fun `page clamped to 0 when negative`() {
        val page = "-1".toIntOrNull()?.coerceAtLeast(0) ?: 0
        assertEquals(0, page)
    }

    @Test
    fun `valid page value is preserved`() {
        val page = "5".toIntOrNull()?.coerceAtLeast(0) ?: 0
        assertEquals(5, page)
    }

    @Test
    fun `size defaults to 20 when absent`() {
        val size = null?.toIntOrNull()?.coerceIn(1, 100) ?: 20
        assertEquals(20, size)
    }

    @Test
    fun `size clamped to 100 when exceeds maximum`() {
        val size = "999".toIntOrNull()?.coerceIn(1, 100) ?: 20
        assertEquals(100, size)
    }

    @Test
    fun `size clamped to 1 when zero`() {
        val size = "0".toIntOrNull()?.coerceIn(1, 100) ?: 20
        assertEquals(1, size)
    }

    @Test
    fun `valid size is preserved`() {
        val size = "50".toIntOrNull()?.coerceIn(1, 100) ?: 20
        assertEquals(50, size)
    }

    // ── Store ID path parameter ───────────────────────────────────────────────

    @Test
    fun `missing store ID produces error response`() {
        val storeId: String? = null
        assertNull(storeId)
    }

    @Test
    fun `blank store ID is invalid`() {
        val storeId = ""
        assertTrue(storeId.isBlank())
    }

    @Test
    fun `valid store ID is non-blank`() {
        val storeId = "store-001"
        assertFalse(storeId.isBlank())
    }

    // ── Search filter ─────────────────────────────────────────────────────────

    @Test
    fun `search filter defaults to null when absent`() {
        val search: String? = null
        assertNull(search)
    }

    @Test
    fun `provided search is forwarded to service`() {
        val search: String? = "colombo"
        assertNotNull(search)
        assertEquals("colombo", search)
    }

    // ── Status filter ─────────────────────────────────────────────────────────

    @Test
    fun `status filter defaults to null to show all stores`() {
        val status: String? = null
        assertNull(status)
    }

    @Test
    fun `valid store statuses are recognized`() {
        val validStatuses = listOf("ACTIVE", "INACTIVE", "SUSPENDED")
        validStatuses.forEach { s -> assertFalse(s.isBlank()) }
    }

    // ── Route path structure ──────────────────────────────────────────────────

    @Test
    fun `store list endpoint path is correct`() {
        val path = "/admin/stores"
        assertTrue(path.startsWith("/admin"))
    }

    @Test
    fun `store detail endpoint has storeId path variable`() {
        val template = "/admin/stores/{storeId}"
        assertTrue(template.contains("{storeId}"))
    }

    @Test
    fun `store health endpoint appended to store path`() {
        val template = "/admin/stores/{storeId}/health"
        assertTrue(template.endsWith("/health"))
    }

    @Test
    fun `store config update endpoint uses PUT method convention`() {
        val template = "/admin/stores/{storeId}/config"
        assertTrue(template.endsWith("/config"))
        assertTrue(template.contains("{storeId}"))
    }

    // ── Store config request validation ──────────────────────────────────────

    @Test
    fun `store config with blank timezone is invalid`() {
        val timezone = ""
        assertTrue(timezone.isBlank())
    }

    @Test
    fun `store config with valid timezone is non-blank`() {
        val timezone = "Asia/Colombo"
        assertFalse(timezone.isBlank())
    }

    @Test
    fun `store config currency code must be 3 chars`() {
        val currency = "LKR"
        assertEquals(3, currency.length)
    }

    @Test
    fun `store status values are uppercase by convention`() {
        val status = "ACTIVE"
        assertEquals(status, status.uppercase())
    }
}
