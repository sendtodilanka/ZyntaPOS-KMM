package com.zyntasolutions.zyntapos.domain.usecase.inventory

import com.zyntasolutions.zyntapos.core.result.Result
import com.zyntasolutions.zyntapos.domain.model.Product
import com.zyntasolutions.zyntapos.domain.repository.MasterProductRepository
import com.zyntasolutions.zyntapos.domain.repository.PricingRuleRepository
import com.zyntasolutions.zyntapos.domain.repository.StoreProductOverrideRepository
import kotlin.time.Clock

/**
 * Resolves the effective selling price for a product.
 *
 * Price resolution order (C2.1 — region-based pricing):
 * 1. Store product override (`localPrice`) — highest priority
 * 2. Pricing rule — active, highest-priority rule for this product+store (time-bounded)
 * 3. Master product base price — global default
 * 4. `Product.price` — legacy fallback for store-local products
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
     */
    suspend operator fun invoke(
        product: Product,
        storeId: String,
        nowEpochMs: Long = Clock.System.now().toEpochMilliseconds(),
    ): Double {
        // 1. Store-specific override via store_products (highest priority)
        val masterProductId = product.masterProductId
        if (masterProductId != null) {
            val overrideResult = storeProductOverrideRepository.getOverride(masterProductId, storeId)
            if (overrideResult is Result.Success) {
                val localPrice = overrideResult.data.localPrice
                if (localPrice != null) return localPrice
            }
        }

        // 2. Pricing rule (store-specific or global, time-bounded)
        val ruleResult = pricingRuleRepository.getEffectiveRule(product.id, storeId, nowEpochMs)
        if (ruleResult is Result.Success) {
            val rule = ruleResult.data
            if (rule != null) return rule.price
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
