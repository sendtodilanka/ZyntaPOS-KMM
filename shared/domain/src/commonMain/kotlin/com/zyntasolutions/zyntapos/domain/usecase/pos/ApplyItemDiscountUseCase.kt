package com.zyntasolutions.zyntapos.domain.usecase.pos

import com.zyntasolutions.zyntapos.core.result.Result
import com.zyntasolutions.zyntapos.core.result.ValidationException
import com.zyntasolutions.zyntapos.domain.model.CartItem
import com.zyntasolutions.zyntapos.domain.model.DiscountType
import com.zyntasolutions.zyntapos.domain.model.Permission
import com.zyntasolutions.zyntapos.domain.usecase.auth.CheckPermissionUseCase

/**
 * Applies a discount to a single cart line item.
 *
 * ### Business Rules
 * 1. The requesting user must hold at least [Permission.APPLY_DISCOUNT] (CASHIER+).
 *    Permission is evaluated via [CheckPermissionUseCase]; if denied, a
 *    [ValidationException] with rule `"PERMISSION_DENIED"` is returned.
 * 2. Discount value must be ≥ 0.
 * 3. [DiscountType.PERCENT]: discount cannot exceed 100%.
 * 4. [DiscountType.FIXED]: discount cannot exceed the item's `unitPrice`.
 * 5. The [CartItem.lineTotal] is **not** recalculated here — call
 *    [CalculateOrderTotalsUseCase] after this use case to refresh totals.
 *
 * @param checkPermissionUseCase Role-based access control evaluator.
 */
class ApplyItemDiscountUseCase(
    private val checkPermissionUseCase: CheckPermissionUseCase,
) {
    /**
     * @param currentCart  The caller's current list of [CartItem]s.
     * @param productId    The product line to discount.
     * @param discount     Discount value (≥ 0).
     * @param discountType [DiscountType.PERCENT] or [DiscountType.FIXED].
     * @param userId       The cashier/manager requesting the discount.
     * @return [Result.Success] with updated cart, or [Result.Error] on violation.
     */
    operator fun invoke(
        currentCart: List<CartItem>,
        productId: String,
        discount: Double,
        discountType: DiscountType,
        userId: String,
    ): Result<List<CartItem>> {
        val hasPermission = checkPermissionUseCase(userId, Permission.APPLY_DISCOUNT)
        if (!hasPermission) {
            return Result.Error(
                ValidationException(
                    message = "User '$userId' does not have permission to apply discounts.",
                    field = "userId",
                    rule = "PERMISSION_DENIED",
                ),
            )
        }

        if (discount < 0.0) {
            return Result.Error(
                ValidationException("Discount must be ≥ 0.", field = "discount", rule = "MIN_VALUE"),
            )
        }

        val item = currentCart.firstOrNull { it.productId == productId }
            ?: return Result.Error(
                ValidationException(
                    "Product '$productId' not found in cart.",
                    field = "productId",
                    rule = "NOT_IN_CART",
                ),
            )

        when (discountType) {
            DiscountType.PERCENT -> if (discount > 100.0) {
                return Result.Error(
                    ValidationException(
                        "Percent discount cannot exceed 100%.",
                        field = "discount",
                        rule = "MAX_PCT_EXCEEDED",
                    ),
                )
            }
            DiscountType.FIXED -> if (discount > item.unitPrice) {
                return Result.Error(
                    ValidationException(
                        "Fixed discount ($discount) exceeds unit price (${item.unitPrice}).",
                        field = "discount",
                        rule = "DISCOUNT_EXCEEDS_PRICE",
                    ),
                )
            }
            DiscountType.BOGO -> { /* BOGO is applied at the cart level; no per-item validation needed */ }
        }

        val updatedCart = currentCart.map {
            if (it.productId == productId) it.copy(discount = discount, discountType = discountType) else it
        }
        return Result.Success(updatedCart)
    }
}
