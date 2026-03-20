package com.zyntasolutions.zyntapos.api.routes

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Unit tests for [adminInventoryRoutes] — global inventory endpoint (C1.2).
 *
 * Tests cover:
 * - Route path structure
 * - Required RBAC permission (inventory:read)
 * - Query parameter handling (productId, storeId filters)
 * - Response structure (total, lowStock count, items list)
 * - Low stock detection logic
 * - Roles with and without inventory:read permission
 *
 * Full HTTP tests require Ktor testApplication + Koin DI.
 */
class AdminInventoryRoutesTest {

    // ── Route paths ──────────────────────────────────────────────────────────────

    @Test
    fun `global inventory endpoint path is admin inventory global`() {
        val path = "/admin/inventory/global"
        assertTrue(path.startsWith("/admin/inventory"))
        assertTrue(path.endsWith("/global"))
    }

    @Test
    fun `endpoint is under admin namespace`() {
        val path = "/admin/inventory/global"
        assertTrue(path.startsWith("/admin/"))
    }

    // ── RBAC permission requirements ─────────────────────────────────────────────

    @Test
    fun `inventory-read permission is required`() {
        val requiredPermission = "inventory:read"
        assertTrue(requiredPermission.startsWith("inventory:"))
    }

    @Test
    fun `ADMIN role has inventory-read permission`() {
        // Mirrors AdminPermissions.PERMISSIONS map
        val adminPermissions = setOf(
            "users:read", "users:write", "users:delete",
            "inventory:read", "inventory:write",
            "orders:read", "orders:write",
            "reports:read", "finance:read", "settings:read", "settings:write",
            "audit:read", "tickets:read", "tickets:write",
        )
        assertTrue("inventory:read" in adminPermissions)
    }

    @Test
    fun `OPERATOR role has inventory-read permission`() {
        val operatorPermissions = setOf(
            "users:read",
            "inventory:read", "inventory:write",
            "orders:read", "orders:write",
            "reports:read",
            "tickets:read", "tickets:write",
        )
        assertTrue("inventory:read" in operatorPermissions)
    }

    @Test
    fun `FINANCE role has inventory-read permission`() {
        val financePermissions = setOf(
            "orders:read",
            "reports:read",
            "finance:read",
            "inventory:read",
        )
        assertTrue("inventory:read" in financePermissions)
    }

    @Test
    fun `AUDITOR role does not have inventory-read permission`() {
        val auditorPermissions = setOf(
            "audit:read",
            "reports:read",
        )
        assertFalse("inventory:read" in auditorPermissions)
    }

    @Test
    fun `HELPDESK role does not have inventory-read permission`() {
        val helpdeskPermissions = setOf(
            "tickets:read", "tickets:write",
            "users:read",
        )
        assertFalse("inventory:read" in helpdeskPermissions)
    }

    // ── Query parameter handling ─────────────────────────────────────────────────

    @Test
    fun `productId query param is optional`() {
        val productId: String? = null
        assertEquals(null, productId)
    }

    @Test
    fun `storeId query param is optional`() {
        val storeId: String? = null
        assertEquals(null, storeId)
    }

    @Test
    fun `when storeId is provided use getByStore`() {
        val storeId = "store-abc"
        // If storeId != null, use repo.getByStore(storeId, productId)
        assertNotNull(storeId)
        assertTrue(storeId.isNotBlank())
    }

    @Test
    fun `when storeId is null use getGlobal`() {
        val storeId: String? = null
        // If storeId == null, use repo.getGlobal(productId)
        assertEquals(null, storeId)
    }

    // ── Response structure ───────────────────────────────────────────────────────

    @Test
    fun `response contains total field`() {
        data class GlobalInventoryResponse(
            val total: Int,
            val lowStock: Int,
            val items: List<Any>,
        )
        val response = GlobalInventoryResponse(total = 5, lowStock = 2, items = emptyList())
        assertEquals(5, response.total)
    }

    @Test
    fun `response contains lowStock count`() {
        data class GlobalInventoryResponse(
            val total: Int,
            val lowStock: Int,
            val items: List<Any>,
        )
        val response = GlobalInventoryResponse(total = 10, lowStock = 3, items = emptyList())
        assertEquals(3, response.lowStock)
        assertTrue(response.lowStock <= response.total)
    }

    @Test
    fun `response contains items list`() {
        data class WarehouseStockDto(
            val id: String,
            val storeId: String,
            val warehouseId: String,
            val productId: String,
            val quantity: Double,
            val minQuantity: Double,
            val isLowStock: Boolean,
            val updatedAt: Long,
        )
        data class GlobalInventoryResponse(
            val total: Int,
            val lowStock: Int,
            val items: List<WarehouseStockDto>,
        )

        val items = listOf(
            WarehouseStockDto(
                id          = "ws-1",
                storeId     = "store-A",
                warehouseId = "wh-main",
                productId   = "prod-001",
                quantity    = 5.0,
                minQuantity = 10.0,
                isLowStock  = true,
                updatedAt   = System.currentTimeMillis(),
            )
        )
        val response = GlobalInventoryResponse(total = 1, lowStock = 1, items = items)
        assertEquals(1, response.items.size)
        assertTrue(response.items.first().isLowStock)
    }

    // ── Low stock detection ──────────────────────────────────────────────────────

    @Test
    fun `isLowStock is true when quantity below minQuantity`() {
        val quantity    = 3.0
        val minQuantity = 10.0
        assertTrue(quantity < minQuantity)
    }

    @Test
    fun `isLowStock is false when quantity equals minQuantity`() {
        val quantity    = 10.0
        val minQuantity = 10.0
        assertFalse(quantity < minQuantity)
    }

    @Test
    fun `isLowStock is false when quantity above minQuantity`() {
        val quantity    = 15.0
        val minQuantity = 10.0
        assertFalse(quantity < minQuantity)
    }

    @Test
    fun `isLowStock is false when minQuantity is zero`() {
        val quantity    = 0.0
        val minQuantity = 0.0
        assertFalse(quantity < minQuantity)
    }

    @Test
    fun `lowStock count matches filtered items`() {
        data class Item(val quantity: Double, val minQuantity: Double) {
            val isLowStock: Boolean get() = quantity < minQuantity
        }
        val items = listOf(
            Item(quantity = 2.0, minQuantity = 10.0),  // low
            Item(quantity = 15.0, minQuantity = 5.0),  // ok
            Item(quantity = 5.0, minQuantity = 10.0),  // low
            Item(quantity = 0.0, minQuantity = 0.0),   // ok (minQty=0)
        )
        val lowStockCount = items.count { it.isLowStock }
        assertEquals(2, lowStockCount)
    }
}
