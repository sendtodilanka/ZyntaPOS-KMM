package com.zyntasolutions.zyntapos.domain.usecase.reports

import com.zyntasolutions.zyntapos.domain.model.report.DailySalesSummaryData
import com.zyntasolutions.zyntapos.domain.repository.ReportRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.datetime.LocalDate

/** Returns end-of-day sales totals for the given calendar date. */
class GenerateDailySalesSummaryUseCase(
    private val reportRepository: ReportRepository,
) {
    /**
     * @param date The calendar date for which the daily sales summary is generated.
     * @return A [Flow] emitting the [DailySalesSummaryData] for [date].
     */
    operator fun invoke(date: LocalDate): Flow<DailySalesSummaryData> = flow {
        emit(reportRepository.getDailySalesSummary(date))
    }
}
