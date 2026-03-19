package com.zyntasolutions.zyntapos.data.repository

import app.cash.turbine.test
import com.zyntasolutions.zyntapos.core.result.Result
import com.zyntasolutions.zyntapos.data.createTestDatabase
import com.zyntasolutions.zyntapos.data.local.SyncEnqueuer
import com.zyntasolutions.zyntapos.db.ZyntaDatabase
import com.zyntasolutions.zyntapos.domain.model.CartItem
import com.zyntasolutions.zyntapos.domain.model.DiscountType
import com.zyntasolutions.zyntapos.domain.model.Order
import com.zyntasolutions.zyntapos.domain.model.OrderItem
import com.zyntasolutions.zyntapos.domain.model.OrderStatus
import com.zyntasolutions.zyntapos.domain.model.OrderType
import com.zyntasolutions.zyntapos.domain.model.PaymentMethod
import com.zyntasolutions.zyntapos.domain.model.SyncStatus
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant

/**
 * ZyntaPOS — OrderRepositoryImpl Integration Tests (jvmTest)
 *
 * Validates [OrderRepositoryImpl] against a real in-memory SQLite database.
 * No mocks are used; every test exercises the full SQLDelight query layer.
 *
 * Coverage:
 *  A. create + getById round-trip preserves order header fields
 *  B. getAll returns empty list for a fresh database
 *  C. getById returns Result.Error for a non-existent order ID
 *  D. holdOrder inserts order with status HELD; retrieveHeld returns it with correct status
 *  E. create with items decrements product stock atomically
 *  F. void reverses stock and transitions order status to VOIDED
 *  G. getAll filters by status via the filters map
 */
class OrderRepositoryImplIntegrationTest {

    private lateinit var db: ZyntaDatabase
    private lateinit var repo: OrderRepositoryImpl

    @BeforeTest
    fun setup() {
        db = createTestDatabase()
        val syncEnqueuer = SyncEnqueuer(db)
        repo = OrderRepositoryImpl(db, syncEnqueuer)
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private val now get() = Clock.System.now().toEpochMilliseconds()

    /** Insert a minimal product row directly via productsQueries (needed for FK / stock tests). */
    private fun insertProduct(
        id: String,
        stockQty: Double = 50.0,
        minStockQty: Double = 5.0,
    ) {
        db.productsQueries.insertProduct(
            id            = id,
            name          = "Test Product $id",
            barcode       = null,
            sku           = null,
            category_id   = null,
            unit_id       = "pcs",
            price         = 10.0,
            cost_price    = 5.0,
            tax_group_id  = null,
            stock_qty     = stockQty,
            min_stock_qty = minStockQty,
            image_url     = null,
            description   = null,
            is_active     = 1L,
            created_at    = now,
            updated_at    = now,
            sync_status   = "PENDING",
            master_product_id = null,
        )
    }

    /** Build a minimal valid [Order] domain object. */
    private fun buildOrder(
        id: String = "ord-1",
        orderNumber: String = "ORD-001",
        status: OrderStatus = OrderStatus.COMPLETED,
        cashierId: String = "cashier-1",
        total: Double = 25.00,
        items: List<OrderItem> = emptyList(),
    ): Order {
        val instant = Instant.fromEpochMilliseconds(now)
        return Order(
            id                = id,
            orderNumber       = orderNumber,
            type              = OrderType.SALE,
            status            = status,
            items             = items,
            subtotal          = total,
            taxAmount         = 0.0,
            discountAmount    = 0.0,
            total             = total,
            paymentMethod     = PaymentMethod.CASH,
            paymentSplits     = emptyList(),
            amountTendered    = total,
            changeAmount      = 0.0,
            customerId        = null,
            cashierId         = cashierId,
            storeId           = "store-1",
            registerSessionId = "session-1",
            notes             = null,
            reference         = null,
            createdAt         = instant,
            updatedAt         = instant,
            syncStatus        = SyncStatus.pending(),
        )
    }

    /** Build a minimal valid [OrderItem] tied to an existing product. */
    private fun buildOrderItem(
        id: String = "item-1",
        orderId: String = "ord-1",
        productId: String = "prod-1",
        quantity: Double = 2.0,
        unitPrice: Double = 10.0,
    ): OrderItem = OrderItem(
        id           = id,
        orderId      = orderId,
        productId    = productId,
        productName  = "Test Product $productId",
        unitPrice    = unitPrice,
        quantity     = quantity,
        discount     = 0.0,
        discountType = DiscountType.FIXED,
        taxRate      = 0.0,
        taxAmount    = 0.0,
        lineTotal    = unitPrice * quantity,
    )

    // ── A. create + getById round-trip ────────────────────────────────────────

    @Test
    fun create_then_getById_round_trip_preserves_header_fields() = runTest {
        val order = buildOrder(
            id          = "ord-rt",
            orderNumber = "ORD-RT-001",
            status      = OrderStatus.COMPLETED,
            cashierId   = "cashier-abc",
            total       = 42.50,
        )

        val createResult = repo.create(order)
        assertIs<Result.Success<Order>>(createResult, "create() should succeed")

        val getResult = repo.getById("ord-rt")
        assertIs<Result.Success<Order>>(getResult, "getById() should find the inserted order")

        val fetched = getResult.data
        assertEquals("ord-rt",              fetched.id)
        assertEquals("ORD-RT-001",          fetched.orderNumber)
        assertEquals(OrderStatus.COMPLETED, fetched.status)
        assertEquals("cashier-abc",         fetched.cashierId)
        assertEquals(42.50,                 fetched.total)
        assertEquals(OrderType.SALE,        fetched.type)
        assertEquals(PaymentMethod.CASH,    fetched.paymentMethod)
    }

    // ── B. getAll returns empty list on fresh DB ───────────────────────────────

    @Test
    fun getAll_returns_empty_list_on_fresh_database() = runTest {
        repo.getAll(emptyMap()).test {
            val items = awaitItem()
            assertTrue(items.isEmpty(), "Initial getAll emission should be an empty list on a fresh DB")
            cancelAndIgnoreRemainingEvents()
        }
    }

    // ── C. getById returns Result.Error for unknown ID ─────────────────────────

    @Test
    fun getById_returns_error_for_non_existent_id() = runTest {
        val result = repo.getById("does-not-exist")
        assertIs<Result.Error>(result, "getById() should return Result.Error for unknown id")
    }

    // ── D. holdOrder inserts HELD order; retrieveHeld returns it with HELD status ──

    @Test
    fun holdOrder_inserts_held_order_and_retrieveHeld_returns_correct_status() = runTest {
        val cart = listOf(
            CartItem(
                productId   = "prod-hold",
                productName = "Held Product",
                unitPrice   = 15.00,
                quantity    = 3.0,
                lineTotal   = 45.00,
            )
        )

        val holdResult = repo.holdOrder(cart)
        assertIs<Result.Success<String>>(holdResult, "holdOrder() should succeed and return an ID")

        val holdId = holdResult.data
        assertNotNull(holdId, "holdOrder() should return a non-null hold ID")

        val retrieveResult = repo.retrieveHeld(holdId)
        assertIs<Result.Success<Order>>(retrieveResult, "retrieveHeld() should find the held order")

        val heldOrder = retrieveResult.data
        assertEquals(OrderStatus.HELD, heldOrder.status, "Retrieved order should have HELD status")
        assertEquals(holdId,           heldOrder.id,     "Retrieved order ID should match the returned hold ID")
    }

    // ── E. create with items decrements product stock atomically ──────────────

    @Test
    fun create_with_items_decrements_product_stock_qty() = runTest {
        insertProduct(id = "prod-stock", stockQty = 50.0)

        val item  = buildOrderItem(productId = "prod-stock", quantity = 5.0, unitPrice = 10.0)
        val order = buildOrder(id = "ord-stock", total = 50.0, items = listOf(item))

        val result = repo.create(order)
        assertIs<Result.Success<Order>>(result)

        val productRow = db.productsQueries.getProductById("prod-stock").executeAsOneOrNull()
        assertNotNull(productRow, "Product should still exist after order creation")
        assertEquals(45.0, productRow.stock_qty, "Stock should have been decremented by the item quantity (50 - 5 = 45)")
    }

    // ── F. void reverses stock and transitions order to VOIDED ────────────────

    @Test
    fun void_reverses_stock_and_order_becomes_voided() = runTest {
        insertProduct(id = "prod-void", stockQty = 20.0)

        val item  = buildOrderItem(id = "item-void", orderId = "ord-void", productId = "prod-void", quantity = 4.0)
        val order = buildOrder(id = "ord-void", total = 40.0, items = listOf(item))

        repo.create(order)

        // Verify stock was decremented during create
        val afterCreate = db.productsQueries.getProductById("prod-void").executeAsOneOrNull()
        assertNotNull(afterCreate)
        assertEquals(16.0, afterCreate.stock_qty, "Stock should be 20 - 4 = 16 after create")

        // Void the order
        val voidResult = repo.void("ord-void", reason = "Void for test")
        assertIs<Result.Success<Unit>>(voidResult, "void() should succeed")

        // Stock should be restored
        val afterVoid = db.productsQueries.getProductById("prod-void").executeAsOneOrNull()
        assertNotNull(afterVoid)
        assertEquals(20.0, afterVoid.stock_qty, "Stock should be restored to 20 after void")

        // Order status should now be VOIDED
        val voidedOrder = repo.getById("ord-void")
        assertIs<Result.Success<Order>>(voidedOrder)
        assertEquals(OrderStatus.VOIDED, voidedOrder.data.status, "Order status should be VOIDED after void()")
    }

    // ── G. getAll filters by status ───────────────────────────────────────────

    @Test
    fun getAll_filters_by_status_correctly() = runTest {
        val completedOrder = buildOrder(id = "ord-comp", status = OrderStatus.COMPLETED, orderNumber = "ORD-C")
        val heldOrder      = buildOrder(id = "ord-held", status = OrderStatus.HELD,      orderNumber = "ORD-H")

        repo.create(completedOrder)
        repo.create(heldOrder)

        repo.getAll(mapOf("status" to OrderStatus.COMPLETED.name)).test {
            val completedList = awaitItem()
            assertTrue(completedList.all { it.status == OrderStatus.COMPLETED }, "Filter should return only COMPLETED orders")
            assertTrue(completedList.any { it.id == "ord-comp" }, "COMPLETED order should appear in filtered list")
            assertTrue(completedList.none { it.id == "ord-held"  }, "HELD order should NOT appear in COMPLETED filter")
            cancelAndIgnoreRemainingEvents()
        }
    }
}
