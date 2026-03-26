package com.zyntasolutions.zyntapos.domain.usecase.staff

import com.zyntasolutions.zyntapos.core.result.Result
import com.zyntasolutions.zyntapos.core.result.ValidationException
import com.zyntasolutions.zyntapos.domain.model.ShiftSwapStatus
import com.zyntasolutions.zyntapos.domain.repository.ShiftRepository
import com.zyntasolutions.zyntapos.domain.repository.ShiftSwapRepository

/**
 * Manager approves a shift swap request that has already been accepted by the target employee.
 *
 * On approval the use case:
 * 1. Validates the request is in [ShiftSwapStatus.TARGET_ACCEPTED].
 * 2. Looks up both shifts from the schedule.
 * 3. Swaps the employee assignments on the two shifts.
 * 4. Marks the swap request as [ShiftSwapStatus.MANAGER_APPROVED].
 *
 * @param id Swap request ID.
 * @param managerNotes Optional notes from the approving manager.
 * @param updatedAt Epoch millis for the record update timestamp.
 */
class ApproveShiftSwapUseCase(
    private val shiftSwapRepository: ShiftSwapRepository,
    private val shiftRepository: ShiftRepository,
) {
    suspend operator fun invoke(
        id: String,
        managerNotes: String? = null,
        updatedAt: Long,
    ): Result<Unit> {
        // 1. Load the swap request
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

        if (existing.status != ShiftSwapStatus.TARGET_ACCEPTED) {
            return Result.Error(
                ValidationException(
                    "Only target-accepted swap requests can be approved by a manager.",
                    field = "status",
                    rule = "STATUS_TARGET_ACCEPTED",
                ),
            )
        }

        // 2. Look up both shifts — they must still exist
        val requestingShift = when (val r = shiftRepository.getById(existing.requestingShiftId)) {
            is Result.Success -> r.data
            is Result.Error -> return r
            is Result.Loading -> return Result.Loading
        } ?: return Result.Error(
            ValidationException("Requesting shift no longer exists.", field = "requestingShiftId", rule = "EXISTS"),
        )

        val targetShift = when (val r = shiftRepository.getById(existing.targetShiftId)) {
            is Result.Success -> r.data
            is Result.Error -> return r
            is Result.Loading -> return Result.Loading
        } ?: return Result.Error(
            ValidationException("Target shift no longer exists.", field = "targetShiftId", rule = "EXISTS"),
        )

        // 3. Swap employee assignments — requesting employee takes target's shift and vice versa
        val swappedRequesting = requestingShift.copy(
            employeeId = existing.targetEmployeeId,
            updatedAt = updatedAt,
        )
        val swappedTarget = targetShift.copy(
            employeeId = existing.requestingEmployeeId,
            updatedAt = updatedAt,
        )

        val updateReq = shiftRepository.update(swappedRequesting)
        if (updateReq is Result.Error) return updateReq

        val updateTgt = shiftRepository.update(swappedTarget)
        if (updateTgt is Result.Error) return updateTgt

        // 4. Approve the swap request
        val approveResult = shiftSwapRepository.updateStatus(
            id = id,
            status = ShiftSwapStatus.MANAGER_APPROVED,
            managerNotes = managerNotes,
            updatedAt = updatedAt,
        )
        if (approveResult is Result.Error) return approveResult

        return Result.Success(Unit)
    }
}
