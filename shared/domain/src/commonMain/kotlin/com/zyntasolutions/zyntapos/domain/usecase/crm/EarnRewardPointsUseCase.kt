package com.zyntasolutions.zyntapos.domain.usecase.crm

import com.zyntasolutions.zyntapos.core.result.Result
import com.zyntasolutions.zyntapos.core.result.ValidationException
import com.zyntasolutions.zyntapos.core.utils.IdGenerator
import com.zyntasolutions.zyntapos.domain.model.LoyaltyTier
import com.zyntasolutions.zyntapos.domain.model.RewardPoints
import com.zyntasolutions.zyntapos.domain.repository.LoyaltyRepository
import kotlinx.datetime.Clock

/**
 * Awards loyalty points to a customer after a successful sale.
 *
 * The points multiplier from the customer's current [LoyaltyTier] is applied automatically.
 * Returns the new points balance.
 */
class EarnRewardPointsUseCase(
    private val loyaltyRepo: LoyaltyRepository,
) {
    suspend operator fun invoke(
        customerId: String,
        basePoints: Int,
        orderId: String,
        tier: LoyaltyTier? = null,
    ): Result<Int> {
        if (basePoints < 0) {
            return Result.Error(ValidationException("Base points cannot be negative"))
        }
        val multiplier = tier?.pointsMultiplier ?: 1.0
        val earned = (basePoints * multiplier).toInt().coerceAtLeast(0)
        if (earned == 0) return Result.Success(0)

        val currentBalanceResult = loyaltyRepo.getBalance(customerId)
        val currentBalance = when (currentBalanceResult) {
            is Result.Success -> currentBalanceResult.data
            is Result.Error -> return currentBalanceResult
            is Result.Loading -> return Result.Loading
        }

        val newBalance = currentBalance + earned
        val entry = RewardPoints(
            id = generateUuid(),
            customerId = customerId,
            points = earned,
            balanceAfter = newBalance,
            type = RewardPoints.PointsType.EARNED,
            referenceType = "ORDER",
            referenceId = orderId,
            createdAt = Clock.System.now().toEpochMilliseconds(),
        )
        return when (val result = loyaltyRepo.recordPoints(entry)) {
            is Result.Success -> Result.Success(newBalance)
            is Result.Error -> result
            is Result.Loading -> Result.Loading
        }
    }

    private fun generateUuid(): String = IdGenerator.newId()
}
