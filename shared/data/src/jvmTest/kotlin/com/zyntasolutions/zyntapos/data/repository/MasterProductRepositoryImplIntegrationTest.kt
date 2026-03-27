package com.zyntasolutions.zyntapos.data.repository

import app.cash.turbine.test
import com.zyntasolutions.zyntapos.core.result.Result
import com.zyntasolutions.zyntapos.data.createTestDatabase
import com.zyntasolutions.zyntapos.db.ZyntaDatabase
import com.zyntasolutions.zyntapos.domain.model.MasterProduct
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Instant
import kotlinx.coroutines.test.runTest

/**
 * ZyntaPOS — MasterProductRepositoryImpl Integration Tests (jvmTest)
 *
 * Validates [MasterProductRepositoryImpl] against a real in-memory SQLite database.
 * No mocks — exercises the full SQLDelight query layer including FTS5 search.
 *
 * Coverage:
 *  A. upsertFromSync → getById round-trip preserves all fields
 *  B. getAll emits all active master products (Turbine)
 *  C. getByBarcode returns the correct product
 *  D. getByBarcode for unknown barcode returns error
 *  E. search filters products by name query via FTS5
 *  F. search with blank query returns all products
 *  G. upsertFromSync twice updates existing product (INSERT OR REPLACE)
 *  H. getById for unknown ID returns error
 */
class MasterProductRepositoryImplIntegrationTest {

    private lateinit var db: ZyntaDatabase
    private lateinit var repo: MasterProductRepositoryImpl

    @BeforeTest
    fun setup() {
        db = createTestDatabase()
        repo = MasterProductRepositoryImpl(db)
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private val baseInstant: Instant = Instant.fromEpochMilliseconds(1_000_000L)

    private fun makeMasterProduct(
        id: String = "mp-01",
        name: String = "Widget Alpha",
        sku: String? = "SKU-001",
        barcode: String? = "1234567890123",
        description: String? = "A test widget",
        basePrice: Double = 25.0,
        costPrice: Double = 12.0,
        isActive: Boolean = true,
        createdAt: Instant = baseInstant,
        updatedAt: Instant = baseInstant,
    ) = MasterProduct(
        id = id,
        name = name,
        sku = sku,
        barcode = barcode,
        description = description,
        basePrice = basePrice,
        costPrice = costPrice,
        isActive = isActive,
        createdAt = createdAt,
        updatedAt = updatedAt,
    )

    // ── Tests ─────────────────────────────────────────────────────────────────

    @Test
    fun `A - upsertFromSync then getById returns full product`() = runTest {
        val mp = makeMasterProduct(id = "mp-01", name = "Premium Coffee", barcode = "9876543210")
        val upsertResult = repo.upsertFromSync(mp)
        assertIs<Result.Success<Unit>>(upsertResult)

        val fetchResult = repo.getById("mp-01")
        assertIs<Result.Success<MasterProduct>>(fetchResult)
        val fetched = fetchResult.data
        assertEquals("mp-01", fetched.id)
        assertEquals("Premium Coffee", fetched.name)
        assertEquals("9876543210", fetched.barcode)
        assertEquals("SKU-001", fetched.sku)
        assertEquals("A test widget", fetched.description)
        assertEquals(25.0, fetched.basePrice)
        assertEquals(12.0, fetched.costPrice)
        assertTrue(fetched.isActive)
        assertEquals(1_000_000L, fetched.createdAt.toEpochMilliseconds())
    }

    @Test
    fun `B - getAll emits all active master products`() = runTest {
        repo.upsertFromSync(makeMasterProduct(id = "mp-01", name = "Product A"))
        repo.upsertFromSync(makeMasterProduct(id = "mp-02", name = "Product B"))
        repo.upsertFromSync(makeMasterProduct(id = "mp-03", name = "Inactive Product", isActive = false))

        repo.getAll().test {
            val list = awaitItem()
            // getAll only returns active products (is_active = 1)
            assertEquals(2, list.size)
            assertTrue(list.none { it.id == "mp-03" })
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `C - getByBarcode returns correct product`() = runTest {
        repo.upsertFromSync(makeMasterProduct(id = "mp-01", name = "Barcode Product", barcode = "BAR-001"))
        repo.upsertFromSync(makeMasterProduct(id = "mp-02", name = "Other Product", barcode = "BAR-002"))

        val result = repo.getByBarcode("BAR-001")
        assertIs<Result.Success<MasterProduct>>(result)
        assertEquals("mp-01", result.data.id)
        assertEquals("Barcode Product", result.data.name)
    }

    @Test
    fun `D - getByBarcode for unknown barcode returns error`() = runTest {
        val result = repo.getByBarcode("UNKNOWN-BAR")
        assertIs<Result.Error>(result)
        assertNotNull((result as Result.Error).exception)
    }

    @Test
    fun `E - search filters products by name query via FTS5`() = runTest {
        repo.upsertFromSync(makeMasterProduct(id = "mp-01", name = "Blue Widget", barcode = "BAR-01"))
        repo.upsertFromSync(makeMasterProduct(id = "mp-02", name = "Red Widget", barcode = "BAR-02"))
        repo.upsertFromSync(makeMasterProduct(id = "mp-03", name = "Green Gadget", barcode = "BAR-03"))

        repo.search("Widget").test {
            val list = awaitItem()
            assertEquals(2, list.size)
            assertTrue(list.any { it.id == "mp-01" })
            assertTrue(list.any { it.id == "mp-02" })
            assertTrue(list.none { it.id == "mp-03" })
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `F - search with blank query returns all products`() = runTest {
        repo.upsertFromSync(makeMasterProduct(id = "mp-01", name = "Alpha", barcode = "BAR-01"))
        repo.upsertFromSync(makeMasterProduct(id = "mp-02", name = "Beta", barcode = "BAR-02"))

        repo.search("").test {
            val list = awaitItem()
            assertEquals(2, list.size)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `G - upsertFromSync twice updates existing product`() = runTest {
        repo.upsertFromSync(makeMasterProduct(id = "mp-01", name = "Original Name", basePrice = 10.0))
        repo.upsertFromSync(makeMasterProduct(id = "mp-01", name = "Updated Name", basePrice = 20.0))

        val fetched = (repo.getById("mp-01") as Result.Success).data
        assertEquals("Updated Name", fetched.name)
        assertEquals(20.0, fetched.basePrice)
    }

    @Test
    fun `H - getById for unknown ID returns error`() = runTest {
        val result = repo.getById("non-existent")
        assertIs<Result.Error>(result)
        assertNotNull((result as Result.Error).exception)
    }
}
