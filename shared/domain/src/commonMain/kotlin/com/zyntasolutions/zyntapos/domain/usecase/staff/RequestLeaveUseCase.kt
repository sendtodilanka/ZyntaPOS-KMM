package com.zyntasolutions.zyntapos.domain.usecase.staff

import com.zyntasolutions.zyntapos.core.result.Result
import com.zyntasolutions.zyntapos.core.result.ValidationException
import com.zyntasolutions.zyntapos.domain.model.LeaveRequest
import com.zyntasolutions.zyntapos.domain.model.LeaveRequestStatus
import com.zyntasolutions.zyntapos.domain.repository.LeaveRepository

/**
 * Submits a new [LeaveRequest] on behalf of an employee.
 *
 * ### Business Rules
 * 1. [LeaveRequest.reason] must not be blank.
 * 2. [LeaveRequest.startDate] must be less than or equal to [LeaveRequest.endDate].
 * 3. [LeaveRequest.employeeId] must not be blank.
 * 4. The request is inserted with status [LeaveRequestStatus.PENDING].
 */
class RequestLeaveUseCase(
    private val leaveRepository: LeaveRepository,
) {
    suspend operator fun invoke(request: LeaveRequest): Result<Unit> {
        if (request.employeeId.isBlank()) {
            return Result.Error(
                ValidationException(
                    "Employee ID is required.",
                    field = "employeeId",
                    rule = "REQUIRED",
                ),
            )
        }
        if (request.reason.isBlank()) {
            return Result.Error(
                ValidationException(
                    "A reason for the leave request is required.",
                    field = "reason",
                    rule = "REQUIRED",
                ),
            )
        }
        if (request.startDate > request.endDate) {
            return Result.Error(
                ValidationException(
                    "Start date must be on or before end date.",
                    field = "startDate",
                    rule = "DATE_ORDER",
                ),
            )
        }
        return leaveRepository.insertLeaveRequest(
            request.copy(status = LeaveRequestStatus.PENDING),
        )
    }
}
