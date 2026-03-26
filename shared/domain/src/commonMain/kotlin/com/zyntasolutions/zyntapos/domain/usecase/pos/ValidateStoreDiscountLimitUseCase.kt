package com.zyntasolutions.zyntapos.domain.usecase.pos

import com.zyntasolutions.zyntapos.domain.model.DiscountType
import com.zyntasolutions.zyntapos.domain.model.Store

/**
 * Validates that a discount does not exceed the store's configured limits (C2.4).
 *
 * Each store can configure:
 * - [Store.maxDiscountPercent]: Max percentage discount (e.g., 20% at Store A, 30% at Store B)
 * - [Store.maxDiscountAmount]: Max fixed discount amount
 *
 * Returns a [DiscountValidationResult] indicating whether the discount is allowed
 * and the capped value if it exceeds the limit.
 */
class ValidateStoreDiscountLimitUseCase {

    operator fun invoke(
        store: Store,
        discountValue: Double,
        discountType: DiscountType,
        lineTotal: Double = 0.0,
    ): DiscountValidationResult {
        return when (discountType) {
            DiscountType.PERCENT -> validatePercentDiscount(store, discountValue)
            DiscountType.FIXED -> validateFixedDiscount(store, discountValue)
            DiscountType.BOGO -> DiscountValidationResult.Allowed(discountValue)
        }
    }

    private fun validatePercentDiscount(
        store: Store,
        percentValue: Double,
    ): DiscountValidationResult {
        val maxPercent = store.maxDiscountPercent ?: return DiscountValidationResult.Allowed(percentValue)
        return if (percentValue <= maxPercent) {
            DiscountValidationResult.Allowed(percentValue)
        } else {
            DiscountValidationResult.ExceedsLimit(
                requestedValue = percentValue,
                cappedValue = maxPercent,
                limitType = "percentage",
                limitValue = maxPercent,
            )
        }
    }

    private fun validateFixedDiscount(
        store: Store,
        fixedValue: Double,
    ): DiscountValidationResult {
        val maxAmount = store.maxDiscountAmount ?: return DiscountValidationResult.Allowed(fixedValue)
        return if (fixedValue <= maxAmount) {
            DiscountValidationResult.Allowed(fixedValue)
        } else {
            DiscountValidationResult.ExceedsLimit(
                requestedValue = fixedValue,
                cappedValue = maxAmount,
                limitType = "amount",
                limitValue = maxAmount,
            )
        }
    }
}

/** Result of store discount limit validation (C2.4). */
sealed class DiscountValidationResult {
    /** Discount is within the store's configured limit. */
    data class Allowed(val value: Double) : DiscountValidationResult()

    /** Discount exceeds the store's limit; [cappedValue] is the maximum allowed. */
    data class ExceedsLimit(
        val requestedValue: Double,
        val cappedValue: Double,
        val limitType: String,
        val limitValue: Double,
    ) : DiscountValidationResult()
}
