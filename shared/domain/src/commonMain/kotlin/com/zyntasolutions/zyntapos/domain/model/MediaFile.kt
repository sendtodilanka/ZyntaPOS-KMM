package com.zyntasolutions.zyntapos.domain.model

/**
 * A media asset (image or document) attached to a business entity.
 *
 * @property id Unique identifier (UUID v4).
 * @property fileName Original file name.
 * @property filePath Local storage path (device-relative).
 * @property remoteUrl URL after upload to backend. Null until synced.
 * @property fileType File category.
 * @property mimeType MIME type string (e.g., "image/jpeg").
 * @property fileSize Size in bytes.
 * @property thumbnailPath Local path to generated thumbnail.
 * @property entityType Polymorphic owner type ('Product', 'Category', 'Store', 'Employee').
 * @property entityId FK to the owning entity.
 * @property isPrimary Whether this is the primary image for the entity.
 * @property uploadedBy User ID who uploaded the file.
 * @property uploadStatus Current upload lifecycle state.
 * @property createdAt Epoch millis of record creation.
 * @property updatedAt Epoch millis of last update.
 */
data class MediaFile(
    val id: String,
    val fileName: String,
    val filePath: String,
    val remoteUrl: String? = null,
    val fileType: MediaFileType,
    val mimeType: String,
    val fileSize: Long,
    val thumbnailPath: String? = null,
    val entityType: String? = null,
    val entityId: String? = null,
    val isPrimary: Boolean = false,
    val uploadedBy: String,
    val uploadStatus: MediaUploadStatus = MediaUploadStatus.LOCAL,
    val createdAt: Long,
    val updatedAt: Long,
) {
    /** File size in kilobytes (2 decimal precision). */
    val fileSizeKb: Double get() = fileSize / 1024.0

    /** File size in megabytes (2 decimal precision). */
    val fileSizeMb: Double get() = fileSize / (1024.0 * 1024.0)

    /** True if this is an image file type. */
    val isImage: Boolean get() = fileType == MediaFileType.IMAGE

    /** The URL to display — prefers [remoteUrl], falls back to [filePath]. */
    val displayUrl: String get() = remoteUrl ?: filePath
}

/** Category of media file. */
enum class MediaFileType {
    IMAGE,
    DOCUMENT,
}

/** Upload lifecycle state of a media file. */
enum class MediaUploadStatus {
    LOCAL,
    UPLOADING,
    UPLOADED,
    FAILED,
}
