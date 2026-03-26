package com.zyntasolutions.zyntapos.domain.usecase.staff

import com.zyntasolutions.zyntapos.core.result.Result
import com.zyntasolutions.zyntapos.core.result.ValidationException
import com.zyntasolutions.zyntapos.domain.model.ShiftSwapRequest
import com.zyntasolutions.zyntapos.domain.repository.ShiftSwapRepository

/**
 * Creates a new shift swap request.
 *
 * ### Business Rules
 * 1. Requesting and target employees must be different.
 * 2. Requesting and target shifts must be different.
 * 3. A reason must be provided.
 */
class RequestShiftSwapUseCase(
    private val shiftSwapRepository: ShiftSwapRepository,
) {
    suspend operator fun invoke(request: ShiftSwapRequest): Result<Unit> {
        if (request.requestingEmployeeId == request.targetEmployeeId) {
            return Result.Error(
                ValidationException(
                    "Cannot swap a shift with yourself.",
                    field = "targetEmployeeId",
                    rule = "DIFFERENT_EMPLOYEE",
                ),
            )
        }
        if (request.requestingShiftId == request.targetShiftId) {
            return Result.Error(
                ValidationException(
                    "Requesting and target shifts must be different.",
                    field = "targetShiftId",
                    rule = "DIFFERENT_SHIFT",
                ),
            )
        }
        if (request.reason.isBlank()) {
            return Result.Error(
                ValidationException(
                    "A reason for the swap request is required.",
                    field = "reason",
                    rule = "REQUIRED",
                ),
            )
        }
        return shiftSwapRepository.insert(request)
    }
}
