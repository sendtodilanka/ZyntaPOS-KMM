package com.zyntasolutions.zyntapos.domain.usecase.reports.enterprise

import com.zyntasolutions.zyntapos.domain.model.report.COGSData
import com.zyntasolutions.zyntapos.domain.repository.ReportRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.datetime.Instant

/**
 * Generates a Cost of Goods Sold (COGS) report for a given date range.
 *
 * Calculates the direct cost of goods sold during the period broken down by
 * product and category. Used in conjunction with revenue figures to compute
 * gross profit and margin analysis.
 *
 * @param reportRepository Source for COGS data.
 */
class GenerateCOGSReportUseCase(
    private val reportRepository: ReportRepository,
) {
    /**
     * @param from Start of the reporting window (inclusive).
     * @param to   End of the reporting window (inclusive).
     * @return A [Flow] emitting the list of [COGSData] per product for the period.
     */
    operator fun invoke(from: Instant, to: Instant): Flow<List<COGSData>> = flow {
        emit(reportRepository.getCOGS(from, to))
    }
}
