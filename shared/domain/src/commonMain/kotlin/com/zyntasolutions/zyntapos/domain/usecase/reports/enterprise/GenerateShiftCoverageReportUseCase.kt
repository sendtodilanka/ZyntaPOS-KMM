package com.zyntasolutions.zyntapos.domain.usecase.reports.enterprise

import com.zyntasolutions.zyntapos.domain.model.report.ShiftCoverageData
import com.zyntasolutions.zyntapos.domain.repository.ReportRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.datetime.Instant

/**
 * Generates a shift coverage report showing scheduled vs. actual staffing levels.
 *
 * Highlights understaffed shifts, overtime occurrences, and gaps in coverage
 * across the given date range.
 *
 * @param reportRepository Source for shift coverage data.
 */
class GenerateShiftCoverageReportUseCase(
    private val reportRepository: ReportRepository,
) {
    /**
     * @param from Start of the reporting window (inclusive).
     * @param to   End of the reporting window (inclusive).
     * @return A [Flow] emitting the list of [ShiftCoverageData] per shift slot.
     */
    operator fun invoke(from: Instant, to: Instant): Flow<List<ShiftCoverageData>> = flow {
        emit(reportRepository.getShiftCoverage(from, to))
    }
}
