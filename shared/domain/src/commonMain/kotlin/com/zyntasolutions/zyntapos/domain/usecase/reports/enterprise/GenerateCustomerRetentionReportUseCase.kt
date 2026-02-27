package com.zyntasolutions.zyntapos.domain.usecase.reports.enterprise

import com.zyntasolutions.zyntapos.domain.model.report.CustomerRetentionData
import com.zyntasolutions.zyntapos.domain.repository.ReportRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.datetime.Instant

/**
 * Generates a customer retention report for a given date range.
 *
 * Provides cohort-based retention metrics including new customer count, returning
 * customer count, churn rate, average purchase frequency, and customer lifetime
 * value indicators for the reporting period.
 *
 * @param reportRepository Source for customer retention metrics.
 */
class GenerateCustomerRetentionReportUseCase(
    private val reportRepository: ReportRepository,
) {
    /**
     * @param from Start of the reporting window (inclusive).
     * @param to   End of the reporting window (inclusive).
     * @return A [Flow] emitting the aggregated [CustomerRetentionData] for the period.
     */
    operator fun invoke(from: Instant, to: Instant): Flow<CustomerRetentionData> = flow {
        emit(reportRepository.getCustomerRetentionMetrics(from, to))
    }
}
