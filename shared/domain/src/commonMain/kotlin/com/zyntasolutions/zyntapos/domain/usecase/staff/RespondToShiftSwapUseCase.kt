package com.zyntasolutions.zyntapos.domain.usecase.staff

import com.zyntasolutions.zyntapos.core.result.Result
import com.zyntasolutions.zyntapos.core.result.ValidationException
import com.zyntasolutions.zyntapos.domain.model.ShiftSwapStatus
import com.zyntasolutions.zyntapos.domain.repository.ShiftSwapRepository

/**
 * Allows the target employee to accept or decline a shift swap request.
 *
 * ### Business Rules
 * 1. The swap request must exist.
 * 2. The swap request must be in [ShiftSwapStatus.PENDING] status.
 * 3. Only the target employee may respond.
 *
 * @param id Swap request ID.
 * @param targetEmployeeId The employee responding (must match the request's target).
 * @param accept True to accept, false to reject.
 * @param updatedAt Epoch millis for the record update timestamp.
 */
class RespondToShiftSwapUseCase(
    private val shiftSwapRepository: ShiftSwapRepository,
) {
    suspend operator fun invoke(
        id: String,
        targetEmployeeId: String,
        accept: Boolean,
        updatedAt: Long,
    ): Result<Unit> {
        val existing = when (val result = shiftSwapRepository.getById(id)) {
            is Result.Success -> result.data
            is Result.Error -> return result
            is Result.Loading -> return Result.Loading
        }

        if (existing == null) {
            return Result.Error(
                ValidationException(
                    "Shift swap request not found.",
                    field = "id",
                    rule = "EXISTS",
                ),
            )
        }

        if (existing.status != ShiftSwapStatus.PENDING) {
            return Result.Error(
                ValidationException(
                    "Only pending swap requests can be responded to.",
                    field = "status",
                    rule = "STATUS_PENDING",
                ),
            )
        }

        if (existing.targetEmployeeId != targetEmployeeId) {
            return Result.Error(
                ValidationException(
                    "Only the target employee may respond to this swap request.",
                    field = "targetEmployeeId",
                    rule = "AUTHORIZED",
                ),
            )
        }

        val newStatus = if (accept) ShiftSwapStatus.TARGET_ACCEPTED else ShiftSwapStatus.REJECTED

        return shiftSwapRepository.updateStatus(
            id = id,
            status = newStatus,
            updatedAt = updatedAt,
        )
    }
}
