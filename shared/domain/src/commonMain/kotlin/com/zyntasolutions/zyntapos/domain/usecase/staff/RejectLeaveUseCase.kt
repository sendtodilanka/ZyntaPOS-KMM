package com.zyntasolutions.zyntapos.domain.usecase.staff

import com.zyntasolutions.zyntapos.core.result.Result
import com.zyntasolutions.zyntapos.core.result.ValidationException
import com.zyntasolutions.zyntapos.domain.model.LeaveStatus
import com.zyntasolutions.zyntapos.domain.repository.LeaveRepository

/**
 * Rejects a pending leave request.
 *
 * ### Business Rules
 * 1. [reason] must not be blank — employees deserve an explanation.
 */
class RejectLeaveUseCase(
    private val leaveRepository: LeaveRepository,
) {
    suspend operator fun invoke(
        id: String,
        rejectedBy: String,
        rejectedAt: Long,
        reason: String,
        updatedAt: Long,
    ): Result<Unit> {
        if (reason.isBlank()) {
            return Result.Error(
                ValidationException(
                    "A rejection reason must be provided.",
                    field = "reason",
                    rule = "REQUIRED",
                ),
            )
        }
        return leaveRepository.updateStatus(
            id = id,
            status = LeaveStatus.REJECTED,
            decidedBy = rejectedBy,
            decidedAt = rejectedAt,
            rejectionReason = reason,
            updatedAt = updatedAt,
        )
    }
}
