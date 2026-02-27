package com.zyntasolutions.zyntapos.domain.usecase.reports.enterprise

import com.zyntasolutions.zyntapos.domain.model.report.StoreSalesData
import com.zyntasolutions.zyntapos.domain.repository.ReportRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.datetime.Instant

/**
 * Generates a multi-store sales comparison report across all stores for a date range.
 *
 * Allows head-office managers to compare revenue, transaction count, average
 * order value, and top-selling categories side-by-side across store locations.
 *
 * @param reportRepository Source for multi-store sales comparison data.
 */
class GenerateMultiStoreComparisonReportUseCase(
    private val reportRepository: ReportRepository,
) {
    /**
     * @param from Start of the reporting window (inclusive).
     * @param to   End of the reporting window (inclusive).
     * @return A [Flow] emitting the list of [StoreSalesData] per store location.
     */
    operator fun invoke(from: Instant, to: Instant): Flow<List<StoreSalesData>> = flow {
        emit(reportRepository.getMultiStoreComparison(from, to))
    }
}
