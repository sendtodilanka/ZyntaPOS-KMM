package com.zyntasolutions.zyntapos.domain.usecase.reports.enterprise

import com.zyntasolutions.zyntapos.domain.model.report.ReturnRefundData
import com.zyntasolutions.zyntapos.domain.repository.ReportRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.datetime.Instant

/**
 * Generates a return and refund summary report for a given date range.
 *
 * Aggregates all return and refund transactions, providing totals by reason code,
 * refund method, product, and cashier to support loss-prevention analysis.
 *
 * @param reportRepository Source for return and refund data.
 */
class GenerateReturnRefundReportUseCase(
    private val reportRepository: ReportRepository,
) {
    /**
     * @param from Start of the reporting window (inclusive).
     * @param to   End of the reporting window (inclusive).
     * @return A [Flow] emitting the aggregated [ReturnRefundData] for the period.
     */
    operator fun invoke(from: Instant, to: Instant): Flow<ReturnRefundData> = flow {
        emit(reportRepository.getReturnRefundSummary(from, to))
    }
}
