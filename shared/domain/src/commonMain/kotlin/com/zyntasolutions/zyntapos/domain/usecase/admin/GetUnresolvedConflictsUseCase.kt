package com.zyntasolutions.zyntapos.domain.usecase.admin

import com.zyntasolutions.zyntapos.domain.model.SyncConflict
import com.zyntasolutions.zyntapos.domain.repository.ConflictLogRepository
import kotlinx.coroutines.flow.Flow

/** Returns a reactive stream of all unresolved sync conflicts, oldest first. */
class GetUnresolvedConflictsUseCase(
    private val conflictLogRepository: ConflictLogRepository,
) {
    operator fun invoke(): Flow<List<SyncConflict>> =
        conflictLogRepository.getUnresolved()
}
