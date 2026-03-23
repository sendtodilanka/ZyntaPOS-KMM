package com.zyntasolutions.zyntapos.domain.usecase.crm

import com.zyntasolutions.zyntapos.core.result.Result
import com.zyntasolutions.zyntapos.domain.model.LoyaltyTier
import com.zyntasolutions.zyntapos.domain.repository.LoyaltyRepository

/**
 * Checks if a customer's loyalty tier has changed based on their current points balance.
 *
 * Returns the new tier (or null if no tier applies). The caller is responsible for
 * notifying the customer of tier upgrades/downgrades.
 */
class CheckLoyaltyTierProgressionUseCase(
    private val loyaltyRepo: LoyaltyRepository,
) {
    /**
     * @param customerId The customer to check.
     * @return The current tier for the customer's balance, or null if below all tiers.
     */
    suspend operator fun invoke(customerId: String): Result<LoyaltyTier?> {
        val balanceResult = loyaltyRepo.getBalance(customerId)
        val balance = when (balanceResult) {
            is Result.Success -> balanceResult.data
            is Result.Error -> return balanceResult
            is Result.Loading -> return Result.Loading
        }
        return loyaltyRepo.getTierForPoints(balance)
    }
}
