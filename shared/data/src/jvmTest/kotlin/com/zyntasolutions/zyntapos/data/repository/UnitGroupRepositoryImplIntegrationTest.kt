package com.zyntasolutions.zyntapos.data.repository

import app.cash.turbine.test
import com.zyntasolutions.zyntapos.core.result.Result
import com.zyntasolutions.zyntapos.data.createTestDatabase
import com.zyntasolutions.zyntapos.data.local.SyncEnqueuer
import com.zyntasolutions.zyntapos.db.ZyntaDatabase
import com.zyntasolutions.zyntapos.domain.model.UnitOfMeasure
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlinx.coroutines.test.runTest

/**
 * ZyntaPOS — UnitGroupRepositoryImpl Integration Tests (jvmTest)
 *
 * Validates [UnitGroupRepositoryImpl] against a real in-memory SQLite database.
 * No mocks — exercises the full SQLDelight query layer.
 *
 * Coverage:
 *  A. insert → getById round-trip preserves all fields
 *  B. getAll emits all units via Turbine
 *  C. update changes name and conversionRate
 *  D. insert with isBaseUnit=true demotes existing base unit
 *  E. delete soft-deletes non-base non-referenced unit
 *  F. delete base unit returns ValidationException
 *  G. delete with active product reference returns ValidationException
 *  H. insert with conversionRate <= 0 returns ValidationException
 *  I. getById for unknown ID returns error
 */
class UnitGroupRepositoryImplIntegrationTest {

    private lateinit var db: ZyntaDatabase
    private lateinit var repo: UnitGroupRepositoryImpl

    @BeforeTest
    fun setup() {
        db = createTestDatabase()
        repo = UnitGroupRepositoryImpl(db, SyncEnqueuer(db))
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private val now get() = Clock.System.now().toEpochMilliseconds()

    private fun makeUnit(
        id: String = "unit-01",
        name: String = "Kilogram",
        abbreviation: String = "kg",
        isBaseUnit: Boolean = false,
        conversionRate: Double = 1.0,
    ) = UnitOfMeasure(
        id = id,
        name = name,
        abbreviation = abbreviation,
        isBaseUnit = isBaseUnit,
        conversionRate = conversionRate,
    )

    private fun insertProductWithUnit(productId: String, unitId: String) {
        db.productsQueries.insertProduct(
            id = productId, name = "Product $productId", barcode = null, sku = null,
            category_id = null, unit_id = unitId, price = 10.0, cost_price = 5.0,
            tax_group_id = null, stock_qty = 10.0, min_stock_qty = 0.0,
            image_url = null, description = null, is_active = 1L,
            created_at = now, updated_at = now, sync_status = "PENDING",
            master_product_id = null,
        )
    }

    // ── Tests ─────────────────────────────────────────────────────────────────

    @Test
    fun `A - insert then getById returns full unit`() = runTest {
        val unit = makeUnit(id = "unit-01", name = "Gram", abbreviation = "g", conversionRate = 0.001)
        val insertResult = repo.insert(unit)
        assertIs<Result.Success<Unit>>(insertResult)

        val fetchResult = repo.getById("unit-01")
        assertIs<Result.Success<UnitOfMeasure>>(fetchResult)
        val fetched = fetchResult.data
        assertEquals("unit-01", fetched.id)
        assertEquals("Gram", fetched.name)
        assertEquals("g", fetched.abbreviation)
        assertEquals(0.001, fetched.conversionRate)
        assertTrue(!fetched.isBaseUnit)
    }

    @Test
    fun `B - getAll emits all units via Turbine`() = runTest {
        repo.insert(makeUnit(id = "unit-01", name = "Kilogram", abbreviation = "kg", conversionRate = 1.0, isBaseUnit = true))
        repo.insert(makeUnit(id = "unit-02", name = "Gram", abbreviation = "g", conversionRate = 0.001))
        repo.insert(makeUnit(id = "unit-03", name = "Pound", abbreviation = "lb", conversionRate = 0.453))

        repo.getAll().test {
            val list = awaitItem()
            assertEquals(3, list.size)
            assertTrue(list.any { it.id == "unit-01" })
            assertTrue(list.any { it.id == "unit-02" })
            assertTrue(list.any { it.id == "unit-03" })
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `C - update changes name and conversionRate`() = runTest {
        repo.insert(makeUnit(id = "unit-01", name = "Old Name", conversionRate = 1.0))

        val updated = makeUnit(id = "unit-01", name = "New Name", abbreviation = "new", conversionRate = 5.0)
        val updateResult = repo.update(updated)
        assertIs<Result.Success<Unit>>(updateResult)

        val fetched = (repo.getById("unit-01") as Result.Success).data
        assertEquals("New Name", fetched.name)
        assertEquals("new", fetched.abbreviation)
        assertEquals(5.0, fetched.conversionRate)
    }

    @Test
    fun `D - insert with isBaseUnit=true demotes existing base unit`() = runTest {
        repo.insert(makeUnit(id = "unit-01", name = "Kilogram", isBaseUnit = true, conversionRate = 1.0))

        // Insert a second unit claiming to be base unit
        repo.insert(makeUnit(id = "unit-02", name = "Gram", isBaseUnit = true, conversionRate = 0.001))

        // unit-02 should be the base unit now; unit-01 should be demoted
        val unit01 = (repo.getById("unit-01") as Result.Success).data
        val unit02 = (repo.getById("unit-02") as Result.Success).data
        assertTrue(!unit01.isBaseUnit)
        assertTrue(unit02.isBaseUnit)
    }

    @Test
    fun `E - delete soft-deletes non-base non-referenced unit`() = runTest {
        repo.insert(makeUnit(id = "unit-01", name = "Base Unit", isBaseUnit = true, conversionRate = 1.0))
        repo.insert(makeUnit(id = "unit-02", name = "Derived", conversionRate = 2.0))

        val deleteResult = repo.delete("unit-02")
        assertIs<Result.Success<Unit>>(deleteResult)

        // Soft-deleted unit is not in getAll (getAllUnits filters deleted_at IS NULL)
        repo.getAll().test {
            val list = awaitItem()
            assertTrue(list.none { it.id == "unit-02" })
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `F - delete base unit returns ValidationException`() = runTest {
        repo.insert(makeUnit(id = "unit-01", name = "Base Unit", isBaseUnit = true, conversionRate = 1.0))

        val deleteResult = repo.delete("unit-01")
        assertIs<Result.Error>(deleteResult)
        assertNotNull(deleteResult.exception)

        // Base unit should still exist
        val fetchResult = repo.getById("unit-01")
        assertIs<Result.Success<UnitOfMeasure>>(fetchResult)
    }

    @Test
    fun `G - delete with active product reference returns ValidationException`() = runTest {
        repo.insert(makeUnit(id = "unit-01", name = "Piece", abbreviation = "pcs", isBaseUnit = false, conversionRate = 1.0))
        insertProductWithUnit("prod-01", "unit-01")

        val deleteResult = repo.delete("unit-01")
        assertIs<Result.Error>(deleteResult)
        assertNotNull(deleteResult.exception)

        // Unit should still be accessible
        val fetchResult = repo.getById("unit-01")
        assertIs<Result.Success<UnitOfMeasure>>(fetchResult)
    }

    @Test
    fun `H - insert with conversionRate 0 returns ValidationException`() = runTest {
        val unitWithZeroRate = makeUnit(id = "unit-01", conversionRate = 0.0)
        val result = repo.insert(unitWithZeroRate)
        assertIs<Result.Error>(result)
        assertNotNull(result.exception)
    }

    @Test
    fun `I - getById for unknown ID returns error`() = runTest {
        val result = repo.getById("non-existent")
        assertIs<Result.Error>(result)
        assertNotNull(result.exception)
    }
}
