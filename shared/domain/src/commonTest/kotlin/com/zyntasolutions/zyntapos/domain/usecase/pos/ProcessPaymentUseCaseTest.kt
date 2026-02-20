package com.zyntasolutions.zyntapos.domain.usecase.pos

import com.zyntasolutions.zyntapos.core.result.Result
import com.zyntasolutions.zyntapos.core.result.ValidationException
import com.zyntasolutions.zyntapos.domain.model.DiscountType
import com.zyntasolutions.zyntapos.domain.model.PaymentMethod
import com.zyntasolutions.zyntapos.domain.model.PaymentSplit
import com.zyntasolutions.zyntapos.domain.usecase.fakes.FakeOrderRepository
import com.zyntasolutions.zyntapos.domain.usecase.fakes.FakeStockRepository
import com.zyntasolutions.zyntapos.domain.usecase.fakes.buildCartItem
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * Unit tests for [ProcessPaymentUseCase] covering cash, card, split, and edge cases.
 */
class ProcessPaymentUseCaseTest {

    private fun makeUseCase(
        orderRepo: FakeOrderRepository = FakeOrderRepository(),
        stockRepo: FakeStockRepository = FakeStockRepository(),
    ) = ProcessPaymentUseCase(orderRepo, stockRepo, CalculateOrderTotalsUseCase())

    private val defaultItems = listOf(
        buildCartItem(productId = "p1", unitPrice = 50.0, quantity = 2.0),  // 100.0
        buildCartItem(productId = "p2", unitPrice = 25.0, quantity = 1.0),  // 25.0
    )  // total = 125.0

    // ─── Happy Paths ──────────────────────────────────────────────────────────

    @Test
    fun `cash payment with exact tender - change is zero`() = runTest {
        val orderRepo = FakeOrderRepository()
        val stockRepo = FakeStockRepository()
        val result = makeUseCase(orderRepo, stockRepo)(
            items = defaultItems,
            paymentMethod = PaymentMethod.CASH,
            amountTendered = 125.0,
            cashierId = "cashier-01",
            storeId = "store-01",
            registerSessionId = "session-01",
        )
        assertIs<Result.Success<*>>(result)
        val order = (result as Result.Success).data
        assertEquals(0.0, order.changeAmount, 0.005)
        assertEquals(125.0, order.total, 0.005)
        assertEquals(2, stockRepo.adjustments.size)  // 2 products decremented
    }

    @Test
    fun `cash payment with overpayment - change calculated correctly`() = runTest {
        val result = makeUseCase()(
            items = defaultItems,
            paymentMethod = PaymentMethod.CASH,
            amountTendered = 150.0,
            cashierId = "cashier-01",
            storeId = "store-01",
            registerSessionId = "session-01",
        ) as Result.Success
        assertEquals(25.0, result.data.changeAmount, 0.005)
    }

    @Test
    fun `card payment - change is always zero`() = runTest {
        val result = makeUseCase()(
            items = defaultItems,
            paymentMethod = PaymentMethod.CARD,
            amountTendered = 125.0,
            cashierId = "cashier-01",
            storeId = "store-01",
            registerSessionId = "session-01",
        ) as Result.Success
        assertEquals(0.0, result.data.changeAmount, 0.005)
        assertEquals(PaymentMethod.CARD, result.data.paymentMethod)
    }

    @Test
    fun `valid split payment - sum equals total, order created successfully`() = runTest {
        val splits = listOf(
            PaymentSplit(PaymentMethod.CASH, 75.0),
            PaymentSplit(PaymentMethod.CARD, 50.0),
        )
        val result = makeUseCase()(
            items = defaultItems,
            paymentMethod = PaymentMethod.SPLIT,
            paymentSplits = splits,
            amountTendered = 125.0,
            cashierId = "cashier-01",
            storeId = "store-01",
            registerSessionId = "session-01",
        )
        assertIs<Result.Success<*>>(result)
    }

    @Test
    fun `order is persisted with correct order number`() = runTest {
        val orderRepo = FakeOrderRepository()
        val result = makeUseCase(orderRepo)(
            items = defaultItems,
            paymentMethod = PaymentMethod.CASH,
            amountTendered = 125.0,
            cashierId = "cashier-01",
            storeId = "store-01",
            registerSessionId = "session-01",
        ) as Result.Success
        assertEquals("ORD-0001", result.data.orderNumber)
        assertEquals(1, orderRepo.orders.size)
    }

    @Test
    fun `stock is decremented for each cart item`() = runTest {
        val stockRepo = FakeStockRepository()
        makeUseCase(stockRepo = stockRepo)(
            items = defaultItems,
            paymentMethod = PaymentMethod.CASH,
            amountTendered = 125.0,
            cashierId = "cashier-01",
            storeId = "store-01",
            registerSessionId = "session-01",
        )
        assertEquals(2, stockRepo.adjustments.size)
        assertEquals("p1", stockRepo.adjustments[0].productId)
        assertEquals("p2", stockRepo.adjustments[1].productId)
    }

    @Test
    fun `payment with order-level discount - total reflects discount`() = runTest {
        val result = makeUseCase()(
            items = defaultItems,
            paymentMethod = PaymentMethod.CASH,
            amountTendered = 100.0,
            orderDiscount = 25.0,
            orderDiscountType = DiscountType.FIXED,
            cashierId = "cashier-01",
            storeId = "store-01",
            registerSessionId = "session-01",
        )
        assertIs<Result.Success<*>>(result)
        val order = (result as Result.Success).data
        assertEquals(25.0, order.discountAmount, 0.005)
        assertEquals(100.0, order.total, 0.005)
    }

    @Test
    fun `optional customer id is stored on order`() = runTest {
        val result = makeUseCase()(
            items = defaultItems,
            paymentMethod = PaymentMethod.CASH,
            amountTendered = 125.0,
            customerId = "customer-42",
            cashierId = "cashier-01",
            storeId = "store-01",
            registerSessionId = "session-01",
        ) as Result.Success
        assertEquals("customer-42", result.data.customerId)
    }

    // ─── Error Cases ──────────────────────────────────────────────────────────

    @Test
    fun `empty cart returns EMPTY_CART error`() = runTest {
        val result = makeUseCase()(
            items = emptyList(),
            paymentMethod = PaymentMethod.CASH,
            amountTendered = 0.0,
            cashierId = "cashier-01",
            storeId = "store-01",
            registerSessionId = "session-01",
        )
        assertIs<Result.Error>(result)
        val ex = (result as Result.Error).exception as ValidationException
        assertEquals("EMPTY_CART", ex.rule)
    }

    @Test
    fun `cash underpayment returns INSUFFICIENT_TENDER error`() = runTest {
        val result = makeUseCase()(
            items = defaultItems,
            paymentMethod = PaymentMethod.CASH,
            amountTendered = 50.0,  // only 50, total=125
            cashierId = "cashier-01",
            storeId = "store-01",
            registerSessionId = "session-01",
        )
        assertIs<Result.Error>(result)
        val ex = (result as Result.Error).exception as ValidationException
        assertEquals("INSUFFICIENT_TENDER", ex.rule)
    }

    @Test
    fun `split payment sums mismatch returns SPLIT_SUM_MISMATCH error`() = runTest {
        val splits = listOf(
            PaymentSplit(PaymentMethod.CASH, 50.0),
            PaymentSplit(PaymentMethod.CARD, 50.0),  // total=100, but order=125
        )
        val result = makeUseCase()(
            items = defaultItems,
            paymentMethod = PaymentMethod.SPLIT,
            paymentSplits = splits,
            amountTendered = 125.0,
            cashierId = "cashier-01",
            storeId = "store-01",
            registerSessionId = "session-01",
        )
        assertIs<Result.Error>(result)
        val ex = (result as Result.Error).exception as ValidationException
        assertEquals("SPLIT_SUM_MISMATCH", ex.rule)
    }

    @Test
    fun `stock failure aborts order creation`() = runTest {
        val stockRepo = FakeStockRepository().also { it.shouldFailAdjust = true }
        val orderRepo = FakeOrderRepository()
        val result = makeUseCase(orderRepo, stockRepo)(
            items = defaultItems,
            paymentMethod = PaymentMethod.CASH,
            amountTendered = 125.0,
            cashierId = "cashier-01",
            storeId = "store-01",
            registerSessionId = "session-01",
        )
        assertIs<Result.Error>(result)
        assertEquals(0, orderRepo.orders.size, "Order must not be persisted on stock failure")
    }
}
