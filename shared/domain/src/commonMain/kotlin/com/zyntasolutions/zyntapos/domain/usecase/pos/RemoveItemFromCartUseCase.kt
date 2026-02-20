package com.zyntasolutions.zyntapos.domain.usecase.pos

import com.zyntasolutions.zyntapos.core.result.Result
import com.zyntasolutions.zyntapos.core.result.ValidationException
import com.zyntasolutions.zyntapos.domain.model.CartItem

/**
 * Removes a product line from the active cart by [productId].
 *
 * ### Business Rules
 * 1. If [productId] does not match any item in [currentCart], the cart is returned
 *    unchanged (idempotent operation — not treated as an error).
 *
 * @param currentCart The caller's current list of [CartItem]s.
 * @param productId   The product to remove from the cart.
 * @return [Result.Success] with the updated cart (item removed), or
 *         [Result.Error] if [productId] is blank.
 */
class RemoveItemFromCartUseCase {
    operator fun invoke(
        currentCart: List<CartItem>,
        productId: String,
    ): Result<List<CartItem>> {
        if (productId.isBlank()) {
            return Result.Error(
                ValidationException(
                    message = "productId must not be blank.",
                    field = "productId",
                    rule = "REQUIRED",
                ),
            )
        }
        return Result.Success(currentCart.filter { it.productId != productId })
    }
}
