package com.zyntasolutions.zyntapos.domain.usecase.reports.enterprise

import com.zyntasolutions.zyntapos.domain.model.report.ClockRecord
import com.zyntasolutions.zyntapos.domain.repository.ReportRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.datetime.Instant

/**
 * Generates a staff clock-in/clock-out log report for a date range.
 *
 * Returns the detailed time-and-attendance log showing each clock-in and clock-out
 * event per employee. Can be filtered to a specific employee when [employeeId] is
 * provided, otherwise all employees are included.
 *
 * @param reportRepository Source for clock-in/clock-out log data.
 */
class GenerateStaffClockInOutReportUseCase(
    private val reportRepository: ReportRepository,
) {
    /**
     * @param from       Start of the reporting window (inclusive).
     * @param to         End of the reporting window (inclusive).
     * @param employeeId Optional employee identifier to filter the log. Pass `null` to include all staff.
     * @return A [Flow] emitting the list of [ClockRecord] entries within the date range.
     */
    operator fun invoke(
        from: Instant,
        to: Instant,
        employeeId: String? = null,
    ): Flow<List<ClockRecord>> = flow {
        emit(reportRepository.getClockInOutLog(from, to, employeeId))
    }
}
