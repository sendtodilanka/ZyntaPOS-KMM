package com.zyntasolutions.zyntapos.data.repository

import app.cash.turbine.test
import com.zyntasolutions.zyntapos.core.result.Result
import com.zyntasolutions.zyntapos.data.createTestDatabase
import com.zyntasolutions.zyntapos.data.local.SyncEnqueuer
import com.zyntasolutions.zyntapos.db.ZyntaDatabase
import com.zyntasolutions.zyntapos.domain.model.ReplenishmentRule
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlinx.coroutines.test.runTest

/**
 * ZyntaPOS — ReplenishmentRuleRepositoryImpl Integration Tests (jvmTest)
 *
 * Validates [ReplenishmentRuleRepositoryImpl] against a real in-memory SQLite database.
 * Queries JOIN products, warehouses, and suppliers tables — these are seeded in @BeforeTest.
 *
 * Coverage:
 *  A. upsert (insert) → getByProductAndWarehouse round-trip preserves all fields
 *  B. getAll emits all active rules with JOIN data (Turbine)
 *  C. getByWarehouse filters rules to a single warehouse (Turbine)
 *  D. getAutoApproveRules returns only auto-approve=true + active rules
 *  E. upsert (update) changes reorder values for existing product+warehouse
 *  F. delete removes rule
 *  G. getByProductAndWarehouse for unknown combo returns Success(null)
 */
class ReplenishmentRuleRepositoryImplIntegrationTest {

    private lateinit var db: ZyntaDatabase
    private lateinit var repo: ReplenishmentRuleRepositoryImpl

    @BeforeTest
    fun setup() {
        db = createTestDatabase()
        repo = ReplenishmentRuleRepositoryImpl(db, SyncEnqueuer(db))

        val now = Clock.System.now().toEpochMilliseconds()

        // Seed products
        db.productsQueries.insertProduct(
            id = "prod-01", name = "Widget", barcode = null, sku = "SKU-01",
            category_id = null, unit_id = "pcs", price = 10.0, cost_price = 5.0,
            tax_group_id = null, stock_qty = 100.0, min_stock_qty = 10.0,
            image_url = null, description = null, is_active = 1L,
            created_at = now, updated_at = now, sync_status = "PENDING",
            master_product_id = null,
        )
        db.productsQueries.insertProduct(
            id = "prod-02", name = "Gadget", barcode = null, sku = "SKU-02",
            category_id = null, unit_id = "pcs", price = 20.0, cost_price = 10.0,
            tax_group_id = null, stock_qty = 50.0, min_stock_qty = 5.0,
            image_url = null, description = null, is_active = 1L,
            created_at = now, updated_at = now, sync_status = "PENDING",
            master_product_id = null,
        )

        // Seed warehouses
        db.warehousesQueries.insertWarehouse(
            "wh-01", "store-01", "Main Warehouse", null, 1L, 1L, null, null, now, now, "PENDING",
        )
        db.warehousesQueries.insertWarehouse(
            "wh-02", "store-01", "Secondary Warehouse", null, 1L, 0L, null, null, now, now, "PENDING",
        )

        // Seed supplier
        db.suppliersQueries.insertSupplier(
            id = "sup-01", name = "Test Supplier",
            contact_person = null, phone = null, email = null,
            address = null, notes = null, is_active = 1L,
            created_at = now, updated_at = now, sync_status = "PENDING",
        )
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private val now get() = Clock.System.now().toEpochMilliseconds()

    private fun makeRule(
        id: String = "rule-01",
        productId: String = "prod-01",
        warehouseId: String = "wh-01",
        supplierId: String = "sup-01",
        reorderPoint: Double = 10.0,
        reorderQty: Double = 50.0,
        autoApprove: Boolean = true,
        isActive: Boolean = true,
    ) = ReplenishmentRule(
        id = id,
        productId = productId,
        warehouseId = warehouseId,
        supplierId = supplierId,
        reorderPoint = reorderPoint,
        reorderQty = reorderQty,
        autoApprove = autoApprove,
        isActive = isActive,
        createdAt = 1_000_000L,
        updatedAt = 1_000_000L,
    )

    // ── Tests ─────────────────────────────────────────────────────────────────

    @Test
    fun `A - upsert insert then getByProductAndWarehouse returns correct rule`() = runTest {
        val rule = makeRule(id = "rule-01", reorderPoint = 15.0, reorderQty = 75.0)
        val upsertResult = repo.upsert(rule)
        assertIs<Result.Success<Unit>>(upsertResult)

        val fetchResult = repo.getByProductAndWarehouse("prod-01", "wh-01")
        assertIs<Result.Success<ReplenishmentRule?>>(fetchResult)
        val fetched = fetchResult.data
        assertNotNull(fetched)
        assertEquals("prod-01", fetched.productId)
        assertEquals("wh-01", fetched.warehouseId)
        assertEquals("sup-01", fetched.supplierId)
        assertEquals(15.0, fetched.reorderPoint)
        assertEquals(75.0, fetched.reorderQty)
        assertTrue(fetched.autoApprove)
        assertTrue(fetched.isActive)
        // JOIN data is populated from seeded tables
        assertEquals("Widget", fetched.productName)
        assertEquals("Main Warehouse", fetched.warehouseName)
        assertEquals("Test Supplier", fetched.supplierName)
    }

    @Test
    fun `B - getAll emits all rules via Turbine`() = runTest {
        repo.upsert(makeRule(id = "rule-01", productId = "prod-01", warehouseId = "wh-01"))
        repo.upsert(makeRule(id = "rule-02", productId = "prod-02", warehouseId = "wh-01"))

        repo.getAll().test {
            val list = awaitItem()
            assertEquals(2, list.size)
            assertTrue(list.any { it.productId == "prod-01" })
            assertTrue(list.any { it.productId == "prod-02" })
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `C - getByWarehouse filters to single warehouse`() = runTest {
        repo.upsert(makeRule(id = "rule-01", productId = "prod-01", warehouseId = "wh-01"))
        repo.upsert(makeRule(id = "rule-02", productId = "prod-01", warehouseId = "wh-02"))

        repo.getByWarehouse("wh-01").test {
            val list = awaitItem()
            assertEquals(1, list.size)
            assertEquals("wh-01", list.first().warehouseId)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `D - getAutoApproveRules returns only auto-approve=true and active rules`() = runTest {
        repo.upsert(makeRule(id = "rule-01", productId = "prod-01", warehouseId = "wh-01", autoApprove = true, isActive = true))
        repo.upsert(makeRule(id = "rule-02", productId = "prod-02", warehouseId = "wh-01", autoApprove = false, isActive = true))

        val result = repo.getAutoApproveRules()
        assertIs<Result.Success<List<ReplenishmentRule>>>(result)
        val list = result.data
        assertEquals(1, list.size)
        assertEquals("prod-01", list.first().productId)
        assertTrue(list.first().autoApprove)
    }

    @Test
    fun `E - upsert update changes reorder values for existing rule`() = runTest {
        repo.upsert(makeRule(id = "rule-01", reorderPoint = 10.0, reorderQty = 50.0))

        // Upsert with same product+warehouse but different values
        repo.upsert(makeRule(id = "rule-01", reorderPoint = 25.0, reorderQty = 100.0))

        val fetched = (repo.getByProductAndWarehouse("prod-01", "wh-01") as Result.Success).data
        assertNotNull(fetched)
        assertEquals(25.0, fetched.reorderPoint)
        assertEquals(100.0, fetched.reorderQty)
    }

    @Test
    fun `F - delete removes the rule`() = runTest {
        repo.upsert(makeRule(id = "rule-01"))

        val deleteResult = repo.delete("rule-01")
        assertIs<Result.Success<Unit>>(deleteResult)

        val fetched = (repo.getByProductAndWarehouse("prod-01", "wh-01") as Result.Success).data
        assertNull(fetched)
    }

    @Test
    fun `G - getByProductAndWarehouse for unknown combo returns Success(null)`() = runTest {
        val result = repo.getByProductAndWarehouse("prod-unknown", "wh-unknown")
        assertIs<Result.Success<ReplenishmentRule?>>(result)
        assertNull(result.data)
    }
}
