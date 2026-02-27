package com.zyntasolutions.zyntapos.domain.usecase.accounting

import com.zyntasolutions.zyntapos.core.result.Result
import com.zyntasolutions.zyntapos.domain.model.FinancialStatement
import com.zyntasolutions.zyntapos.domain.repository.FinancialStatementRepository

/**
 * Computes the Balance Sheet as of a specific date.
 */
class GetBalanceSheetUseCase(
    private val statementRepository: FinancialStatementRepository,
) {
    /**
     * Compute the balance sheet for [storeId] as of [asOfDate] (ISO: YYYY-MM-DD).
     */
    suspend fun execute(storeId: String, asOfDate: String): Result<FinancialStatement.BalanceSheet> =
        statementRepository.getBalanceSheet(storeId, asOfDate)
}
