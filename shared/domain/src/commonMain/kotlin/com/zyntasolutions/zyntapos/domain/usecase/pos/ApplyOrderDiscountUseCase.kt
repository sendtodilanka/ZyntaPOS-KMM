package com.zyntasolutions.zyntapos.domain.usecase.pos

import com.zyntasolutions.zyntapos.core.result.Result
import com.zyntasolutions.zyntapos.core.result.ValidationException
import com.zyntasolutions.zyntapos.domain.model.CartItem
import com.zyntasolutions.zyntapos.domain.model.DiscountType
import com.zyntasolutions.zyntapos.domain.model.OrderTotals
import com.zyntasolutions.zyntapos.domain.repository.SettingsRepository

/**
 * Applies an order-level discount and returns recalculated [OrderTotals].
 *
 * ### Business Rules
 * 1. Discount value must be ≥ 0.
 * 2. The maximum allowable discount percentage is read dynamically from
 *    [SettingsRepository] via key `"pos.max_order_discount_pct"`.
 *    Defaults to **20%** if the setting is absent or unparseable.
 * 3. For [DiscountType.PERCENT]: discount cannot exceed the configured max %.
 * 4. For [DiscountType.FIXED]: discount cannot exceed the order subtotal.
 * 5. Recalculates and returns [OrderTotals] after applying the discount.
 *
 * @param settingsRepository Provides runtime POS configuration values.
 * @param calculateOrderTotalsUseCase Delegate for total recalculation.
 */
class ApplyOrderDiscountUseCase(
    private val settingsRepository: SettingsRepository,
    private val calculateOrderTotalsUseCase: CalculateOrderTotalsUseCase,
) {
    /**
     * @param items        Current cart items.
     * @param discount     The discount value to apply.
     * @param discountType [DiscountType.PERCENT] or [DiscountType.FIXED].
     * @return [Result.Success] with updated [OrderTotals], or [Result.Error] on violation.
     */
    suspend operator fun invoke(
        items: List<CartItem>,
        discount: Double,
        discountType: DiscountType,
    ): Result<OrderTotals> {
        if (discount < 0.0) {
            return Result.Error(
                ValidationException("Discount must be ≥ 0.", field = "discount", rule = "MIN_VALUE"),
            )
        }

        val maxPct = settingsRepository.get("pos.max_order_discount_pct")
            ?.toDoubleOrNull() ?: DEFAULT_MAX_DISCOUNT_PCT

        val subtotal = items.sumOf { it.unitPrice * it.quantity }

        when (discountType) {
            DiscountType.PERCENT -> {
                if (discount > maxPct) {
                    return Result.Error(
                        ValidationException(
                            message = "Order discount ($discount%) exceeds maximum allowed ($maxPct%).",
                            field = "discount",
                            rule = "MAX_DISCOUNT_EXCEEDED",
                        ),
                    )
                }
            }
            DiscountType.FIXED -> {
                if (discount > subtotal) {
                    return Result.Error(
                        ValidationException(
                            message = "Fixed discount ($discount) exceeds order subtotal ($subtotal).",
                            field = "discount",
                            rule = "DISCOUNT_EXCEEDS_SUBTOTAL",
                        ),
                    )
                }
            }
            DiscountType.BOGO -> { /* BOGO promotions are resolved at line-item level; no order-level cap needed */ }
        }

        return calculateOrderTotalsUseCase(items, discount, discountType)
    }

    companion object {
        const val DEFAULT_MAX_DISCOUNT_PCT = 20.0
        const val SETTING_KEY = "pos.max_order_discount_pct"
    }
}
