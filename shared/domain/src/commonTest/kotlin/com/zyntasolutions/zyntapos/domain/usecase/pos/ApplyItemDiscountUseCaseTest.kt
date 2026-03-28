package com.zyntasolutions.zyntapos.domain.usecase.pos

import com.zyntasolutions.zyntapos.core.result.Result
import com.zyntasolutions.zyntapos.core.result.ValidationException
import com.zyntasolutions.zyntapos.domain.model.CartItem
import com.zyntasolutions.zyntapos.domain.model.DiscountType
import com.zyntasolutions.zyntapos.domain.model.Permission
import com.zyntasolutions.zyntapos.domain.model.Role
import com.zyntasolutions.zyntapos.domain.model.User
import com.zyntasolutions.zyntapos.domain.usecase.auth.CheckPermissionUseCase
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlinx.coroutines.flow.flowOf
import kotlinx.datetime.Instant

/**
 * ZyntaPOS — ApplyItemDiscountUseCaseTest Unit Tests (commonTest)
 *
 * Validates cart item discount business rules in [ApplyItemDiscountUseCase.invoke].
 *
 * Coverage:
 *  A. valid PERCENT discount within 0-100 range succeeds
 *  B. valid FIXED discount below unitPrice succeeds
 *  C. user without APPLY_DISCOUNT permission returns PERMISSION_DENIED
 *  D. negative discount returns MIN_VALUE error
 *  E. PERCENT discount > 100 returns MAX_PCT_EXCEEDED error
 *  F. FIXED discount > unitPrice returns DISCOUNT_EXCEEDS_PRICE error
 *  G. productId not in cart returns NOT_IN_CART error
 *  H. only the target cart item is updated; others remain unchanged
 *  I. BOGO discount type applies without validation error
 */
class ApplyItemDiscountUseCaseTest {

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun buildCheckPermission(user: User): CheckPermissionUseCase {
        val uc = CheckPermissionUseCase(flowOf(user))
        uc.updateSession(user)
        return uc
    }

    private fun makeUser(
        id: String = "user-1",
        role: Role = Role.CASHIER,
    ) = User(
        id = id,
        name = "Test User",
        email = "test@example.com",
        role = role,
        storeId = "store-1",
        createdAt = Instant.fromEpochMilliseconds(1_000_000L),
        updatedAt = Instant.fromEpochMilliseconds(1_000_000L),
    )

    private fun makeCartItem(
        productId: String = "prod-1",
        unitPrice: Double = 10.0,
        quantity: Double = 2.0,
    ) = CartItem(
        productId = productId,
        productName = "Espresso",
        unitPrice = unitPrice,
        quantity = quantity,
    )

    // ── Tests ─────────────────────────────────────────────────────────────────

    @Test
    fun `A - valid PERCENT discount within range succeeds`() {
        val cashier = makeUser()
        val useCase = ApplyItemDiscountUseCase(buildCheckPermission(cashier))
        val cart = listOf(makeCartItem())

        val result = useCase(cart, "prod-1", 10.0, DiscountType.PERCENT, cashier.id)

        assertIs<Result.Success<List<CartItem>>>(result)
        assertEquals(10.0, result.data.first().discount)
        assertEquals(DiscountType.PERCENT, result.data.first().discountType)
    }

    @Test
    fun `B - valid FIXED discount below unitPrice succeeds`() {
        val cashier = makeUser()
        val useCase = ApplyItemDiscountUseCase(buildCheckPermission(cashier))
        val cart = listOf(makeCartItem(unitPrice = 10.0))

        val result = useCase(cart, "prod-1", 5.0, DiscountType.FIXED, cashier.id)

        assertIs<Result.Success<List<CartItem>>>(result)
        assertEquals(5.0, result.data.first().discount)
    }

    @Test
    fun `C - user without APPLY_DISCOUNT permission returns PERMISSION_DENIED`() {
        // STOCK_MANAGER role does not have APPLY_DISCOUNT
        val stockMgr = makeUser(role = Role.STOCK_MANAGER)
        val useCase = ApplyItemDiscountUseCase(buildCheckPermission(stockMgr))
        val cart = listOf(makeCartItem())

        val result = useCase(cart, "prod-1", 10.0, DiscountType.PERCENT, stockMgr.id)

        assertIs<Result.Error>(result)
        val error = result.exception as? ValidationException
        assertEquals("PERMISSION_DENIED", error?.rule)
    }

    @Test
    fun `D - negative discount returns MIN_VALUE error`() {
        val cashier = makeUser()
        val useCase = ApplyItemDiscountUseCase(buildCheckPermission(cashier))
        val cart = listOf(makeCartItem())

        val result = useCase(cart, "prod-1", -5.0, DiscountType.FIXED, cashier.id)

        assertIs<Result.Error>(result)
        val error = result.exception as? ValidationException
        assertEquals("MIN_VALUE", error?.rule)
    }

    @Test
    fun `E - PERCENT discount greater than 100 returns MAX_PCT_EXCEEDED error`() {
        val cashier = makeUser()
        val useCase = ApplyItemDiscountUseCase(buildCheckPermission(cashier))
        val cart = listOf(makeCartItem())

        val result = useCase(cart, "prod-1", 101.0, DiscountType.PERCENT, cashier.id)

        assertIs<Result.Error>(result)
        val error = result.exception as? ValidationException
        assertEquals("MAX_PCT_EXCEEDED", error?.rule)
    }

    @Test
    fun `F - FIXED discount exceeding unitPrice returns DISCOUNT_EXCEEDS_PRICE error`() {
        val cashier = makeUser()
        val useCase = ApplyItemDiscountUseCase(buildCheckPermission(cashier))
        val cart = listOf(makeCartItem(unitPrice = 10.0))

        val result = useCase(cart, "prod-1", 15.0, DiscountType.FIXED, cashier.id)

        assertIs<Result.Error>(result)
        val error = result.exception as? ValidationException
        assertEquals("DISCOUNT_EXCEEDS_PRICE", error?.rule)
    }

    @Test
    fun `G - productId not in cart returns NOT_IN_CART error`() {
        val cashier = makeUser()
        val useCase = ApplyItemDiscountUseCase(buildCheckPermission(cashier))
        val cart = listOf(makeCartItem(productId = "prod-1"))

        val result = useCase(cart, "prod-NONEXISTENT", 5.0, DiscountType.FIXED, cashier.id)

        assertIs<Result.Error>(result)
        val error = result.exception as? ValidationException
        assertEquals("NOT_IN_CART", error?.rule)
    }

    @Test
    fun `H - only the target item is updated others remain unchanged`() {
        val cashier = makeUser()
        val useCase = ApplyItemDiscountUseCase(buildCheckPermission(cashier))
        val cart = listOf(
            makeCartItem(productId = "prod-1", unitPrice = 10.0),
            makeCartItem(productId = "prod-2", unitPrice = 20.0),
        )

        val result = useCase(cart, "prod-1", 5.0, DiscountType.FIXED, cashier.id)

        assertIs<Result.Success<List<CartItem>>>(result)
        assertEquals(2, result.data.size)
        assertEquals(5.0, result.data.first { it.productId == "prod-1" }.discount)
        assertEquals(0.0, result.data.first { it.productId == "prod-2" }.discount)
    }

    @Test
    fun `I - BOGO discount type applies without validation error`() {
        val cashier = makeUser()
        val useCase = ApplyItemDiscountUseCase(buildCheckPermission(cashier))
        val cart = listOf(makeCartItem())

        val result = useCase(cart, "prod-1", 0.0, DiscountType.BOGO, cashier.id)

        assertIs<Result.Success<List<CartItem>>>(result)
        assertEquals(DiscountType.BOGO, result.data.first().discountType)
    }
}
