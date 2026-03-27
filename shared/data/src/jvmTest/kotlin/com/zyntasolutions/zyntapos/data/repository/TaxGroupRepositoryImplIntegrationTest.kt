package com.zyntasolutions.zyntapos.data.repository

import app.cash.turbine.test
import com.zyntasolutions.zyntapos.core.result.Result
import com.zyntasolutions.zyntapos.data.createTestDatabase
import com.zyntasolutions.zyntapos.data.local.SyncEnqueuer
import com.zyntasolutions.zyntapos.db.ZyntaDatabase
import com.zyntasolutions.zyntapos.domain.model.TaxGroup
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlinx.coroutines.test.runTest

/**
 * ZyntaPOS — TaxGroupRepositoryImpl Integration Tests (jvmTest)
 *
 * Validates [TaxGroupRepositoryImpl] against a real in-memory SQLite database.
 * No mocks — exercises the full SQLDelight query layer.
 *
 * Coverage:
 *  A. insert → getById round-trip preserves all fields
 *  B. getAll emits only active, non-deleted tax groups (Turbine)
 *  C. update changes name and rate
 *  D. delete (soft) removes from getAll but getById still returns it
 *  E. delete with active product reference returns ValidationException error
 *  F. getById for unknown ID returns error
 *  G. getAll excludes soft-deleted groups
 */
class TaxGroupRepositoryImplIntegrationTest {

    private lateinit var db: ZyntaDatabase
    private lateinit var repo: TaxGroupRepositoryImpl

    @BeforeTest
    fun setup() {
        db = createTestDatabase()
        repo = TaxGroupRepositoryImpl(db, SyncEnqueuer(db))
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private val now get() = Clock.System.now().toEpochMilliseconds()

    private fun makeTaxGroup(
        id: String = "tax-01",
        name: String = "Standard VAT",
        rate: Double = 15.0,
        isInclusive: Boolean = false,
        isActive: Boolean = true,
    ) = TaxGroup(
        id = id,
        name = name,
        rate = rate,
        isInclusive = isInclusive,
        isActive = isActive,
    )

    private fun insertProductWithTaxGroup(productId: String, taxGroupId: String) {
        db.productsQueries.insertProduct(
            id = productId, name = "Product $productId", barcode = null, sku = null,
            category_id = null, unit_id = "pcs", price = 10.0, cost_price = 5.0,
            tax_group_id = taxGroupId, stock_qty = 10.0, min_stock_qty = 0.0,
            image_url = null, description = null, is_active = 1L,
            created_at = now, updated_at = now, sync_status = "PENDING",
            master_product_id = null,
        )
    }

    // ── Tests ─────────────────────────────────────────────────────────────────

    @Test
    fun `A - insert then getById returns full tax group`() = runTest {
        val tg = makeTaxGroup(id = "tax-01", name = "Zero Rate", rate = 0.0, isInclusive = false)
        val insertResult = repo.insert(tg)
        assertIs<Result.Success<Unit>>(insertResult)

        val fetchResult = repo.getById("tax-01")
        assertIs<Result.Success<TaxGroup>>(fetchResult)
        val fetched = fetchResult.data
        assertEquals("tax-01", fetched.id)
        assertEquals("Zero Rate", fetched.name)
        assertEquals(0.0, fetched.rate)
        assertTrue(!fetched.isInclusive)
        assertTrue(fetched.isActive)
    }

    @Test
    fun `B - getAll emits only active non-deleted tax groups`() = runTest {
        repo.insert(makeTaxGroup(id = "tax-01", name = "Standard VAT"))
        repo.insert(makeTaxGroup(id = "tax-02", name = "Reduced VAT", rate = 5.0))
        repo.insert(makeTaxGroup(id = "tax-03", name = "Inactive Group", isActive = false))

        repo.getAll().test {
            val list = awaitItem()
            // is_active=0 group excluded by getAllTaxGroups query
            assertEquals(2, list.size)
            assertTrue(list.any { it.name == "Standard VAT" })
            assertTrue(list.any { it.name == "Reduced VAT" })
            assertTrue(list.none { it.name == "Inactive Group" })
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `C - update changes name and rate`() = runTest {
        repo.insert(makeTaxGroup(id = "tax-01", name = "Old Name", rate = 10.0))

        val updated = makeTaxGroup(id = "tax-01", name = "New Name", rate = 20.0, isInclusive = true)
        val updateResult = repo.update(updated)
        assertIs<Result.Success<Unit>>(updateResult)

        val fetched = (repo.getById("tax-01") as Result.Success).data
        assertEquals("New Name", fetched.name)
        assertEquals(20.0, fetched.rate)
        assertTrue(fetched.isInclusive)
    }

    @Test
    fun `D - delete soft-deletes and getById still returns it but getAll excludes it`() = runTest {
        repo.insert(makeTaxGroup(id = "tax-01", name = "To Delete"))

        val deleteResult = repo.delete("tax-01")
        assertIs<Result.Success<Unit>>(deleteResult)

        // getById returns the soft-deleted row (no deleted_at filter on getTaxGroupById)
        val fetchResult = repo.getById("tax-01")
        assertIs<Result.Success<TaxGroup>>(fetchResult)

        // getAll excludes it (deleted_at IS NULL filter)
        repo.getAll().test {
            val list = awaitItem()
            assertTrue(list.none { it.id == "tax-01" })
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `E - delete with active product reference returns ValidationException`() = runTest {
        repo.insert(makeTaxGroup(id = "tax-01", name = "In-Use Tax"))
        insertProductWithTaxGroup("prod-01", "tax-01")

        val deleteResult = repo.delete("tax-01")
        assertIs<Result.Error>(deleteResult)
        assertNotNull((deleteResult as Result.Error).exception)

        // Tax group should still be accessible after failed delete
        val fetchResult = repo.getById("tax-01")
        assertIs<Result.Success<TaxGroup>>(fetchResult)
    }

    @Test
    fun `F - getById for unknown ID returns error`() = runTest {
        val result = repo.getById("non-existent")
        assertIs<Result.Error>(result)
        assertNotNull((result as Result.Error).exception)
    }

    @Test
    fun `G - getAll excludes soft-deleted groups`() = runTest {
        repo.insert(makeTaxGroup(id = "tax-01", name = "Active"))
        repo.insert(makeTaxGroup(id = "tax-02", name = "Deleted"))
        repo.delete("tax-02")

        repo.getAll().test {
            val list = awaitItem()
            assertEquals(1, list.size)
            assertEquals("Active", list.first().name)
            cancelAndIgnoreRemainingEvents()
        }
    }
}
