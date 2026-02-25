package com.zyntasolutions.zyntapos.domain.usecase.accounting

import com.zyntasolutions.zyntapos.core.result.Result
import com.zyntasolutions.zyntapos.domain.model.AccountSummary
import com.zyntasolutions.zyntapos.domain.repository.AccountingRepository

/**
 * Returns an aggregated balance per account code for a range of fiscal periods.
 *
 * @param storeId Store scope.
 * @param fromPeriod Inclusive start period (YYYY-MM format, e.g., "2026-01").
 * @param toPeriod Inclusive end period (YYYY-MM format, e.g., "2026-03").
 */
class GetPeriodSummaryUseCase(
    private val accountingRepository: AccountingRepository,
) {
    suspend operator fun invoke(
        storeId: String,
        fromPeriod: String,
        toPeriod: String,
    ): Result<List<AccountSummary>> =
        accountingRepository.getSummaryForPeriodRange(storeId, fromPeriod, toPeriod)
}
