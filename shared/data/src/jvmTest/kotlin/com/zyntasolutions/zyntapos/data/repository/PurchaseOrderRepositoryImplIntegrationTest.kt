package com.zyntasolutions.zyntapos.data.repository

import app.cash.turbine.test
import com.zyntasolutions.zyntapos.core.result.Result
import com.zyntasolutions.zyntapos.data.createTestDatabase
import com.zyntasolutions.zyntapos.data.local.SyncEnqueuer
import com.zyntasolutions.zyntapos.db.ZyntaDatabase
import com.zyntasolutions.zyntapos.domain.model.PurchaseOrder
import com.zyntasolutions.zyntapos.domain.model.PurchaseOrderItem
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlinx.coroutines.test.runTest

/**
 * ZyntaPOS — PurchaseOrderRepositoryImpl Integration Tests (jvmTest)
 *
 * Validates [PurchaseOrderRepositoryImpl] against a real in-memory SQLite database.
 * No mocks — exercises the full SQLDelight query layer including FK constraints.
 *
 * Coverage:
 *  A. create → getById round-trip preserves PO header and items
 *  B. getAll emits all purchase orders (Turbine)
 *  C. getBySupplierId filters by supplier
 *  D. getByStatus filters by status
 *  E. getByDateRange filters by order_date
 *  F. receiveItems PARTIAL — status transitions to PARTIAL when not all received
 *  G. receiveItems RECEIVED — status transitions to RECEIVED when all items received
 *  H. cancel transitions PENDING → CANCELLED
 *  I. cancel of RECEIVED PO returns ValidationException error
 *  J. getById for unknown ID returns DatabaseException error
 */
class PurchaseOrderRepositoryImplIntegrationTest {

    private lateinit var db: ZyntaDatabase
    private lateinit var repo: PurchaseOrderRepositoryImpl

    @BeforeTest
    fun setup() {
        db = createTestDatabase()
        repo = PurchaseOrderRepositoryImpl(db, SyncEnqueuer(db))

        // Seed a supplier (FK required by purchase_orders)
        val now = Clock.System.now().toEpochMilliseconds()
        db.suppliersQueries.insertSupplier(
            id = "sup-01",
            name = "Test Supplier",
            contact_person = null,
            phone = null,
            email = null,
            address = null,
            notes = null,
            is_active = 1L,
            created_at = now,
            updated_at = now,
            sync_status = "PENDING",
        )

        // Seed a product (FK required by purchase_order_items)
        db.productsQueries.insertProduct(
            id = "prod-01",
            name = "Widget",
            barcode = null,
            sku = "SKU-001",
            category_id = null,
            unit_id = "pcs",
            price = 15.0,
            cost_price = 8.0,
            tax_group_id = null,
            stock_qty = 0.0,
            min_stock_qty = 0.0,
            image_url = null,
            description = null,
            is_active = 1L,
            created_at = now,
            updated_at = now,
            sync_status = "PENDING",
            master_product_id = null,
        )
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private val now get() = Clock.System.now().toEpochMilliseconds()

    private fun makeItem(
        id: String = "item-01",
        productId: String = "prod-01",
        quantityOrdered: Double = 10.0,
        unitCost: Double = 8.0,
    ) = PurchaseOrderItem(
        id = id,
        purchaseOrderId = "",
        productId = productId,
        quantityOrdered = quantityOrdered,
        unitCost = unitCost,
        lineTotal = quantityOrdered * unitCost,
    )

    private fun makePO(
        id: String = "po-01",
        supplierId: String = "sup-01",
        orderNumber: String = "PO-001",
        orderDate: Long = 1_000_000L,
        expectedDate: Long? = null,
        totalAmount: Double = 80.0,
        createdBy: String = "user-01",
        items: List<PurchaseOrderItem> = listOf(makeItem()),
    ) = PurchaseOrder(
        id = id,
        supplierId = supplierId,
        orderNumber = orderNumber,
        status = PurchaseOrder.Status.PENDING,
        orderDate = orderDate,
        expectedDate = expectedDate,
        totalAmount = totalAmount,
        currency = "LKR",
        createdBy = createdBy,
        items = items,
    )

    // ── Tests ─────────────────────────────────────────────────────────────────

    @Test
    fun `A - create then getById returns PO with items`() = runTest {
        val po = makePO(id = "po-01", orderNumber = "PO-001")
        val createResult = repo.create(po)
        assertIs<Result.Success<Unit>>(createResult)

        val fetchResult = repo.getById("po-01")
        assertIs<Result.Success<PurchaseOrder>>(fetchResult)
        val fetched = fetchResult.data
        assertEquals("po-01", fetched.id)
        assertEquals("sup-01", fetched.supplierId)
        assertEquals("PO-001", fetched.orderNumber)
        assertEquals(PurchaseOrder.Status.PENDING, fetched.status)
        assertEquals(80.0, fetched.totalAmount)
        assertEquals("LKR", fetched.currency)
        assertEquals(1, fetched.items.size)
        assertEquals("prod-01", fetched.items.first().productId)
        assertEquals(10.0, fetched.items.first().quantityOrdered)
        assertEquals(8.0, fetched.items.first().unitCost)
    }

    @Test
    fun `B - getAll emits all purchase orders`() = runTest {
        repo.create(makePO(id = "po-01", orderNumber = "PO-001"))
        repo.create(makePO(id = "po-02", orderNumber = "PO-002", items = listOf(makeItem("item-02"))))

        repo.getAll().test {
            val list = awaitItem()
            assertEquals(2, list.size)
            assertTrue(list.any { it.orderNumber == "PO-001" })
            assertTrue(list.any { it.orderNumber == "PO-002" })
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `C - getBySupplierId filters by supplier`() = runTest {
        // Insert second supplier
        db.suppliersQueries.insertSupplier(
            id = "sup-02", name = "Other Supplier",
            contact_person = null, phone = null, email = null,
            address = null, notes = null, is_active = 1L,
            created_at = now, updated_at = now, sync_status = "PENDING",
        )
        repo.create(makePO(id = "po-01", supplierId = "sup-01", orderNumber = "PO-001"))
        repo.create(makePO(id = "po-02", supplierId = "sup-02", orderNumber = "PO-002", items = listOf(makeItem("item-02"))))

        val result = repo.getBySupplierId("sup-01")
        assertIs<Result.Success<List<PurchaseOrder>>>(result)
        val list = result.data
        assertEquals(1, list.size)
        assertEquals("po-01", list.first().id)
    }

    @Test
    fun `D - getByStatus filters by status`() = runTest {
        repo.create(makePO(id = "po-01", orderNumber = "PO-001"))
        repo.create(makePO(id = "po-02", orderNumber = "PO-002", items = listOf(makeItem("item-02"))))
        repo.cancel("po-02")

        val pending = repo.getByStatus(PurchaseOrder.Status.PENDING)
        assertIs<Result.Success<List<PurchaseOrder>>>(pending)
        assertEquals(1, pending.data.size)
        assertEquals("po-01", pending.data.first().id)

        val cancelled = repo.getByStatus(PurchaseOrder.Status.CANCELLED)
        assertIs<Result.Success<List<PurchaseOrder>>>(cancelled)
        assertEquals(1, cancelled.data.size)
        assertEquals("po-02", cancelled.data.first().id)
    }

    @Test
    fun `E - getByDateRange filters by order_date`() = runTest {
        repo.create(makePO(id = "po-01", orderNumber = "PO-001", orderDate = 1_000L))
        repo.create(makePO(id = "po-02", orderNumber = "PO-002", orderDate = 2_000L, items = listOf(makeItem("item-02"))))
        repo.create(makePO(id = "po-03", orderNumber = "PO-003", orderDate = 3_000L, items = listOf(makeItem("item-03"))))

        val result = repo.getByDateRange(startDate = 1_500L, endDate = 2_500L)
        assertIs<Result.Success<List<PurchaseOrder>>>(result)
        val list = result.data
        assertEquals(1, list.size)
        assertEquals("po-02", list.first().id)
    }

    @Test
    fun `F - receiveItems partial transitions status to PARTIAL`() = runTest {
        val items = listOf(
            makeItem("item-01", quantityOrdered = 10.0),
            makeItem("item-02", quantityOrdered = 20.0),
        )
        repo.create(makePO(id = "po-01", items = items))

        // Receive only item-01 fully
        val receiveResult = repo.receiveItems(
            purchaseOrderId = "po-01",
            receivedItems = mapOf("item-01" to 10.0),
            receivedBy = "user-01",
        )
        assertIs<Result.Success<Unit>>(receiveResult)

        val po = (repo.getById("po-01") as Result.Success).data
        assertEquals(PurchaseOrder.Status.PARTIAL, po.status)
    }

    @Test
    fun `G - receiveItems all transitions status to RECEIVED`() = runTest {
        val items = listOf(makeItem("item-01", quantityOrdered = 10.0))
        repo.create(makePO(id = "po-01", items = items))

        val receiveResult = repo.receiveItems(
            purchaseOrderId = "po-01",
            receivedItems = mapOf("item-01" to 10.0),
            receivedBy = "user-01",
        )
        assertIs<Result.Success<Unit>>(receiveResult)

        val po = (repo.getById("po-01") as Result.Success).data
        assertEquals(PurchaseOrder.Status.RECEIVED, po.status)
        assertNotNull(po.receivedDate)
    }

    @Test
    fun `H - cancel transitions PENDING to CANCELLED`() = runTest {
        repo.create(makePO(id = "po-01"))

        val cancelResult = repo.cancel("po-01")
        assertIs<Result.Success<Unit>>(cancelResult)

        val po = (repo.getById("po-01") as Result.Success).data
        assertEquals(PurchaseOrder.Status.CANCELLED, po.status)
    }

    @Test
    fun `I - cancel RECEIVED PO returns error`() = runTest {
        val items = listOf(makeItem("item-01", quantityOrdered = 5.0))
        repo.create(makePO(id = "po-01", items = items))
        repo.receiveItems("po-01", mapOf("item-01" to 5.0), "user-01")

        val cancelResult = repo.cancel("po-01")
        assertIs<Result.Error>(cancelResult)
    }

    @Test
    fun `J - getById unknown ID returns error`() = runTest {
        val result = repo.getById("non-existent")
        assertIs<Result.Error>(result)
        assertNotNull((result as Result.Error).exception)
    }
}
