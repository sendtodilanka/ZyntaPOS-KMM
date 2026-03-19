package com.zyntasolutions.zyntapos.domain.usecase.inventory

import com.zyntasolutions.zyntapos.core.result.Result
import com.zyntasolutions.zyntapos.domain.model.MasterProduct
import com.zyntasolutions.zyntapos.domain.model.Product
import com.zyntasolutions.zyntapos.domain.model.StoreProductOverride
import com.zyntasolutions.zyntapos.domain.repository.MasterProductRepository
import com.zyntasolutions.zyntapos.domain.repository.StoreProductOverrideRepository

/**
 * Resolves the effective selling price for a product.
 *
 * Price resolution order:
 * 1. [StoreProductOverride.localPrice] (store-specific override) — if non-null
 * 2. [MasterProduct.basePrice] (global default) — if product is linked to a master product
 * 3. [Product.price] (legacy per-store price) — fallback for store-local products
 */
class GetEffectiveProductPriceUseCase(
    private val masterProductRepository: MasterProductRepository,
    private val storeProductOverrideRepository: StoreProductOverrideRepository,
) {
    suspend operator fun invoke(product: Product, storeId: String): Double {
        val masterProductId = product.masterProductId ?: return product.price

        // Check for store-specific override
        val overrideResult = storeProductOverrideRepository.getOverride(masterProductId, storeId)
        if (overrideResult is Result.Success) {
            val override = overrideResult.data
            if (override.localPrice != null) return override.localPrice
        }

        // Fall back to master product base price
        val masterResult = masterProductRepository.getById(masterProductId)
        if (masterResult is Result.Success) {
            return masterResult.data.basePrice
        }

        // Final fallback to product's own price
        return product.price
    }
}
