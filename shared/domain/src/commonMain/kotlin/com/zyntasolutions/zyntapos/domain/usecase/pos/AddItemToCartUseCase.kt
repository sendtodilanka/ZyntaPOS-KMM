package com.zyntasolutions.zyntapos.domain.usecase.pos

import com.zyntasolutions.zyntapos.core.result.Result
import com.zyntasolutions.zyntapos.core.result.ValidationException
import com.zyntasolutions.zyntapos.domain.model.CartItem
import com.zyntasolutions.zyntapos.domain.model.DiscountType
import com.zyntasolutions.zyntapos.domain.model.Product
import com.zyntasolutions.zyntapos.domain.repository.ProductRepository
import com.zyntasolutions.zyntapos.domain.repository.TaxGroupRepository
import com.zyntasolutions.zyntapos.domain.usecase.inventory.GetEffectiveProductPriceUseCase
import kotlin.time.Clock

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
 * 6. Price is resolved via [GetEffectiveProductPriceUseCase] which checks:
 *    store override → pricing rule → master product base price → product.price.
 *
 * @param productRepository Source of truth for real-time stock levels.
 * @param getEffectivePrice Resolves the store-aware price (C2.1 region-based pricing).
 * @param taxGroupRepository Looks up TaxGroup by ID for tax rate resolution (C2.3).
 * @param getEffectiveTaxRate Resolves regional tax overrides per store (C2.3).
 */
class AddItemToCartUseCase(
    private val productRepository: ProductRepository,
    private val getEffectivePrice: GetEffectiveProductPriceUseCase? = null,
    private val taxGroupRepository: TaxGroupRepository? = null,
    private val getEffectiveTaxRate: GetEffectiveTaxRateUseCase? = null,
) {
    /**
     * @param currentCart The caller's current list of [CartItem]s (may be empty).
     * @param productId   The [Product] to add.
     * @param quantity    Number of units to add (must be > 0).
     * @param storeId     The current store context for price resolution (C2.1).
     *                    When non-blank, [getEffectivePrice] resolves the store-aware price.
     * @return [Result.Success] with the updated cart list, or
     *         [Result.Error] wrapping a [ValidationException] if validation fails.
     */
    suspend operator fun invoke(
        currentCart: List<CartItem>,
        productId: String,
        quantity: Double = 1.0,
        storeId: String = "",
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

                // Resolve effective price: store override → pricing rule → master → fallback
                val effectivePrice = if (getEffectivePrice != null && storeId.isNotBlank()) {
                    getEffectivePrice(product, storeId)
                } else {
                    product.price
                }

                // Resolve tax rate from product's TaxGroup + regional override (C2.3)
                val (effectiveTaxRate, taxInclusive) = resolveTaxRate(product, storeId)

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
                        unitPrice = effectivePrice,
                        quantity = quantity,
                        discountType = DiscountType.FIXED,
                        taxRate = effectiveTaxRate,
                        isTaxInclusive = taxInclusive,
                    )
                }

                Result.Success(updatedCart)
            }
        }
    }

    /**
     * Resolves the effective tax rate and inclusive flag for a product at a store.
     *
     * Resolution: Product.taxGroupId → TaxGroup → GetEffectiveTaxRateUseCase (regional override)
     * Falls back to (0.0, false) if no TaxGroup is assigned or repos are not injected.
     */
    private suspend fun resolveTaxRate(product: Product, storeId: String): Pair<Double, Boolean> {
        val taxGroupId = product.taxGroupId ?: return 0.0 to false
        val taxGroupRepo = taxGroupRepository ?: return 0.0 to false

        val taxGroup = when (val result = taxGroupRepo.getById(taxGroupId)) {
            is Result.Success -> result.data
            else -> return 0.0 to false
        }

        if (!taxGroup.isActive) return 0.0 to false

        val effectiveRate = if (getEffectiveTaxRate != null && storeId.isNotBlank()) {
            getEffectiveTaxRate.invoke(
                taxGroup = taxGroup,
                storeId = storeId,
                nowEpochMs = Clock.System.now().toEpochMilliseconds(),
            )
        } else {
            taxGroup.rate
        }

        return effectiveRate to taxGroup.isInclusive
    }
}
