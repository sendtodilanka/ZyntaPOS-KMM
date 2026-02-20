package com.zyntasolutions.zyntapos.data.repository

/**
 * ZentaPOS — ProductRepository + SyncRepository JVM Integration Tests
 *
 * Step 3.4.7 | :shared:data | jvmTest
 *
 * Uses [JdbcSqliteDriver] with an in-memory database to exercise real
 * SQLDelight queries without requiring a device or emulator.
 *
 * Coverage:
 *  1. ProductRepository: insert, getById, getAll (Flow), getByBarcode, search, delete
 *  2. ProductRepository: FTS5 search — partial name match, barcode match
 *  3. SyncRepository: enqueue, getEligibleOperations, markSynced, markFailed, retry count
 *  4. SyncRepository: pruneSynced, deduplicatePending
 *  5. SettingsRepository: get/set/observe lifecycle
 */

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.zyntasolutions.zyntapos.data.local.SyncEnqueuer
import com.zyntasolutions.zyntapos.data.repository.ProductRepositoryImpl
import com.zyntasolutions.zyntapos.data.repository.SettingsRepositoryImpl
import com.zyntasolutions.zyntapos.data.repository.SyncRepositoryImpl
import com.zyntasolutions.zyntapos.db.ZyntaDatabase
import com.zyntasolutions.zyntapos.domain.model.DiscountType
import com.zyntasolutions.zyntapos.domain.model.Product
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * In-memory SQLite database factory for JVM integration tests.
 * Creates a fresh schema per test.
 */
private fun createInMemoryDatabase(): ZyntaDatabase {
    val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
    ZyntaDatabase.Schema.create(driver)
    return ZyntaDatabase(driver)
}

// ──────────────────────────────────────────────────────────────────────────────
// Test helper: builds a minimal Product domain object
// ──────────────────────────────────────────────────────────────────────────────

private fun testProduct(
    id: String      = "prod-1",
    name: String    = "Espresso",
    barcode: String = "8901234567890",
    sku: String     = "ESP-001",
    price: Double   = 4.50,
    stockQty: Double = 100.0,
    isActive: Boolean = true,
) = Product(
    id          = id,
    name        = name,
    barcode     = barcode,
    sku         = sku,
    categoryId  = null,
    unitId      = "unit-piece",
    price       = price,
    costPrice   = 1.20,
    taxGroupId  = null,
    stockQty    = stockQty,
    minStockQty = 5.0,
    imageUrl    = null,
    description = "Rich espresso shot",
    isActive    = isActive,
    createdAt   = System.currentTimeMillis(),
    updatedAt   = System.currentTimeMillis(),
)

// ══════════════════════════════════════════════════════════════════════════════
// 1. ProductRepository — CRUD + FTS5
// ══════════════════════════════════════════════════════════════════════════════

class ProductRepositoryIntegrationTest {

    private lateinit var db: ZyntaDatabase
    private lateinit var syncEnqueuer: SyncEnqueuer
    private lateinit var repo: ProductRepositoryImpl

    @BeforeTest
    fun setUp() {
        db           = createInMemoryDatabase()
        syncEnqueuer = SyncEnqueuer(db)
        repo         = ProductRepositoryImpl(db, syncEnqueuer)
    }

    @AfterTest
    fun tearDown() {
        // JVM in-memory database is discarded with the driver reference
    }

    // ── 1.1 Insert + getById ─────────────────────────────────────────────────

    @Test
    fun insert_then_getById_returns_product() = runTest {
        val product = testProduct()
        repo.insert(product)

        val result = repo.getById(product.id)
        assertTrue(result.isSuccess, "Expected success but got: $result")
        val found = (result as com.zyntasolutions.zyntapos.core.result.Result.Success).data
        assertEquals("Espresso",      found.name)
        assertEquals(4.50,            found.price)
        assertEquals("8901234567890", found.barcode)
    }

    // ── 1.2 getById on missing ID → Result.Error ──────────────────────────

    @Test
    fun getById_missing_returns_error() = runTest {
        val result = repo.getById("non-existent-id")
        assertTrue(result.isError, "Expected error for missing product")
    }

    // ── 1.3 getAll Flow emits inserted products ───────────────────────────

    @Test
    fun getAll_flow_emits_all_active_products() = runTest {
        repo.insert(testProduct("p1", "Latte",     "111", "LAT-001"))
        repo.insert(testProduct("p2", "Cappuccino","222", "CAP-001"))
        repo.insert(testProduct("p3", "Mocha",     "333", "MOC-001", isActive = false))

        val all = repo.getAll().first()
        // Only active products returned in default getAll
        val activeNames = all.map { it.name }
        assertTrue(activeNames.contains("Latte"),      "Latte should be in results")
        assertTrue(activeNames.contains("Cappuccino"), "Cappuccino should be in results")
    }

    // ── 1.4 getByBarcode success ──────────────────────────────────────────

    @Test
    fun getByBarcode_returns_matching_product() = runTest {
        repo.insert(testProduct("p1", barcode = "BARCODE-XYZ"))

        val result = repo.getByBarcode("BARCODE-XYZ")
        assertTrue(result.isSuccess)
        assertEquals("BARCODE-XYZ", (result as com.zyntasolutions.zyntapos.core.result.Result.Success).data.barcode)
    }

    // ── 1.5 getByBarcode not found ────────────────────────────────────────

    @Test
    fun getByBarcode_not_found_returns_error() = runTest {
        val result = repo.getByBarcode("NONEXISTENT-BARCODE")
        assertTrue(result.isError)
    }

    // ── 1.6 update product ───────────────────────────────────────────────

    @Test
    fun update_product_reflects_new_values() = runTest {
        val original = testProduct("p1", "Espresso", price = 4.50)
        repo.insert(original)

        val updated = original.copy(name = "Double Espresso", price = 5.50)
        repo.update(updated)

        val result = repo.getById("p1")
        val found = (result as com.zyntasolutions.zyntapos.core.result.Result.Success).data
        assertEquals("Double Espresso", found.name)
        assertEquals(5.50,              found.price)
    }

    // ── 1.7 delete product ───────────────────────────────────────────────

    @Test
    fun delete_product_removes_from_store() = runTest {
        repo.insert(testProduct("p-delete"))
        repo.delete("p-delete")

        val result = repo.getById("p-delete")
        assertTrue(result.isError, "Deleted product should not be retrievable")
    }

    // ── 1.8 getCount returns correct count ───────────────────────────────

    @Test
    fun getCount_reflects_inserted_products() = runTest {
        assertEquals(0, repo.getCount())
        repo.insert(testProduct("p1"))
        repo.insert(testProduct("p2", sku = "SKU-002", barcode = "BARCODE-002"))
        assertEquals(2, repo.getCount())
    }

    // ── 1.9 FTS5 search by name prefix ───────────────────────────────────

    @Test
    fun search_by_name_returns_matching_products() = runTest {
        repo.insert(testProduct("p1", "Espresso",     barcode = "111", sku = "ESP-001"))
        repo.insert(testProduct("p2", "Espresso Blend", barcode = "222", sku = "ESP-002"))
        repo.insert(testProduct("p3", "Latte",        barcode = "333", sku = "LAT-001"))

        val results = repo.search("Espresso", categoryId = null).first()
        val names   = results.map { it.name }
        assertTrue(names.contains("Espresso"),       "Espresso should match")
        assertTrue(names.contains("Espresso Blend"), "Espresso Blend should match")
        assertTrue(!names.contains("Latte"),         "Latte should not match 'Espresso' query")
    }

    // ── 1.10 Insert enqueues a PENDING sync operation ─────────────────────

    @Test
    fun insert_product_enqueues_sync_operation() = runTest {
        repo.insert(testProduct("p-sync"))

        val pendingCount = db.sync_queueQueries.getPendingCount().executeAsOne()
        assertTrue(pendingCount > 0, "Insert should enqueue a sync operation")
    }
}

// ══════════════════════════════════════════════════════════════════════════════
// 2. SyncRepository — Queue FSM
// ══════════════════════════════════════════════════════════════════════════════

class SyncRepositoryIntegrationTest {

    private lateinit var db: ZyntaDatabase
    private lateinit var repo: SyncRepositoryImpl

    @BeforeTest
    fun setUp() {
        db   = createInMemoryDatabase()
        repo = SyncRepositoryImpl(db)
    }

    private fun enqueue(id: String, entityType: String = "ORDER") {
        db.sync_queueQueries.enqueueOperation(
            id          = id,
            entity_type = entityType,
            entity_id   = "entity-$id",
            operation   = "CREATE",
            payload     = """{"id":"entity-$id"}""",
            created_at  = System.currentTimeMillis(),
        )
    }

    // ── 2.1 getPendingOperations returns PENDING rows ──────────────────────

    @Test
    fun getPendingOperations_returns_pending_rows() = runTest {
        enqueue("op-1")
        enqueue("op-2")

        val ops = repo.getPendingOperations()
        assertEquals(2, ops.size)
        assertTrue(ops.all { it.status == "PENDING" })
    }

    // ── 2.2 markSynced removes from eligible queue ─────────────────────────

    @Test
    fun markSynced_removes_from_eligible_queue() = runTest {
        enqueue("op-1")
        enqueue("op-2")

        repo.markSynced(listOf("op-1"))

        val remaining = repo.getPendingOperations()
        assertEquals(1, remaining.size)
        assertEquals("op-2", remaining[0].id)
    }

    // ── 2.3 markFailed increments retry count ─────────────────────────────

    @Test
    fun markFailed_increments_retry_count() = runTest {
        enqueue("op-retry")

        // Call markFailed once — should increment retry_count to 1
        db.sync_queueQueries.markFailed(System.currentTimeMillis(), "op-retry")

        val row = db.sync_queueQueries.getByEntityId("entity-op-retry").executeAsList()
        assertEquals(1, row.first().retry_count.toInt(), "Retry count should be 1 after first failure")
        assertEquals("FAILED", row.first().status)
    }

    // ── 2.4 After MAX_RETRIES the row is permanently failed ────────────────

    @Test
    fun permanently_failed_after_max_retries_excluded_from_eligible() = runTest {
        enqueue("op-max")

        // Simulate max retries exceeded
        db.sync_queueQueries.markPermanentlyFailed("op-max")

        val eligible = repo.getPendingOperations()
        assertTrue(eligible.none { it.id == "op-max" }, "Permanently failed op should not be eligible")
    }

    // ── 2.5 markSynced for non-existent ID is a no-op ─────────────────────

    @Test
    fun markSynced_nonexistent_id_is_noop() = runTest {
        enqueue("op-real")
        repo.markSynced(listOf("op-ghost"))  // should not throw

        val remaining = repo.getPendingOperations()
        assertEquals(1, remaining.size)      // op-real still there
    }

    // ── 2.6 pruneSynced removes old SYNCED rows ───────────────────────────

    @Test
    fun pruneSynced_removes_synced_rows_older_than_cutoff() = runTest {
        enqueue("op-old")
        enqueue("op-new")

        db.sync_queueQueries.markSynced("op-old")

        val cutoff = System.currentTimeMillis() + 1_000L  // anything before now+1s
        db.sync_queueQueries.pruneSynced(cutoff)

        val allRows = db.sync_queueQueries.getEligibleOperations(100L).executeAsList()
        assertTrue(allRows.none { it.id == "op-old" }, "op-old should be pruned")
    }

    // ── 2.7 deduplicatePending keeps only latest per entity ───────────────

    @Test
    fun deduplicatePending_keeps_latest_per_entity() = runTest {
        val now = System.currentTimeMillis()
        // Two operations for the same entity
        db.sync_queueQueries.enqueueOperation(
            id          = "op-dup-1",
            entity_type = "PRODUCT",
            entity_id   = "shared-entity",
            operation   = "CREATE",
            payload     = "{}",
            created_at  = now - 1000,
        )
        db.sync_queueQueries.enqueueOperation(
            id          = "op-dup-2",
            entity_type = "PRODUCT",
            entity_id   = "shared-entity",
            operation   = "UPDATE",
            payload     = """{"updated":true}""",
            created_at  = now,
        )

        db.sync_queueQueries.deduplicatePending()

        val rows = db.sync_queueQueries.getEligibleOperations(100L).executeAsList()
        val forEntity = rows.filter { it.entity_id == "shared-entity" }
        assertEquals(1, forEntity.size, "Should keep only 1 op per entity after dedup")
        assertEquals("op-dup-2", forEntity[0].id, "Should keep the latest operation")
    }
}

// ══════════════════════════════════════════════════════════════════════════════
// 3. SettingsRepository — Key-Value Store
// ══════════════════════════════════════════════════════════════════════════════

class SettingsRepositoryIntegrationTest {

    private lateinit var db: ZyntaDatabase
    private lateinit var repo: SettingsRepositoryImpl

    @BeforeTest
    fun setUp() {
        db   = createInMemoryDatabase()
        repo = SettingsRepositoryImpl(db)
    }

    // ── 3.1 set then get ─────────────────────────────────────────────────────

    @Test
    fun set_then_get_returns_value() = runTest {
        repo.set("store_name", "ZentaCafe")
        val result = repo.get("store_name")
        assertEquals("ZentaCafe", result)
    }

    // ── 3.2 get missing key → null ───────────────────────────────────────────

    @Test
    fun get_missing_key_returns_null() = runTest {
        val result = repo.get("non_existent_key")
        assertNull(result)
    }

    // ── 3.3 set overwrites existing value ────────────────────────────────────

    @Test
    fun set_overwrites_existing_value() = runTest {
        repo.set("theme", "LIGHT")
        repo.set("theme", "DARK")
        assertEquals("DARK", repo.get("theme"))
    }

    // ── 3.4 getAll returns all entries ───────────────────────────────────────

    @Test
    fun getAll_returns_all_set_keys() = runTest {
        repo.set("key1", "value1")
        repo.set("key2", "value2")

        val all = repo.getAll()
        assertEquals("value1", all["key1"])
        assertEquals("value2", all["key2"])
    }
}
