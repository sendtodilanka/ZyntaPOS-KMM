package com.zyntasolutions.zyntapos.domain.usecase.crm

import com.zyntasolutions.zyntapos.core.result.Result
import com.zyntasolutions.zyntapos.domain.repository.LoyaltyRepository
import kotlin.time.Clock

/**
 * Expires all earned loyalty points for [customerId] that have passed their expiry timestamp.
 *
 * Delegates to [LoyaltyRepository.expirePointsForCustomer] which inserts negative EXPIRED ledger
 * entries while preserving the append-only invariant of the reward_points table.
 *
 * @return [Result.Success] with the total points expired (0 if none expired), or [Result.Error].
 */
class ExpireLoyaltyPointsUseCase(
    private val loyaltyRepo: LoyaltyRepository,
) {
    suspend operator fun invoke(
        customerId: String,
        nowEpochMillis: Long = Clock.System.now().toEpochMilliseconds(),
    ): Result<Int> = loyaltyRepo.expirePointsForCustomer(customerId, nowEpochMillis)
}
