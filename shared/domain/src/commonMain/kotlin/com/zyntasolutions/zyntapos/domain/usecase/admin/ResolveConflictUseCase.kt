package com.zyntasolutions.zyntapos.domain.usecase.admin

import com.zyntasolutions.zyntapos.core.result.Result
import com.zyntasolutions.zyntapos.domain.model.SyncConflict
import com.zyntasolutions.zyntapos.domain.repository.ConflictLogRepository
import kotlin.time.Clock

/**
 * Resolves a sync conflict by recording the admin's chosen strategy and final value.
 *
 * @param conflictLogRepository The conflict log data source.
 */
class ResolveConflictUseCase(
    private val conflictLogRepository: ConflictLogRepository,
) {
    /**
     * @param conflictId  The conflict record to resolve.
     * @param resolvedBy  The resolution strategy (LOCAL, SERVER, MERGE, MANUAL).
     * @param resolution  The final value chosen (serialised as string).
     */
    suspend operator fun invoke(
        conflictId: String,
        resolvedBy: SyncConflict.Resolution,
        resolution: String,
    ): Result<Unit> = conflictLogRepository.resolve(
        id = conflictId,
        resolvedBy = resolvedBy,
        resolution = resolution,
        resolvedAt = Clock.System.now().toEpochMilliseconds(),
    )
}
