package com.zyntasolutions.zyntapos.domain.usecase.reports.enterprise

import com.zyntasolutions.zyntapos.domain.model.report.StaffSalesData
import com.zyntasolutions.zyntapos.domain.repository.ReportRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.datetime.Instant

/**
 * Generates a sales-by-cashier report for a given date range.
 *
 * Returns per-cashier sales metrics including total revenue, number of transactions,
 * average transaction value, and discounts applied. Useful for performance reviews
 * and commission calculations.
 *
 * @param reportRepository Source for cashier sales performance data.
 */
class GenerateSalesByCashierReportUseCase(
    private val reportRepository: ReportRepository,
) {
    /**
     * @param from Start of the reporting window (inclusive).
     * @param to   End of the reporting window (inclusive).
     * @return A [Flow] emitting the list of [StaffSalesData] per cashier.
     */
    operator fun invoke(from: Instant, to: Instant): Flow<List<StaffSalesData>> = flow {
        emit(reportRepository.getStaffSalesSummary(from, to))
    }
}
