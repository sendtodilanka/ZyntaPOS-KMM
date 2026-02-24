package com.zyntasolutions.zyntapos.domain.usecase.crm

import com.zyntasolutions.zyntapos.core.result.Result
import com.zyntasolutions.zyntapos.core.result.ValidationException
import com.zyntasolutions.zyntapos.domain.repository.CustomerWalletRepository

/**
 * Credits a monetary amount to a customer's pre-paid wallet.
 *
 * Validates the amount before delegating to [CustomerWalletRepository.credit].
 */
class WalletTopUpUseCase(
    private val walletRepo: CustomerWalletRepository,
) {
    suspend operator fun invoke(
        customerId: String,
        amount: Double,
        note: String? = null,
    ): Result<Unit> {
        if (amount <= 0.0) {
            return Result.Error(ValidationException("Top-up amount must be positive"))
        }
        val walletResult = walletRepo.getOrCreate(customerId)
        val wallet = when (walletResult) {
            is Result.Success -> walletResult.data
            is Result.Error -> return walletResult
            is Result.Loading -> return Result.Loading
        }
        return walletRepo.credit(wallet.id, amount, note = note)
    }
}
