package com.zyntasolutions.zyntapos.domain.usecase.fakes

import com.zyntasolutions.zyntapos.core.result.DatabaseException
import com.zyntasolutions.zyntapos.core.result.Result
import com.zyntasolutions.zyntapos.domain.model.MediaFile
import com.zyntasolutions.zyntapos.domain.model.MediaFileType
import com.zyntasolutions.zyntapos.domain.model.MediaUploadStatus
import com.zyntasolutions.zyntapos.domain.repository.MediaRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlin.time.Clock

// ─────────────────────────────────────────────────────────────────────────────
// MediaFile Fixtures
// ─────────────────────────────────────────────────────────────────────────────

fun buildMediaFile(
    id: String = "media-01",
    fileName: String = "product_image.jpg",
    filePath: String = "/images/product_image.jpg",
    fileType: MediaFileType = MediaFileType.IMAGE,
    mimeType: String = "image/jpeg",
    fileSize: Long = 102_400L,
    entityType: String? = "Product",
    entityId: String? = "prod-01",
    isPrimary: Boolean = false,
    uploadedBy: String = "user-01",
    uploadStatus: MediaUploadStatus = MediaUploadStatus.LOCAL,
) = MediaFile(
    id = id,
    fileName = fileName,
    filePath = filePath,
    fileType = fileType,
    mimeType = mimeType,
    fileSize = fileSize,
    entityType = entityType,
    entityId = entityId,
    isPrimary = isPrimary,
    uploadedBy = uploadedBy,
    uploadStatus = uploadStatus,
    createdAt = Clock.System.now().toEpochMilliseconds(),
    updatedAt = Clock.System.now().toEpochMilliseconds(),
)

// ─────────────────────────────────────────────────────────────────────────────
// Fake MediaRepository
// ─────────────────────────────────────────────────────────────────────────────

/**
 * In-memory fake for [MediaRepository].
 */
class FakeMediaRepository : MediaRepository {
    val files = mutableListOf<MediaFile>()
    var shouldFail = false
    var lastDeletedId: String? = null
    var lastDeletedAt: Long? = null

    private val _filesFlow = MutableStateFlow<List<MediaFile>>(emptyList())

    override fun getByEntity(entityType: String, entityId: String): Flow<List<MediaFile>> =
        _filesFlow.map { list ->
            list.filter { it.entityType == entityType && it.entityId == entityId }
        }

    override suspend fun getPrimaryForEntity(entityType: String, entityId: String): Result<MediaFile?> {
        if (shouldFail) return Result.Error(DatabaseException("DB error"))
        return Result.Success(
            files.find { it.entityType == entityType && it.entityId == entityId && it.isPrimary },
        )
    }

    override suspend fun getPendingUpload(): Result<List<MediaFile>> {
        if (shouldFail) return Result.Error(DatabaseException("DB error"))
        return Result.Success(files.filter { it.uploadStatus == MediaUploadStatus.LOCAL }.take(20))
    }

    override suspend fun getById(id: String): Result<MediaFile> {
        if (shouldFail) return Result.Error(DatabaseException("DB error"))
        return files.find { it.id == id }
            ?.let { Result.Success(it) }
            ?: Result.Error(DatabaseException("MediaFile not found: $id"))
    }

    override suspend fun insert(file: MediaFile): Result<Unit> {
        if (shouldFail) return Result.Error(DatabaseException("DB error"))
        files.add(file)
        _filesFlow.value = files.toList()
        return Result.Success(Unit)
    }

    override suspend fun updateUploadStatus(
        id: String,
        status: MediaUploadStatus,
        remoteUrl: String?,
        updatedAt: Long,
    ): Result<Unit> {
        if (shouldFail) return Result.Error(DatabaseException("DB error"))
        val idx = files.indexOfFirst { it.id == id }
        if (idx < 0) return Result.Error(DatabaseException("MediaFile not found: $id"))
        files[idx] = files[idx].copy(uploadStatus = status, remoteUrl = remoteUrl, updatedAt = updatedAt)
        _filesFlow.value = files.toList()
        return Result.Success(Unit)
    }

    override suspend fun setPrimary(id: String, entityType: String, entityId: String, updatedAt: Long): Result<Unit> {
        if (shouldFail) return Result.Error(DatabaseException("DB error"))
        // Clear existing primary flags for entity
        val updated = files.map { file ->
            if (file.entityType == entityType && file.entityId == entityId) {
                file.copy(isPrimary = file.id == id, updatedAt = updatedAt)
            } else {
                file
            }
        }
        files.clear()
        files.addAll(updated)
        _filesFlow.value = files.toList()
        return Result.Success(Unit)
    }

    override suspend fun delete(id: String, deletedAt: Long, updatedAt: Long): Result<Unit> {
        if (shouldFail) return Result.Error(DatabaseException("DB error"))
        val idx = files.indexOfFirst { it.id == id }
        if (idx < 0) return Result.Error(DatabaseException("MediaFile not found: $id"))
        // Soft delete: remove from the in-memory list (simulating filtered-out deleted records)
        files.removeAt(idx)
        _filesFlow.value = files.toList()
        lastDeletedId = id
        lastDeletedAt = deletedAt
        return Result.Success(Unit)
    }
}
