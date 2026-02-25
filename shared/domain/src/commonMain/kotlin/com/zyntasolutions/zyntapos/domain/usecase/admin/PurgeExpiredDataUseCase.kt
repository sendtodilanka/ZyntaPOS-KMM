package com.zyntasolutions.zyntapos.domain.usecase.admin

import com.zyntasolutions.zyntapos.core.result.Result
import com.zyntasolutions.zyntapos.domain.model.PurgeResult
import com.zyntasolutions.zyntapos.domain.repository.SystemRepository

/**
 * Permanently removes soft-deleted records older than the specified retention period.
 *
 * @param olderThanMillis Purge records with deleted_at older than this epoch millis threshold.
 */
class PurgeExpiredDataUseCase(
    private val systemRepository: SystemRepository,
) {
    suspend operator fun invoke(olderThanMillis: Long): Result<PurgeResult> =
        systemRepository.purgeExpiredData(olderThanMillis)
}
