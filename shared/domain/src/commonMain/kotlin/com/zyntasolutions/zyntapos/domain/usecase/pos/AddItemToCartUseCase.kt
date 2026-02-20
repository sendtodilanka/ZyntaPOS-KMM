package com.zyntasolutions.zyntapos.domain.usecase.pos

import com.zyntasolutions.zyntapos.core.result.Result
import com.zyntasolutions.zyntapos.core.result.ValidationException
import com.zyntasolutions.zyntapos.domain.model.CartItem
import com.zyntasolutions.zyntapos.domain.model.DiscountType
import com.zyntasolutions.zyntapos.domain.model.Product
import com.zyntasolutions.zyntapos.domain.repository.ProductRepository

/**
 * Adds a product to the active cart, validating stock availability and applying
 * unit conversion where applicable.
 *
 * ### Business Rules
 * 1. The product must exist and be active (`product.isActive == true`).
 * 2. Requested quantity must be > 0.
 * 3. `product.stockQty` must be ≥ requested quantity; otherwise a
 *    [ValidationException] with rule `"OUT_OF_STOCK"` is returned.
 * 4. If the product already exists in the cart, quantities are summed and
 *    the combined quantity is re-validated against stock.
 * 5. Unit conversion: `quantity` is expected in the product's base unit.
 *    Multi-unit conversion (e.g., dozen → each) is applied when
 *    `product.unitId` differs from the cart unit (future extension point).
 *
 * @param productRepository Source of truth for real-time stock levels.
 */
class AddItemToCartUseCase(
    private val productRepository: ProductRepository,
) {
    /**
     * @param currentCart The caller's current list of [CartItem]s (may be empty).
     * @param productId   The [Product] to add.
     * @param quantity    Number of units to add (must be > 0).
     * @return [Result.Success] with the updated cart list, or
     *         [Result.Error] wrapping a [ValidationException] if validation fails.
     */
    suspend operator fun invoke(
        currentCart: List<CartItem>,
        productId: String,
        quantity: Double = 1.0,
    ): Result<List<CartItem>> {
        if (quantity <= 0.0) {
            return Result.Error(
                ValidationException(
                    message = "Quantity must be greater than zero.",
                    field = "quantity",
                    rule = "MIN_VALUE",
                ),
            )
        }

        return when (val productResult = productRepository.getById(productId)) {
            is Result.Error -> productResult
            is Result.Loading -> Result.Loading
            is Result.Success -> {
                val product = productResult.data

                if (!product.isActive) {
                    return Result.Error(
                        ValidationException(
                            message = "Product '${product.name}' is not active.",
                            field = "productId",
                            rule = "PRODUCT_INACTIVE",
                        ),
                    )
                }

                // Calculate combined quantity (existing + new)
                val existingItem = currentCart.firstOrNull { it.productId == productId }
                val newTotalQty = (existingItem?.quantity ?: 0.0) + quantity

                if (product.stockQty < newTotalQty) {
                    return Result.Error(
                        ValidationException(
                            message = "Insufficient stock for '${product.name}'. " +
                                "Available: ${product.stockQty}, requested total: $newTotalQty.",
                            field = "quantity",
                            rule = "OUT_OF_STOCK",
                        ),
                    )
                }

                val updatedCart = if (existingItem != null) {
                    currentCart.map { item ->
                        if (item.productId == productId) {
                            item.copy(quantity = newTotalQty)
                        } else {
                            item
                        }
                    }
                } else {
                    currentCart + CartItem(
                        productId = product.id,
                        productName = product.name,
                        unitPrice = product.price,
                        quantity = quantity,
                        discountType = DiscountType.FIXED,
                        taxRate = 0.0, // Resolved by CalculateOrderTotalsUseCase via TaxGroup
                    )
                }

                Result.Success(updatedCart)
            }
        }
    }
}
