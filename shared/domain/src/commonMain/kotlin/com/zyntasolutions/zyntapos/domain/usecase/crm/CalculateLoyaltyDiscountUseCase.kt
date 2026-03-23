package com.zyntasolutions.zyntapos.domain.usecase.crm

/**
 * Converts loyalty points to a monetary discount value.
 *
 * The conversion rate is configurable (default: 100 points = 1 currency unit).
 * Ensures the discount never exceeds the order total.
 */
class CalculateLoyaltyDiscountUseCase {

    /**
     * @param pointsToRedeem Number of loyalty points the customer wants to redeem.
     * @param orderTotal Current order total (used as ceiling).
     * @param pointsPerCurrencyUnit How many points equal 1 currency unit (default 100).
     * @return Monetary discount amount, capped at [orderTotal].
     */
    operator fun invoke(
        pointsToRedeem: Int,
        orderTotal: Double,
        pointsPerCurrencyUnit: Int = DEFAULT_POINTS_PER_CURRENCY_UNIT,
    ): Double {
        if (pointsToRedeem <= 0 || orderTotal <= 0.0 || pointsPerCurrencyUnit <= 0) return 0.0
        val rawDiscount = pointsToRedeem.toDouble() / pointsPerCurrencyUnit
        return rawDiscount.coerceAtMost(orderTotal)
    }

    companion object {
        /** Default conversion: 100 loyalty points = 1 currency unit. */
        const val DEFAULT_POINTS_PER_CURRENCY_UNIT = 100
    }
}
