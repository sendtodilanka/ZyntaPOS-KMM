package com.zyntasolutions.zyntapos.domain.usecase.reports.enterprise

import com.zyntasolutions.zyntapos.domain.model.report.MonthlySalesData
import com.zyntasolutions.zyntapos.domain.repository.ReportRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * Generates an annual sales trend report broken down by calendar month.
 *
 * Returns 12 monthly data points for the specified year, each containing
 * total revenue, order count, and average order value. Useful for year-over-year
 * comparisons and seasonal trend identification.
 *
 * @param reportRepository Source for annual sales trend data.
 */
class GenerateAnnualSalesTrendReportUseCase(
    private val reportRepository: ReportRepository,
) {
    /**
     * @param year The calendar year (e.g. 2025) to generate the sales trend for.
     * @return A [Flow] emitting a list of [MonthlySalesData] — one entry per month (Jan–Dec).
     */
    operator fun invoke(year: Int): Flow<List<MonthlySalesData>> = flow {
        emit(reportRepository.getAnnualSalesTrend(year))
    }
}
