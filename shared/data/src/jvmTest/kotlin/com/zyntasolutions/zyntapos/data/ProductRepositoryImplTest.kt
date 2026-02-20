package com.zyntasolutions.zyntapos.data

/**
 * ZentaPOS — ProductRepositoryImpl Integration Tests
 *
 * Step 3.4.7 | :shared:data | jvmTest
 *
 * Uses SQLDelight [JdbcSqliteDriver.IN_MEMORY] to exercise all CRUD paths in
 * [ProductRepositoryImpl] against a real (in-memory) SQLite schema.
 *
 * Coverage:
 *  A. Insert → getById → verifies domain mapping
 *  B. Insert → getAll → reactive emission contains inserted product
 *  C. Insert → update → getById → verifies mutation
 *  D. Insert → delete (soft) → getAll → product hidden (isActive=false)
 *  E. getByBarcode → hit / miss
 *  F. getCount increments on insert
 *  G. Insert enqueues a PENDING sync operation
 *  H. Update enqueues a PENDING sync operation (second row)
 *  I. Delete enqueues a PENDING sync operation (third row)
 *  J. getById for unknown id returns Result.Error(DatabaseException)
 *  K. getByBarcode for unknown barcode returns Result.Error(DatabaseException)
 */

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.zyntasolutions.zyntapos.core.result.DatabaseException
import com.zyntasolutions.zyntapos.core.result.Result
import com.zyntasolutions.zyntapos.data.local.SyncEnqueuer
import com.zyntasolutions.zyntapos.data.repository.ProductRepositoryImpl
import com.zyntasolutions.zyntapos.db.ZyntaDatabase
import com.zyntasolutions.zyntapos.domain.model.Product
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Clock
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class ProductRepositoryImplTest {

    private lateinit var db: ZyntaDatabase
    private lateinit var repo: ProductRepositoryImpl

    @BeforeTest
    fun setUp() {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        ZyntaDatabase.Schema.create(driver)
        db = ZyntaDatabase(driver)
        repo = ProductRepositoryImpl(db = db, syncEnqueuer = SyncEnqueuer(db))
    }

    // ─── Helpers ───────────────────────────────────────────────────────────────

    private fun sampleProduct(
        id: String      = "p-1",
        name: String    = "Espresso",
        barcode: String = "8901234567890",
        sku: String     = "ESP-001",
        price: Double   = 4.50,
        stockQty: Double = 100.0,
    ): Product {
        val now = Clock.System.now()
        return Product(
            id         = id,
            name       = name,
            barcode    = barcode,
            sku        = sku,
            categoryId = "cat-1",
            unitId     = "pcs",
            price      = price,
            costPrice  = 2.0,
            stockQty   = stockQty,
            isActive   = true,
            createdAt  = now,
            updatedAt  = now,
        )
    }

    // ─── A. Insert → getById ───────────────────────────────────────────────────

    @Test
    fun insert_then_getById_returns_correct_product() = runTest {
        val product = sampleProduct()
        val insertResult = repo.insert(product)
        assertIs<Result.Success<Unit>>(insertResult)

        val fetchResult = repo.getById("p-1")
        assertIs<Result.Success<Product>>(fetchResult)
        assertEquals("p-1",          fetchResult.data.id)
        assertEquals("Espresso",     fetchResult.data.name)
        assertEquals("8901234567890",fetchResult.data.barcode)
        assertEquals(4.50,           fetchResult.data.price)
        assertTrue(fetchResult.data.isActive)
    }

    // ─── B. Insert → getAll (Flow) ─────────────────────────────────────────────

    @Test
    fun insert_then_getAll_flow_emits_inserted_product() = runTest {
        repo.insert(sampleProduct(id = "p-1"))
        repo.insert(sampleProduct(id = "p-2", name = "Latte", barcode = "8901234567891", sku = "LAT-001"))

        val products = repo.getAll().first()
        assertEquals(2, products.size)
        assertTrue(products.any { it.id == "p-1" })
        assertTrue(products.any { it.id == "p-2" })
    }

    // ─── C. Insert → update → getById ─────────────────────────────────────────

    @Test
    fun update_changes_product_fields() = runTest {
        val original = sampleProduct()
        repo.insert(original)

        val updated = original.copy(name = "Double Espresso", price = 6.00)
        val updateResult = repo.update(updated)
        assertIs<Result.Success<Unit>>(updateResult)

        val fetched = repo.getById("p-1") as Result.Success
        assertEquals("Double Espresso", fetched.data.name)
        assertEquals(6.00,              fetched.data.price)
    }

    // ─── D. Soft-delete: product disappears from getAll ────────────────────────

    @Test
    fun delete_soft_removes_product_from_getAll() = runTest {
        repo.insert(sampleProduct(id = "p-1"))
        repo.insert(sampleProduct(id = "p-2", name = "Latte", barcode = "8901234567891", sku = "LAT-001"))

        val deleteResult = repo.delete("p-1")
        assertIs<Result.Success<Unit>>(deleteResult)

        val products = repo.getAll().first()
        assertEquals(1, products.size, "Soft-deleted product must not appear in getAll")
        assertEquals("p-2", products[0].id)
    }

    // ─── E. getByBarcode hit / miss ────────────────────────────────────────────

    @Test
    fun getByBarcode_returns_correct_product() = runTest {
        repo.insert(sampleProduct())
        val result = repo.getByBarcode("8901234567890")
        assertIs<Result.Success<Product>>(result)
        assertEquals("p-1", result.data.id)
    }

    @Test
    fun getByBarcode_unknown_barcode_returns_error() = runTest {
        val result = repo.getByBarcode("0000000000000")
        assertIs<Result.Error<*>>(result)
        assertIs<DatabaseException>((result as Result.Error<*>).error)
    }

    // ─── F. getCount increments ────────────────────────────────────────────────

    @Test
    fun getCount_increases_after_each_insert() = runTest {
        assertEquals(0, repo.getCount())
        repo.insert(sampleProduct(id = "p-1"))
        assertEquals(1, repo.getCount())
        repo.insert(sampleProduct(id = "p-2", barcode = "0000000000001", sku = "SKU-2"))
        assertEquals(2, repo.getCount())
    }

    // ─── G. Insert enqueues PENDING sync op ────────────────────────────────────

    @Test
    fun insert_enqueues_pending_sync_operation() = runTest {
        repo.insert(sampleProduct())

        val pending = db.sync_queueQueries.getEligibleOperations(10L).executeAsList()
        assertEquals(1, pending.size)
        assertEquals("PRODUCT", pending[0].entity_type)
        assertEquals("p-1",     pending[0].entity_id)
        assertEquals("CREATE",  pending[0].operation)
        assertEquals("PENDING", pending[0].status)
    }

    // ─── H. Update enqueues PENDING sync op ───────────────────────────────────

    @Test
    fun update_enqueues_pending_sync_operation() = runTest {
        val product = sampleProduct()
        repo.insert(product)

        // Drain first queue entry so update produces a clean entry
        db.sync_queueQueries.markSynced(
            db.sync_queueQueries.getEligibleOperations(1L).executeAsOne().id
        )

        repo.update(product.copy(name = "Updated"))

        val pending = db.sync_queueQueries.getEligibleOperations(10L).executeAsList()
        assertTrue(pending.any { it.operation == "UPDATE" && it.entity_id == "p-1" })
    }

    // ─── I. Delete enqueues PENDING sync op ───────────────────────────────────

    @Test
    fun delete_enqueues_pending_sync_operation() = runTest {
        repo.insert(sampleProduct())

        // Drain first queue entry
        db.sync_queueQueries.markSynced(
            db.sync_queueQueries.getEligibleOperations(1L).executeAsOne().id
        )

        repo.delete("p-1")

        val pending = db.sync_queueQueries.getEligibleOperations(10L).executeAsList()
        assertTrue(pending.any { it.operation == "DELETE" && it.entity_id == "p-1" })
    }

    // ─── J. getById unknown id → DatabaseException ────────────────────────────

    @Test
    fun getById_unknown_id_returns_error() = runTest {
        val result = repo.getById("nonexistent-id")
        assertIs<Result.Error<*>>(result)
        assertIs<DatabaseException>((result as Result.Error<*>).error)
    }

    // ─── K. delete nonexistent → DatabaseException ────────────────────────────

    @Test
    fun delete_nonexistent_product_returns_error() = runTest {
        val result = repo.delete("ghost-id")
        assertIs<Result.Error<*>>(result)
        assertIs<DatabaseException>((result as Result.Error<*>).error)
    }
}
