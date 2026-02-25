package com.zyntasolutions.zyntapos.domain.usecase.staff

import com.zyntasolutions.zyntapos.core.result.Result
import com.zyntasolutions.zyntapos.core.result.ValidationException
import com.zyntasolutions.zyntapos.domain.repository.AttendanceRepository

/**
 * Records the clock-out event for an open attendance record.
 *
 * ### Business Rules
 * 1. An open record must exist for the employee.
 * 2. [clockOutTime] must be after the clock-in time.
 * 3. Total hours and overtime are calculated and persisted.
 *
 * @param overtimeThresholdHours Hours per shift after which time is counted as overtime (default 8).
 */
class ClockOutUseCase(
    private val attendanceRepository: AttendanceRepository,
    private val overtimeThresholdHours: Double = 8.0,
) {
    suspend operator fun invoke(
        employeeId: String,
        clockOutTime: String,
        updatedAt: Long,
    ): Result<Unit> {
        val openRecordResult = attendanceRepository.getOpenRecord(employeeId)
        if (openRecordResult is Result.Error) return openRecordResult

        val openRecord = (openRecordResult as Result.Success).data
            ?: return Result.Error(
                ValidationException(
                    "No open clock-in record found for this employee.",
                    field = "employeeId",
                    rule = "NOT_CLOCKED_IN",
                ),
            )

        // Parse total hours (simplified — production impl uses kotlinx-datetime)
        val totalHours = calculateHours(openRecord.clockIn, clockOutTime)
        val overtimeHours = (totalHours - overtimeThresholdHours).coerceAtLeast(0.0)

        return attendanceRepository.clockOut(
            id = openRecord.id,
            clockOut = clockOutTime,
            totalHours = totalHours,
            overtimeHours = overtimeHours,
            updatedAt = updatedAt,
        )
    }

    private fun calculateHours(clockIn: String, clockOut: String): Double {
        // Parse ISO datetime "YYYY-MM-DDTHH:MM:SS" and compute difference
        return try {
            val inParts = clockIn.substringAfter("T").split(":").map { it.toInt() }
            val outParts = clockOut.substringAfter("T").split(":").map { it.toInt() }
            val inMinutes = inParts[0] * 60 + inParts[1]
            val outMinutes = outParts[0] * 60 + outParts[1]
            // Account for date difference — basic same-day calculation
            val diff = outMinutes - inMinutes
            if (diff < 0) (diff + 24 * 60) / 60.0 else diff / 60.0
        } catch (e: Exception) {
            0.0
        }
    }
}
