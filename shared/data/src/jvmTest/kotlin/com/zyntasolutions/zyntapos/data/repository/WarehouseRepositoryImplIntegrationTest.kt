package com.zyntasolutions.zyntapos.data.repository

import app.cash.turbine.test
import com.zyntasolutions.zyntapos.core.result.Result
import com.zyntasolutions.zyntapos.data.createTestDatabase
import com.zyntasolutions.zyntapos.data.local.SyncEnqueuer
import com.zyntasolutions.zyntapos.db.ZyntaDatabase
import com.zyntasolutions.zyntapos.domain.model.StockTransfer
import com.zyntasolutions.zyntapos.domain.model.Warehouse
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
 * ZyntaPOS — WarehouseRepositoryImpl Integration Tests (jvmTest)
 *
 * Validates [WarehouseRepositoryImpl] against a real in-memory SQLite database.
 * No mocks — exercises the full SQLDelight query layer.
 *
 * Coverage:
 *  A. insert → getById round-trip preserves all fields
 *  B. getByStore emits only active warehouses via Turbine
 *  C. getDefault returns default warehouse for store
 *  D. getDefault returns null when no default exists
 *  E. insert with isDefault=true clears other defaults for same store
 *  F. update changes warehouse name and address
 *  G. getById for unknown ID returns error
 *  H. createTransfer → getTransferById round-trip
 *  I. cancelTransfer transitions PENDING → CANCELLED
 *  J. cancelTransfer on CANCELLED returns error
 */
class WarehouseRepositoryImplIntegrationTest {

    private lateinit var db: ZyntaDatabase
    private lateinit var repo: WarehouseRepositoryImpl

    @BeforeTest
    fun setup() {
        db = createTestDatabase()
        repo = WarehouseRepositoryImpl(db, SyncEnqueuer(db))

        val now = Clock.System.now().toEpochMilliseconds()
        // Seed product required for stock transfer FK
        db.productsQueries.insertProduct(
            id = "prod-01", name = "Widget", barcode = null, sku = "SKU-01",
            category_id = null, unit_id = "pcs", price = 10.0, cost_price = 5.0,
            tax_group_id = null, stock_qty = 100.0, min_stock_qty = 0.0,
            image_url = null, description = null, is_active = 1L,
            created_at = now, updated_at = now, sync_status = "PENDING",
            master_product_id = null,
        )
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private fun makeWarehouse(
        id: String = "wh-01",
        storeId: String = "store-01",
        name: String = "Main Warehouse",
        managerId: String? = null,
        isActive: Boolean = true,
        isDefault: Boolean = false,
        address: String? = "123 Warehouse Row",
        imageUrl: String? = null,
    ) = Warehouse(
        id = id,
        storeId = storeId,
        name = name,
        managerId = managerId,
        isActive = isActive,
        isDefault = isDefault,
        address = address,
        imageUrl = imageUrl,
    )

    private fun makeTransfer(
        id: String = "tr-01",
        sourceWarehouseId: String = "wh-01",
        destWarehouseId: String = "wh-02",
        productId: String = "prod-01",
        quantity: Double = 10.0,
        notes: String? = "Test transfer",
        createdBy: String = "user-01",
        transferredBy: String = "user-01",
    ) = StockTransfer(
        id = id,
        sourceWarehouseId = sourceWarehouseId,
        destWarehouseId = destWarehouseId,
        productId = productId,
        quantity = quantity,
        notes = notes,
        createdBy = createdBy,
        transferredBy = transferredBy,
    )

    // ── Tests ─────────────────────────────────────────────────────────────────

    @Test
    fun `A - insert then getById returns full warehouse`() = runTest {
        val wh = makeWarehouse(
            id = "wh-01",
            name = "Storage Bay",
            address = "Dock 5, Colombo Port",
            isDefault = true,
        )
        val insertResult = repo.insert(wh)
        assertIs<Result.Success<Unit>>(insertResult)

        val fetchResult = repo.getById("wh-01")
        assertIs<Result.Success<Warehouse>>(fetchResult)
        val fetched = fetchResult.data
        assertEquals("wh-01", fetched.id)
        assertEquals("store-01", fetched.storeId)
        assertEquals("Storage Bay", fetched.name)
        assertEquals("Dock 5, Colombo Port", fetched.address)
        assertTrue(fetched.isActive)
        assertTrue(fetched.isDefault)
    }

    @Test
    fun `B - getByStore emits only active warehouses via Turbine`() = runTest {
        repo.insert(makeWarehouse(id = "wh-01", storeId = "store-01", name = "Active 1"))
        repo.insert(makeWarehouse(id = "wh-02", storeId = "store-01", name = "Active 2"))
        repo.insert(makeWarehouse(id = "wh-03", storeId = "store-01", name = "Inactive", isActive = false))
        repo.insert(makeWarehouse(id = "wh-04", storeId = "store-02", name = "Other Store"))

        repo.getByStore("store-01").test {
            val list = awaitItem()
            assertEquals(2, list.size)
            assertTrue(list.any { it.name == "Active 1" })
            assertTrue(list.any { it.name == "Active 2" })
            assertTrue(list.none { it.name == "Inactive" })
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `C - getDefault returns default warehouse for store`() = runTest {
        repo.insert(makeWarehouse(id = "wh-01", storeId = "store-01", name = "Non Default"))
        repo.insert(makeWarehouse(id = "wh-02", storeId = "store-01", name = "Default WH", isDefault = true))

        val result = repo.getDefault("store-01")
        assertIs<Result.Success<Warehouse?>>(result)
        assertNotNull(result.data)
        assertEquals("wh-02", result.data!!.id)
        assertTrue(result.data!!.isDefault)
    }

    @Test
    fun `D - getDefault returns null when no default exists`() = runTest {
        repo.insert(makeWarehouse(id = "wh-01", storeId = "store-01", isDefault = false))

        val result = repo.getDefault("store-01")
        assertIs<Result.Success<Warehouse?>>(result)
        assertNull(result.data)
    }

    @Test
    fun `E - insert with isDefault clears existing default for same store`() = runTest {
        repo.insert(makeWarehouse(id = "wh-01", storeId = "store-01", name = "First Default", isDefault = true))
        repo.insert(makeWarehouse(id = "wh-02", storeId = "store-01", name = "New Default", isDefault = true))

        // wh-02 is now default; wh-01 should be cleared
        val defaultResult = repo.getDefault("store-01")
        assertIs<Result.Success<Warehouse?>>(defaultResult)
        assertNotNull(defaultResult.data)
        assertEquals("wh-02", defaultResult.data!!.id)

        val wh01 = (repo.getById("wh-01") as Result.Success).data
        assertTrue(!wh01.isDefault)
    }

    @Test
    fun `F - update changes warehouse name and address`() = runTest {
        repo.insert(makeWarehouse(id = "wh-01", name = "Old Name", address = "Old Address"))

        val updated = makeWarehouse(id = "wh-01", name = "New Name", address = "New Address")
        val updateResult = repo.update(updated)
        assertIs<Result.Success<Unit>>(updateResult)

        val fetched = (repo.getById("wh-01") as Result.Success).data
        assertEquals("New Name", fetched.name)
        assertEquals("New Address", fetched.address)
    }

    @Test
    fun `G - getById for unknown ID returns error`() = runTest {
        val result = repo.getById("non-existent-wh")
        assertIs<Result.Error>(result)
        assertNotNull(result.exception)
    }

    @Test
    fun `H - createTransfer then getTransferById round-trip`() = runTest {
        repo.insert(makeWarehouse(id = "wh-01", storeId = "store-01"))
        repo.insert(makeWarehouse(id = "wh-02", storeId = "store-01"))

        val transfer = makeTransfer(
            id = "tr-01",
            sourceWarehouseId = "wh-01",
            destWarehouseId = "wh-02",
            quantity = 20.0,
            notes = "Monthly replenishment",
        )
        val createResult = repo.createTransfer(transfer)
        assertIs<Result.Success<Unit>>(createResult)

        val fetchResult = repo.getTransferById("tr-01")
        assertIs<Result.Success<StockTransfer>>(fetchResult)
        val fetched = fetchResult.data
        assertEquals("tr-01", fetched.id)
        assertEquals("wh-01", fetched.sourceWarehouseId)
        assertEquals("wh-02", fetched.destWarehouseId)
        assertEquals("prod-01", fetched.productId)
        assertEquals(20.0, fetched.quantity)
        assertEquals(StockTransfer.Status.PENDING, fetched.status)
        assertEquals("Monthly replenishment", fetched.notes)
    }

    @Test
    fun `I - cancelTransfer transitions PENDING to CANCELLED`() = runTest {
        repo.insert(makeWarehouse(id = "wh-01", storeId = "store-01"))
        repo.insert(makeWarehouse(id = "wh-02", storeId = "store-01"))
        repo.createTransfer(makeTransfer(id = "tr-01"))

        val cancelResult = repo.cancelTransfer("tr-01")
        assertIs<Result.Success<Unit>>(cancelResult)

        val fetched = (repo.getTransferById("tr-01") as Result.Success).data
        assertEquals(StockTransfer.Status.CANCELLED, fetched.status)
    }

    @Test
    fun `J - cancelTransfer on already CANCELLED transfer returns error`() = runTest {
        repo.insert(makeWarehouse(id = "wh-01", storeId = "store-01"))
        repo.insert(makeWarehouse(id = "wh-02", storeId = "store-01"))
        repo.createTransfer(makeTransfer(id = "tr-01"))
        repo.cancelTransfer("tr-01")

        val secondCancelResult = repo.cancelTransfer("tr-01")
        assertIs<Result.Error>(secondCancelResult)
    }
}
