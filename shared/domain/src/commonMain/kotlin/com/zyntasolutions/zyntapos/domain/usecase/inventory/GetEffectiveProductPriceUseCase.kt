package com.zyntasolutions.zyntapos.domain.usecase.inventory

import com.zyntasolutions.zyntapos.core.result.Result
import com.zyntasolutions.zyntapos.domain.model.MasterProduct
import com.zyntasolutions.zyntapos.domain.model.PricingRule
import com.zyntasolutions.zyntapos.domain.model.Product
import com.zyntasolutions.zyntapos.domain.model.StoreProductOverride
import com.zyntasolutions.zyntapos.domain.repository.MasterProductRepository
import com.zyntasolutions.zyntapos.domain.repository.PricingRuleRepository
import com.zyntasolutions.zyntapos.domain.repository.StoreProductOverrideRepository

/**
 * Resolves the effective selling price for a product.
 *
 * Price resolution order (C2.1 — region-based pricing):
 * 1. [StoreProductOverride.localPrice] (store-specific override) — highest priority
 * 2. [PricingRule] — active, highest-priority rule for this product+store — time-bounded
 * 3. [MasterProduct.basePrice] (global default) — if product is linked to a master product
 * 4. [Product.price] (legacy per-store price) — fallback for store-local products
 */
class GetEffectiveProductPriceUseCase(
    private val masterProductRepository: MasterProductRepository,
    private val storeProductOverrideRepository: StoreProductOverrideRepository,
    private val pricingRuleRepository: PricingRuleRepository,
) {
    /**
     * @param product The product to resolve pricing for.
     * @param storeId The store context.
     * @param nowEpochMs Current time in epoch milliseconds for time-bounded rule evaluation.
     *                   Defaults to [kotlinx.datetime.Clock.System.now] if not specified.
     */
    suspend operator fun invoke(
        product: Product,
        storeId: String,
        nowEpochMs: Long = kotlinx.datetime.Clock.System.now().toEpochMilliseconds(),
    ): Double {
        // 1. Store-specific override via store_products (highest priority)
        val masterProductId = product.masterProductId
        if (masterProductId != null) {
            val overrideResult = storeProductOverrideRepository.getOverride(masterProductId, storeId)
            if (overrideResult is Result.Success && overrideResult.data.localPrice != null) {
                return overrideResult.data.localPrice
            }
        }

        // 2. Pricing rule (store-specific or global, time-bounded)
        val ruleResult = pricingRuleRepository.getEffectiveRule(product.id, storeId, nowEpochMs)
        if (ruleResult is Result.Success && ruleResult.data != null) {
            return ruleResult.data.price
        }

        // 3. Master product base price
        if (masterProductId != null) {
            val masterResult = masterProductRepository.getById(masterProductId)
            if (masterResult is Result.Success) {
                return masterResult.data.basePrice
            }
        }

        // 4. Final fallback to product's own price
        return product.price
    }
}
