package com.zyntasolutions.zyntapos.domain.usecase.reports.enterprise

import com.zyntasolutions.zyntapos.domain.model.report.GrossMarginData
import com.zyntasolutions.zyntapos.domain.repository.ReportRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.datetime.Instant

/**
 * Generates a gross margin report for a given date range.
 *
 * Returns revenue, cost of goods sold, gross profit, and gross margin percentage
 * per product and category for the reporting period. Enables margin optimisation
 * and identification of low-margin product lines.
 *
 * @param reportRepository Source for gross margin data.
 */
class GenerateGrossMarginReportUseCase(
    private val reportRepository: ReportRepository,
) {
    /**
     * @param from Start of the reporting window (inclusive).
     * @param to   End of the reporting window (inclusive).
     * @return A [Flow] emitting the list of [GrossMarginData] per product for the period.
     */
    operator fun invoke(from: Instant, to: Instant): Flow<List<GrossMarginData>> = flow {
        emit(reportRepository.getGrossMargin(from, to))
    }
}
