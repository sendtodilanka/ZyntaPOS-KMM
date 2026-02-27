package com.zyntasolutions.zyntapos.domain.usecase.reports

import com.zyntasolutions.zyntapos.domain.model.report.CategorySalesData
import com.zyntasolutions.zyntapos.domain.repository.ReportRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.datetime.Instant

/** Returns revenue totals broken down by product category for a given date range. */
class GenerateCategorySalesReportUseCase(
    private val reportRepository: ReportRepository,
) {
    /**
     * @param from Start of the reporting window (inclusive).
     * @param to   End of the reporting window (inclusive).
     * @return A [Flow] emitting a list of [CategorySalesData] for the given date range,
     *         including each category's share of total revenue as a percentage.
     */
    operator fun invoke(from: Instant, to: Instant): Flow<List<CategorySalesData>> = flow {
        emit(reportRepository.getSalesByCategory(from, to))
    }
}
