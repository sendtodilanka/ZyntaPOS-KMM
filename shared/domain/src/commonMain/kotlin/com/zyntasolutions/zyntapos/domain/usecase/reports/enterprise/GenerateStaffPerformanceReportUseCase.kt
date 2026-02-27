package com.zyntasolutions.zyntapos.domain.usecase.reports.enterprise

import com.zyntasolutions.zyntapos.domain.model.report.StaffSalesData
import com.zyntasolutions.zyntapos.domain.repository.ReportRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.datetime.Instant

/**
 * Generates a staff performance report ranking employees by sales metrics for a date range.
 *
 * Each [StaffSalesData] entry includes total sales value, number of transactions,
 * average transaction value, and discounts applied by the employee.
 *
 * @param reportRepository Source for staff sales performance data.
 */
class GenerateStaffPerformanceReportUseCase(
    private val reportRepository: ReportRepository,
) {
    /**
     * @param from Start of the reporting window (inclusive).
     * @param to   End of the reporting window (inclusive).
     * @return A [Flow] emitting the list of [StaffSalesData] per employee.
     */
    operator fun invoke(from: Instant, to: Instant): Flow<List<StaffSalesData>> = flow {
        emit(reportRepository.getStaffSalesSummary(from, to))
    }
}
