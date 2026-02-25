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
        return shiftRepository.upsert(shift)
    }
}
