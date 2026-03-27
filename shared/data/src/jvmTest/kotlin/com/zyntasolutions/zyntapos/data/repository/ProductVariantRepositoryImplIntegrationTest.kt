package com.zyntasolutions.zyntapos.data.repository

import app.cash.turbine.test
import com.zyntasolutions.zyntapos.core.result.Result
import com.zyntasolutions.zyntapos.data.createTestDatabase
import com.zyntasolutions.zyntapos.data.local.SyncEnqueuer
import com.zyntasolutions.zyntapos.db.ZyntaDatabase
import com.zyntasolutions.zyntapos.domain.model.ProductVariant
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
 * ZyntaPOS — ProductVariantRepositoryImpl Integration Tests (jvmTest)
 *
 * Validates [ProductVariantRepositoryImpl] against a real in-memory SQLite database.
 * Requires a product seeded to satisfy the product_id FK constraint.
 *
 * Coverage:
 *  A. insert → getById round-trip preserves all fields including attributes
 *  B. getByProductId emits variants via Turbine
 *  C. getByBarcode returns correct variant
 *  D. getByBarcode unknown barcode returns error
 *  E. update changes name, price, stock, barcode, and attributes
 *  F. delete removes a variant
 *  G. deleteByProductId removes all variants for a product
 *  H. replaceAll replaces existing variants atomically
 *  I. getById unknown id returns error
 */
class ProductVariantRepositoryImplIntegrationTest {

    private lateinit var db: ZyntaDatabase
    private lateinit var repo: ProductVariantRepositoryImpl

    @BeforeTest
    fun setup() {
        db = createTestDatabase()
        repo = ProductVariantRepositoryImpl(db, SyncEnqueuer(db))

        val now = Clock.System.now().toEpochMilliseconds()

        // Seed products required by product_id FK
        db.productsQueries.insertProduct(
            id = "prod-01", name = "T-Shirt", barcode = null, sku = "TSH-01",
            category_id = null, unit_id = null, price = 500.0, cost_price = 300.0,
            tax_group_id = null, stock_qty = 0.0, min_stock_qty = 5.0,
            image_url = null, description = null, is_active = 1L,
            created_at = now, updated_at = now, sync_status = "PENDING",
            master_product_id = null,
        )
        db.productsQueries.insertProduct(
            id = "prod-02", name = "Hoodie", barcode = null, sku = "HOD-01",
            category_id = null, unit_id = null, price = 1000.0, cost_price = 600.0,
            tax_group_id = null, stock_qty = 0.0, min_stock_qty = 5.0,
            image_url = null, description = null, is_active = 1L,
            created_at = now, updated_at = now, sync_status = "PENDING",
            master_product_id = null,
        )
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private fun makeVariant(
        id: String = "var-01",
        productId: String = "prod-01",
        name: String = "Blue / XL",
        attributes: Map<String, String> = mapOf("Color" to "Blue", "Size" to "XL"),
        price: Double? = 550.0,
        stock: Double = 10.0,
        barcode: String? = "BC-001",
    ) = ProductVariant(
        id = id,
        productId = productId,
        name = name,
        attributes = attributes,
        price = price,
        stock = stock,
        barcode = barcode,
    )

    // ── Tests ─────────────────────────────────────────────────────────────────

    @Test
    fun `A - insert then getById round-trip preserves all fields including attributes`() = runTest {
        val variant = makeVariant(
            id = "var-01",
            productId = "prod-01",
            name = "Blue / XL",
            attributes = mapOf("Color" to "Blue", "Size" to "XL"),
            price = 550.0,
            stock = 10.0,
            barcode = "BC-001",
        )
        val insertResult = repo.insert(variant)
        assertIs<Result.Success<Unit>>(insertResult)

        val fetchResult = repo.getById("var-01")
        assertIs<Result.Success<ProductVariant>>(fetchResult)
        val fetched = fetchResult.data
        assertEquals("var-01", fetched.id)
        assertEquals("prod-01", fetched.productId)
        assertEquals("Blue / XL", fetched.name)
        assertEquals(mapOf("Color" to "Blue", "Size" to "XL"), fetched.attributes)
        assertEquals(550.0, fetched.price)
        assertEquals(10.0, fetched.stock)
        assertEquals("BC-001", fetched.barcode)
    }

    @Test
    fun `B - getByProductId emits variants via Turbine`() = runTest {
        repo.insert(makeVariant(id = "var-01", productId = "prod-01", name = "Blue / XL"))
        repo.insert(makeVariant(id = "var-02", productId = "prod-01", name = "Red / M", barcode = "BC-002"))

        repo.getByProductId("prod-01").test {
            val list = awaitItem()
            assertEquals(2, list.size)
            assertTrue(list.any { it.name == "Blue / XL" })
            assertTrue(list.any { it.name == "Red / M" })
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `C - getByBarcode returns correct variant`() = runTest {
        repo.insert(makeVariant(id = "var-01", barcode = "BC-001"))
        repo.insert(makeVariant(id = "var-02", barcode = "BC-002", name = "Red / S"))

        val result = repo.getByBarcode("BC-002")
        assertIs<Result.Success<ProductVariant>>(result)
        assertEquals("var-02", result.data.id)
        assertEquals("Red / S", result.data.name)
    }

    @Test
    fun `D - getByBarcode returns error for unknown barcode`() = runTest {
        val result = repo.getByBarcode("NON-EXISTENT")
        assertIs<Result.Error>(result)
        assertNotNull(result.exception)
    }

    @Test
    fun `E - update changes name, price, stock, barcode, and attributes`() = runTest {
        repo.insert(makeVariant(id = "var-01", name = "Old Name", price = 100.0, stock = 5.0, barcode = "BC-OLD"))

        val updateResult = repo.update(
            makeVariant(id = "var-01", name = "New Name", price = 200.0, stock = 15.0,
                barcode = "BC-NEW", attributes = mapOf("Color" to "Green"))
        )
        assertIs<Result.Success<Unit>>(updateResult)

        val fetched = (repo.getById("var-01") as Result.Success).data
        assertEquals("New Name", fetched.name)
        assertEquals(200.0, fetched.price)
        assertEquals(15.0, fetched.stock)
        assertEquals("BC-NEW", fetched.barcode)
        assertEquals(mapOf("Color" to "Green"), fetched.attributes)
    }

    @Test
    fun `F - delete removes the variant`() = runTest {
        repo.insert(makeVariant(id = "var-01"))
        repo.insert(makeVariant(id = "var-02", barcode = "BC-002", name = "Red / S"))

        val deleteResult = repo.delete("var-01")
        assertIs<Result.Success<Unit>>(deleteResult)

        repo.getByProductId("prod-01").test {
            val list = awaitItem()
            assertEquals(1, list.size)
            assertEquals("var-02", list.first().id)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `G - deleteByProductId removes all variants for a product`() = runTest {
        repo.insert(makeVariant(id = "var-01", productId = "prod-01", barcode = "BC-001"))
        repo.insert(makeVariant(id = "var-02", productId = "prod-01", barcode = "BC-002", name = "Red / M"))
        repo.insert(makeVariant(id = "var-03", productId = "prod-02", barcode = "BC-003", name = "Green / S"))

        val deleteResult = repo.deleteByProductId("prod-01")
        assertIs<Result.Success<Unit>>(deleteResult)

        repo.getByProductId("prod-01").test {
            val list = awaitItem()
            assertTrue(list.isEmpty())
            cancelAndIgnoreRemainingEvents()
        }

        // prod-02 variants unaffected
        repo.getByProductId("prod-02").test {
            val list = awaitItem()
            assertEquals(1, list.size)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `H - replaceAll atomically replaces existing variants`() = runTest {
        repo.insert(makeVariant(id = "var-old-01", productId = "prod-01", barcode = "BC-001"))
        repo.insert(makeVariant(id = "var-old-02", productId = "prod-01", barcode = "BC-002", name = "Old"))

        val newVariants = listOf(
            makeVariant(id = "var-new-01", productId = "prod-01", barcode = "BC-NEW-1", name = "New Blue"),
            makeVariant(id = "var-new-02", productId = "prod-01", barcode = "BC-NEW-2", name = "New Red"),
            makeVariant(id = "var-new-03", productId = "prod-01", barcode = "BC-NEW-3", name = "New Green"),
        )
        val replaceResult = repo.replaceAll("prod-01", newVariants)
        assertIs<Result.Success<Unit>>(replaceResult)

        repo.getByProductId("prod-01").test {
            val list = awaitItem()
            assertEquals(3, list.size)
            assertTrue(list.none { it.id == "var-old-01" || it.id == "var-old-02" })
            assertTrue(list.any { it.name == "New Blue" })
            assertTrue(list.any { it.name == "New Red" })
            assertTrue(list.any { it.name == "New Green" })
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `I - getById returns error for unknown id`() = runTest {
        val result = repo.getById("non-existent-variant")
        assertIs<Result.Error>(result)
        assertNotNull(result.exception)
    }

    @Test
    fun `J - insert variant with null price and null barcode`() = runTest {
        val variant = makeVariant(id = "var-min", price = null, barcode = null, attributes = emptyMap())
        val insertResult = repo.insert(variant)
        assertIs<Result.Success<Unit>>(insertResult)

        val fetched = (repo.getById("var-min") as Result.Success).data
        assertNull(fetched.price)
        assertNull(fetched.barcode)
        assertTrue(fetched.attributes.isEmpty())
    }
}
