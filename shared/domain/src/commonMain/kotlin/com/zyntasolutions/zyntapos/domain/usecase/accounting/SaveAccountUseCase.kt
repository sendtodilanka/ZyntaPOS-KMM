package com.zyntasolutions.zyntapos.domain.usecase.accounting

import com.zyntasolutions.zyntapos.core.result.Result
import com.zyntasolutions.zyntapos.core.result.ValidationException
import com.zyntasolutions.zyntapos.domain.model.Account
import com.zyntasolutions.zyntapos.domain.repository.AccountRepository

/**
 * Creates or updates a Chart of Accounts entry.
 *
 * Business rules enforced:
 * - accountCode must not be blank.
 * - accountName must not be blank.
 * - accountCode must be unique within the store (excluding the account being updated).
 * - System accounts cannot be modified.
 */
class SaveAccountUseCase(
    private val accountRepository: AccountRepository,
) {
    suspend fun execute(account: Account, storeId: String): Result<Unit> {
        if (account.accountCode.isBlank()) {
            return Result.Error(
                ValidationException(
                    "Account code must not be blank.",
                    field = "accountCode",
                    rule = "REQUIRED",
                ),
            )
        }

        if (account.accountName.isBlank()) {
            return Result.Error(
                ValidationException(
                    "Account name must not be blank.",
                    field = "accountName",
                    rule = "REQUIRED",
                ),
            )
        }

        // Check if the existing account is a system account (updating scenario)
        val existingResult = accountRepository.getById(account.id)
        if (existingResult is Result.Error) return existingResult
        val existing = (existingResult as Result.Success).data
        if (existing != null && existing.isSystemAccount) {
            return Result.Error(
                ValidationException(
                    "System accounts cannot be modified.",
                    field = "isSystemAccount",
                    rule = "SYSTEM_ACCOUNT",
                ),
            )
        }

        val excludeId = if (existing != null) account.id else null
        val codeCheck = accountRepository.isAccountCodeTaken(storeId, account.accountCode, excludeId)
        if (codeCheck is Result.Error) return codeCheck
        if ((codeCheck as Result.Success).data) {
            return Result.Error(
                ValidationException(
                    "Account code '${account.accountCode}' is already in use for this store.",
                    field = "accountCode",
                    rule = "DUPLICATE_CODE",
                ),
            )
        }

        return if (existing == null) {
            accountRepository.create(account)
        } else {
            accountRepository.update(account)
        }
    }
}
