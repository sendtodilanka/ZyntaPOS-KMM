package com.zyntasolutions.zyntapos.domain.usecase.pos

import com.zyntasolutions.zyntapos.core.result.Result
import com.zyntasolutions.zyntapos.core.result.ValidationException
import com.zyntasolutions.zyntapos.domain.model.CartItem
import com.zyntasolutions.zyntapos.domain.model.DiscountType
import com.zyntasolutions.zyntapos.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

/**
 * ZyntaPOS — ApplyOrderDiscountUseCaseTest Unit Tests (commonTest)
 *
 * Validates order-level discount business rules in [ApplyOrderDiscountUseCase.invoke].
 *
 * Coverage:
 *  A. negative discount returns MIN_VALUE error
 *  B. PERCENT discount within default 20% max succeeds
 *  C. PERCENT discount exceeding max returns MAX_DISCOUNT_EXCEEDED error
 *  D. custom maxPct from settings is honoured
 *  E. FIXED discount within subtotal succeeds
 *  F. FIXED discount exceeding subtotal returns DISCOUNT_EXCEEDS_SUBTOTAL error
 *  G. BOGO discount type bypasses percentage cap validation
 *  H. missing/unparseable setting falls back to 20% default
 */
class ApplyOrderDiscountUseCaseTest {

    private class FakeSettingsRepository(
        private val maxDiscountPct: String? = null,
    ) : SettingsRepository {
        override suspend fun get(key: String): String? =
            if (key == ApplyOrderDiscountUseCase.SETTING_KEY) maxDiscountPct else null
        override suspend fun set(key: String, value: String): Result<Unit> = Result.Success(Unit)
        override suspend fun getAll(): Map<String, String> = emptyMap()
        override fun observe(key: String): Flow<String?> = flowOf(null)
    }

    private fun makeUseCase(maxDiscountPct: String? = null): ApplyOrderDiscountUseCase =
        ApplyOrderDiscountUseCase(
            settingsRepository = FakeSettingsRepository(maxDiscountPct),
            calculateOrderTotalsUseCase = CalculateOrderTotalsUseCase(),
        )

    private fun makeCartItem(
        productId: String = "prod-1",
        unitPrice: Double = 10.0,
        quantity: Double = 2.0,
    ) = CartItem(productId = productId, productName = "Item", unitPrice = unitPrice, quantity = quantity)

    // ── Tests ─────────────────────────────────────────────────────────────────

    @Test
    fun `A - negative discount returns MIN_VALUE error`() = runTest {
        val useCase = makeUseCase()
        val result = useCase(listOf(makeCartItem()), -5.0, DiscountType.PERCENT)
        assertIs<Result.Error>(result)
        val error = result.exception as? ValidationException
        assertEquals("MIN_VALUE", error?.rule)
    }

    @Test
    fun `B - PERCENT discount within default 20 percent max succeeds`() = runTest {
        val useCase = makeUseCase()
        val result = useCase(listOf(makeCartItem(unitPrice = 10.0, quantity = 1.0)), 15.0, DiscountType.PERCENT)
        assertIs<Result.Success<*>>(result)
    }

    @Test
    fun `C - PERCENT discount exceeding default max returns MAX_DISCOUNT_EXCEEDED`() = runTest {
        val useCase = makeUseCase() // default max = 20%
        val result = useCase(listOf(makeCartItem()), 25.0, DiscountType.PERCENT)
        assertIs<Result.Error>(result)
        val error = result.exception as? ValidationException
        assertEquals("MAX_DISCOUNT_EXCEEDED", error?.rule)
    }

    @Test
    fun `D - custom maxPct from settings is honoured`() = runTest {
        val useCase = makeUseCase(maxDiscountPct = "30.0") // custom 30%
        // 25% is within 30% limit
        val result = useCase(listOf(makeCartItem(unitPrice = 10.0, quantity = 1.0)), 25.0, DiscountType.PERCENT)
        assertIs<Result.Success<*>>(result)
    }

    @Test
    fun `E - FIXED discount within subtotal succeeds`() = runTest {
        val useCase = makeUseCase()
        // subtotal = 10.0 * 2.0 = 20.0; discount = 5.0
        val result = useCase(listOf(makeCartItem(unitPrice = 10.0, quantity = 2.0)), 5.0, DiscountType.FIXED)
        assertIs<Result.Success<*>>(result)
    }

    @Test
    fun `F - FIXED discount exceeding subtotal returns DISCOUNT_EXCEEDS_SUBTOTAL`() = runTest {
        val useCase = makeUseCase()
        // subtotal = 10.0; discount = 15.0 (exceeds)
        val result = useCase(listOf(makeCartItem(unitPrice = 10.0, quantity = 1.0)), 15.0, DiscountType.FIXED)
        assertIs<Result.Error>(result)
        val error = result.exception as? ValidationException
        assertEquals("DISCOUNT_EXCEEDS_SUBTOTAL", error?.rule)
    }

    @Test
    fun `G - BOGO discount type bypasses percentage cap validation`() = runTest {
        val useCase = makeUseCase() // default max = 20%
        // 99% would normally exceed cap, but BOGO bypasses it
        val result = useCase(listOf(makeCartItem(unitPrice = 10.0, quantity = 1.0)), 99.0, DiscountType.BOGO)
        assertIs<Result.Success<*>>(result)
    }

    @Test
    fun `H - unparseable max setting falls back to 20 percent default`() = runTest {
        val useCase = makeUseCase(maxDiscountPct = "not-a-number")
        // 25% > 20% default → fails
        val result = useCase(listOf(makeCartItem()), 25.0, DiscountType.PERCENT)
        assertIs<Result.Error>(result)
        val error = result.exception as? ValidationException
        assertEquals("MAX_DISCOUNT_EXCEEDED", error?.rule)
    }
}
