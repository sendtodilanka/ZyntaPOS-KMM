package com.zyntasolutions.zyntapos.domain.usecase.crm

import com.zyntasolutions.zyntapos.core.result.Result
import com.zyntasolutions.zyntapos.core.result.ValidationException
import com.zyntasolutions.zyntapos.core.utils.IdGenerator
import com.zyntasolutions.zyntapos.domain.model.RewardPoints
import com.zyntasolutions.zyntapos.domain.repository.LoyaltyRepository
import kotlinx.datetime.Clock

/**
 * Redeems [pointsToRedeem] from a customer's loyalty balance.
 *
 * Fails with [ValidationException] if the customer has insufficient points.
 */
class RedeemRewardPointsUseCase(
    private val loyaltyRepo: LoyaltyRepository,
) {
    suspend operator fun invoke(
        customerId: String,
        pointsToRedeem: Int,
        orderId: String,
    ): Result<Int> {
        if (pointsToRedeem <= 0) {
            return Result.Error(ValidationException("Points to redeem must be positive"))
        }

        val currentBalanceResult = loyaltyRepo.getBalance(customerId)
        val currentBalance = when (currentBalanceResult) {
            is Result.Success -> currentBalanceResult.data
            is Result.Error -> return currentBalanceResult
            is Result.Loading -> return Result.Loading
        }

        if (currentBalance < pointsToRedeem) {
            return Result.Error(
                ValidationException("Insufficient points: available $currentBalance, requested $pointsToRedeem")
            )
        }

        val newBalance = currentBalance - pointsToRedeem
        val entry = RewardPoints(
            id = IdGenerator.newId(),
            customerId = customerId,
            points = -pointsToRedeem,
            balanceAfter = newBalance,
            type = RewardPoints.PointsType.REDEEMED,
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
}
