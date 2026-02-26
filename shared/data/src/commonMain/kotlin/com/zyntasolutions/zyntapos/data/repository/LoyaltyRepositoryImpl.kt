package com.zyntasolutions.zyntapos.data.repository

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import com.zyntasolutions.zyntapos.core.result.DatabaseException
import com.zyntasolutions.zyntapos.core.result.Result
import com.zyntasolutions.zyntapos.db.Loyalty_tiers
import com.zyntasolutions.zyntapos.db.Reward_points
import com.zyntasolutions.zyntapos.db.ZyntaDatabase
import com.zyntasolutions.zyntapos.domain.model.LoyaltyTier
import com.zyntasolutions.zyntapos.domain.model.RewardPoints
import com.zyntasolutions.zyntapos.domain.model.SyncOperation
import com.zyntasolutions.zyntapos.domain.repository.LoyaltyRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlin.time.Clock
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive

class LoyaltyRepositoryImpl(
    private val db: ZyntaDatabase,
) : LoyaltyRepository {

    private val rpq get() = db.reward_pointsQueries
    private val ltq get() = db.reward_pointsQueries

    override fun getPointsHistory(customerId: String): Flow<List<RewardPoints>> =
        rpq.getPointsByCustomer(customerId)
            .asFlow()
            .mapToList(Dispatchers.IO)
            .map { rows -> rows.map(::toPointsDomain) }

    override suspend fun getBalance(customerId: String): Result<Int> = withContext(Dispatchers.IO) {
        runCatching {
            rpq.getPointsBalanceForCustomer(customerId).executeAsOne().toInt()
        }.fold(
            onSuccess = { Result.Success(it) },
            onFailure = { t -> Result.Error(DatabaseException(t.message ?: "DB error", cause = t)) },
        )
    }

    override suspend fun recordPoints(entry: RewardPoints): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            rpq.insertRewardPoints(
                id = entry.id,
                customer_id = entry.customerId,
                points = entry.points.toLong(),
                balance_after = entry.balanceAfter.toLong(),
                type = entry.type.name,
                reference_type = entry.referenceType,
                reference_id = entry.referenceId,
                note = entry.note,
                expires_at = entry.expiresAt,
                created_at = entry.createdAt,
                sync_status = "PENDING",
            )
        }.fold(
            onSuccess = { Result.Success(Unit) },
            onFailure = { t -> Result.Error(DatabaseException(t.message ?: "Insert failed", cause = t)) },
        )
    }

    override fun getAllTiers(): Flow<List<LoyaltyTier>> =
        ltq.getAllLoyaltyTiers()
            .asFlow()
            .mapToList(Dispatchers.IO)
            .map { rows -> rows.map(::toTierDomain) }

    override suspend fun getTierForPoints(points: Int): Result<LoyaltyTier?> = withContext(Dispatchers.IO) {
        runCatching {
            ltq.getTierForPoints(points.toLong()).executeAsOneOrNull()?.let { toTierDomain(it) }
        }.fold(
            onSuccess = { Result.Success(it) },
            onFailure = { t -> Result.Error(DatabaseException(t.message ?: "DB error", cause = t)) },
        )
    }

    override suspend fun saveTier(tier: LoyaltyTier): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val existing = ltq.getLoyaltyTierById(tier.id).executeAsOneOrNull()
            val now = Clock.System.now().toEpochMilliseconds()
            val benefitsJson = tier.benefits.joinToString(prefix = "[", postfix = "]") { "\"$it\"" }
            if (existing == null) {
                ltq.insertLoyaltyTier(
                    id = tier.id, name = tier.name,
                    min_points = tier.minPoints.toLong(),
                    discount_percent = tier.discountPercent,
                    points_multiplier = tier.pointsMultiplier,
                    benefits = benefitsJson,
                    created_at = now, updated_at = now, sync_status = "PENDING",
                )
            } else {
                ltq.updateLoyaltyTier(
                    name = tier.name,
                    min_points = tier.minPoints.toLong(),
                    discount_percent = tier.discountPercent,
                    points_multiplier = tier.pointsMultiplier,
                    benefits = benefitsJson,
                    updated_at = now, id = tier.id,
                )
            }
        }.fold(
            onSuccess = { Result.Success(Unit) },
            onFailure = { t -> Result.Error(DatabaseException(t.message ?: "Save failed", cause = t)) },
        )
    }

    override suspend fun deleteTier(id: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching { ltq.deleteLoyaltyTier(id) }.fold(
            onSuccess = { Result.Success(Unit) },
            onFailure = { t -> Result.Error(DatabaseException(t.message ?: "Delete failed", cause = t)) },
        )
    }

    private fun toPointsDomain(row: Reward_points) = RewardPoints(
        id = row.id,
        customerId = row.customer_id,
        points = row.points.toInt(),
        balanceAfter = row.balance_after.toInt(),
        type = runCatching { RewardPoints.PointsType.valueOf(row.type) }.getOrDefault(RewardPoints.PointsType.EARNED),
        referenceType = row.reference_type,
        referenceId = row.reference_id,
        note = row.note,
        expiresAt = row.expires_at,
        createdAt = row.created_at,
    )

    private fun toTierDomain(row: Loyalty_tiers): LoyaltyTier {
        val benefits = runCatching {
            Json.parseToJsonElement(row.benefits).jsonArray.map { it.jsonPrimitive.content }
        }.getOrDefault(emptyList())
        return LoyaltyTier(
            id = row.id,
            name = row.name,
            minPoints = row.min_points.toInt(),
            discountPercent = row.discount_percent,
            pointsMultiplier = row.points_multiplier,
            benefits = benefits,
        )
    }
}
