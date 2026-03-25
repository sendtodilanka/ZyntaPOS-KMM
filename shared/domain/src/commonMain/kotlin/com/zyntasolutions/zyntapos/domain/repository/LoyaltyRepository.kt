package com.zyntasolutions.zyntapos.domain.repository

import com.zyntasolutions.zyntapos.core.result.Result
import com.zyntasolutions.zyntapos.domain.model.LoyaltyTier
import com.zyntasolutions.zyntapos.domain.model.RewardPoints
import kotlinx.coroutines.flow.Flow

/**
 * Contract for loyalty points and tier management.
 */
interface LoyaltyRepository {

    // ── Points ledger ──────────────────────────────────────────────────────────

    /** Emits the points ledger for the given customer, most recent entry first. */
    fun getPointsHistory(customerId: String): Flow<List<RewardPoints>>

    /** Reads the current points balance for the given customer. */
    suspend fun getBalance(customerId: String): Result<Int>

    /**
     * Appends a [RewardPoints] ledger entry. The caller is responsible for computing
     * [RewardPoints.balanceAfter] correctly before calling this method.
     */
    suspend fun recordPoints(entry: RewardPoints): Result<Unit>

    /**
     * Expires all earned points for [customerId] that have an [RewardPoints.expiresAt] before
     * [nowEpochMillis] and have not already been expired.
     *
     * For each qualifying EARNED entry, a corresponding negative EXPIRED entry is inserted to
     * cancel the points. This preserves the append-only ledger invariant.
     *
     * @return [Result.Success] with the total points expired (0 if none), or [Result.Error] on DB failure.
     */
    suspend fun expirePointsForCustomer(customerId: String, nowEpochMillis: Long): Result<Int>

    // ── Loyalty tiers ─────────────────────────────────────────────────────────

    /** Emits all loyalty tier definitions, ordered by minimum points ascending. Re-emits on change. */
    fun getAllTiers(): Flow<List<LoyaltyTier>>

    /** Returns the highest tier that the given [points] balance qualifies for. */
    suspend fun getTierForPoints(points: Int): Result<LoyaltyTier?>

    /** Inserts or updates a loyalty tier. */
    suspend fun saveTier(tier: LoyaltyTier): Result<Unit>

    /** Deletes a loyalty tier. */
    suspend fun deleteTier(id: String): Result<Unit>
}
