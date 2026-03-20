package com.zyntasolutions.zyntapos.domain.repository

import com.zyntasolutions.zyntapos.core.result.Result
import com.zyntasolutions.zyntapos.domain.model.ReplenishmentRule
import kotlinx.coroutines.flow.Flow

/**
 * Contract for warehouse-to-store replenishment rule management (C1.5).
 *
 * Rules define per-product thresholds and auto-PO configuration.
 * The [AutoReplenishmentUseCase] evaluates active rules to generate
 * [PurchaseOrder] records when stock falls at or below the [ReplenishmentRule.reorderPoint].
 */
interface ReplenishmentRuleRepository {

    /** Emits all replenishment rules with joined product/warehouse/supplier names. */
    fun getAll(): Flow<List<ReplenishmentRule>>

    /** Emits all active rules for a given warehouse. */
    fun getByWarehouse(warehouseId: String): Flow<List<ReplenishmentRule>>

    /** Returns active rules whose [autoApprove] flag is true — used by the auto-PO job. */
    suspend fun getAutoApproveRules(): Result<List<ReplenishmentRule>>

    /** Returns the rule for a specific product-warehouse combination, if any. */
    suspend fun getByProductAndWarehouse(productId: String, warehouseId: String): Result<ReplenishmentRule?>

    /** Inserts or updates a replenishment rule. */
    suspend fun upsert(rule: ReplenishmentRule): Result<Unit>

    /** Deletes the rule with [id]. */
    suspend fun delete(id: String): Result<Unit>
}
