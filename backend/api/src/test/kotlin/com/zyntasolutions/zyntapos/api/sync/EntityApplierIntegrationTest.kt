package com.zyntasolutions.zyntapos.api.sync

import com.zyntasolutions.zyntapos.api.repository.DeadLetterRepository
import com.zyntasolutions.zyntapos.api.models.SyncOperation
import com.zyntasolutions.zyntapos.api.service.Products
import com.zyntasolutions.zyntapos.api.test.TestFixtures
import kotlinx.coroutines.test.runTest
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import org.junit.jupiter.api.Test
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * A1.1 — Integration tests for [EntityApplier] with a real PostgreSQL container.
 *
 * Tests cover at least 8 entity types (PRODUCT, CATEGORY, CUSTOMER, SUPPLIER,
 * STOCK_ADJUSTMENT, REGISTER_SESSION, SETTINGS, EMPLOYEE) with INSERT / UPDATE /
 * DELETE and edge cases (circular CATEGORY parent, idempotent upsert).
 *
 * All tests extend [AbstractSyncIntegrationTest] which inherits the singleton
 * PostgreSQL container + Flyway migrations from [com.zyntasolutions.zyntapos.api.test.AbstractIntegrationTest].
 */
class EntityApplierIntegrationTest : AbstractSyncIntegrationTest() {

    private val applier = EntityApplier(DeadLetterRepository())
    private val storeId = "store-ea-test"

    // Convenience: wrap in a real transaction and apply
    private suspend fun apply(op: SyncOperation) =
        newSuspendedTransaction(db = database) { applier.applyInTransaction(storeId, "device-1", op) }

    private fun op(
        entityType: String,
        entityId: String,
        operation: String = "CREATE",
        payload: String,
        createdAt: Long = System.currentTimeMillis(),
    ) = SyncOperation(
        id = "op-${UUID.randomUUID()}",
        entityType = entityType,
        entityId = entityId,
        operation = operation,
        payload = payload,
        createdAt = createdAt,
    )

    // ── PRODUCT ─────────────────────────────────────────────────────────────

    @Test
    fun `PRODUCT CREATE inserts row into products table`() = runTest {
        TestFixtures.insertStore(id = storeId)

        val entityId = "prod-it-${UUID.randomUUID().toString().take(8)}"
        apply(op("PRODUCT", entityId, payload = """{"name":"Widget","price":9.99,"cost_price":4.99,"stock_qty":10.0,"is_active":true}"""))

        val row = newSuspendedTransaction(db = database) {
            Products.selectAll().where { Products.id eq entityId }.singleOrNull()
        }
        assertNotNull(row)
        assertEquals("Widget", row[Products.name])
        assertTrue(row[Products.isActive])
    }

    @Test
    fun `PRODUCT UPDATE replaces existing row`() = runTest {
        TestFixtures.insertStore(id = storeId)

        val entityId = "prod-upd-${UUID.randomUUID().toString().take(8)}"
        apply(op("PRODUCT", entityId, payload = """{"name":"Original","price":1.00,"cost_price":0.50,"stock_qty":5.0,"is_active":true}"""))
        apply(op("PRODUCT", entityId, operation = "UPDATE", payload = """{"name":"Renamed","price":2.00,"cost_price":1.00,"stock_qty":5.0,"is_active":true}"""))

        val row = newSuspendedTransaction(db = database) {
            Products.selectAll().where { Products.id eq entityId }.singleOrNull()
        }
        assertNotNull(row)
        assertEquals("Renamed", row[Products.name])
    }

    @Test
    fun `PRODUCT DELETE soft-deletes the row`() = runTest {
        TestFixtures.insertStore(id = storeId)

        val entityId = "prod-del-${UUID.randomUUID().toString().take(8)}"
        apply(op("PRODUCT", entityId, payload = """{"name":"ToDelete","price":3.00,"cost_price":1.00,"stock_qty":1.0,"is_active":true}"""))
        apply(op("PRODUCT", entityId, operation = "DELETE", payload = """{}"""))

        val row = newSuspendedTransaction(db = database) {
            Products.selectAll().where { Products.id eq entityId }.singleOrNull()
        }
        assertNotNull(row, "Row should still exist after soft delete")
        assertEquals(false, row[Products.isActive])
    }

    @Test
    fun `PRODUCT CREATE is idempotent - duplicate op does not duplicate row`() = runTest {
        TestFixtures.insertStore(id = storeId)

        val entityId = "prod-idem-${UUID.randomUUID().toString().take(8)}"
        val payload = """{"name":"Idempotent","price":5.00,"cost_price":2.00,"stock_qty":10.0,"is_active":true}"""
        apply(op("PRODUCT", entityId, payload = payload))
        apply(op("PRODUCT", entityId, payload = payload)) // same entityId, upsert should not duplicate

        val count = newSuspendedTransaction(db = database) {
            Products.selectAll().where { Products.id eq entityId }.count()
        }
        assertEquals(1L, count)
    }

    // ── CATEGORY ────────────────────────────────────────────────────────────

    @Test
    fun `CATEGORY CREATE inserts into categories table`() = runTest {
        TestFixtures.insertStore(id = storeId)

        val entityId = "cat-it-${UUID.randomUUID().toString().take(8)}"
        apply(op("CATEGORY", entityId, payload = """{"name":"Electronics","sort_order":1,"is_active":true}"""))

        val row = newSuspendedTransaction(db = database) {
            Categories.selectAll().where { Categories.id eq entityId }.singleOrNull()
        }
        assertNotNull(row)
        assertEquals("Electronics", row[Categories.name])
    }

    @Test
    fun `CATEGORY with valid parent is persisted`() = runTest {
        TestFixtures.insertStore(id = storeId)

        val parentId = "cat-parent-${UUID.randomUUID().toString().take(8)}"
        val childId = "cat-child-${UUID.randomUUID().toString().take(8)}"

        apply(op("CATEGORY", parentId, payload = """{"name":"Parent","sort_order":0,"is_active":true}"""))
        apply(op("CATEGORY", childId, payload = """{"name":"Child","parent_id":"$parentId","sort_order":1,"is_active":true}"""))

        val childRow = newSuspendedTransaction(db = database) {
            Categories.selectAll().where { Categories.id eq childId }.singleOrNull()
        }
        assertNotNull(childRow)
        assertEquals(parentId, childRow[Categories.parentId])
    }

    @Test
    fun `CATEGORY self-referencing parentId is silently rejected`() = runTest {
        TestFixtures.insertStore(id = storeId)

        val entityId = "cat-self-${UUID.randomUUID().toString().take(8)}"
        // parentId == entityId → self-reference → no DB write
        apply(op("CATEGORY", entityId, payload = """{"name":"Self","parent_id":"$entityId","sort_order":0,"is_active":true}"""))

        val row = newSuspendedTransaction(db = database) {
            Categories.selectAll().where { Categories.id eq entityId }.singleOrNull()
        }
        // Row should NOT have been inserted due to self-reference rejection
        assertNull(row)
    }

    // ── CUSTOMER ────────────────────────────────────────────────────────────

    @Test
    fun `CUSTOMER CREATE inserts into customers table`() = runTest {
        TestFixtures.insertStore(id = storeId)

        val entityId = "cust-it-${UUID.randomUUID().toString().take(8)}"
        apply(op("CUSTOMER", entityId, payload = """{"name":"Alice","email":"alice@test.com","phone":"0771234567","loyalty_points":0,"is_active":true}"""))

        val row = newSuspendedTransaction(db = database) {
            Customers.selectAll().where { Customers.id eq entityId }.singleOrNull()
        }
        assertNotNull(row)
        assertEquals("Alice", row[Customers.name])
        assertEquals("alice@test.com", row[Customers.email])
    }

    // ── SUPPLIER ────────────────────────────────────────────────────────────

    @Test
    fun `SUPPLIER CREATE inserts into suppliers table`() = runTest {
        TestFixtures.insertStore(id = storeId)

        val entityId = "sup-it-${UUID.randomUUID().toString().take(8)}"
        apply(op("SUPPLIER", entityId, payload = """{"name":"Acme Corp","contact_name":"Bob","phone":"0779876543","is_active":true}"""))

        val row = newSuspendedTransaction(db = database) {
            Suppliers.selectAll().where { Suppliers.id eq entityId }.singleOrNull()
        }
        assertNotNull(row)
        assertEquals("Acme Corp", row[Suppliers.name])
    }

    // ── STOCK_ADJUSTMENT ────────────────────────────────────────────────────

    @Test
    fun `STOCK_ADJUSTMENT CREATE inserts into stock_adjustments table`() = runTest {
        TestFixtures.insertStore(id = storeId)

        val entityId = "sa-it-${UUID.randomUUID().toString().take(8)}"
        apply(op("STOCK_ADJUSTMENT", entityId, payload = """{"product_id":"prod-x","type":"INCREASE","quantity":10.0,"reason":"Restock","is_active":true}"""))

        val row = newSuspendedTransaction(db = database) {
            StockAdjustments.selectAll().where { StockAdjustments.id eq entityId }.singleOrNull()
        }
        assertNotNull(row)
        assertEquals("INCREASE", row[StockAdjustments.type])
    }

    // ── REGISTER_SESSION ────────────────────────────────────────────────────

    @Test
    fun `REGISTER_SESSION CREATE inserts into register_sessions table`() = runTest {
        TestFixtures.insertStore(id = storeId)

        val entityId = "rs-it-${UUID.randomUUID().toString().take(8)}"
        apply(op("REGISTER_SESSION", entityId, payload = """{"register_id":"reg-1","opened_by":"cashier-1","opening_balance":500.00,"expected_balance":500.00,"status":"OPEN","is_active":true}"""))

        val row = newSuspendedTransaction(db = database) {
            RegisterSessions.selectAll().where { RegisterSessions.id eq entityId }.singleOrNull()
        }
        assertNotNull(row)
        assertEquals("OPEN", row[RegisterSessions.status])
    }

    // ── SETTINGS ────────────────────────────────────────────────────────────

    @Test
    fun `SETTINGS CREATE inserts into settings table`() = runTest {
        TestFixtures.insertStore(id = storeId)

        val entityId = "set-it-${UUID.randomUUID().toString().take(8)}"
        apply(op("SETTINGS", entityId, payload = """{"key":"currency","value":"LKR","is_active":true}"""))

        val row = newSuspendedTransaction(db = database) {
            Settings.selectAll().where { Settings.id eq entityId }.singleOrNull()
        }
        assertNotNull(row)
        assertEquals("currency", row[Settings.key])
        assertEquals("LKR", row[Settings.value])
    }

    // ── EMPLOYEE ────────────────────────────────────────────────────────────

    @Test
    fun `EMPLOYEE CREATE inserts into employees table`() = runTest {
        TestFixtures.insertStore(id = storeId)

        val entityId = "emp-it-${UUID.randomUUID().toString().take(8)}"
        apply(op("EMPLOYEE", entityId, payload = """{"name":"Charlie","role":"CASHIER","hourly_rate":200.00,"is_active":true}"""))

        val row = newSuspendedTransaction(db = database) {
            Employees.selectAll().where { Employees.id eq entityId }.singleOrNull()
        }
        assertNotNull(row)
        assertEquals("Charlie", row[Employees.name])
        assertEquals("CASHIER", row[Employees.role])
    }

    // ── Unknown entity type ──────────────────────────────────────────────────

    @Test
    fun `unknown entity type is a no-op and does not throw`() = runTest {
        apply(op("TOTALLY_UNKNOWN", "e-1", payload = """{"field":"value"}"""))
        // No exception expected — entity_snapshots trigger handles via DB-level logic
    }
}
