package com.zyntasolutions.zyntapos.domain.repository

import com.zyntasolutions.zyntapos.core.result.Result
import com.zyntasolutions.zyntapos.domain.model.MediaFile
import com.zyntasolutions.zyntapos.domain.model.MediaUploadStatus
import kotlinx.coroutines.flow.Flow

/**
 * Contract for media file asset management.
 */
interface MediaRepository {

    /** Emits all media files for the given [entityType]/[entityId], primary first. Re-emits on change. */
    fun getByEntity(entityType: String, entityId: String): Flow<List<MediaFile>>

    /**
     * Returns the primary media file for an entity.
     * Returns null inside [Result.Success] if no primary is set.
     */
    suspend fun getPrimaryForEntity(entityType: String, entityId: String): Result<MediaFile?>

    /** Returns up to 20 files with [MediaUploadStatus.LOCAL] status (pending upload). */
    suspend fun getPendingUpload(): Result<List<MediaFile>>

    /** Returns a single media file by [id]. */
    suspend fun getById(id: String): Result<MediaFile>

    /** Inserts a new media file record. */
    suspend fun insert(file: MediaFile): Result<Unit>

    /**
     * Updates the upload status and remote URL after a successful backend upload.
     */
    suspend fun updateUploadStatus(
        id: String,
        status: MediaUploadStatus,
        remoteUrl: String? = null,
        updatedAt: Long,
    ): Result<Unit>

    /**
     * Clears the primary flag for all files of [entityType]/[entityId],
     * then sets [id] as primary.
     */
    suspend fun setPrimary(id: String, entityType: String, entityId: String, updatedAt: Long): Result<Unit>

    /** Soft-deletes the media file record. */
    suspend fun delete(id: String, deletedAt: Long, updatedAt: Long): Result<Unit>
}
