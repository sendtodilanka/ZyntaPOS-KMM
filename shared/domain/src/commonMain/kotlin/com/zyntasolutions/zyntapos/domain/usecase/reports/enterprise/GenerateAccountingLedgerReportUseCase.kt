package com.zyntasolutions.zyntapos.domain.usecase.reports.enterprise

import com.zyntasolutions.zyntapos.domain.model.report.AccountingLedgerRecord
import com.zyntasolutions.zyntapos.domain.repository.ReportRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.datetime.Instant

/**
 * Generates an accounting ledger report for a given date range.
 *
 * Returns all double-entry accounting records within the period, including
 * debit/credit accounts, amounts, references, and journal entry descriptions.
 * Intended for accountants reconciling the general ledger.
 *
 * @param reportRepository Source for accounting ledger data.
 */
class GenerateAccountingLedgerReportUseCase(
    private val reportRepository: ReportRepository,
) {
    /**
     * @param from Start of the reporting window (inclusive).
     * @param to   End of the reporting window (inclusive).
     * @return A [Flow] emitting the list of [AccountingLedgerRecord] for the period.
     */
    operator fun invoke(from: Instant, to: Instant): Flow<List<AccountingLedgerRecord>> = flow {
        emit(reportRepository.getAccountingLedger(from, to))
    }
}
