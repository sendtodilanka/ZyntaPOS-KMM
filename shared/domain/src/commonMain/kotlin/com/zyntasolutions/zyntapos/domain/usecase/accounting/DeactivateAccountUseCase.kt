package com.zyntasolutions.zyntapos.domain.usecase.accounting

import com.zyntasolutions.zyntapos.core.result.Result
import com.zyntasolutions.zyntapos.core.result.ValidationException
import com.zyntasolutions.zyntapos.domain.repository.AccountRepository

/**
 * Soft-deletes an account by setting isActive = false.
 *
 * System accounts cannot be deactivated.
 */
class DeactivateAccountUseCase(
    private val accountRepository: AccountRepository,
) {
    suspend fun execute(accountId: String, now: Long): Result<Unit> {
        val accountResult = accountRepository.getById(accountId)
        if (accountResult is Result.Error) return accountResult
        val account = (accountResult as Result.Success).data
            ?: return Result.Error(
                ValidationException(
                    "Account not found: $accountId",
                    field = "accountId",
                    rule = "NOT_FOUND",
                ),
            )

        if (account.isSystemAccount) {
            return Result.Error(
                ValidationException(
                    "System account '${account.accountName}' cannot be deactivated.",
                    field = "isSystemAccount",
                    rule = "SYSTEM_ACCOUNT",
                ),
            )
        }

        return accountRepository.deactivate(accountId, now)
    }
}
