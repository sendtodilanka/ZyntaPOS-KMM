package com.zyntasolutions.zyntapos.data.repository

import app.cash.turbine.test
import com.zyntasolutions.zyntapos.core.result.Result
import com.zyntasolutions.zyntapos.data.createTestDatabase
import com.zyntasolutions.zyntapos.data.local.SyncEnqueuer
import com.zyntasolutions.zyntapos.db.ZyntaDatabase
import com.zyntasolutions.zyntapos.domain.model.CustomerGroup
import com.zyntasolutions.zyntapos.domain.model.DiscountType
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

/**
 * ZyntaPOS — CustomerGroupRepositoryImpl Integration Tests (jvmTest)
 *
 * Validates [CustomerGroupRepositoryImpl] against a real in-memory SQLite database.
 * No mocks — exercises the full SQLDelight query layer.
 *
 * Coverage:
 *  A. insert → getById round-trip preserves all fields
 *  B. getAll emits only non-deleted groups (Turbine)
 *  C. update changes name and discount settings
 *  D. delete (soft) removes from getAll but getById still returns it
 *  E. getById for unknown ID returns error
 *  F. insert with null discountType stores null correctly
 *  G. getAll excludes soft-deleted groups
 */
class CustomerGroupRepositoryImplIntegrationTest {

    private lateinit var db: ZyntaDatabase
    private lateinit var repo: CustomerGroupRepositoryImpl

    @BeforeTest
    fun setup() {
        db = createTestDatabase()
        repo = CustomerGroupRepositoryImpl(db, SyncEnqueuer(db))
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private fun makeGroup(
        id: String = "grp-01",
        name: String = "VIP",
        description: String? = "VIP Customers",
        discountType: DiscountType? = DiscountType.PERCENT,
        discountValue: Double = 10.0,
        priceType: CustomerGroup.PriceType = CustomerGroup.PriceType.RETAIL,
    ) = CustomerGroup(
        id = id,
        name = name,
        description = description,
        discountType = discountType,
        discountValue = discountValue,
        priceType = priceType,
    )

    // ── Tests ─────────────────────────────────────────────────────────────────

    @Test
    fun `A - insert then getById returns full group`() = runTest {
        val group = makeGroup(
            id = "grp-01",
            name = "Wholesale",
            description = "Wholesale Customers",
            discountType = DiscountType.PERCENT,
            discountValue = 15.0,
            priceType = CustomerGroup.PriceType.WHOLESALE,
        )
        val insertResult = repo.insert(group)
        assertIs<Result.Success<Unit>>(insertResult)

        val fetchResult = repo.getById("grp-01")
        assertIs<Result.Success<CustomerGroup>>(fetchResult)
        val fetched = fetchResult.data
        assertEquals("grp-01", fetched.id)
        assertEquals("Wholesale", fetched.name)
        assertEquals("Wholesale Customers", fetched.description)
        assertEquals(DiscountType.PERCENT, fetched.discountType)
        assertEquals(15.0, fetched.discountValue)
        assertEquals(CustomerGroup.PriceType.WHOLESALE, fetched.priceType)
    }

    @Test
    fun `B - getAll emits only non-deleted groups via Turbine`() = runTest {
        repo.insert(makeGroup(id = "grp-01", name = "VIP"))
        repo.insert(makeGroup(id = "grp-02", name = "Wholesale"))
        repo.insert(makeGroup(id = "grp-03", name = "Staff"))

        repo.getAll().test {
            val list = awaitItem()
            assertEquals(3, list.size)
            assertTrue(list.any { it.name == "VIP" })
            assertTrue(list.any { it.name == "Wholesale" })
            assertTrue(list.any { it.name == "Staff" })
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `C - update changes name and discount settings`() = runTest {
        repo.insert(makeGroup(id = "grp-01", name = "Old Name", discountValue = 5.0))

        val updated = makeGroup(
            id = "grp-01",
            name = "New Name",
            discountType = DiscountType.FIXED,
            discountValue = 100.0,
            priceType = CustomerGroup.PriceType.CUSTOM,
        )
        val updateResult = repo.update(updated)
        assertIs<Result.Success<Unit>>(updateResult)

        val fetched = (repo.getById("grp-01") as Result.Success).data
        assertEquals("New Name", fetched.name)
        assertEquals(DiscountType.FIXED, fetched.discountType)
        assertEquals(100.0, fetched.discountValue)
        assertEquals(CustomerGroup.PriceType.CUSTOM, fetched.priceType)
    }

    @Test
    fun `D - delete soft-deletes and getById still returns it but getAll excludes it`() = runTest {
        repo.insert(makeGroup(id = "grp-01", name = "To Delete"))

        val deleteResult = repo.delete("grp-01")
        assertIs<Result.Success<Unit>>(deleteResult)

        // getById uses no deleted_at filter — still returns soft-deleted row
        val fetchResult = repo.getById("grp-01")
        assertIs<Result.Success<CustomerGroup>>(fetchResult)

        // getAll uses deleted_at IS NULL — excludes soft-deleted
        repo.getAll().test {
            val list = awaitItem()
            assertTrue(list.none { it.id == "grp-01" })
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `E - getById for unknown ID returns error`() = runTest {
        val result = repo.getById("non-existent")
        assertIs<Result.Error>(result)
        assertNotNull(result.exception)
    }

    @Test
    fun `F - insert with null discountType stores null correctly`() = runTest {
        repo.insert(makeGroup(id = "grp-01", name = "No Discount", discountType = null, discountValue = 0.0))

        val fetched = (repo.getById("grp-01") as Result.Success).data
        assertNull(fetched.discountType)
        assertEquals(0.0, fetched.discountValue)
    }

    @Test
    fun `G - getAll excludes soft-deleted groups`() = runTest {
        repo.insert(makeGroup(id = "grp-01", name = "Active"))
        repo.insert(makeGroup(id = "grp-02", name = "Deleted"))
        repo.delete("grp-02")

        repo.getAll().test {
            val list = awaitItem()
            assertEquals(1, list.size)
            assertEquals("Active", list.first().name)
            cancelAndIgnoreRemainingEvents()
        }
    }
}
