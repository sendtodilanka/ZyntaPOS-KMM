package com.zyntasolutions.zyntapos.domain.usecase.reports.enterprise

import com.zyntasolutions.zyntapos.domain.model.report.EInvoiceStatusData
import com.zyntasolutions.zyntapos.domain.repository.ReportRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.datetime.Instant

/**
 * Generates an e-invoice status report for a given date range.
 *
 * Lists all e-invoices issued in the period with their current IRD submission
 * status (PENDING, SUBMITTED, ACCEPTED, REJECTED), enabling compliance tracking
 * and follow-up on failed submissions.
 *
 * @param reportRepository Source for e-invoice status data.
 */
class GenerateEInvoiceStatusReportUseCase(
    private val reportRepository: ReportRepository,
) {
    /**
     * @param from Start of the reporting window (inclusive).
     * @param to   End of the reporting window (inclusive).
     * @return A [Flow] emitting the list of [EInvoiceStatusData] for the period.
     */
    operator fun invoke(from: Instant, to: Instant): Flow<List<EInvoiceStatusData>> = flow {
        emit(reportRepository.getEInvoiceStatus(from, to))
    }
}
