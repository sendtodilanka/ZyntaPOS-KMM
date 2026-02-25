package com.zyntasolutions.zyntapos.domain.usecase.staff

import com.zyntasolutions.zyntapos.core.result.Result
import com.zyntasolutions.zyntapos.domain.model.AttendanceSummary
import com.zyntasolutions.zyntapos.domain.repository.AttendanceRepository

/**
 * Computes an attendance summary for an employee over a date range.
 *
 * @param employeeId The employee to summarise.
 * @param from ISO date: YYYY-MM-DD (inclusive).
 * @param to ISO date: YYYY-MM-DD (inclusive).
 */
class GetAttendanceSummaryUseCase(
    private val attendanceRepository: AttendanceRepository,
) {
    suspend operator fun invoke(
        employeeId: String,
        from: String,
        to: String,
    ): Result<AttendanceSummary> =
        attendanceRepository.getSummary(employeeId, from, to)
}
