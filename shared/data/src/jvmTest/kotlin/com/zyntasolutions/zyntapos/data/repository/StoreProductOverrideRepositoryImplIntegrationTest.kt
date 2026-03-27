package com.zyntasolutions.zyntapos.data.repository

import app.cash.turbine.test
import com.zyntasolutions.zyntapos.core.result.Result
import com.zyntasolutions.zyntapos.data.createTestDatabase
import com.zyntasolutions.zyntapos.data.local.SyncEnqueuer
import com.zyntasolutions.zyntapos.db.ZyntaDatabase
import com.zyntasolutions.zyntapos.domain.model.StoreProductOverride
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Instant
import kotlinx.coroutines.test.runTest

/**
 * ZyntaPOS — StoreProductOverrideRepositoryImpl Integration Tests (jvmTest)
 *
 * Validates [StoreProductOverrideRepositoryImpl] against a real in-memory SQLite database.
 * No FK constraints on store_products — no pre-seeding required.
 *
 * Coverage:
 *  A. upsertFromSync → getOverride round-trip preserves all fields
 *  B. getByStore emits all overrides for a store via Turbine
 *  C. getByStore excludes overrides for other stores
 *  D. getOverride for unknown combination returns error
 *  E. updateLocalPrice changes the price field
 *  F. updateLocalPrice with null clears the price
 *  G. updateLocalStock changes the stock quantity
 */
class StoreProductOverrideRepositoryImplIntegrationTest {

    private lateinit var db: ZyntaDatabase
    private lateinit var repo: StoreProductOverrideRepositoryImpl

    @BeforeTest
    fun setup() {
        db = createTestDatabase()
        repo = StoreProductOverrideRepositoryImpl(db, SyncEnqueuer(db))
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private fun makeOverride(
        id: String = "spo-01",
        masterProductId: String = "mp-01",
        storeId: String = "store-01",
        localPrice: Double? = 99.0,
        localCostPrice: Double? = 60.0,
        localStockQty: Double = 50.0,
        minStockQty: Double = 5.0,
        isActive: Boolean = true,
    ) = StoreProductOverride(
        id = id,
        masterProductId = masterProductId,
        storeId = storeId,
        localPrice = localPrice,
        localCostPrice = localCostPrice,
        localStockQty = localStockQty,
        minStockQty = minStockQty,
        isActive = isActive,
        createdAt = Instant.fromEpochMilliseconds(1_000_000L),
        updatedAt = Instant.fromEpochMilliseconds(1_000_000L),
    )

    // ── Tests ─────────────────────────────────────────────────────────────────

    @Test
    fun `A - upsertFromSync then getOverride returns full override`() = runTest {
        val override = makeOverride(
            id = "spo-01",
            masterProductId = "mp-01",
            storeId = "store-01",
            localPrice = 129.0,
            localCostPrice = 80.0,
            localStockQty = 100.0,
            minStockQty = 10.0,
        )
        val upsertResult = repo.upsertFromSync(override)
        assertIs<Result.Success<Unit>>(upsertResult)

        val fetchResult = repo.getOverride("mp-01", "store-01")
        assertIs<Result.Success<StoreProductOverride>>(fetchResult)
        val fetched = fetchResult.data
        assertEquals("spo-01", fetched.id)
        assertEquals("mp-01", fetched.masterProductId)
        assertEquals("store-01", fetched.storeId)
        assertEquals(129.0, fetched.localPrice)
        assertEquals(80.0, fetched.localCostPrice)
        assertEquals(100.0, fetched.localStockQty)
        assertEquals(10.0, fetched.minStockQty)
        assertTrue(fetched.isActive)
    }

    @Test
    fun `B - getByStore emits all overrides for a store via Turbine`() = runTest {
        repo.upsertFromSync(makeOverride(id = "spo-01", masterProductId = "mp-01", storeId = "store-01"))
        repo.upsertFromSync(makeOverride(id = "spo-02", masterProductId = "mp-02", storeId = "store-01"))

        repo.getByStore("store-01").test {
            val list = awaitItem()
            assertEquals(2, list.size)
            assertTrue(list.any { it.masterProductId == "mp-01" })
            assertTrue(list.any { it.masterProductId == "mp-02" })
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `C - getByStore excludes overrides for other stores`() = runTest {
        repo.upsertFromSync(makeOverride(id = "spo-01", masterProductId = "mp-01", storeId = "store-01"))
        repo.upsertFromSync(makeOverride(id = "spo-02", masterProductId = "mp-01", storeId = "store-02"))

        repo.getByStore("store-01").test {
            val list = awaitItem()
            assertEquals(1, list.size)
            assertEquals("store-01", list.first().storeId)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `D - getOverride for unknown combination returns error`() = runTest {
        val result = repo.getOverride("non-existent-mp", "non-existent-store")
        assertIs<Result.Error>(result)
        assertNotNull(result.exception)
    }

    @Test
    fun `E - updateLocalPrice changes the price field`() = runTest {
        repo.upsertFromSync(makeOverride(id = "spo-01", localPrice = 99.0))

        val updateResult = repo.updateLocalPrice("mp-01", "store-01", 149.0)
        assertIs<Result.Success<Unit>>(updateResult)

        val fetched = (repo.getOverride("mp-01", "store-01") as Result.Success).data
        assertEquals(149.0, fetched.localPrice)
    }

    @Test
    fun `F - updateLocalPrice with null clears the price`() = runTest {
        repo.upsertFromSync(makeOverride(id = "spo-01", localPrice = 99.0))

        val updateResult = repo.updateLocalPrice("mp-01", "store-01", null)
        assertIs<Result.Success<Unit>>(updateResult)

        val fetched = (repo.getOverride("mp-01", "store-01") as Result.Success).data
        assertNull(fetched.localPrice)
    }

    @Test
    fun `G - updateLocalStock changes the stock quantity`() = runTest {
        repo.upsertFromSync(makeOverride(id = "spo-01", localStockQty = 50.0))

        val updateResult = repo.updateLocalStock("mp-01", "store-01", 75.0)
        assertIs<Result.Success<Unit>>(updateResult)

        val fetched = (repo.getOverride("mp-01", "store-01") as Result.Success).data
        assertEquals(75.0, fetched.localStockQty)
    }
}
