package com.zyntasolutions.zyntapos.domain.usecase.reports

import com.zyntasolutions.zyntapos.domain.model.report.DiscountVoidData
import com.zyntasolutions.zyntapos.domain.repository.ReportRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.datetime.Instant

/** Returns discount and void analysis for a given date range. */
class GenerateDiscountVoidAnalysisReportUseCase(
    private val reportRepository: ReportRepository,
) {
    /**
     * @param from Start of the reporting window (inclusive).
     * @param to   End of the reporting window (inclusive).
     * @return A [Flow] emitting [DiscountVoidData] containing aggregate discount and void totals
     *         along with a per-cashier breakdown.
     */
    operator fun invoke(from: Instant, to: Instant): Flow<DiscountVoidData> = flow {
        emit(reportRepository.getDiscountVoidAnalysis(from, to))
    }
}
