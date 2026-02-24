package com.zyntasolutions.zyntapos.domain.usecase.coupons

import com.zyntasolutions.zyntapos.domain.model.Coupon
import com.zyntasolutions.zyntapos.domain.model.DiscountType

/**
 * Pure function that computes the monetary discount for a [Coupon] given the cart total.
 *
 * @param coupon The validated coupon to apply.
 * @param cartTotal The pre-discount cart total.
 * @return The monetary discount amount (never exceeds [cartTotal]).
 */
class CalculateCouponDiscountUseCase {
    operator fun invoke(coupon: Coupon, cartTotal: Double): Double {
        val raw = when (coupon.discountType) {
            DiscountType.FIXED -> coupon.discountValue
            DiscountType.PERCENT -> cartTotal * (coupon.discountValue / 100.0)
            DiscountType.BOGO -> 0.0  // BOGO is handled by the promotion engine, not this use case
        }
        val capped = if (coupon.maximumDiscount != null) minOf(raw, coupon.maximumDiscount) else raw
        return minOf(capped, cartTotal).coerceAtLeast(0.0)
    }
}
