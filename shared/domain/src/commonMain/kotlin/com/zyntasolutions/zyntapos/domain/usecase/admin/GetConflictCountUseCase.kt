package com.zyntasolutions.zyntapos.domain.usecase.admin

import com.zyntasolutions.zyntapos.core.result.Result
import com.zyntasolutions.zyntapos.domain.repository.ConflictLogRepository

/** Returns the count of unresolved sync conflicts. Used for admin badge display. */
class GetConflictCountUseCase(
    private val conflictLogRepository: ConflictLogRepository,
) {
    suspend operator fun invoke(): Result<Int> =
        conflictLogRepository.getUnresolvedCount()
}
