package com.zyntasolutions.zyntapos.feature.settings.backup

// ─────────────────────────────────────────────────────────────────────────────
// BackupService — Platform-specific database backup & restore
//
// Handles the actual file-system operations for database backup and restore.
// The SettingsViewModel delegates to this interface when the user triggers
// backup or restore actions.
//
// Platform implementations:
//   Android  → copies the encrypted DB file to app's external storage or
//              a user-selected directory via SAF (Storage Access Framework).
//   Desktop  → copies the DB file to a user-selected directory via
//              JFileChooser, or a default path under ~/.zyntapos/backups/.
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Result of a backup operation.
 *
 * @property filePath  The absolute path where the backup was saved.
 * @property sizeBytes Size of the backup file in bytes.
 */
data class BackupResult(
    val filePath: String,
    val sizeBytes: Long,
)

/**
 * Platform-specific backup and restore service.
 *
 * Implementations handle the underlying file I/O for copying the encrypted
 * SQLite database to and from backup locations.
 */
interface BackupService {

    /**
     * Creates a backup of the current database.
     *
     * @return [Result.success] with [BackupResult] containing the backup path,
     *         or [Result.failure] if the backup failed.
     */
    suspend fun createBackup(): Result<BackupResult>

    /**
     * Restores the database from a backup file.
     *
     * @param sourcePath The path to the backup file (content URI on Android,
     *                   absolute file path on Desktop).
     * @return [Result.success] if the restore completed successfully,
     *         [Result.failure] with a descriptive error on any I/O failure.
     */
    suspend fun restoreFromBackup(sourcePath: String): Result<Unit>

    /**
     * Returns the default backup directory path for the current platform.
     * Used for display purposes in the UI.
     */
    fun getDefaultBackupDirectory(): String
}
