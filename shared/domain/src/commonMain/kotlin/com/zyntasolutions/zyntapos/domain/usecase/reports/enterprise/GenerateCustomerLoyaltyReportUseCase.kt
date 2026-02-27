package com.zyntasolutions.zyntapos.domain.usecase.reports.enterprise

import com.zyntasolutions.zyntapos.domain.model.report.CustomerLoyaltyData
import com.zyntasolutions.zyntapos.domain.repository.ReportRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.datetime.Instant

/**
 * Generates a customer loyalty summary report for a given date range.
 *
 * Provides per-customer loyalty metrics including points earned, points redeemed,
 * current balance, tier status, and visit frequency during the reporting window.
 *
 * @param reportRepository Source for customer loyalty data.
 */
class GenerateCustomerLoyaltyReportUseCase(
    private val reportRepository: ReportRepository,
) {
    /**
     * @param from Start of the reporting window (inclusive).
     * @param to   End of the reporting window (inclusive).
     * @return A [Flow] emitting the list of [CustomerLoyaltyData] per customer.
     */
    operator fun invoke(from: Instant, to: Instant): Flow<List<CustomerLoyaltyData>> = flow {
        emit(reportRepository.getCustomerLoyaltySummary(from, to))
    }
}
