package com.zyntasolutions.zyntapos.data.repository

import app.cash.turbine.test
import com.zyntasolutions.zyntapos.core.result.Result
import com.zyntasolutions.zyntapos.data.createTestDatabase
import com.zyntasolutions.zyntapos.data.local.SyncEnqueuer
import com.zyntasolutions.zyntapos.db.ZyntaDatabase
import com.zyntasolutions.zyntapos.domain.model.WarehouseRack
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlinx.coroutines.test.runTest

/**
 * ZyntaPOS — WarehouseRackRepositoryImpl Integration Tests (jvmTest)
 *
 * Validates [WarehouseRackRepositoryImpl] against a real in-memory SQLite database.
 * Requires a warehouse seeded to satisfy the warehouse_id FK constraint.
 *
 * Coverage:
 *  A. insert → getById round-trip preserves all fields
 *  B. getByWarehouse emits racks via Turbine (excludes soft-deleted)
 *  C. update changes name, description, and capacity
 *  D. delete soft-deletes — getByWarehouse excludes it, getById still returns it
 *  E. getById for unknown id returns error
 *  F. insert rack with null capacity and null description
 *  G. getByWarehouse excludes racks for other warehouses
 */
class WarehouseRackRepositoryImplIntegrationTest {

    private lateinit var db: ZyntaDatabase
    private lateinit var repo: WarehouseRackRepositoryImpl

    @BeforeTest
    fun setup() {
        db = createTestDatabase()
        repo = WarehouseRackRepositoryImpl(db, SyncEnqueuer(db))

        val now = Clock.System.now().toEpochMilliseconds()

        // Seed warehouses required by warehouse_id FK
        db.warehousesQueries.insertWarehouse(
            "wh-01", "store-01", "Main Warehouse", null, 1L, 0L, null, null, now, now, "PENDING",
        )
        db.warehousesQueries.insertWarehouse(
            "wh-02", "store-01", "Secondary Warehouse", null, 0L, 0L, null, null, now, now, "PENDING",
        )
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private val now get() = Clock.System.now().toEpochMilliseconds()

    private fun makeRack(
        id: String = "rack-01",
        warehouseId: String = "wh-01",
        name: String = "Rack A",
        description: String? = "Cold storage rack",
        capacity: Int? = 100,
    ) = WarehouseRack(
        id = id,
        warehouseId = warehouseId,
        name = name,
        description = description,
        capacity = capacity,
        createdAt = now,
        updatedAt = now,
    )

    // ── Tests ─────────────────────────────────────────────────────────────────

    @Test
    fun `A - insert then getById returns full rack record`() = runTest {
        val rack = makeRack(
            id = "rack-01",
            warehouseId = "wh-01",
            name = "Rack A",
            description = "Cold storage rack",
            capacity = 200,
        )
        val insertResult = repo.insert(rack)
        assertIs<Result.Success<Unit>>(insertResult)

        val fetchResult = repo.getById("rack-01")
        assertIs<Result.Success<WarehouseRack>>(fetchResult)
        val fetched = fetchResult.data
        assertEquals("rack-01", fetched.id)
        assertEquals("wh-01", fetched.warehouseId)
        assertEquals("Rack A", fetched.name)
        assertEquals("Cold storage rack", fetched.description)
        assertEquals(200, fetched.capacity)
    }

    @Test
    fun `B - getByWarehouse emits active racks via Turbine`() = runTest {
        repo.insert(makeRack(id = "rack-01", name = "Rack A"))
        repo.insert(makeRack(id = "rack-02", name = "Rack B"))

        repo.getByWarehouse("wh-01").test {
            val list = awaitItem()
            assertEquals(2, list.size)
            assertTrue(list.any { it.name == "Rack A" })
            assertTrue(list.any { it.name == "Rack B" })
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `C - update changes name, description, and capacity`() = runTest {
        repo.insert(makeRack(id = "rack-01", name = "Old Name", description = "Old desc", capacity = 50))

        val updateResult = repo.update(
            makeRack(id = "rack-01", name = "New Name", description = "New desc", capacity = 150)
        )
        assertIs<Result.Success<Unit>>(updateResult)

        val fetched = (repo.getById("rack-01") as Result.Success).data
        assertEquals("New Name", fetched.name)
        assertEquals("New desc", fetched.description)
        assertEquals(150, fetched.capacity)
    }

    @Test
    fun `D - delete soft-deletes rack excluded from getByWarehouse and getById`() = runTest {
        repo.insert(makeRack(id = "rack-01", name = "Rack A"))
        repo.insert(makeRack(id = "rack-02", name = "Rack B"))

        val deleteResult = repo.delete("rack-01", deletedAt = now, updatedAt = now)
        assertIs<Result.Success<Unit>>(deleteResult)

        // Soft-deleted rack excluded from list
        repo.getByWarehouse("wh-01").test {
            val list = awaitItem()
            assertEquals(1, list.size)
            assertEquals("rack-02", list.first().id)
            cancelAndIgnoreRemainingEvents()
        }

        // selectById also filters deleted_at IS NULL, so soft-deleted rack returns error
        val fetchResult = repo.getById("rack-01")
        assertIs<Result.Error>(fetchResult)
    }

    @Test
    fun `E - getById for unknown id returns error`() = runTest {
        val result = repo.getById("non-existent-rack")
        assertIs<Result.Error>(result)
        assertNotNull(result.exception)
    }

    @Test
    fun `F - insert rack with null capacity and null description`() = runTest {
        val rack = makeRack(id = "rack-minimal", name = "Minimal Rack", description = null, capacity = null)
        val insertResult = repo.insert(rack)
        assertIs<Result.Success<Unit>>(insertResult)

        val fetched = (repo.getById("rack-minimal") as Result.Success).data
        assertEquals("rack-minimal", fetched.id)
        assertEquals("Minimal Rack", fetched.name)
        assertEquals(null, fetched.description)
        assertEquals(null, fetched.capacity)
    }

    @Test
    fun `G - getByWarehouse excludes racks from other warehouses`() = runTest {
        repo.insert(makeRack(id = "rack-01", warehouseId = "wh-01", name = "Rack WH1"))
        repo.insert(makeRack(id = "rack-02", warehouseId = "wh-02", name = "Rack WH2"))

        repo.getByWarehouse("wh-01").test {
            val list = awaitItem()
            assertEquals(1, list.size)
            assertEquals("wh-01", list.first().warehouseId)
            cancelAndIgnoreRemainingEvents()
        }
    }
}
