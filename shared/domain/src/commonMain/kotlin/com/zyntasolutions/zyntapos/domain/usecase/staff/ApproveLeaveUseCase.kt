package com.zyntasolutions.zyntapos.domain.usecase.staff

import com.zyntasolutions.zyntapos.core.result.Result
import com.zyntasolutions.zyntapos.core.result.ValidationException
import com.zyntasolutions.zyntapos.domain.model.LeaveRequestStatus
import com.zyntasolutions.zyntapos.domain.repository.LeaveRepository

/**
 * Approves or rejects a pending [LeaveRequest][com.zyntasolutions.zyntapos.domain.model.LeaveRequest].
 *
 * ### Business Rules
 * 1. The leave request must exist.
 * 2. The leave request must be in [LeaveRequestStatus.PENDING] status.
 * 3. When [approve] is `true`, status changes to [LeaveRequestStatus.APPROVED].
 * 4. When [approve] is `false`, status changes to [LeaveRequestStatus.REJECTED].
 *
 * @param id Leave request ID.
 * @param approve True to approve, false to reject.
 * @param approverNotes Optional notes from the approver.
 * @param updatedAt Epoch millis for the record update timestamp.
 */
class ApproveLeaveUseCase(
    private val leaveRepository: LeaveRepository,
) {
    suspend operator fun invoke(
        id: String,
        approve: Boolean,
        approverNotes: String?,
        updatedAt: Long,
    ): Result<Unit> {
        val existing = when (val result = leaveRepository.getLeaveRequestById(id)) {
            is Result.Success -> result.data
            is Result.Error -> return result
            is Result.Loading -> return Result.Loading
        }

        if (existing == null) {
            return Result.Error(
                ValidationException(
                    "Leave request not found.",
                    field = "id",
                    rule = "EXISTS",
                ),
            )
        }

        if (existing.status != LeaveRequestStatus.PENDING) {
            return Result.Error(
                ValidationException(
                    "Only pending leave requests can be approved or rejected.",
                    field = "status",
                    rule = "STATUS_PENDING",
                ),
            )
        }

        val newStatus = if (approve) LeaveRequestStatus.APPROVED else LeaveRequestStatus.REJECTED

        return leaveRepository.updateLeaveRequestStatus(
            id = id,
            status = newStatus,
            approverNotes = approverNotes,
            updatedAt = updatedAt,
        )
    }
}
