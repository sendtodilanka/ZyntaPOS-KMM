package com.zyntasolutions.zyntapos.domain.usecase.staff

import com.zyntasolutions.zyntapos.core.result.Result
import com.zyntasolutions.zyntapos.domain.repository.ShiftRepository

/** Deletes a shift schedule entry by its unique ID. */
class DeleteShiftScheduleUseCase(
    private val shiftRepository: ShiftRepository,
) {
    suspend operator fun invoke(id: String): Result<Unit> =
        shiftRepository.deleteById(id)
}
