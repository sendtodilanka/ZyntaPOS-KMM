package com.zyntasolutions.zyntapos.data.repository

import app.cash.turbine.test
import com.zyntasolutions.zyntapos.core.result.Result
import com.zyntasolutions.zyntapos.data.createTestDatabase
import com.zyntasolutions.zyntapos.data.local.SyncEnqueuer
import com.zyntasolutions.zyntapos.db.ZyntaDatabase
import com.zyntasolutions.zyntapos.core.pagination.PageRequest
import com.zyntasolutions.zyntapos.domain.model.Product
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant

/**
 * ZyntaPOS — ProductRepositoryImpl Integration Tests (jvmTest)
 *
 * Validates [ProductRepositoryImpl] against a real in-memory SQLite database.
 * products has no FK constraints (category_id, unit_id, tax_group_id are TEXT only).
 *
 * Coverage:
 *  A. insert → getById round-trip preserves all fields
 *  B. getAll emits only active products via Turbine
 *  C. search by name returns matching products
 *  D. search by category filters results
 *  E. getByBarcode returns matching product
 *  F. getByBarcode returns error for unknown barcode
 *  G. update changes product fields
 *  H. delete soft-deletes (isActive = false, excluded from getAll)
 *  I. getPage returns paginated results
 *  J. getCount returns correct count
 */
class ProductRepositoryImplIntegrationTest {

    private lateinit var db: ZyntaDatabase
    private lateinit var repo: ProductRepositoryImpl

    @BeforeTest
    fun setup() {
        db = createTestDatabase()
        repo = ProductRepositoryImpl(db, SyncEnqueuer(db))
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private val nowInstant get() = Instant.fromEpochMilliseconds(Clock.System.now().toEpochMilliseconds())

    private fun makeProduct(
        id: String = "prod-01",
        name: String = "T-Shirt",
        barcode: String? = "1234567890123",
        sku: String? = "TS-001",
        categoryId: String = "cat-clothing",
        unitId: String = "pcs",
        price: Double = 2500.0,
        costPrice: Double = 1200.0,
        isActive: Boolean = true,
    ) = Product(
        id = id,
        name = name,
        barcode = barcode,
        sku = sku,
        categoryId = categoryId,
        unitId = unitId,
        price = price,
        costPrice = costPrice,
        taxGroupId = null,
        stockQty = 100.0,
        minStockQty = 10.0,
        imageUrl = null,
        description = null,
        isActive = isActive,
        createdAt = nowInstant,
        updatedAt = nowInstant,
        masterProductId = null,
    )

    // ── Tests ─────────────────────────────────────────────────────────────────

    @Test
    fun `A - insert then getById round-trip preserves all fields`() = runTest {
        val product = makeProduct(
            id = "prod-01",
            name = "Premium T-Shirt",
            barcode = "9781234567897",
            sku = "TS-PREM-001",
            categoryId = "cat-clothing",
            price = 3500.0,
            costPrice = 1500.0,
        )
        val insertResult = repo.insert(product)
        assertIs<Result.Success<Unit>>(insertResult)

        val fetchResult = repo.getById("prod-01")
        assertIs<Result.Success<Product>>(fetchResult)
        val fetched = fetchResult.data
        assertEquals("prod-01", fetched.id)
        assertEquals("Premium T-Shirt", fetched.name)
        assertEquals("9781234567897", fetched.barcode)
        assertEquals("TS-PREM-001", fetched.sku)
        assertEquals("cat-clothing", fetched.categoryId)
        assertEquals(3500.0, fetched.price)
        assertEquals(1500.0, fetched.costPrice)
        assertTrue(fetched.isActive)
    }

    @Test
    fun `B - getAll emits only active products via Turbine`() = runTest {
        repo.insert(makeProduct(id = "prod-01", name = "Active Product", isActive = true))
        repo.insert(makeProduct(id = "prod-02", name = "Inactive Product", barcode = null, sku = null,
            isActive = false))

        repo.getAll().test {
            val list = awaitItem()
            assertEquals(1, list.size)
            assertEquals("prod-01", list.first().id)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `C - search by name returns matching products`() = runTest {
        repo.insert(makeProduct(id = "prod-01", name = "Blue T-Shirt", barcode = "1000000000001", sku = "TS-B"))
        repo.insert(makeProduct(id = "prod-02", name = "Red Hoodie", barcode = "1000000000002", sku = "HOO-R"))
        repo.insert(makeProduct(id = "prod-03", name = "Black T-Shirt", barcode = "1000000000003", sku = "TS-BL"))

        repo.search("T-Shirt", null).test {
            val list = awaitItem()
            assertEquals(2, list.size)
            assertTrue(list.all { it.name.contains("T-Shirt") })
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `D - search by category filters results`() = runTest {
        repo.insert(makeProduct(id = "prod-01", name = "T-Shirt", barcode = "2000000000001",
            sku = "TS1", categoryId = "cat-clothing"))
        repo.insert(makeProduct(id = "prod-02", name = "Coffee", barcode = "2000000000002",
            sku = "COF1", categoryId = "cat-beverages"))

        repo.search("", "cat-clothing").test {
            val list = awaitItem()
            assertEquals(1, list.size)
            assertEquals("cat-clothing", list.first().categoryId)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `E - getByBarcode returns matching product`() = runTest {
        repo.insert(makeProduct(id = "prod-01", name = "T-Shirt", barcode = "5901234123457"))

        val result = repo.getByBarcode("5901234123457")
        assertIs<Result.Success<Product>>(result)
        assertEquals("prod-01", result.data.id)
    }

    @Test
    fun `F - getByBarcode returns error for unknown barcode`() = runTest {
        val result = repo.getByBarcode("9999999999999")
        assertIs<Result.Error>(result)
        assertNotNull(result.exception)
    }

    @Test
    fun `G - update changes product fields`() = runTest {
        repo.insert(makeProduct(id = "prod-01", name = "Old Name", price = 1000.0))

        val original = (repo.getById("prod-01") as Result.Success).data
        val updated = original.copy(name = "New Name", price = 1500.0)
        val updateResult = repo.update(updated)
        assertIs<Result.Success<Unit>>(updateResult)

        val fetched = (repo.getById("prod-01") as Result.Success).data
        assertEquals("New Name", fetched.name)
        assertEquals(1500.0, fetched.price)
    }

    @Test
    fun `H - delete soft-deletes product excluded from getAll`() = runTest {
        repo.insert(makeProduct(id = "prod-01", name = "Delete Me"))
        repo.insert(makeProduct(id = "prod-02", name = "Keep Me", barcode = null, sku = null))

        val deleteResult = repo.delete("prod-01")
        assertIs<Result.Success<Unit>>(deleteResult)

        repo.getAll().test {
            val list = awaitItem()
            assertEquals(1, list.size)
            assertEquals("prod-02", list.first().id)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `I - getPage returns paginated results`() = runTest {
        (1..5).forEach { i ->
            repo.insert(makeProduct(id = "prod-0$i", name = "Product $i",
                barcode = "30000000$i", sku = "P0$i"))
        }

        val result = repo.getPage(PageRequest(limit = 3, offset = 0), categoryId = null)
        assertIs<Result.Success<*>>(result)
        val page = result.data as com.zyntasolutions.zyntapos.core.pagination.PaginatedResult<*>
        assertEquals(3, page.items.size)
    }

    @Test
    fun `J - getCount returns correct count of active products`() = runTest {
        assertEquals(0, repo.getCount())

        repo.insert(makeProduct(id = "prod-01", name = "P1"))
        repo.insert(makeProduct(id = "prod-02", name = "P2", barcode = null, sku = null))
        assertEquals(2, repo.getCount())

        repo.delete("prod-01")
        assertEquals(1, repo.getCount())
    }
}
