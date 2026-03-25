package com.zyntasolutions.zyntapos.domain.usecase.staff

import com.zyntasolutions.zyntapos.core.result.Result
import com.zyntasolutions.zyntapos.core.result.ValidationException
import com.zyntasolutions.zyntapos.domain.model.ShiftSchedule
import com.zyntasolutions.zyntapos.domain.repository.ShiftRepository

/**
 * Saves (upserts) a shift schedule entry.
 *
 * ### Business Rules
 * 1. [ShiftSchedule.startTime] must be a valid HH:MM string.
 * 2. [ShiftSchedule.endTime] must be a valid HH:MM string.
 * 3. endTime must be after startTime.
 * 4. [ShiftSchedule.shiftDate] must not be blank.
 */
class SaveShiftScheduleUseCase(
    private val shiftRepository: ShiftRepository,
) {
    suspend operator fun invoke(shift: ShiftSchedule): Result<Unit> {
        if (shift.shiftDate.isBlank()) {
            return Result.Error(
                ValidationException("Shift date is required.", field = "shiftDate", rule = "REQUIRED"),
            )
        }
        val timeRegex = Regex("^([01]\\d|2[0-3]):[0-5]\\d$")
        if (!timeRegex.matches(shift.startTime)) {
            return Result.Error(
                ValidationException(
                    "Start time must be in HH:MM format.",
                    field = "startTime",
                    rule = "FORMAT",
                ),
            )
        }
        if (!timeRegex.matches(shift.endTime)) {
            return Result.Error(
                ValidationException(
                    "End time must be in HH:MM format.",
                    field = "endTime",
                    rule = "FORMAT",
                ),
            )
        }
        if (shift.endTime <= shift.startTime) {
            return Result.Error(
                ValidationException(
                    "End time must be after start time.",
                    field = "endTime",
                    rule = "TIME_ORDER",
                ),
            )
        }

        // Check for overlapping shifts across all stores for this employee on the same date
        val existingResult = shiftRepository.getAllShiftsByEmployeeAndDate(shift.employeeId, shift.shiftDate)
        if (existingResult is Result.Error) return existingResult

        val existingShifts = (existingResult as Result.Success).data
        val overlapping = existingShifts
            .filter { it.id != shift.id } // exclude the shift being edited
            .find { existing ->
                // Two intervals overlap when: existingStart < newEnd AND newStart < existingEnd
                existing.startTime < shift.endTime && shift.startTime < existing.endTime
            }

        if (overlapping != null) {
            return Result.Error(
                ValidationException(
                    "Shift overlaps with existing shift (${overlapping.startTime}-${overlapping.endTime}).",
                    field = "startTime",
                    rule = "SHIFT_OVERLAP",
                ),
            )
        }

        return shiftRepository.upsert(shift)
    }
}
