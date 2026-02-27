package com.zyntasolutions.zyntapos.domain.usecase.accounting

import com.zyntasolutions.zyntapos.core.result.Result
import com.zyntasolutions.zyntapos.domain.model.AccountingPeriod
import com.zyntasolutions.zyntapos.domain.repository.AccountingPeriodRepository
import kotlinx.coroutines.flow.Flow

/**
 * Provides reactive and one-shot access to accounting period records.
 */
class GetAccountingPeriodsUseCase(
    private val periodRepository: AccountingPeriodRepository,
) {
    /**
     * Observe all periods for a store (reactive), ordered by startDate descending.
     */
    fun execute(storeId: String): Flow<List<AccountingPeriod>> =
        periodRepository.getAll(storeId)

    /**
     * Find the open period whose date range contains [date] (ISO: YYYY-MM-DD).
     */
    suspend fun executeForDate(storeId: String, date: String): Result<AccountingPeriod?> =
        periodRepository.getPeriodForDate(storeId, date)

    /**
     * Load a single period by UUID.
     */
    suspend fun executeById(id: String): Result<AccountingPeriod?> =
        periodRepository.getById(id)
}
