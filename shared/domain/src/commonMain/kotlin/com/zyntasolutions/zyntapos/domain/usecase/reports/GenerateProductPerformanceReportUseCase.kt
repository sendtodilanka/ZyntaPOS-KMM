package com.zyntasolutions.zyntapos.domain.usecase.reports

import com.zyntasolutions.zyntapos.domain.model.report.ProductPerformanceData
import com.zyntasolutions.zyntapos.domain.repository.ReportRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.datetime.Instant

/** Returns per-product performance metrics for a given date range. */
class GenerateProductPerformanceReportUseCase(
    private val reportRepository: ReportRepository,
) {
    /**
     * @param from Start of the reporting window (inclusive).
     * @param to   End of the reporting window (inclusive).
     * @return A [Flow] emitting a list of [ProductPerformanceData] including
     *         units sold, revenue, COGS, margin, and return count per product.
     */
    operator fun invoke(from: Instant, to: Instant): Flow<List<ProductPerformanceData>> = flow {
        emit(reportRepository.getProductPerformance(from, to))
    }
}
