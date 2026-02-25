package com.zyntasolutions.zyntapos.domain.model

/**
 * Metadata about a database backup.
 *
 * @property id Unique identifier for this backup.
 * @property fileName Backup file name.
 * @property filePath Local or remote path to the backup file.
 * @property sizeBytes Backup file size in bytes.
 * @property status Current status of the backup.
 * @property createdAt Epoch millis when the backup was started.
 * @property completedAt Epoch millis when the backup completed. Null if in progress.
 * @property schemaVersion Database schema version at time of backup.
 * @property appVersion Application version at time of backup.
 * @property errorMessage Error message if [status] = FAILED.
 */
data class BackupInfo(
    val id: String,
    val fileName: String,
    val filePath: String,
    val sizeBytes: Long,
    val status: BackupStatus,
    val createdAt: Long,
    val completedAt: Long? = null,
    val schemaVersion: Long,
    val appVersion: String,
    val errorMessage: String? = null,
) {
    /** Backup size in megabytes (2 decimal precision). */
    val sizeMb: Double get() = sizeBytes / (1024.0 * 1024.0)
}

/** Current status of a backup or restore operation. */
enum class BackupStatus {
    CREATING,
    SUCCESS,
    FAILED,
    RESTORING,
}
