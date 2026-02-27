package com.zyntasolutions.zyntapos.domain.usecase.accounting

import com.zyntasolutions.zyntapos.core.result.Result
import com.zyntasolutions.zyntapos.domain.model.FinancialStatement
import com.zyntasolutions.zyntapos.domain.repository.FinancialStatementRepository

/**
 * Retrieves the Cash Flow Statement (Direct Method) for a given store and date range.
 *
 * Delegates all computation to [FinancialStatementRepository.getCashFlowStatement], which
 * aggregates posted journal entry lines that touch cash accounts (1010 Cash, 1020 Bank,
 * 1030 Petty Cash) and classifies them by [com.zyntasolutions.zyntapos.domain.model.JournalReferenceType]
 * into Operating, Investing, and Financing sections.
 *
 * @param financialStatementRepository Data source for financial statement computation.
 */
class GetCashFlowStatementUseCase(
    private val financialStatementRepository: FinancialStatementRepository,
) {
    /**
     * @param storeId Scopes the report to a specific store.
     * @param fromDate Inclusive start of the reporting period (ISO: YYYY-MM-DD).
     * @param toDate Inclusive end of the reporting period (ISO: YYYY-MM-DD).
     * @return [Result] wrapping the computed [FinancialStatement.CashFlow].
     */
    suspend fun execute(
        storeId: String,
        fromDate: String,
        toDate: String,
    ): Result<FinancialStatement.CashFlow> =
        financialStatementRepository.getCashFlowStatement(storeId, fromDate, toDate)
}
