package com.zyntasolutions.zyntapos.domain.usecase.pos

import com.zyntasolutions.zyntapos.core.result.Result
import com.zyntasolutions.zyntapos.domain.model.CartItem
import com.zyntasolutions.zyntapos.domain.model.Order
import com.zyntasolutions.zyntapos.domain.model.OrderItem
import com.zyntasolutions.zyntapos.domain.model.OrderStatus
import com.zyntasolutions.zyntapos.domain.model.OrderType
import com.zyntasolutions.zyntapos.domain.model.PaymentMethod
import com.zyntasolutions.zyntapos.domain.model.SyncStatus
import com.zyntasolutions.zyntapos.domain.repository.OrderRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CrossStoreReturnUseCaseTest {

    private val now = Instant.fromEpochMilliseconds(1700000000000)

    private val testItems = listOf(
        OrderItem(
            id = "item-001",
            orderId = "order-001",
            productId = "prod-001",
            productName = "Widget A",
            unitPrice = 500.0,
            quantity = 2.0,
            discount = 0.0,
            discountType = "NONE",
            taxRate = 10.0,
            taxAmount = 100.0,
            lineTotal = 1000.0,
        ),
    )

    private val completedSale = Order(
        id = "order-001",
        orderNumber = "ORD-001",
        type = OrderType.SALE,
        status = OrderStatus.COMPLETED,
        items = testItems,
        subtotal = 1000.0,
        taxAmount = 100.0,
        discountAmount = 0.0,
        total = 1100.0,
        paymentMethod = PaymentMethod.CASH,
        amountTendered = 1200.0,
        changeAmount = 100.0,
        customerId = "cust-001",
        cashierId = "cashier-001",
        storeId = "store-A",
        registerSessionId = "sess-001",
        createdAt = now,
        updatedAt = now,
        syncStatus = SyncStatus.synced(),
    )

    private val fakeOrderRepository = FakeOrderRepository()

    // ── LookupOrderForReturnUseCase ──────────────────────────

    @Test
    fun `lookup returns completed SALE order`() = runTest {
        fakeOrderRepository.orders["order-001"] = completedSale
        val useCase = LookupOrderForReturnUseCase(fakeOrderRepository)

        val result = useCase("order-001")
        assertIs<Result.Success<Order>>(result)
        assertEquals("ORD-001", result.data.orderNumber)
    }

    @Test
    fun `lookup rejects REFUND order`() = runTest {
        fakeOrderRepository.orders["order-002"] = completedSale.copy(
            id = "order-002",
            type = OrderType.REFUND,
        )
        val useCase = LookupOrderForReturnUseCase(fakeOrderRepository)

        val result = useCase("order-002")
        assertIs<Result.Error>(result)
    }

    @Test
    fun `lookup rejects VOIDED order`() = runTest {
        fakeOrderRepository.orders["order-003"] = completedSale.copy(
            id = "order-003",
            status = OrderStatus.VOIDED,
        )
        val useCase = LookupOrderForReturnUseCase(fakeOrderRepository)

        val result = useCase("order-003")
        assertIs<Result.Error>(result)
    }

    @Test
    fun `lookup returns error for unknown order`() = runTest {
        val useCase = LookupOrderForReturnUseCase(fakeOrderRepository)
        val result = useCase("nonexistent")
        assertIs<Result.Error>(result)
    }

    // ── ProcessCrossStoreRefundUseCase ────────────────────────

    @Test
    fun `process refund creates REFUND order`() = runTest {
        val useCase = ProcessCrossStoreRefundUseCase(fakeOrderRepository)

        val result = useCase(
            originalOrder = completedSale,
            returnItems = testItems,
            currentStoreId = "store-B",
            cashierId = "cashier-002",
            registerSessionId = "sess-002",
            refundMethod = PaymentMethod.CASH,
        )

        assertIs<Result.Success<Order>>(result)
        val refund = result.data
        assertEquals(OrderType.REFUND, refund.type)
        assertEquals(OrderStatus.COMPLETED, refund.status)
        assertEquals("order-001", refund.originalOrderId)
        assertEquals("store-A", refund.originalStoreId)
        assertEquals("store-B", refund.storeId)
        assertEquals("R-ORD-001", refund.orderNumber)
        assertEquals(1100.0, refund.total)
    }

    @Test
    fun `same-store refund does not set originalStoreId`() = runTest {
        val useCase = ProcessCrossStoreRefundUseCase(fakeOrderRepository)

        val result = useCase(
            originalOrder = completedSale,
            returnItems = testItems,
            currentStoreId = "store-A",
            cashierId = "cashier-001",
            registerSessionId = "sess-001",
            refundMethod = PaymentMethod.CASH,
        )

        assertIs<Result.Success<Order>>(result)
        assertNull(result.data.originalStoreId)
        assertEquals("order-001", result.data.originalOrderId)
    }

    @Test
    fun `refund preserves customer from original order`() = runTest {
        val useCase = ProcessCrossStoreRefundUseCase(fakeOrderRepository)

        val result = useCase(
            originalOrder = completedSale,
            returnItems = testItems,
            currentStoreId = "store-B",
            cashierId = "cashier-002",
            registerSessionId = "sess-002",
            refundMethod = PaymentMethod.CASH,
        )

        assertIs<Result.Success<Order>>(result)
        assertEquals("cust-001", result.data.customerId)
    }

    @Test
    fun `refund rejects empty items`() = runTest {
        val useCase = ProcessCrossStoreRefundUseCase(fakeOrderRepository)

        val result = useCase(
            originalOrder = completedSale,
            returnItems = emptyList(),
            currentStoreId = "store-B",
            cashierId = "cashier-002",
            registerSessionId = "sess-002",
            refundMethod = PaymentMethod.CASH,
        )

        assertIs<Result.Error>(result)
    }

    @Test
    fun `refund rejects non-SALE order`() = runTest {
        val useCase = ProcessCrossStoreRefundUseCase(fakeOrderRepository)

        val result = useCase(
            originalOrder = completedSale.copy(type = OrderType.HOLD),
            returnItems = testItems,
            currentStoreId = "store-B",
            cashierId = "cashier-002",
            registerSessionId = "sess-002",
            refundMethod = PaymentMethod.CASH,
        )

        assertIs<Result.Error>(result)
    }

    @Test
    fun `refund rejects non-COMPLETED order`() = runTest {
        val useCase = ProcessCrossStoreRefundUseCase(fakeOrderRepository)

        val result = useCase(
            originalOrder = completedSale.copy(status = OrderStatus.IN_PROGRESS),
            returnItems = testItems,
            currentStoreId = "store-B",
            cashierId = "cashier-002",
            registerSessionId = "sess-002",
            refundMethod = PaymentMethod.CASH,
        )

        assertIs<Result.Error>(result)
    }

    @Test
    fun `refund with notes stores them`() = runTest {
        val useCase = ProcessCrossStoreRefundUseCase(fakeOrderRepository)

        val result = useCase(
            originalOrder = completedSale,
            returnItems = testItems,
            currentStoreId = "store-B",
            cashierId = "cashier-002",
            registerSessionId = "sess-002",
            refundMethod = PaymentMethod.CASH,
            notes = "Customer dissatisfied",
        )

        assertIs<Result.Success<Order>>(result)
        assertEquals("Customer dissatisfied", result.data.notes)
    }
}

/** Minimal in-memory fake for testing. */
private class FakeOrderRepository : OrderRepository {
    val orders = mutableMapOf<String, Order>()

    override suspend fun create(order: Order): Result<Order> {
        orders[order.id] = order
        return Result.Success(order)
    }

    override suspend fun getById(id: String): Result<Order> {
        val order = orders[id]
            ?: return Result.Error(
                com.zyntasolutions.zyntapos.core.result.DatabaseException("Order not found: $id"),
            )
        return Result.Success(order)
    }

    override fun getAll(filters: Map<String, String>): Flow<List<Order>> =
        flowOf(orders.values.toList())

    override suspend fun update(order: Order): Result<Unit> {
        orders[order.id] = order
        return Result.Success(Unit)
    }

    override suspend fun void(id: String, reason: String): Result<Unit> {
        orders[id] = orders[id]!!.copy(status = OrderStatus.VOIDED)
        return Result.Success(Unit)
    }

    override fun getByDateRange(from: Instant, to: Instant): Flow<List<Order>> =
        flowOf(orders.values.toList())

    override suspend fun holdOrder(cart: List<CartItem>): Result<String> =
        Result.Success("hold-001")

    override suspend fun retrieveHeld(holdId: String): Result<Order> =
        Result.Error(com.zyntasolutions.zyntapos.core.result.DatabaseException("Not implemented"))
}
