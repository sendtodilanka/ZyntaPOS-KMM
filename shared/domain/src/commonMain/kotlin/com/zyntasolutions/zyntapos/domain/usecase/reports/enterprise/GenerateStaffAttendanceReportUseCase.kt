package com.zyntasolutions.zyntapos.domain.usecase.reports.enterprise

import com.zyntasolutions.zyntapos.domain.model.report.StaffAttendanceData
import com.zyntasolutions.zyntapos.domain.repository.ReportRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.datetime.Instant

/**
 * Generates a staff attendance summary report for a given date range.
 *
 * Aggregates clock-in/clock-out records and computed attendance metrics
 * (present days, absent days, late arrivals) per employee.
 *
 * @param reportRepository Source for staff attendance data.
 */
class GenerateStaffAttendanceReportUseCase(
    private val reportRepository: ReportRepository,
) {
    /**
     * @param from Start of the reporting window (inclusive).
     * @param to   End of the reporting window (inclusive).
     * @return A [Flow] emitting the list of [StaffAttendanceData] per employee.
     */
    operator fun invoke(from: Instant, to: Instant): Flow<List<StaffAttendanceData>> = flow {
        emit(reportRepository.getStaffAttendanceSummary(from, to))
    }
}
