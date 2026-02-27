package com.zyntasolutions.zyntapos.domain.usecase.accounting

import com.zyntasolutions.zyntapos.core.result.Result
import com.zyntasolutions.zyntapos.domain.model.FinancialStatement
import com.zyntasolutions.zyntapos.domain.repository.FinancialStatementRepository

/**
 * Computes the trial balance as of a given date.
 */
class GetTrialBalanceUseCase(
    private val statementRepository: FinancialStatementRepository,
) {
    /**
     * Compute the trial balance for [storeId] as of [asOfDate] (ISO: YYYY-MM-DD).
     */
    suspend fun execute(storeId: String, asOfDate: String): Result<FinancialStatement.TrialBalance> =
        statementRepository.getTrialBalance(storeId, asOfDate)
}
