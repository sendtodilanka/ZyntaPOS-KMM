package com.zyntasolutions.zyntapos.domain.usecase.staff

import com.zyntasolutions.zyntapos.core.result.Result
import com.zyntasolutions.zyntapos.domain.model.LeaveStatus
import com.zyntasolutions.zyntapos.domain.repository.LeaveRepository

/**
 * Approves a pending leave request.
 *
 * @param id Leave record ID.
 * @param approvedBy User ID of the approver.
 * @param approvedAt Epoch millis of the approval.
 * @param updatedAt Epoch millis for the record update timestamp.
 */
class ApproveLeaveUseCase(
    private val leaveRepository: LeaveRepository,
) {
    suspend operator fun invoke(
        id: String,
        approvedBy: String,
        approvedAt: Long,
        updatedAt: Long,
    ): Result<Unit> =
        leaveRepository.updateStatus(
            id = id,
            status = LeaveStatus.APPROVED,
            decidedBy = approvedBy,
            decidedAt = approvedAt,
            updatedAt = updatedAt,
        )
}
