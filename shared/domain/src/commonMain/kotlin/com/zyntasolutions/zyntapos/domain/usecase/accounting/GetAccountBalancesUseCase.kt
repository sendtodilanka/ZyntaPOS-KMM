package com.zyntasolutions.zyntapos.domain.usecase.accounting

import com.zyntasolutions.zyntapos.core.result.Result
import com.zyntasolutions.zyntapos.domain.model.AccountBalance
import com.zyntasolutions.zyntapos.domain.repository.AccountRepository
import kotlinx.coroutines.flow.Flow

/**
 * Provides access to cached account balance records for a given accounting period.
 */
class GetAccountBalancesUseCase(
    private val accountRepository: AccountRepository,
) {
    /**
     * Observe all balance records for a given period (reactive).
     */
    fun execute(storeId: String, periodId: String): Flow<List<AccountBalance>> =
        accountRepository.getAllBalances(storeId, periodId)

    /**
     * One-shot load of a single account balance for a specific period.
     */
    suspend fun executeForAccount(accountId: String, periodId: String): Result<AccountBalance?> =
        accountRepository.getBalance(accountId, periodId)
}
