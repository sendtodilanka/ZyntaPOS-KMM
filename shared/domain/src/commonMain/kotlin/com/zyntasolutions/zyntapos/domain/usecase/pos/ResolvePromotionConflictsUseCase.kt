package com.zyntasolutions.zyntapos.domain.usecase.pos

import com.zyntasolutions.zyntapos.domain.model.Coupon
import com.zyntasolutions.zyntapos.domain.model.DiscountType

/**
 * Resolves conflicts when multiple promotions/coupons match the same cart or item (C2.4).
 *
 * ### Resolution Strategy
 *
 * When multiple coupons/promotions are eligible for the same cart item or order:
 *
 * 1. **BEST_FOR_CUSTOMER** (default): Select the single promotion that gives the highest
 *    discount to the customer. Only one promotion per scope is applied.
 *
 * 2. **BEST_FOR_STORE**: Select the single promotion that gives the lowest discount
 *    (minimizes revenue loss). Useful for stores with strict margin requirements.
 *
 * 3. **STACK_ALL**: Apply all eligible promotions cumulatively. The total discount
 *    is capped at the line total (cannot exceed 100% of the item/cart value).
 *
 * 4. **PRIORITY_BASED**: Apply the first eligible promotion by creation order
 *    (oldest coupon takes precedence). Only one promotion applies.
 *
 * @see ValidateStoreDiscountLimitUseCase for store-level discount caps.
 */
class ResolvePromotionConflictsUseCase {

    /**
     * Given a list of eligible coupons for a cart/item, returns the resolved coupon(s)
     * to apply based on the [strategy].
     *
     * @param eligibleCoupons All coupons that passed validation (valid dates, min purchase, scope match).
     * @param cartSubtotal The cart or line item subtotal (used to compute actual discount amounts).
     * @param strategy Conflict resolution strategy.
     * @return Ordered list of coupons to apply. Single-element for non-stacking strategies.
     */
    operator fun invoke(
        eligibleCoupons: List<Coupon>,
        cartSubtotal: Double,
        strategy: PromotionConflictStrategy = PromotionConflictStrategy.BEST_FOR_CUSTOMER,
    ): List<ResolvedPromotion> {
        if (eligibleCoupons.isEmpty()) return emptyList()
        if (eligibleCoupons.size == 1) {
            return listOf(toResolvedPromotion(eligibleCoupons.first(), cartSubtotal))
        }

        return when (strategy) {
            PromotionConflictStrategy.BEST_FOR_CUSTOMER -> {
                val best = eligibleCoupons
                    .map { toResolvedPromotion(it, cartSubtotal) }
                    .maxByOrNull { it.effectiveDiscountAmount }
                listOfNotNull(best)
            }

            PromotionConflictStrategy.BEST_FOR_STORE -> {
                val best = eligibleCoupons
                    .map { toResolvedPromotion(it, cartSubtotal) }
                    .minByOrNull { it.effectiveDiscountAmount }
                listOfNotNull(best)
            }

            PromotionConflictStrategy.STACK_ALL -> {
                val all = eligibleCoupons.map { toResolvedPromotion(it, cartSubtotal) }
                // Cap cumulative discount at cart subtotal
                var runningTotal = 0.0
                all.mapNotNull { promo ->
                    if (runningTotal >= cartSubtotal) return@mapNotNull null
                    val cappedAmount = minOf(promo.effectiveDiscountAmount, cartSubtotal - runningTotal)
                    runningTotal += cappedAmount
                    promo.copy(effectiveDiscountAmount = cappedAmount)
                }
            }

            PromotionConflictStrategy.PRIORITY_BASED -> {
                // Oldest coupon (earliest validFrom) takes priority
                val oldest = eligibleCoupons
                    .sortedBy { it.validFrom }
                    .firstOrNull()
                listOfNotNull(oldest?.let { toResolvedPromotion(it, cartSubtotal) })
            }
        }
    }

    private fun toResolvedPromotion(coupon: Coupon, subtotal: Double): ResolvedPromotion {
        val rawDiscount = when (coupon.discountType) {
            DiscountType.FIXED -> coupon.discountValue
            DiscountType.PERCENT -> subtotal * (coupon.discountValue / 100.0)
            DiscountType.BOGO -> 0.0
        }
        // Apply maximum discount cap if set
        val capped = if (coupon.maximumDiscount != null) {
            minOf(rawDiscount, coupon.maximumDiscount)
        } else {
            rawDiscount
        }
        // Never exceed the subtotal
        val effective = minOf(capped, subtotal)

        return ResolvedPromotion(
            couponId = coupon.id,
            couponCode = coupon.code,
            discountType = coupon.discountType,
            discountValue = coupon.discountValue,
            effectiveDiscountAmount = effective,
        )
    }
}

/** Strategy for resolving multiple eligible promotions (C2.4). */
enum class PromotionConflictStrategy {
    /** Apply the single promotion giving the highest discount (customer-friendly). */
    BEST_FOR_CUSTOMER,
    /** Apply the single promotion giving the lowest discount (store-friendly). */
    BEST_FOR_STORE,
    /** Stack all eligible promotions cumulatively (capped at 100% of subtotal). */
    STACK_ALL,
    /** Apply the oldest eligible promotion (by validFrom date). */
    PRIORITY_BASED,
}

/** A coupon selected by the conflict resolution engine with its computed discount. */
data class ResolvedPromotion(
    val couponId: String,
    val couponCode: String,
    val discountType: DiscountType,
    val discountValue: Double,
    val effectiveDiscountAmount: Double,
)
