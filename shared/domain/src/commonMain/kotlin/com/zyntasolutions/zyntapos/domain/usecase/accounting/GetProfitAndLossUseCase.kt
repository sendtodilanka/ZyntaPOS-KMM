package com.zyntasolutions.zyntapos.domain.usecase.accounting

import com.zyntasolutions.zyntapos.core.result.Result
import com.zyntasolutions.zyntapos.domain.model.FinancialStatement
import com.zyntasolutions.zyntapos.domain.repository.FinancialStatementRepository

/**
 * Computes the Profit and Loss (Income) Statement for a date range.
 */
class GetProfitAndLossUseCase(
    private val statementRepository: FinancialStatementRepository,
) {
    /**
     * Compute the P&L statement for [storeId] from [fromDate] to [toDate] (both ISO: YYYY-MM-DD, inclusive).
     */
    suspend fun execute(storeId: String, fromDate: String, toDate: String): Result<FinancialStatement.PAndL> =
        statementRepository.getProfitAndLoss(storeId, fromDate, toDate)
}
