package com.zyntasolutions.zyntapos.domain.usecase.pos

import com.zyntasolutions.zyntapos.core.result.Result
import com.zyntasolutions.zyntapos.core.result.ValidationException
import com.zyntasolutions.zyntapos.domain.model.CartItem
import com.zyntasolutions.zyntapos.domain.repository.ProductRepository

/**
 * Updates the quantity of an existing cart line item.
 *
 * ### Business Rules
 * 1. Minimum quantity is **1** — attempting to set quantity < 1 returns a
 *    [ValidationException] with rule `"MIN_QTY"`.
 * 2. The new quantity must not exceed `product.stockQty`.
 * 3. If [productId] is not found in [currentCart], a [ValidationException] with
 *    rule `"NOT_IN_CART"` is returned.
 *
 * @param productRepository Source of truth for real-time stock levels.
 */
class UpdateCartItemQuantityUseCase(
    private val productRepository: ProductRepository,
) {
    /**
     * @param currentCart The caller's current list of [CartItem]s.
     * @param productId   Product whose quantity should be updated.
     * @param newQuantity The desired quantity (must be ≥ 1).
     * @return [Result.Success] with the updated cart, or [Result.Error] on violation.
     */
    suspend operator fun invoke(
        currentCart: List<CartItem>,
        productId: String,
        newQuantity: Double,
    ): Result<List<CartItem>> {
        if (newQuantity < 1.0) {
            return Result.Error(
                ValidationException(
                    message = "Quantity must be at least 1, got $newQuantity.",
                    field = "quantity",
                    rule = "MIN_QTY",
                ),
            )
        }

        if (currentCart.none { it.productId == productId }) {
            return Result.Error(
                ValidationException(
                    message = "Product '$productId' is not in the current cart.",
                    field = "productId",
                    rule = "NOT_IN_CART",
                ),
            )
        }

        return when (val productResult = productRepository.getById(productId)) {
            is Result.Error -> productResult
            is Result.Loading -> Result.Loading
            is Result.Success -> {
                val product = productResult.data
                if (product.stockQty < newQuantity) {
                    return Result.Error(
                        ValidationException(
                            message = "Cannot set quantity to $newQuantity — only " +
                                "${product.stockQty} units of '${product.name}' in stock.",
                            field = "quantity",
                            rule = "EXCEEDS_STOCK",
                        ),
                    )
                }
                val updatedCart = currentCart.map { item ->
                    if (item.productId == productId) item.copy(quantity = newQuantity) else item
                }
                Result.Success(updatedCart)
            }
        }
    }
}
