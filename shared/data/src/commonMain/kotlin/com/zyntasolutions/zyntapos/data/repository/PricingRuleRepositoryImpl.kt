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
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.time.Clock

/**
 * SQLDelight-backed implementation of [PricingRuleRepository].
 *
 * Pricing rules are synced from the backend (read-only on POS devices for most operations).
 * Admin writes go through the backend API and sync down to devices.
 */
class PricingRuleRepositoryImpl(
    private val db: ZyntaDatabase,
) : PricingRuleRepository {

    @Serializable
    private data class PricingRuleSyncPayload(
        val id: String,
        @SerialName("product_id")  val productId: String,
        @SerialName("store_id")    val storeId: String? = null,
        val price: Double,
        @SerialName("cost_price")  val costPrice: Double? = null,
        val priority: Int = 0,
        @SerialName("valid_from")  val validFrom: Long? = null,
        @SerialName("valid_to")    val validTo: Long? = null,
        @SerialName("is_active")   val isActive: Boolean = true,
        val description: String = "",
        @SerialName("updated_at")  val updatedAt: Long = 0L,
    )

    companion object {
        private val syncJson = Json { ignoreUnknownKeys = true; isLenient = true }
    }

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

    /**
     * Applies a server-authoritative pricing rule snapshot from a sync delta payload.
     * Uses INSERT OR REPLACE for idempotent upsert with sync_status = SYNCED.
     * Does NOT enqueue a SyncOperation — server data must not be re-pushed.
     */
    suspend fun upsertFromSync(payload: String) = withContext(Dispatchers.IO) {
        val dto = syncJson.decodeFromString<PricingRuleSyncPayload>(payload)
        val isActive = if (dto.isActive) 1L else 0L
        val now = Clock.System.now().toEpochMilliseconds()
        q.insertRule(
            id = dto.id,
            product_id = dto.productId,
            store_id = dto.storeId,
            price = dto.price,
            cost_price = dto.costPrice,
            priority = dto.priority.toLong(),
            valid_from = dto.validFrom,
            valid_to = dto.validTo,
            is_active = isActive,
            description = dto.description,
            created_at = dto.updatedAt.takeIf { it > 0 } ?: now,
            updated_at = dto.updatedAt,
            sync_status = "SYNCED",
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
