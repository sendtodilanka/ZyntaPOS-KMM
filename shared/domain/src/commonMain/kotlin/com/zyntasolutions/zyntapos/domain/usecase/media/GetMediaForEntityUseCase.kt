package com.zyntasolutions.zyntapos.domain.usecase.media

import com.zyntasolutions.zyntapos.domain.model.MediaFile
import com.zyntasolutions.zyntapos.domain.repository.MediaRepository
import kotlinx.coroutines.flow.Flow

/**
 * Returns a reactive stream of media files attached to a business entity.
 *
 * @param entityType Entity class name (e.g., "Product", "Employee").
 * @param entityId Unique ID of the entity.
 */
class GetMediaForEntityUseCase(
    private val mediaRepository: MediaRepository,
) {
    operator fun invoke(entityType: String, entityId: String): Flow<List<MediaFile>> =
        mediaRepository.getByEntity(entityType, entityId)
}
