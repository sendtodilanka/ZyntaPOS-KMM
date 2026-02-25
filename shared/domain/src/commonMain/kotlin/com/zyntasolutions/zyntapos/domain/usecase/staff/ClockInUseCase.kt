package com.zyntasolutions.zyntapos.domain.usecase.staff

import com.zyntasolutions.zyntapos.core.result.Result
import com.zyntasolutions.zyntapos.core.result.ValidationException
import com.zyntasolutions.zyntapos.domain.model.AttendanceRecord
import com.zyntasolutions.zyntapos.domain.repository.AttendanceRepository

/**
 * Records a clock-in event for an employee.
 *
 * ### Business Rules
 * 1. Employee must not already have an open (unclocked-out) attendance record.
 * 2. [clockInTime] must be a valid ISO datetime string.
 */
class ClockInUseCase(
    private val attendanceRepository: AttendanceRepository,
) {
    suspend operator fun invoke(record: AttendanceRecord): Result<Unit> {
        // Ensure no open record already exists
        val openRecordResult = attendanceRepository.getOpenRecord(record.employeeId)
        if (openRecordResult is Result.Error) return openRecordResult

        val openRecord = (openRecordResult as Result.Success).data
        if (openRecord != null) {
            return Result.Error(
                ValidationException(
                    "Employee is already clocked in. Clock out first.",
                    field = "employeeId",
                    rule = "ALREADY_CLOCKED_IN",
                ),
            )
        }

        return attendanceRepository.insert(record)
    }
}
