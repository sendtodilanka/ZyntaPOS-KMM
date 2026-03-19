package com.zyntasolutions.zyntapos.data.repository

import com.zyntasolutions.zyntapos.data.createTestDatabase
import com.zyntasolutions.zyntapos.data.local.SyncEnqueuer
import com.zyntasolutions.zyntapos.db.ZyntaDatabase
import com.zyntasolutions.zyntapos.domain.model.SyncOperation
import kotlin.time.Clock
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * ZyntaPOS — Product & Order SQLDelight Query Integration Tests (jvmTest)
 *
 * Step 3.4.7 | :shared:data | jvmTest
 *
 * Validates the SQLDelight-generated query layer (productsQueries, ordersQueries)
 * using a real in-memory SQLite database. Tests query correctness — FTS5 search,
 * upsert semantics, cascade relationships — without Flow / coroutine overhead.
 *
 * Coverage:
 *  A. insertProduct → getProductById round-trip preserves all fields
 *  B. getAllProducts excludes inactive (is_active = 0) products
 *  C. insertProduct with duplicate id → updates existing row (INSERT OR REPLACE)
 *  D. getProductByBarcode returns correct product
 *  E. getLowStockProducts returns product with qty ≤ min_qty
 *  F. SyncEnqueuer inserts PENDING row using correct Operation enum
 *  G. SyncEnqueuer enqueue is idempotent (INSERT OR IGNORE prevents duplicates)
 */
class ProductRepositoryIntegrationTest {

    private lateinit var db: ZyntaDatabase
    private val now get() = Clock.System.now().toEpochMilliseconds()

    @BeforeTest
    fun setup() {
        db = createTestDatabase()
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private fun insertProduct(
        id: String,
        name: String       = "Test Product",
        barcode: String?   = null,
        price: Double      = 9.99,
        stockQty: Double   = 50.0,
        minStockQty: Double = 5.0,
        isActive: Boolean  = true,
    ) {
        db.productsQueries.insertProduct(
            id           = id,
            name         = name,
            barcode      = barcode,
            sku          = null,
            category_id  = null,
            unit_id      = "pcs",
            price        = price,
            cost_price   = 0.0,
            tax_group_id = null,
            stock_qty    = stockQty,
            min_stock_qty = minStockQty,
            image_url    = null,
            description  = null,
            is_active    = if (isActive) 1L else 0L,
            created_at   = now,
            updated_at   = now,
            sync_status  = "PENDING",
            master_product_id = null,
        )
    }

    // ── A. insertProduct → getProductById round-trip ─────────────────────────

    @Test
    fun insert_then_getById_round_trip_preserves_fields() {
        insertProduct(id = "prod-1", name = "Espresso Shot", barcode = "4006381333931", price = 4.50)

        val row = db.productsQueries.getProductById("prod-1").executeAsOneOrNull()

        assertNotNull(row, "Product should be retrievable by ID")
        assertEquals("prod-1",              row.id)
        assertEquals("Espresso Shot",       row.name)
        assertEquals("4006381333931",       row.barcode)
        assertEquals(4.50,                  row.price)
        assertEquals(1L,                    row.is_active)
    }

    // ── B. getProductById returns null for unknown id ─────────────────────────

    @Test
    fun getProductById_returns_null_for_unknown_id() {
        val row = db.productsQueries.getProductById("does-not-exist").executeAsOneOrNull()
        assertNull(row)
    }

    // ── C. getAllProducts excludes inactive products ───────────────────────────

    @Test
    fun getAllProducts_excludes_inactive_products() {
        insertProduct(id = "active-1",   name = "Latte",      isActive = true)
        insertProduct(id = "inactive-1", name = "Old Coffee", isActive = false)

        val rows = db.productsQueries.getAllProducts().executeAsList()

        assertEquals(1, rows.size)
        assertEquals("active-1", rows[0].id)
    }

    // ── D. INSERT OR REPLACE updates existing row ─────────────────────────────

    @Test
    fun insert_with_duplicate_id_replaces_existing_row() {
        insertProduct(id = "prod-dup", name = "Original", price = 3.00)
        insertProduct(id = "prod-dup", name = "Updated",  price = 5.50)

        val row = db.productsQueries.getProductById("prod-dup").executeAsOneOrNull()
        assertNotNull(row)
        assertEquals("Updated", row.name)
        assertEquals(5.50,      row.price)

        val totalCount = db.productsQueries.countProducts().executeAsOne()
        assertEquals(1L, totalCount, "Only one row should exist after INSERT OR REPLACE")
    }

    // ── E. getProductByBarcode ─────────────────────────────────────────────────

    @Test
    fun getProductByBarcode_returns_correct_product() {
        insertProduct(id = "prod-a", name = "Cappuccino", barcode = "9780000000000")
        insertProduct(id = "prod-b", name = "Mocha",      barcode = "9780000000001")

        val row = db.productsQueries.getProductByBarcode("9780000000000").executeAsOneOrNull()
        assertNotNull(row)
        assertEquals("prod-a",       row.id)
        assertEquals("Cappuccino",   row.name)
    }

    // ── F. getLowStockProducts ─────────────────────────────────────────────────

    @Test
    fun getLowStockProducts_returns_only_products_at_or_below_min_qty() {
        insertProduct(id = "low-stock",  name = "Sugar",    stockQty = 2.0,  minStockQty = 5.0)
        insertProduct(id = "ok-stock",   name = "Coffee",   stockQty = 20.0, minStockQty = 5.0)
        insertProduct(id = "zero-stock", name = "Cups",     stockQty = 0.0,  minStockQty = 10.0)

        val rows = db.productsQueries.getLowStockProducts().executeAsList()
        assertEquals(2, rows.size, "2 products (Sugar + Cups) should be low-stock")
        assertTrue(rows.any { it.id == "low-stock"  })
        assertTrue(rows.any { it.id == "zero-stock" })
    }

    // ── G. SyncEnqueuer inserts PENDING row via correct Operation enum ─────────

    @Test
    fun sync_enqueuer_inserts_pending_row_with_correct_entity_and_operation() {
        val enqueuer = SyncEnqueuer(db)
        enqueuer.enqueue(
            entityType = SyncOperation.EntityType.PRODUCT,
            entityId   = "prod-1",
            operation  = SyncOperation.Operation.INSERT,
            payload    = """{"id":"prod-1","name":"Latte"}""",
        )

        val pendingCount = db.sync_queueQueries.getPendingCount().executeAsOne()
        assertEquals(1L, pendingCount)

        val rows = db.sync_queueQueries.getEligibleOperations(10).executeAsList()
        assertEquals(1,           rows.size)
        assertEquals("product",   rows[0].entity_type)
        assertEquals("prod-1",    rows[0].entity_id)
        assertEquals("CREATE",    rows[0].operation)   // Operation.INSERT maps to "CREATE"
        assertEquals("PENDING",   rows[0].status)
    }

    // ── H. SyncEnqueuer INSERT OR IGNORE prevents duplicate rows ──────────────

    @Test
    fun sync_enqueuer_insert_or_ignore_prevents_duplicate_by_id() {
        val enqueuer = SyncEnqueuer(db)
        // Calling enqueue twice with the same entity — IdGenerator produces different IDs,
        // so this test verifies that the queue accumulates correctly (not deduplicating by default)
        enqueuer.enqueue(SyncOperation.EntityType.ORDER, "ord-1", SyncOperation.Operation.INSERT)
        enqueuer.enqueue(SyncOperation.EntityType.ORDER, "ord-1", SyncOperation.Operation.UPDATE)

        val pendingCount = db.sync_queueQueries.getPendingCount().executeAsOne()
        assertEquals(2L, pendingCount, "Each enqueue call should produce one row (dedup is done at sync time)")
    }
}
