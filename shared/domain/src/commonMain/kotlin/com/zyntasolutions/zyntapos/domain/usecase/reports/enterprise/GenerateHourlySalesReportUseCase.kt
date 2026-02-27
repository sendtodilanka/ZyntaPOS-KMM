package com.zyntasolutions.zyntapos.domain.usecase.reports.enterprise

import com.zyntasolutions.zyntapos.domain.model.report.HourlySalesData
import com.zyntasolutions.zyntapos.domain.repository.ReportRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.datetime.LocalDate

/**
 * Generates an hourly sales breakdown report for a specific date.
 *
 * Returns sales revenue and transaction count for each hour of the day,
 * enabling store managers to identify peak trading hours and optimise
 * staffing levels accordingly.
 *
 * @param reportRepository Source for hourly sales data.
 */
class GenerateHourlySalesReportUseCase(
    private val reportRepository: ReportRepository,
) {
    /**
     * @param date The calendar date to report hourly sales for.
     * @return A [Flow] emitting the list of [HourlySalesData] (one entry per hour, 0–23).
     */
    operator fun invoke(date: LocalDate): Flow<List<HourlySalesData>> = flow {
        emit(reportRepository.getHourlySales(date))
    }
}
