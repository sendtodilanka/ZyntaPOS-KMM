package com.zyntasolutions.zyntapos.domain.usecase.reports

import com.zyntasolutions.zyntapos.domain.model.report.ProfitLossData
import com.zyntasolutions.zyntapos.domain.repository.ReportRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.datetime.Instant

/** Returns a profit-and-loss statement for a given date range. */
class GenerateProfitLossReportUseCase(
    private val reportRepository: ReportRepository,
) {
    /**
     * @param from Start of the reporting window (inclusive).
     * @param to   End of the reporting window (inclusive).
     * @return A [Flow] emitting [ProfitLossData] containing gross profit,
     *         total expenses, net profit, and gross margin percentage.
     */
    operator fun invoke(from: Instant, to: Instant): Flow<ProfitLossData> = flow {
        emit(reportRepository.getProfitLoss(from, to))
    }
}
