package com.zyntasolutions.zyntapos.data.repository

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import com.zyntasolutions.zyntapos.core.result.DatabaseException
import com.zyntasolutions.zyntapos.core.result.Result
import com.zyntasolutions.zyntapos.db.Pricing_rules
import com.zyntasolutions.zyntapos.db.ZyntaDatabase
import com.zyntasolutions.zyntapos.domain.model.PricingRule
import com.zyntasolutions.zyntapos.domain.repository.PricingRuleRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/**
 * SQLDelight-backed implementation of [PricingRuleRepository].
 *
 * Pricing rules are synced from the backend (read-only on POS devices for most operations).
 * Admin writes go through the backend API and sync down to devices.
 */
class PricingRuleRepositoryImpl(
    private val db: ZyntaDatabase,
) : PricingRuleRepository {

    private val q get() = db.pricing_rulesQueries

    override fun getActiveRulesForProduct(productId: String, storeId: String): Flow<List<PricingRule>> =
        q.getActiveRulesForProduct(productId, storeId)
            .asFlow()
            .mapToList(Dispatchers.IO)
            .map { rows -> rows.map(::toDomain) }

    override suspend fun getEffectiveRule(
        productId: String,
        storeId: String,
        nowEpochMs: Long,
    ): Result<PricingRule?> = withContext(Dispatchers.IO) {
        runCatching {
            q.getEffectiveRule(productId, storeId, nowEpochMs, nowEpochMs)
                .executeAsOneOrNull()
        }.fold(
            onSuccess = { row -> Result.Success(row?.let(::toDomain)) },
            onFailure = { t -> Result.Error(DatabaseException(t.message ?: "DB error", cause = t)) },
        )
    }

    override fun getAllRules(): Flow<List<PricingRule>> =
        q.getAllRules()
            .asFlow()
            .mapToList(Dispatchers.IO)
            .map { rows -> rows.map(::toDomain) }

    override fun getRulesForProduct(productId: String): Flow<List<PricingRule>> =
        q.getRulesForProduct(productId)
            .asFlow()
            .mapToList(Dispatchers.IO)
            .map { rows -> rows.map(::toDomain) }

    override suspend fun upsert(rule: PricingRule): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            q.insertRule(
                id = rule.id,
                product_id = rule.productId,
                store_id = rule.storeId,
                price = rule.price,
                cost_price = rule.costPrice,
                priority = rule.priority.toLong(),
                valid_from = rule.validFrom,
                valid_to = rule.validTo,
                is_active = if (rule.isActive) 1L else 0L,
                description = rule.description,
                created_at = rule.createdAt,
                updated_at = rule.updatedAt,
                sync_status = "SYNCED",
            )
        }.fold(
            onSuccess = { Result.Success(Unit) },
            onFailure = { t -> Result.Error(DatabaseException(t.message ?: "DB error", cause = t)) },
        )
    }

    override suspend fun delete(ruleId: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching { q.deleteRule(ruleId) }.fold(
            onSuccess = { Result.Success(Unit) },
            onFailure = { t -> Result.Error(DatabaseException(t.message ?: "DB error", cause = t)) },
        )
    }

    private fun toDomain(row: Pricing_rules): PricingRule = PricingRule(
        id = row.id,
        productId = row.product_id,
        storeId = row.store_id,
        price = row.price,
        costPrice = row.cost_price,
        priority = row.priority.toInt(),
        validFrom = row.valid_from,
        validTo = row.valid_to,
        isActive = row.is_active == 1L,
        description = row.description,
        createdAt = row.created_at,
        updatedAt = row.updated_at,
    )
}
