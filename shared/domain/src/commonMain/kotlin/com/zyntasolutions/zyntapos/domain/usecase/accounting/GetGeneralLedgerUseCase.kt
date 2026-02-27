package com.zyntasolutions.zyntapos.domain.usecase.accounting

import com.zyntasolutions.zyntapos.core.result.Result
import com.zyntasolutions.zyntapos.domain.model.GeneralLedgerEntry
import com.zyntasolutions.zyntapos.domain.repository.FinancialStatementRepository

/**
 * Computes the General Ledger for a specific account over a date range.
 */
class GetGeneralLedgerUseCase(
    private val statementRepository: FinancialStatementRepository,
) {
    /**
     * Compute the general ledger for [accountId] within [storeId]
     * from [fromDate] to [toDate] (both ISO: YYYY-MM-DD, inclusive).
     */
    suspend fun execute(
        storeId: String,
        accountId: String,
        fromDate: String,
        toDate: String,
    ): Result<List<GeneralLedgerEntry>> =
        statementRepository.getGeneralLedger(storeId, accountId, fromDate, toDate)
}
