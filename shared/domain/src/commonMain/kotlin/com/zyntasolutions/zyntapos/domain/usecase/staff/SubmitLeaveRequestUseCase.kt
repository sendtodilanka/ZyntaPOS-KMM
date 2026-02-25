package com.zyntasolutions.zyntapos.domain.usecase.staff

import com.zyntasolutions.zyntapos.core.result.Result
import com.zyntasolutions.zyntapos.core.result.ValidationException
import com.zyntasolutions.zyntapos.domain.model.LeaveRecord
import com.zyntasolutions.zyntapos.domain.repository.LeaveRepository

/**
 * Submits a new leave request on behalf of an employee.
 *
 * ### Business Rules
 * 1. [LeaveRecord.startDate] must not be blank.
 * 2. [LeaveRecord.endDate] must not be blank.
 * 3. endDate must not be before startDate.
 */
class SubmitLeaveRequestUseCase(
    private val leaveRepository: LeaveRepository,
) {
    suspend operator fun invoke(record: LeaveRecord): Result<Unit> {
        if (record.startDate.isBlank()) {
            return Result.Error(
                ValidationException("Start date is required.", field = "startDate", rule = "REQUIRED"),
            )
        }
        if (record.endDate.isBlank()) {
            return Result.Error(
                ValidationException("End date is required.", field = "endDate", rule = "REQUIRED"),
            )
        }
        if (record.endDate < record.startDate) {
            return Result.Error(
                ValidationException(
                    "End date must not be before start date.",
                    field = "endDate",
                    rule = "DATE_ORDER",
                ),
            )
        }
        return leaveRepository.insert(record)
    }
}
