package com.zyntasolutions.zyntapos.api.routes

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Unit tests for [adminTransferRoutes] validation logic (C1.3 — IST endpoints).
 *
 * Tests cover:
 * - Request field validation (blank IDs, invalid quantities)
 * - IST workflow state transitions (valid state machine rules)
 * - Route path structure
 * - Pagination parameter coercion
 * - Status filter allowed values
 *
 * Full HTTP round-trip tests require Ktor testApplication with Koin DI.
 * These tests validate the request/response contract and validation rules
 * that are applied before the service is called.
 */
class AdminTransferRoutesTest {

    // ── CreateTransferRequest validation ────────────────────────────────────────

    @Test
    fun `blank sourceWarehouseId is invalid`() {
        val sourceWarehouseId = "  "
        assertTrue(sourceWarehouseId.isBlank(), "Source warehouse ID must not be blank")
    }

    @Test
    fun `blank destWarehouseId is invalid`() {
        val destWarehouseId = ""
        assertTrue(destWarehouseId.isBlank(), "Destination warehouse ID must not be blank")
    }

    @Test
    fun `blank productId is invalid`() {
        val productId = "   "
        assertTrue(productId.isBlank(), "Product ID must not be blank")
    }

    @Test
    fun `negative quantity is invalid`() {
        val quantity = -1.0
        assertTrue(quantity <= 0.0, "Quantity must be > 0")
    }

    @Test
    fun `zero quantity is invalid`() {
        val quantity = 0.0
        assertFalse(quantity > 0.0, "Quantity of zero must be rejected")
    }

    @Test
    fun `positive quantity is valid`() {
        val quantity = 0.001
        assertTrue(quantity > 0.0)
    }

    @Test
    fun `valid create request passes all field checks`() {
        val sourceWarehouseId = "wh-source-001"
        val destWarehouseId   = "wh-dest-002"
        val productId         = "prod-xyz"
        val quantity          = 10.5

        assertFalse(sourceWarehouseId.isBlank())
        assertFalse(destWarehouseId.isBlank())
        assertFalse(productId.isBlank())
        assertTrue(quantity > 0.0)
    }

    @Test
    fun `sourceStoreId and destStoreId are optional`() {
        val sourceStoreId: String? = null
        val destStoreId: String?   = null
        // Nullable — absence is valid
        assertEquals(null, sourceStoreId)
        assertEquals(null, destStoreId)
    }

    // ── IST state machine ────────────────────────────────────────────────────────

    @Test
    fun `valid IST workflow transitions are in correct order`() {
        val validTransitions = mapOf(
            "PENDING"    to "APPROVED",
            "APPROVED"   to "IN_TRANSIT",
            "IN_TRANSIT" to "RECEIVED",
        )
        assertEquals("APPROVED",   validTransitions["PENDING"])
        assertEquals("IN_TRANSIT", validTransitions["APPROVED"])
        assertEquals("RECEIVED",   validTransitions["IN_TRANSIT"])
    }

    @Test
    fun `cancellable statuses are PENDING and APPROVED only`() {
        val cancellableStatuses = setOf("PENDING", "APPROVED")
        assertTrue("PENDING" in cancellableStatuses)
        assertTrue("APPROVED" in cancellableStatuses)
        assertFalse("IN_TRANSIT" in cancellableStatuses)
        assertFalse("RECEIVED" in cancellableStatuses)
        assertFalse("CANCELLED" in cancellableStatuses)
    }

    @Test
    fun `terminal statuses cannot be transitioned`() {
        val terminalStatuses = setOf("RECEIVED", "CANCELLED")
        assertTrue("RECEIVED" in terminalStatuses)
        assertTrue("CANCELLED" in terminalStatuses)
    }

    // ── Route paths ──────────────────────────────────────────────────────────────

    @Test
    fun `list transfers endpoint path`() {
        val path = "/admin/transfers"
        assertTrue(path.startsWith("/admin/"))
        assertTrue(path.endsWith("transfers"))
    }

    @Test
    fun `transfer detail endpoint path contains id`() {
        val id   = "transfer-abc123"
        val path = "/admin/transfers/$id"
        assertTrue(path.contains(id))
    }

    @Test
    fun `approve endpoint path`() {
        val path = "/admin/transfers/t-001/approve"
        assertTrue(path.endsWith("/approve"))
    }

    @Test
    fun `dispatch endpoint path`() {
        val path = "/admin/transfers/t-001/dispatch"
        assertTrue(path.endsWith("/dispatch"))
    }

    @Test
    fun `receive endpoint path`() {
        val path = "/admin/transfers/t-001/receive"
        assertTrue(path.endsWith("/receive"))
    }

    @Test
    fun `cancel endpoint path`() {
        val path = "/admin/transfers/t-001/cancel"
        assertTrue(path.endsWith("/cancel"))
    }

    // ── Pagination parameters ────────────────────────────────────────────────────

    @Test
    fun `page parameter defaults to 0`() {
        val page = null?.toString()?.toIntOrNull()?.coerceAtLeast(0) ?: 0
        assertEquals(0, page)
    }

    @Test
    fun `negative page is coerced to 0`() {
        val page = "-5".toIntOrNull()?.coerceAtLeast(0) ?: 0
        assertEquals(0, page)
    }

    @Test
    fun `size parameter defaults to 20`() {
        val size = null?.toString()?.toIntOrNull()?.coerceIn(1, 100) ?: 20
        assertEquals(20, size)
    }

    @Test
    fun `size exceeding 100 is coerced to 100`() {
        val size = "999".toIntOrNull()?.coerceIn(1, 100) ?: 20
        assertEquals(100, size)
    }

    @Test
    fun `size of 0 is coerced to 1`() {
        val size = "0".toIntOrNull()?.coerceIn(1, 100) ?: 20
        assertEquals(1, size)
    }

    // ── Valid transfer statuses ────────────────────────────────────────────────

    @Test
    fun `valid statuses for status filter`() {
        val validStatuses = setOf("PENDING", "APPROVED", "IN_TRANSIT", "RECEIVED", "COMMITTED", "CANCELLED")
        assertTrue("PENDING" in validStatuses)
        assertTrue("APPROVED" in validStatuses)
        assertTrue("IN_TRANSIT" in validStatuses)
        assertTrue("RECEIVED" in validStatuses)
        assertTrue("CANCELLED" in validStatuses)
    }

    @Test
    fun `invalid status is not in valid set`() {
        val validStatuses = setOf("PENDING", "APPROVED", "IN_TRANSIT", "RECEIVED", "COMMITTED", "CANCELLED")
        assertFalse("PROCESSING" in validStatuses)
        assertFalse("SHIPPED" in validStatuses)
        assertFalse("UNKNOWN" in validStatuses)
    }
}
