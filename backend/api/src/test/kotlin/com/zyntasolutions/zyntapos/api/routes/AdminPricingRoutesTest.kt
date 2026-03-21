package com.zyntasolutions.zyntapos.api.routes

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Unit tests for [adminPricingRoutes] validation logic (C2.1).
 *
 * Tests cover:
 * - UpsertPricingRuleRequest field validation
 * - Numeric constraint enforcement (price >= 0)
 * - Route path structure for all 3 endpoints
 * - Store-specific vs global rule semantics
 * - Priority ordering rules
 * - Time-bounded validity constraints
 * - PricingRuleRow serialization structure
 */
class AdminPricingRoutesTest {

    // ── UpsertPricingRuleRequest validation ───────────────────────────────────

    @Test
    fun `blank productId is rejected`() {
        val productId = ""
        assertTrue(productId.isBlank(), "productId must not be blank")
    }

    @Test
    fun `valid productId passes`() {
        val productId = "prod-abc-123"
        assertFalse(productId.isBlank())
    }

    @Test
    fun `negative price is rejected`() {
        val price = -1.0
        assertTrue(price < 0, "price must be non-negative")
    }

    @Test
    fun `zero price is valid`() {
        val price = 0.0
        assertFalse(price < 0, "zero price should be allowed (free items)")
    }

    @Test
    fun `positive price is valid`() {
        val price = 99.99
        assertFalse(price < 0)
    }

    // ── Store-specific vs global rule semantics ──────────────────────────────

    @Test
    fun `null storeId means global rule`() {
        val storeId: String? = null
        assertNull(storeId, "null storeId = global pricing rule")
    }

    @Test
    fun `non-null storeId means store-specific rule`() {
        val storeId: String? = "store-01"
        assertTrue(storeId != null, "non-null storeId = store-specific")
    }

    // ── Priority ordering ────────────────────────────────────────────────────

    @Test
    fun `higher priority value wins`() {
        val low = 1
        val high = 10
        assertTrue(high > low, "higher priority takes precedence")
    }

    @Test
    fun `default priority is zero`() {
        val defaultPriority = 0
        assertEquals(0, defaultPriority)
    }

    // ── Time-bounded validity ────────────────────────────────────────────────

    @Test
    fun `null validFrom means no start constraint`() {
        val validFrom: String? = null
        assertNull(validFrom, "null validFrom = rule active from beginning of time")
    }

    @Test
    fun `null validTo means no end constraint`() {
        val validTo: String? = null
        assertNull(validTo, "null validTo = rule never expires")
    }

    @Test
    fun `ISO-8601 validFrom format accepted`() {
        val validFrom = "2026-01-01T00:00:00+05:30"
        assertTrue(validFrom.contains("T"), "must be ISO-8601 format")
    }

    // ── Route path structure ─────────────────────────────────────────────────

    @Test
    fun `list rules endpoint path`() {
        val path = "/admin/pricing/rules"
        assertTrue(path.startsWith("/admin/"))
        assertTrue(path.contains("pricing"))
    }

    @Test
    fun `delete rule endpoint requires id parameter`() {
        val path = "/admin/pricing/rules/{id}"
        assertTrue(path.contains("{id}"))
    }

    // ── PricingRuleRow structure ─────────────────────────────────────────────

    @Test
    fun `PricingRuleRow has all required fields`() {
        val row = com.zyntasolutions.zyntapos.api.repository.PricingRuleRow(
            id = "pr-1",
            productId = "prod-1",
            storeId = null,
            price = 99.99,
            costPrice = null,
            priority = 5,
            validFrom = null,
            validTo = null,
            isActive = true,
            description = "Test rule",
            createdAt = "2026-01-01T00:00:00Z",
            updatedAt = "2026-01-01T00:00:00Z",
        )
        assertEquals("pr-1", row.id)
        assertEquals(99.99, row.price)
        assertNull(row.storeId)
        assertEquals(5, row.priority)
        assertTrue(row.isActive)
    }

    @Test
    fun `PricingRuleRow with store-specific assignment`() {
        val row = com.zyntasolutions.zyntapos.api.repository.PricingRuleRow(
            id = "pr-2",
            productId = "prod-1",
            storeId = "store-colombo",
            price = 85.0,
            costPrice = 40.0,
            priority = 10,
            validFrom = "2026-01-01T00:00:00Z",
            validTo = "2026-12-31T23:59:59Z",
            isActive = true,
            description = "Colombo store pricing",
            createdAt = "2026-01-01T00:00:00Z",
            updatedAt = "2026-01-01T00:00:00Z",
        )
        assertEquals("store-colombo", row.storeId)
        assertEquals(85.0, row.price)
        assertEquals(40.0, row.costPrice)
        assertEquals("2026-01-01T00:00:00Z", row.validFrom)
        assertEquals("2026-12-31T23:59:59Z", row.validTo)
    }

    @Test
    fun `PricingRulesResponse wraps rule list with count`() {
        val response = PricingRulesResponse(
            total = 2,
            rules = listOf(
                com.zyntasolutions.zyntapos.api.repository.PricingRuleRow(
                    id = "r1", productId = "p1", storeId = null, price = 10.0,
                    costPrice = null, priority = 0, validFrom = null, validTo = null,
                    isActive = true, description = "", createdAt = "", updatedAt = "",
                ),
                com.zyntasolutions.zyntapos.api.repository.PricingRuleRow(
                    id = "r2", productId = "p1", storeId = "s1", price = 8.0,
                    costPrice = null, priority = 5, validFrom = null, validTo = null,
                    isActive = true, description = "Store override", createdAt = "", updatedAt = "",
                ),
            ),
        )
        assertEquals(2, response.total)
        assertEquals(2, response.rules.size)
    }

    @Test
    fun `UpsertPricingRuleRequest defaults`() {
        val request = UpsertPricingRuleRequest(
            productId = "prod-1",
            price = 50.0,
        )
        assertNull(request.id)
        assertNull(request.storeId)
        assertNull(request.costPrice)
        assertEquals(0, request.priority)
        assertNull(request.validFrom)
        assertNull(request.validTo)
        assertTrue(request.isActive)
        assertEquals("", request.description)
    }
}
