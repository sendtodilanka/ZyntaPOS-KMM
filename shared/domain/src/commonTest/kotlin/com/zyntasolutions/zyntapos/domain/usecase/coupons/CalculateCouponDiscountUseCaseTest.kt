package com.zyntasolutions.zyntapos.domain.usecase.coupons

import com.zyntasolutions.zyntapos.domain.model.DiscountType
import com.zyntasolutions.zyntapos.domain.usecase.fakes.buildCoupon
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Unit tests for [CalculateCouponDiscountUseCase] (pure function, no coroutines).
 *
 * Covers:
 * - FIXED discount: returns exact discount value
 * - FIXED discount exceeds cart total: capped at cart total
 * - PERCENT discount: correct percentage of cart total
 * - PERCENT with maximumDiscount cap applied
 * - PERCENT with maximumDiscount larger than raw discount (cap not triggered)
 * - BOGO type returns 0.0 (handled by promotion engine)
 * - Zero cart total: always returns 0.0
 */
class CalculateCouponDiscountUseCaseTest {

    private val useCase = CalculateCouponDiscountUseCase()

    @Test
    fun `FIXED discount - returns exact value`() {
        val coupon = buildCoupon(discountType = DiscountType.FIXED, discountValue = 50.0)
        assertEquals(50.0, useCase(coupon, cartTotal = 200.0))
    }

    @Test
    fun `FIXED discount exceeds cart total - capped at cart total`() {
        val coupon = buildCoupon(discountType = DiscountType.FIXED, discountValue = 500.0)
        assertEquals(200.0, useCase(coupon, cartTotal = 200.0))
    }

    @Test
    fun `PERCENT discount - correct percentage of cart total`() {
        val coupon = buildCoupon(discountType = DiscountType.PERCENT, discountValue = 20.0)
        assertEquals(40.0, useCase(coupon, cartTotal = 200.0))
    }

    @Test
    fun `PERCENT with maximumDiscount cap triggers`() {
        val coupon = buildCoupon(
            discountType = DiscountType.PERCENT,
            discountValue = 30.0,
            maximumDiscount = 25.0,
        )
        // Raw = 200 * 0.30 = 60; capped at 25
        assertEquals(25.0, useCase(coupon, cartTotal = 200.0))
    }

    @Test
    fun `PERCENT with maximumDiscount cap not triggered`() {
        val coupon = buildCoupon(
            discountType = DiscountType.PERCENT,
            discountValue = 10.0,
            maximumDiscount = 100.0,
        )
        // Raw = 200 * 0.10 = 20; cap = 100 → not triggered
        assertEquals(20.0, useCase(coupon, cartTotal = 200.0))
    }

    @Test
    fun `BOGO type - returns 0 (handled by promotion engine)`() {
        val coupon = buildCoupon(discountType = DiscountType.BOGO, discountValue = 1.0)
        assertEquals(0.0, useCase(coupon, cartTotal = 200.0))
    }

    @Test
    fun `zero cart total - always returns 0`() {
        val fixedCoupon = buildCoupon(discountType = DiscountType.FIXED, discountValue = 50.0)
        val percentCoupon = buildCoupon(discountType = DiscountType.PERCENT, discountValue = 20.0)
        assertEquals(0.0, useCase(fixedCoupon, cartTotal = 0.0))
        assertEquals(0.0, useCase(percentCoupon, cartTotal = 0.0))
    }
}
