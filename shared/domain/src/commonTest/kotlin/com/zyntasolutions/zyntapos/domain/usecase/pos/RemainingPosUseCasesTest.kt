package com.zyntasolutions.zyntapos.domain.usecase.pos

import com.zyntasolutions.zyntapos.core.result.Result
import com.zyntasolutions.zyntapos.domain.model.DiscountType
import com.zyntasolutions.zyntapos.domain.model.ReturnStockPolicy
import com.zyntasolutions.zyntapos.domain.model.Store
import com.zyntasolutions.zyntapos.domain.usecase.fakes.FakeReceiptPrinterPort
import com.zyntasolutions.zyntapos.domain.usecase.fakes.buildCoupon
import com.zyntasolutions.zyntapos.domain.usecase.fakes.buildOrder
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

// ─────────────────────────────────────────────────────────────────────────────
// Fixtures
// ─────────────────────────────────────────────────────────────────────────────

private fun buildStore(
    id: String = "store-01",
    maxDiscountPercent: Double? = null,
    maxDiscountAmount: Double? = null,
    returnStockPolicy: ReturnStockPolicy = ReturnStockPolicy.RETURN_TO_CURRENT_STORE,
) = Store(
    id = id,
    name = "Test Store",
    maxDiscountPercent = maxDiscountPercent,
    maxDiscountAmount = maxDiscountAmount,
    returnStockPolicy = returnStockPolicy,
    createdAt = Instant.fromEpochSeconds(0),
    updatedAt = Instant.fromEpochSeconds(0),
)

// ─────────────────────────────────────────────────────────────────────────────
// PrintReceiptUseCaseTest
// ─────────────────────────────────────────────────────────────────────────────

class PrintReceiptUseCaseTest {

    @Test
    fun `invoke_delegatesToPrinterPort`() = runTest {
        val port = FakeReceiptPrinterPort()
        val order = buildOrder(id = "order-01")
        val result = PrintReceiptUseCase(port).invoke(order, "cashier-01")
        assertIs<Result.Success<*>>(result)
        assertEquals(1, port.printedOrders.size)
        assertEquals("order-01", port.printedOrders.first().first.id)
        assertEquals("cashier-01", port.printedOrders.first().second)
    }

    @Test
    fun `printerFailure_returnsError`() = runTest {
        val port = FakeReceiptPrinterPort().apply { shouldFail = true }
        val result = PrintReceiptUseCase(port).invoke(buildOrder(), "cashier-01")
        assertIs<Result.Error>(result)
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// OpenCashDrawerUseCaseTest
// ─────────────────────────────────────────────────────────────────────────────

class OpenCashDrawerUseCaseTest {

    @Test
    fun `invoke_opensCashDrawer`() = runTest {
        val port = FakeReceiptPrinterPort()
        val result = OpenCashDrawerUseCase(port).invoke()
        assertIs<Result.Success<*>>(result)
    }

    @Test
    fun `printerFailure_returnsError`() = runTest {
        val port = FakeReceiptPrinterPort().apply { shouldFail = true }
        val result = OpenCashDrawerUseCase(port).invoke()
        assertIs<Result.Error>(result)
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// ValidateStoreDiscountLimitUseCaseTest
// ─────────────────────────────────────────────────────────────────────────────

class ValidateStoreDiscountLimitUseCaseTest {

    private val useCase = ValidateStoreDiscountLimitUseCase()

    @Test
    fun `percentDiscount_withinLimit_returnsAllowed`() {
        val store = buildStore(maxDiscountPercent = 30.0)
        val result = useCase(store, 20.0, DiscountType.PERCENT)
        assertIs<DiscountValidationResult.Allowed>(result)
        assertEquals(20.0, result.value)
    }

    @Test
    fun `percentDiscount_exceedsLimit_returnsExceedsLimit`() {
        val store = buildStore(maxDiscountPercent = 20.0)
        val result = useCase(store, 25.0, DiscountType.PERCENT)
        assertIs<DiscountValidationResult.ExceedsLimit>(result)
        assertEquals(20.0, result.cappedValue)
        assertEquals(25.0, result.requestedValue)
    }

    @Test
    fun `fixedDiscount_withinLimit_returnsAllowed`() {
        val store = buildStore(maxDiscountAmount = 100.0)
        val result = useCase(store, 80.0, DiscountType.FIXED)
        assertIs<DiscountValidationResult.Allowed>(result)
    }

    @Test
    fun `fixedDiscount_exceedsLimit_returnsExceedsLimit`() {
        val store = buildStore(maxDiscountAmount = 50.0)
        val result = useCase(store, 75.0, DiscountType.FIXED)
        assertIs<DiscountValidationResult.ExceedsLimit>(result)
        assertEquals(50.0, result.cappedValue)
    }

    @Test
    fun `bogoDiscount_alwaysAllowed`() {
        val store = buildStore(maxDiscountPercent = 10.0)
        val result = useCase(store, 100.0, DiscountType.BOGO)
        assertIs<DiscountValidationResult.Allowed>(result)
    }

    @Test
    fun `noLimitConfigured_alwaysAllowed`() {
        val store = buildStore() // null limits
        val result = useCase(store, 99.9, DiscountType.PERCENT)
        assertIs<DiscountValidationResult.Allowed>(result)
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// ResolveReturnStockDestinationUseCaseTest
// ─────────────────────────────────────────────────────────────────────────────

class ResolveReturnStockDestinationUseCaseTest {

    private val useCase = ResolveReturnStockDestinationUseCase()

    @Test
    fun `sameStoreReturn_alwaysReturnsToCurrent`() {
        val store = buildStore("store-01", returnStockPolicy = ReturnStockPolicy.RETURN_TO_ORIGINAL_STORE)
        val destination = useCase(store, "store-01")
        assertEquals("store-01", destination)
    }

    @Test
    fun `crossStoreReturn_returnToCurrentStorePolicy`() {
        val store = buildStore("store-01", returnStockPolicy = ReturnStockPolicy.RETURN_TO_CURRENT_STORE)
        val destination = useCase(store, "store-02")
        assertEquals("store-01", destination)
    }

    @Test
    fun `crossStoreReturn_returnToOriginalStorePolicy`() {
        val store = buildStore("store-01", returnStockPolicy = ReturnStockPolicy.RETURN_TO_ORIGINAL_STORE)
        val destination = useCase(store, "store-02")
        assertEquals("store-02", destination)
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// ResolvePromotionConflictsUseCaseTest
// ─────────────────────────────────────────────────────────────────────────────

class ResolvePromotionConflictsUseCaseTest {

    private val useCase = ResolvePromotionConflictsUseCase()

    @Test
    fun `emptyCoupons_returnsEmptyList`() {
        val result = useCase(emptyList(), 100.0)
        assertTrue(result.isEmpty())
    }

    @Test
    fun `singleCoupon_returnsThatCoupon`() {
        val coupon = buildCoupon(id = "c1", discountType = DiscountType.PERCENT, discountValue = 10.0)
        val result = useCase(listOf(coupon), 100.0)
        assertEquals(1, result.size)
        assertEquals("c1", result.first().couponId)
    }

    @Test
    fun `bestForCustomer_selectsHighestDiscount`() {
        val low = buildCoupon(id = "low", discountType = DiscountType.PERCENT, discountValue = 10.0)
        val high = buildCoupon(id = "high", discountType = DiscountType.PERCENT, discountValue = 25.0)
        val result = useCase(
            listOf(low, high),
            100.0,
            PromotionConflictStrategy.BEST_FOR_CUSTOMER,
        )
        assertEquals(1, result.size)
        assertEquals("high", result.first().couponId)
    }

    @Test
    fun `bestForStore_selectsLowestDiscount`() {
        val low = buildCoupon(id = "low", discountType = DiscountType.PERCENT, discountValue = 10.0)
        val high = buildCoupon(id = "high", discountType = DiscountType.PERCENT, discountValue = 25.0)
        val result = useCase(
            listOf(low, high),
            100.0,
            PromotionConflictStrategy.BEST_FOR_STORE,
        )
        assertEquals(1, result.size)
        assertEquals("low", result.first().couponId)
    }

    @Test
    fun `stackAll_appliesAllCoupons`() {
        val c1 = buildCoupon(id = "c1", discountType = DiscountType.FIXED, discountValue = 10.0)
        val c2 = buildCoupon(id = "c2", discountType = DiscountType.FIXED, discountValue = 20.0)
        val result = useCase(listOf(c1, c2), 100.0, PromotionConflictStrategy.STACK_ALL)
        assertEquals(2, result.size)
    }

    @Test
    fun `stackAll_capsAtSubtotal`() {
        val c1 = buildCoupon(id = "c1", discountType = DiscountType.FIXED, discountValue = 80.0)
        val c2 = buildCoupon(id = "c2", discountType = DiscountType.FIXED, discountValue = 50.0)
        val result = useCase(listOf(c1, c2), 100.0, PromotionConflictStrategy.STACK_ALL)
        val total = result.sumOf { it.effectiveDiscountAmount }
        assertEquals(100.0, total, 0.001)
    }

    @Test
    fun `priorityBased_selectsOldestCoupon`() {
        val now = 1_000_000L
        val older = buildCoupon(id = "older", validFrom = now - 200_000L, validTo = now + 200_000L)
        val newer = buildCoupon(id = "newer", validFrom = now - 100_000L, validTo = now + 200_000L)
        val result = useCase(listOf(newer, older), 100.0, PromotionConflictStrategy.PRIORITY_BASED)
        assertEquals(1, result.size)
        assertEquals("older", result.first().couponId)
    }
}
