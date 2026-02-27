package com.zyntasolutions.zyntapos.domain.usecase.reports

import com.zyntasolutions.zyntapos.domain.repository.ReportRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.datetime.Instant

/** Returns a revenue breakdown by payment method for a given date range. */
class GeneratePaymentMethodReportUseCase(
    private val reportRepository: ReportRepository,
) {
    /**
     * @param from Start of the reporting window (inclusive).
     * @param to   End of the reporting window (inclusive).
     * @return A [Flow] emitting a map of payment method name (e.g., "CASH", "CARD", "MOBILE")
     *         to total revenue for that method over the given date range.
     */
    operator fun invoke(from: Instant, to: Instant): Flow<Map<String, Double>> = flow {
        emit(reportRepository.getPaymentMethodBreakdown(from, to))
    }
}
