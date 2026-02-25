package com.zyntasolutions.zyntapos.data.repository

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import com.zyntasolutions.zyntapos.core.result.DatabaseException
import com.zyntasolutions.zyntapos.core.result.Result
import com.zyntasolutions.zyntapos.data.local.SyncEnqueuer
import com.zyntasolutions.zyntapos.db.Media_files
import com.zyntasolutions.zyntapos.db.ZyntaDatabase
import com.zyntasolutions.zyntapos.domain.model.MediaFile
import com.zyntasolutions.zyntapos.domain.model.MediaFileType
import com.zyntasolutions.zyntapos.domain.model.MediaUploadStatus
import com.zyntasolutions.zyntapos.domain.model.SyncOperation
import com.zyntasolutions.zyntapos.domain.repository.MediaRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.datetime.Clock

class MediaRepositoryImpl(
    private val db: ZyntaDatabase,
    private val syncEnqueuer: SyncEnqueuer,
) : MediaRepository {

    private val q get() = db.media_filesQueries

    override fun getByEntity(entityType: String, entityId: String): Flow<List<MediaFile>> =
        q.selectByEntity(entityType, entityId)
            .asFlow()
            .mapToList(Dispatchers.IO)
            .map { rows -> rows.map(::toDomain) }

    override suspend fun getPrimaryForEntity(
        entityType: String,
        entityId: String,
    ): Result<MediaFile?> = withContext(Dispatchers.IO) {
        runCatching {
            q.selectPrimaryForEntity(entityType, entityId)
                .executeAsOneOrNull()
                ?.let(::toDomain)
        }.fold(
            onSuccess = { Result.Success(it) },
            onFailure = { t -> Result.Error(DatabaseException(t.message ?: "DB error", cause = t)) },
        )
    }

    override suspend fun getPendingUpload(): Result<List<MediaFile>> = withContext(Dispatchers.IO) {
        runCatching {
            q.selectPendingUpload().executeAsList().map(::toDomain)
        }.fold(
            onSuccess = { Result.Success(it) },
            onFailure = { t -> Result.Error(DatabaseException(t.message ?: "DB error", cause = t)) },
        )
    }

    override suspend fun getById(id: String): Result<MediaFile> = withContext(Dispatchers.IO) {
        runCatching {
            q.selectById(id).executeAsOneOrNull()
                ?: return@withContext Result.Error(DatabaseException("Media file not found: $id"))
        }.fold(
            onSuccess = { Result.Success(toDomain(it)) },
            onFailure = { t -> Result.Error(DatabaseException(t.message ?: "DB error", cause = t)) },
        )
    }

    override suspend fun insert(file: MediaFile): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val now = Clock.System.now().toEpochMilliseconds()
            db.transaction {
                q.insertMediaFile(
                    id = file.id,
                    file_name = file.fileName,
                    file_path = file.filePath,
                    remote_url = file.remoteUrl,
                    file_type = file.fileType.name,
                    mime_type = file.mimeType,
                    file_size = file.fileSize,
                    thumbnail_path = file.thumbnailPath,
                    entity_type = file.entityType,
                    entity_id = file.entityId,
                    is_primary = if (file.isPrimary) 1L else 0L,
                    uploaded_by = file.uploadedBy,
                    upload_status = file.uploadStatus.name,
                    created_at = now,
                    updated_at = now,
                    deleted_at = null,
                    sync_status = "PENDING",
                )
                syncEnqueuer.enqueue(
                    SyncOperation.EntityType.MEDIA_FILE,
                    file.id,
                    SyncOperation.Operation.INSERT,
                )
            }
        }.fold(
            onSuccess = { Result.Success(Unit) },
            onFailure = { t -> Result.Error(DatabaseException(t.message ?: "Insert failed", cause = t)) },
        )
    }

    override suspend fun updateUploadStatus(
        id: String,
        status: MediaUploadStatus,
        remoteUrl: String?,
        updatedAt: Long,
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            db.transaction {
                q.updateUploadStatus(
                    upload_status = status.name,
                    remote_url = remoteUrl,
                    updated_at = updatedAt,
                    id = id,
                )
                syncEnqueuer.enqueue(
                    SyncOperation.EntityType.MEDIA_FILE,
                    id,
                    SyncOperation.Operation.UPDATE,
                )
            }
        }.fold(
            onSuccess = { Result.Success(Unit) },
            onFailure = { t -> Result.Error(DatabaseException(t.message ?: "updateUploadStatus failed", cause = t)) },
        )
    }

    override suspend fun setPrimary(
        id: String,
        entityType: String,
        entityId: String,
        updatedAt: Long,
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            db.transaction {
                // Clear existing primary flag for the entity
                q.clearPrimaryForEntity(updated_at = updatedAt, entity_type = entityType, entity_id = entityId)
                // Set this file as primary
                q.setPrimary(is_primary = 1L, updated_at = updatedAt, id = id)
                syncEnqueuer.enqueue(
                    SyncOperation.EntityType.MEDIA_FILE,
                    id,
                    SyncOperation.Operation.UPDATE,
                )
            }
        }.fold(
            onSuccess = { Result.Success(Unit) },
            onFailure = { t -> Result.Error(DatabaseException(t.message ?: "setPrimary failed", cause = t)) },
        )
    }

    override suspend fun delete(id: String, deletedAt: Long, updatedAt: Long): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                db.transaction {
                    q.softDelete(deleted_at = deletedAt, updated_at = updatedAt, id = id)
                    syncEnqueuer.enqueue(
                        SyncOperation.EntityType.MEDIA_FILE,
                        id,
                        SyncOperation.Operation.DELETE,
                    )
                }
            }.fold(
                onSuccess = { Result.Success(Unit) },
                onFailure = { t -> Result.Error(DatabaseException(t.message ?: "Delete failed", cause = t)) },
            )
        }

    // ── Mapping ───────────────────────────────────────────────────────────────

    private fun toDomain(row: Media_files) = MediaFile(
        id = row.id,
        fileName = row.file_name,
        filePath = row.file_path,
        remoteUrl = row.remote_url,
        fileType = runCatching { MediaFileType.valueOf(row.file_type) }.getOrDefault(MediaFileType.IMAGE),
        mimeType = row.mime_type,
        fileSize = row.file_size,
        thumbnailPath = row.thumbnail_path,
        entityType = row.entity_type,
        entityId = row.entity_id,
        isPrimary = row.is_primary == 1L,
        uploadedBy = row.uploaded_by,
        uploadStatus = runCatching { MediaUploadStatus.valueOf(row.upload_status) }
            .getOrDefault(MediaUploadStatus.LOCAL),
        createdAt = row.created_at,
        updatedAt = row.updated_at,
    )
}
