package com.zyntasolutions.zyntapos.domain.usecase.media

import com.zyntasolutions.zyntapos.core.result.Result
import com.zyntasolutions.zyntapos.domain.repository.MediaRepository

/**
 * Soft-deletes a media file record.
 *
 * The underlying file on disk or remote storage is NOT deleted by this use case;
 * cleanup is handled by a separate background maintenance job.
 */
class DeleteMediaFileUseCase(
    private val mediaRepository: MediaRepository,
) {
    suspend operator fun invoke(id: String, deletedAt: Long, updatedAt: Long): Result<Unit> =
        mediaRepository.delete(id, deletedAt, updatedAt)
}
