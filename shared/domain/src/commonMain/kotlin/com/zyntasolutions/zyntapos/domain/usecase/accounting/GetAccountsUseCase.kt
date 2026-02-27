package com.zyntasolutions.zyntapos.domain.usecase.accounting

import com.zyntasolutions.zyntapos.core.result.Result
import com.zyntasolutions.zyntapos.domain.model.Account
import com.zyntasolutions.zyntapos.domain.model.AccountType
import com.zyntasolutions.zyntapos.domain.repository.AccountRepository
import kotlinx.coroutines.flow.Flow

/**
 * Provides reactive and one-shot access to Chart of Accounts entries.
 */
class GetAccountsUseCase(
    private val accountRepository: AccountRepository,
) {
    /**
     * Observe all accounts for a store (reactive).
     */
    fun execute(storeId: String): Flow<List<Account>> =
        accountRepository.getAll(storeId)

    /**
     * Observe accounts filtered by type (reactive).
     */
    fun executeByType(storeId: String, accountType: AccountType): Flow<List<Account>> =
        accountRepository.getByType(storeId, accountType)

    /**
     * One-shot load of a single account by UUID.
     */
    suspend fun executeById(id: String): Result<Account?> =
        accountRepository.getById(id)

    /**
     * One-shot load of a single account by account code within a store.
     */
    suspend fun executeByCode(storeId: String, code: String): Result<Account?> =
        accountRepository.getByCode(storeId, code)
}
