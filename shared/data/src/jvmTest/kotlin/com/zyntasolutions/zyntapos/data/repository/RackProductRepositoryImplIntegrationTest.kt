package com.zyntasolutions.zyntapos.data.repository

import app.cash.turbine.test
import com.zyntasolutions.zyntapos.core.result.Result
import com.zyntasolutions.zyntapos.data.createTestDatabase
import com.zyntasolutions.zyntapos.db.ZyntaDatabase
import com.zyntasolutions.zyntapos.domain.model.RackProduct
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlinx.coroutines.test.runTest

/**
 * ZyntaPOS — RackProductRepositoryImpl Integration Tests (jvmTest)
 *
 * Validates [RackProductRepositoryImpl] against a real in-memory SQLite database.
 * FK chain: rack_products → warehouse_racks → warehouses (+ products FK).
 *
 * Coverage:
 *  A. upsert → getByRack round-trip via Turbine with product join fields
 *  B. getByRack excludes products for other racks
 *  C. upsert updates quantity and binLocation (idempotent by rack+product)
 *  D. delete removes a specific product from a rack
 *  E. delete of non-existent combination succeeds (no-op)
 */
class RackProductRepositoryImplIntegrationTest {

    private lateinit var db: ZyntaDatabase
    private lateinit var repo: RackProductRepositoryImpl

    @BeforeTest
    fun setup() {
        db = createTestDatabase()
        repo = RackProductRepositoryImpl(db)

        val now = Clock.System.now().toEpochMilliseconds()

        // Seed warehouses (required by warehouse_racks FK)
        db.warehousesQueries.insertWarehouse(
            "wh-01", "store-01", "Main Warehouse", null, 1L, 0L, null, null, now, now, "PENDING",
        )

        // Seed warehouse racks (required by rack_products FK)
        db.warehouse_racksQueries.insertRack(
            "rack-01", "wh-01", "Rack A", null, null, now, now, null, "PENDING",
        )
        db.warehouse_racksQueries.insertRack(
            "rack-02", "wh-01", "Rack B", null, null, now, now, null, "PENDING",
        )

        // Seed products (required by rack_products FK)
        db.productsQueries.insertProduct(
            id = "prod-01", name = "Widget", barcode = "BC-001", sku = "WGT-01",
            category_id = null, unit_id = "pcs", price = 100.0, cost_price = 60.0,
            tax_group_id = null, stock_qty = 0.0, min_stock_qty = 5.0,
            image_url = null, description = null, is_active = 1L,
            created_at = now, updated_at = now, sync_status = "PENDING",
            master_product_id = null,
        )
        db.productsQueries.insertProduct(
            id = "prod-02", name = "Gadget", barcode = "BC-002", sku = "GDG-01",
            category_id = null, unit_id = "pcs", price = 200.0, cost_price = 120.0,
            tax_group_id = null, stock_qty = 0.0, min_stock_qty = 5.0,
            image_url = null, description = null, is_active = 1L,
            created_at = now, updated_at = now, sync_status = "PENDING",
            master_product_id = null,
        )
        db.productsQueries.insertProduct(
            id = "prod-03", name = "Doohickey", barcode = "BC-003", sku = "DOH-01",
            category_id = null, unit_id = "pcs", price = 50.0, cost_price = 30.0,
            tax_group_id = null, stock_qty = 0.0, min_stock_qty = 5.0,
            image_url = null, description = null, is_active = 1L,
            created_at = now, updated_at = now, sync_status = "PENDING",
            master_product_id = null,
        )
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private fun makeRackProduct(
        id: String = "rp-01",
        rackId: String = "rack-01",
        productId: String = "prod-01",
        quantity: Double = 10.0,
        binLocation: String? = "A1",
    ) = RackProduct(
        id = id,
        rackId = rackId,
        productId = productId,
        quantity = quantity,
        binLocation = binLocation,
        updatedAt = 0L,
        productName = null,
        productSku = null,
        productBarcode = null,
    )

    // ── Tests ─────────────────────────────────────────────────────────────────

    @Test
    fun `A - upsert then getByRack returns rackProduct with product join fields`() = runTest {
        val upsertResult = repo.upsert(makeRackProduct(
            id = "rp-01",
            rackId = "rack-01",
            productId = "prod-01",
            quantity = 25.0,
            binLocation = "A1",
        ))
        assertIs<Result.Success<Unit>>(upsertResult)

        repo.getByRack("rack-01").test {
            val list = awaitItem()
            assertEquals(1, list.size)
            val item = list.first()
            assertEquals("rp-01", item.id)
            assertEquals("rack-01", item.rackId)
            assertEquals("prod-01", item.productId)
            assertEquals(25.0, item.quantity)
            assertEquals("A1", item.binLocation)
            // Joined product fields
            assertEquals("Widget", item.productName)
            assertEquals("WGT-01", item.productSku)
            assertEquals("BC-001", item.productBarcode)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `B - getByRack excludes products assigned to other racks`() = runTest {
        repo.upsert(makeRackProduct(id = "rp-01", rackId = "rack-01", productId = "prod-01"))
        repo.upsert(makeRackProduct(id = "rp-02", rackId = "rack-02", productId = "prod-02"))

        repo.getByRack("rack-01").test {
            val list = awaitItem()
            assertEquals(1, list.size)
            assertEquals("prod-01", list.first().productId)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `C - upsert with same rack+product updates quantity and binLocation`() = runTest {
        repo.upsert(makeRackProduct(id = "rp-01", rackId = "rack-01", productId = "prod-01", quantity = 10.0, binLocation = "A1"))
        // Upsert same rack+product — should update quantity/bin
        repo.upsert(makeRackProduct(id = "rp-01", rackId = "rack-01", productId = "prod-01", quantity = 50.0, binLocation = "B2"))

        repo.getByRack("rack-01").test {
            val list = awaitItem()
            assertEquals(1, list.size)
            assertEquals(50.0, list.first().quantity)
            assertEquals("B2", list.first().binLocation)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `D - delete removes a specific product from a rack`() = runTest {
        repo.upsert(makeRackProduct(id = "rp-01", rackId = "rack-01", productId = "prod-01"))
        repo.upsert(makeRackProduct(id = "rp-02", rackId = "rack-01", productId = "prod-02"))

        val deleteResult = repo.delete(rackId = "rack-01", productId = "prod-01")
        assertIs<Result.Success<Unit>>(deleteResult)

        repo.getByRack("rack-01").test {
            val list = awaitItem()
            assertEquals(1, list.size)
            assertEquals("prod-02", list.first().productId)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `E - delete of non-existent combination returns success (no-op)`() = runTest {
        val result = repo.delete(rackId = "rack-01", productId = "non-existent-prod")
        assertIs<Result.Success<Unit>>(result)
    }
}
