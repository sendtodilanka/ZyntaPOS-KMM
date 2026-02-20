package com.zyntasolutions.zyntapos.domain.usecase.pos

import com.zyntasolutions.zyntapos.core.result.Result
import com.zyntasolutions.zyntapos.core.result.ValidationException
import com.zyntasolutions.zyntapos.domain.model.DiscountType
import com.zyntasolutions.zyntapos.domain.model.Permission
import com.zyntasolutions.zyntapos.domain.model.Role
import com.zyntasolutions.zyntapos.domain.usecase.auth.CheckPermissionUseCase
import com.zyntasolutions.zyntapos.domain.usecase.fakes.FakeSettingsRepository
import com.zyntasolutions.zyntapos.domain.usecase.fakes.buildCartItem
import com.zyntasolutions.zyntapos.domain.usecase.fakes.buildUser
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * Unit tests for [ApplyOrderDiscountUseCase] and [ApplyItemDiscountUseCase].
 */
class DiscountUseCasesTest {

    // ─── Helpers ──────────────────────────────────────────────────────────────

    private fun makeCheckPermission(role: Role = Role.CASHIER, userId: String = "user-01"): CheckPermissionUseCase {
        val useCase = CheckPermissionUseCase(flowOf(buildUser(id = userId, role = role)))
        useCase.updateSession(buildUser(id = userId, role = role))
        return useCase
    }

    private fun makeOrderDiscountUseCase(
        maxPct: Double? = null,
        settingsRepo: FakeSettingsRepository = FakeSettingsRepository(),
    ): ApplyOrderDiscountUseCase {
        maxPct?.let { settingsRepo.put(ApplyOrderDiscountUseCase.SETTING_KEY, it.toString()) }
        return ApplyOrderDiscountUseCase(settingsRepo, CalculateOrderTotalsUseCase())
    }

    private val sampleCart = listOf(
        buildCartItem(productId = "p1", unitPrice = 100.0, quantity = 1.0),
        buildCartItem(productId = "p2", unitPrice = 50.0, quantity = 2.0),
    )  // subtotal = 200.0

    // ─── ApplyOrderDiscountUseCase ────────────────────────────────────────────

    @Test
    fun `order percent discount within max - returns updated totals`() = runTest {
        val result = makeOrderDiscountUseCase(maxPct = 20.0)(
            sampleCart, 10.0, DiscountType.PERCENT
        ) as Result.Success
        // 10% of 200 = 20 discount
        assertEquals(20.0, result.data.discountAmount, 0.005)
        assertEquals(180.0, result.data.total, 0.005)
    }

    @Test
    fun `order percent discount at exact max - allowed`() = runTest {
        val result = makeOrderDiscountUseCase(maxPct = 20.0)(
            sampleCart, 20.0, DiscountType.PERCENT
        )
        assertIs<Result.Success<*>>(result)
    }

    @Test
    fun `order percent discount exceeds max - returns MAX_DISCOUNT_EXCEEDED error`() = runTest {
        val result = makeOrderDiscountUseCase(maxPct = 20.0)(
            sampleCart, 25.0, DiscountType.PERCENT
        )
        assertIs<Result.Error>(result)
        assertEquals("MAX_DISCOUNT_EXCEEDED", ((result as Result.Error).exception as ValidationException).rule)
    }

    @Test
    fun `order fixed discount valid - returns correct totals`() = runTest {
        val result = makeOrderDiscountUseCase()(
            sampleCart, 50.0, DiscountType.FIXED
        ) as Result.Success
        assertEquals(50.0, result.data.discountAmount, 0.005)
        assertEquals(150.0, result.data.total, 0.005)
    }

    @Test
    fun `order fixed discount exceeds subtotal - returns DISCOUNT_EXCEEDS_SUBTOTAL error`() = runTest {
        val result = makeOrderDiscountUseCase()(
            sampleCart, 250.0, DiscountType.FIXED
        )
        assertIs<Result.Error>(result)
        assertEquals("DISCOUNT_EXCEEDS_SUBTOTAL", ((result as Result.Error).exception as ValidationException).rule)
    }

    @Test
    fun `order negative discount - returns MIN_VALUE error`() = runTest {
        val result = makeOrderDiscountUseCase()(sampleCart, -5.0, DiscountType.FIXED)
        assertIs<Result.Error>(result)
        assertEquals("MIN_VALUE", ((result as Result.Error).exception as ValidationException).rule)
    }

    @Test
    fun `order discount uses default max 20pct when setting is absent`() = runTest {
        val result = makeOrderDiscountUseCase(maxPct = null)(
            sampleCart, 21.0, DiscountType.PERCENT
        )
        assertIs<Result.Error>(result)
        assertEquals("MAX_DISCOUNT_EXCEEDED", ((result as Result.Error).exception as ValidationException).rule)
    }

    // ─── ApplyItemDiscountUseCase ─────────────────────────────────────────────

    private fun makeItemDiscountUseCase(role: Role = Role.CASHIER, userId: String = "user-01") =
        ApplyItemDiscountUseCase(makeCheckPermission(role, userId))

    private val cartWithItem = listOf(
        buildCartItem(productId = "p1", unitPrice = 100.0, quantity = 1.0),
    )

    @Test
    fun `item percent discount applied by cashier - discount stored on item`() {
        val result = makeItemDiscountUseCase(Role.CASHIER, "user-01")(
            cartWithItem, "p1", 15.0, DiscountType.PERCENT, "user-01"
        ) as Result.Success
        val item = result.data.first { it.productId == "p1" }
        assertEquals(15.0, item.discount, 0.001)
        assertEquals(DiscountType.PERCENT, item.discountType)
    }

    @Test
    fun `item fixed discount applied - stored on item`() {
        val result = makeItemDiscountUseCase(Role.STORE_MANAGER, "manager-01")(
            cartWithItem, "p1", 10.0, DiscountType.FIXED, "manager-01"
        ) as Result.Success
        assertEquals(10.0, result.data[0].discount, 0.001)
    }

    @Test
    fun `item discount by ACCOUNTANT - returns PERMISSION_DENIED`() {
        val result = makeItemDiscountUseCase(Role.ACCOUNTANT, "acct-01")(
            cartWithItem, "p1", 10.0, DiscountType.FIXED, "acct-01"
        )
        assertIs<Result.Error>(result)
        assertEquals("PERMISSION_DENIED", ((result as Result.Error).exception as ValidationException).rule)
    }

    @Test
    fun `item percent discount over 100pct - returns MAX_PCT_EXCEEDED error`() {
        val result = makeItemDiscountUseCase()(
            cartWithItem, "p1", 101.0, DiscountType.PERCENT, "user-01"
        )
        assertIs<Result.Error>(result)
        assertEquals("MAX_PCT_EXCEEDED", ((result as Result.Error).exception as ValidationException).rule)
    }

    @Test
    fun `item fixed discount exceeds unit price - returns DISCOUNT_EXCEEDS_PRICE error`() {
        val result = makeItemDiscountUseCase()(
            cartWithItem, "p1", 150.0, DiscountType.FIXED, "user-01"
        )
        assertIs<Result.Error>(result)
        assertEquals("DISCOUNT_EXCEEDS_PRICE", ((result as Result.Error).exception as ValidationException).rule)
    }

    @Test
    fun `item discount product not in cart - returns NOT_IN_CART error`() {
        val result = makeItemDiscountUseCase()(
            cartWithItem, "nonexistent", 10.0, DiscountType.FIXED, "user-01"
        )
        assertIs<Result.Error>(result)
        assertEquals("NOT_IN_CART", ((result as Result.Error).exception as ValidationException).rule)
    }

    @Test
    fun `item negative discount - returns MIN_VALUE error`() {
        val result = makeItemDiscountUseCase()(
            cartWithItem, "p1", -5.0, DiscountType.FIXED, "user-01"
        )
        assertIs<Result.Error>(result)
        assertEquals("MIN_VALUE", ((result as Result.Error).exception as ValidationException).rule)
    }
}
