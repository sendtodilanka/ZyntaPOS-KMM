package com.zyntasolutions.zyntapos.domain.repository

import com.zyntasolutions.zyntapos.core.result.Result
import com.zyntasolutions.zyntapos.domain.model.PricingRule
import kotlinx.coroutines.flow.Flow

/**
 * Contract for pricing rule operations.
 *
 * Pricing rules enable store-specific and time-bounded price overrides
 * for products. Rules are synced from the backend (read-only on POS devices).
 */
interface PricingRuleRepository {

    /** Emits all active pricing rules for a given product at a specific store. */
    fun getActiveRulesForProduct(productId: String, storeId: String): Flow<List<PricingRule>>

    /** Returns the highest-priority active rule for a product at a store, or null if none match. */
    suspend fun getEffectiveRule(productId: String, storeId: String, nowEpochMs: Long): Result<PricingRule?>

    /** Emits all pricing rules (active + inactive) for admin management. */
    fun getAllRules(): Flow<List<PricingRule>>

    /** Emits all rules for a specific product across all stores. */
    fun getRulesForProduct(productId: String): Flow<List<PricingRule>>

    /** Upserts a pricing rule from sync or admin action. */
    suspend fun upsert(rule: PricingRule): Result<Unit>

    /** Deletes a pricing rule by ID. */
    suspend fun delete(ruleId: String): Result<Unit>
}
