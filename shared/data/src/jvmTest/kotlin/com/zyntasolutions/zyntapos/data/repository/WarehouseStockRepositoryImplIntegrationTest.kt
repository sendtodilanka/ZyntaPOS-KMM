package com.zyntasolutions.zyntapos.data.repository

import com.zyntasolutions.zyntapos.core.result.Result
import com.zyntasolutions.zyntapos.data.createTestDatabase
import com.zyntasolutions.zyntapos.data.local.SyncEnqueuer
import com.zyntasolutions.zyntapos.db.ZyntaDatabase
import com.zyntasolutions.zyntapos.domain.model.WarehouseStock
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
 * ZyntaPOS — WarehouseStockRepositoryImpl Integration Tests (jvmTest)
 *
 * Validates [WarehouseStockRepositoryImpl] against a real in-memory SQLite database.
 * No mocks — exercises the full SQLDelight query layer.
 *
 * Coverage:
 *  A. upsert → getEntry round-trip preserves all fields
 *  B. getEntry for unknown warehouse/product returns Success(null)
 *  C. getTotalStock aggregates across multiple warehouses
 *  D. adjustStock increases quantity correctly
 *  E. adjustStock decreases quantity correctly
 *  F. adjustStock auto-creates missing row (zero baseline)
 *  G. transferStock moves quantity from source to destination
 *  H. transferStock with insufficient stock returns ValidationException error
 *  I. deleteEntry removes the stock row
 *  J. upsert updates existing row (ON CONFLICT DO UPDATE)
 */
class WarehouseStockRepositoryImplIntegrationTest {

    private lateinit var db: ZyntaDatabase
    private lateinit var repo: WarehouseStockRepositoryImpl

    @BeforeTest
    fun setup() {
        db = createTestDatabase()
        repo = WarehouseStockRepositoryImpl(db, SyncEnqueuer(db))
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private val now get() = Clock.System.now().toEpochMilliseconds()

    private fun makeStock(
        id: String = "ws-01",
        warehouseId: String = "wh-01",
        productId: String = "prod-01",
        quantity: Double = 100.0,
        minQuantity: Double = 10.0,
    ) = WarehouseStock(
        id = id,
        warehouseId = warehouseId,
        productId = productId,
        quantity = quantity,
        minQuantity = minQuantity,
    )

    // ── Tests ─────────────────────────────────────────────────────────────────

    @Test
    fun `A - upsert then getEntry returns correct stock`() = runTest {
        val stock = makeStock(id = "ws-01", warehouseId = "wh-01", productId = "prod-01", quantity = 100.0, minQuantity = 10.0)
        val upsertResult = repo.upsert(stock)
        assertIs<Result.Success<Unit>>(upsertResult)

        val entryResult = repo.getEntry("wh-01", "prod-01")
        assertIs<Result.Success<WarehouseStock?>>(entryResult)
        val entry = entryResult.data
        assertNotNull(entry)
        assertEquals("wh-01", entry.warehouseId)
        assertEquals("prod-01", entry.productId)
        assertEquals(100.0, entry.quantity)
        assertEquals(10.0, entry.minQuantity)
    }

    @Test
    fun `B - getEntry for unknown warehouse_product returns Success(null)`() = runTest {
        val result = repo.getEntry("wh-unknown", "prod-unknown")
        assertIs<Result.Success<WarehouseStock?>>(result)
        assertNull(result.data)
    }

    @Test
    fun `C - getTotalStock aggregates across warehouses`() = runTest {
        repo.upsert(makeStock(id = "ws-01", warehouseId = "wh-01", productId = "prod-01", quantity = 60.0))
        repo.upsert(makeStock(id = "ws-02", warehouseId = "wh-02", productId = "prod-01", quantity = 40.0))

        val totalResult = repo.getTotalStock("prod-01")
        assertIs<Result.Success<Double>>(totalResult)
        assertEquals(100.0, totalResult.data)
    }

    @Test
    fun `D - adjustStock increases quantity`() = runTest {
        repo.upsert(makeStock(id = "ws-01", warehouseId = "wh-01", productId = "prod-01", quantity = 50.0))

        val adjustResult = repo.adjustStock("wh-01", "prod-01", delta = 25.0)
        assertIs<Result.Success<Unit>>(adjustResult)

        val entry = (repo.getEntry("wh-01", "prod-01") as Result.Success).data
        assertNotNull(entry)
        assertEquals(75.0, entry.quantity)
    }

    @Test
    fun `E - adjustStock decreases quantity`() = runTest {
        repo.upsert(makeStock(id = "ws-01", warehouseId = "wh-01", productId = "prod-01", quantity = 80.0))

        val adjustResult = repo.adjustStock("wh-01", "prod-01", delta = -30.0)
        assertIs<Result.Success<Unit>>(adjustResult)

        val entry = (repo.getEntry("wh-01", "prod-01") as Result.Success).data
        assertNotNull(entry)
        assertEquals(50.0, entry.quantity)
    }

    @Test
    fun `F - adjustStock auto-creates missing row`() = runTest {
        // No prior upsert — row doesn't exist yet
        val adjustResult = repo.adjustStock("wh-new", "prod-new", delta = 20.0)
        assertIs<Result.Success<Unit>>(adjustResult)

        val entry = (repo.getEntry("wh-new", "prod-new") as Result.Success).data
        assertNotNull(entry)
        assertEquals(20.0, entry.quantity)
    }

    @Test
    fun `G - transferStock moves quantity from source to destination`() = runTest {
        repo.upsert(makeStock(id = "ws-01", warehouseId = "wh-src", productId = "prod-01", quantity = 100.0))

        val transferResult = repo.transferStock(
            sourceWarehouseId = "wh-src",
            destWarehouseId = "wh-dst",
            productId = "prod-01",
            quantity = 40.0,
        )
        assertIs<Result.Success<Unit>>(transferResult)

        val srcEntry = (repo.getEntry("wh-src", "prod-01") as Result.Success).data
        val dstEntry = (repo.getEntry("wh-dst", "prod-01") as Result.Success).data
        assertNotNull(srcEntry)
        assertNotNull(dstEntry)
        assertEquals(60.0, srcEntry.quantity)
        assertEquals(40.0, dstEntry.quantity)
    }

    @Test
    fun `H - transferStock with insufficient stock returns error`() = runTest {
        repo.upsert(makeStock(id = "ws-01", warehouseId = "wh-src", productId = "prod-01", quantity = 10.0))

        val transferResult = repo.transferStock(
            sourceWarehouseId = "wh-src",
            destWarehouseId = "wh-dst",
            productId = "prod-01",
            quantity = 50.0, // exceeds available 10.0
        )
        assertIs<Result.Error>(transferResult)

        // Source quantity must remain unchanged
        val srcEntry = (repo.getEntry("wh-src", "prod-01") as Result.Success).data
        assertNotNull(srcEntry)
        assertEquals(10.0, srcEntry.quantity)
    }

    @Test
    fun `I - deleteEntry removes the stock row`() = runTest {
        repo.upsert(makeStock(id = "ws-01", warehouseId = "wh-01", productId = "prod-01", quantity = 50.0))

        val deleteResult = repo.deleteEntry("wh-01", "prod-01")
        assertIs<Result.Success<Unit>>(deleteResult)

        val entry = (repo.getEntry("wh-01", "prod-01") as Result.Success).data
        assertNull(entry)
    }

    @Test
    fun `J - upsert updates existing row on conflict`() = runTest {
        repo.upsert(makeStock(id = "ws-01", warehouseId = "wh-01", productId = "prod-01", quantity = 50.0, minQuantity = 5.0))
        // Same warehouse+product, new values
        repo.upsert(makeStock(id = "ws-01", warehouseId = "wh-01", productId = "prod-01", quantity = 200.0, minQuantity = 20.0))

        val entry = (repo.getEntry("wh-01", "prod-01") as Result.Success).data
        assertNotNull(entry)
        assertEquals(200.0, entry.quantity)
        assertEquals(20.0, entry.minQuantity)
    }
}
