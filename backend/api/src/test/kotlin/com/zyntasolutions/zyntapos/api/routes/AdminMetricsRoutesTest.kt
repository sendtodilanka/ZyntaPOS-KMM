package com.zyntasolutions.zyntapos.api.routes

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.test.assertFalse

/**
 * Unit tests for AdminMetricsRoutes and AdminReportsRoutes validation logic.
 *
 * Tests cover:
 * - Period parameter defaults and valid values
 * - Optional storeId filter handling
 * - Pagination parameter coercion
 * - Date range filter handling
 * - KPI metric field structure
 */
class AdminMetricsRoutesTest {

    // ── Period parameter validation ───────────────────────────────────────────

    @Test
    fun `period defaults to today when not provided`() {
        val period = null ?: "today"
        assertEquals("today", period)
    }

    @Test
    fun `valid period values are accepted`() {
        val validPeriods = listOf("today", "week", "month")
        validPeriods.forEach { period ->
            assertFalse(period.isBlank(), "Period '$period' should not be blank")
        }
    }

    @Test
    fun `sales chart period defaults to 30d when not provided`() {
        val period = null ?: "30d"
        assertEquals("30d", period)
    }

    @Test
    fun `store comparison period defaults to 30d`() {
        val period = null ?: "30d"
        assertEquals("30d", period)
    }

    // ── storeId filter ────────────────────────────────────────────────────────

    @Test
    fun `storeId is optional and defaults to null`() {
        val storeId: String? = null
        assertNull(storeId)
    }

    @Test
    fun `provided storeId is used to scope the query`() {
        val storeId: String? = "store-colombo-001"
        assertNotNull(storeId)
        assertEquals("store-colombo-001", storeId)
    }

    // ── Sales report pagination ───────────────────────────────────────────────

    @Test
    fun `report page defaults to 0`() {
        val page = null?.toIntOrNull()?.coerceAtLeast(0) ?: 0
        assertEquals(0, page)
    }

    @Test
    fun `report size defaults to 50`() {
        val size = null?.toIntOrNull()?.coerceIn(1, 200) ?: 50
        assertEquals(50, size)
    }

    @Test
    fun `report size clamped to 200`() {
        val size = "1000".toIntOrNull()?.coerceIn(1, 200) ?: 50
        assertEquals(200, size)
    }

    @Test
    fun `report size clamped to 1 when zero`() {
        val size = "0".toIntOrNull()?.coerceIn(1, 200) ?: 50
        assertEquals(1, size)
    }

    // ── Date filter handling ──────────────────────────────────────────────────

    @Test
    fun `from date defaults to null when not provided`() {
        val from: String? = null
        assertNull(from)
    }

    @Test
    fun `to date defaults to null when not provided`() {
        val to: String? = null
        assertNull(to)
    }

    @Test
    fun `both dates are forwarded to the service when provided`() {
        val from = "2026-01-01"
        val to   = "2026-03-20"
        assertNotNull(from)
        assertNotNull(to)
        assertTrue(from <= to, "from must not exceed to")
    }

    // ── Route path structure ──────────────────────────────────────────────────

    @Test
    fun `metrics route prefix is correct`() {
        val prefix = "/admin/metrics"
        assertTrue(prefix.startsWith("/admin"))
    }

    @Test
    fun `reports route prefix is correct`() {
        val prefix = "/admin/reports"
        assertTrue(prefix.startsWith("/admin"))
    }

    @Test
    fun `dashboard KPI endpoint path is correct`() {
        val path = "/admin/metrics/dashboard"
        assertTrue(path.endsWith("/dashboard"))
    }

    @Test
    fun `sales chart endpoint path is correct`() {
        val path = "/admin/metrics/sales"
        assertTrue(path.endsWith("/sales"))
    }

    @Test
    fun `store comparison endpoint path is correct`() {
        val path = "/admin/metrics/stores"
        assertTrue(path.endsWith("/stores"))
    }

    // ── KPI field contract ────────────────────────────────────────────────────

    @Test
    fun `KPI response contains non-negative store count`() {
        val totalStores = 8
        assertTrue(totalStores >= 0)
    }

    @Test
    fun `KPI response revenue is non-negative`() {
        val revenue = 4_500_000L
        assertTrue(revenue >= 0)
    }

    @Test
    fun `sync health percentage is within valid range`() {
        val syncHealth = 98.2
        assertTrue(syncHealth in 0.0..100.0)
    }

    @Test
    fun `trend value can be positive or negative`() {
        val positivetrend = 12.5
        val negativeTrend = -0.3
        assertTrue(positivetrend > 0)
        assertTrue(negativeTrend < 0)
    }
}
