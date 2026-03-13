package com.zyntasolutions.zyntapos.data.repository

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import com.zyntasolutions.zyntapos.core.result.DatabaseException
import com.zyntasolutions.zyntapos.core.result.Result
import com.zyntasolutions.zyntapos.data.backup.BackupFileManager
import com.zyntasolutions.zyntapos.db.Backups
import com.zyntasolutions.zyntapos.db.ZyntaDatabase
import com.zyntasolutions.zyntapos.domain.model.BackupInfo
import com.zyntasolutions.zyntapos.domain.model.BackupStatus
import com.zyntasolutions.zyntapos.domain.repository.BackupRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlin.time.Clock

/**
 * [BackupRepository] implementation backed by [ZyntaDatabase] (backups table) and
 * [BackupFileManager] (platform-specific file I/O).
 *
 * ## Storage layout
 * - Backup metadata is persisted in the `backups` SQLDelight table across sessions.
 * - Backup files are stored in the platform-specific directory returned by
 *   [BackupFileManager.backupsDir]:
 *     - Android: `getExternalFilesDir("backups")` or `filesDir/backups`
 *     - Desktop: `~/.zyntapos/backups/` (macOS/Linux) or `%APPDATA%/ZyntaPOS/backups/`
 *
 * ## Restore note
 * After [restoreBackup], the DB driver must be closed and re-opened (or the app restarted)
 * for SQLite to use the restored file. The actual driver lifecycle is managed by the
 * application layer, not this repository.
 */
class BackupRepositoryImpl(
    private val db: ZyntaDatabase,
    private val fileManager: BackupFileManager,
) : BackupRepository {

    private val q get() = db.backupsQueries

    override fun getAll(): Flow<List<BackupInfo>> =
        q.getAll()
            .asFlow()
            .mapToList(Dispatchers.IO)
            .map { rows -> rows.map(::toDomain) }

    override suspend fun getById(id: String): Result<BackupInfo> =
        withContext(Dispatchers.IO) {
            val row = q.getById(id).executeAsOneOrNull()
                ?: return@withContext Result.Error(DatabaseException("Backup not found: $id"))
            Result.Success(toDomain(row))
        }

    override suspend fun createBackup(backupId: String, timestamp: Long): Result<BackupInfo> =
        withContext(Dispatchers.IO) {
            val fileName = "zyntapos-backup-${timestamp}.db"
            val now = Clock.System.now().toEpochMilliseconds()

            // Insert CREATING record so the UI can observe progress immediately
            runCatching {
                q.insert(
                    id             = backupId,
                    file_name      = fileName,
                    file_path      = "",
                    size_bytes     = 0L,
                    status         = BackupStatus.CREATING.name,
                    schema_version = CURRENT_SCHEMA_VERSION,
                    app_version    = APP_VERSION,
                    error_message  = null,
                    created_at     = timestamp,
                    completed_at   = null,
                )
            }.onFailure { t ->
                return@withContext Result.Error(DatabaseException("Failed to insert backup record: ${t.message}", cause = t))
            }

            // Perform actual file copy
            runCatching {
                val sizeBytes = fileManager.copyDbToBackup(fileName)
                val filePath  = "${fileManager.backupsDir()}/$fileName"
                q.updateStatus(
                    status        = BackupStatus.SUCCESS.name,
                    size_bytes    = sizeBytes,
                    file_path     = filePath,
                    error_message = null,
                    completed_at  = now,
                    id            = backupId,
                )
                BackupInfo(
                    id            = backupId,
                    fileName      = fileName,
                    filePath      = filePath,
                    sizeBytes     = sizeBytes,
                    status        = BackupStatus.SUCCESS,
                    createdAt     = timestamp,
                    completedAt   = now,
                    schemaVersion = CURRENT_SCHEMA_VERSION,
                    appVersion    = APP_VERSION,
                )
            }.fold(
                onSuccess = { Result.Success(it) },
                onFailure = { t ->
                    // Update to FAILED so UI shows error state
                    runCatching {
                        q.updateStatus(
                            status        = BackupStatus.FAILED.name,
                            size_bytes    = 0L,
                            file_path     = "",
                            error_message = t.message,
                            completed_at  = now,
                            id            = backupId,
                        )
                    }
                    Result.Error(DatabaseException(t.message ?: "createBackup failed", cause = t))
                },
            )
        }

    override suspend fun restoreBackup(backupId: String): Result<Unit> =
        withContext(Dispatchers.IO) {
            val row = q.getById(backupId).executeAsOneOrNull()
                ?: return@withContext Result.Error(DatabaseException("Backup not found: $backupId"))

            if (row.status != BackupStatus.SUCCESS.name) {
                return@withContext Result.Error(
                    DatabaseException("Cannot restore backup with status ${row.status}")
                )
            }

            // Mark as RESTORING
            q.updateStatus(
                status        = BackupStatus.RESTORING.name,
                size_bytes    = row.size_bytes,
                file_path     = row.file_path,
                error_message = null,
                completed_at  = row.completed_at,
                id            = backupId,
            )

            runCatching {
                fileManager.copyBackupToDb(row.file_name)
                // After copyBackupToDb, the DB file is replaced.
                // The application must be restarted for SQLite to use the new file.
            }.fold(
                onSuccess = { Result.Success(Unit) },
                onFailure = { t ->
                    // Revert to SUCCESS status since the restore attempt failed
                    runCatching {
                        q.updateStatus(
                            status        = BackupStatus.SUCCESS.name,
                            size_bytes    = row.size_bytes,
                            file_path     = row.file_path,
                            error_message = "Restore failed: ${t.message}",
                            completed_at  = row.completed_at,
                            id            = backupId,
                        )
                    }
                    Result.Error(DatabaseException(t.message ?: "restoreBackup failed", cause = t))
                },
            )
        }

    override suspend fun deleteBackup(id: String): Result<Unit> =
        withContext(Dispatchers.IO) {
            val row = q.getById(id).executeAsOneOrNull()
                ?: return@withContext Result.Error(DatabaseException("Backup not found: $id"))

            runCatching {
                // Delete file first, then metadata
                if (row.file_name.isNotBlank()) {
                    fileManager.deleteBackupFile(row.file_name)
                }
                q.delete(id)
            }.fold(
                onSuccess = { Result.Success(Unit) },
                onFailure = { t -> Result.Error(DatabaseException(t.message ?: "deleteBackup failed", cause = t)) },
            )
        }

    override suspend fun exportBackup(id: String, exportPath: String): Result<Unit> =
        withContext(Dispatchers.IO) {
            val row = q.getById(id).executeAsOneOrNull()
                ?: return@withContext Result.Error(DatabaseException("Backup not found: $id"))

            runCatching {
                fileManager.exportBackupFile(row.file_name, exportPath)
            }.fold(
                onSuccess = { Result.Success(Unit) },
                onFailure = { t -> Result.Error(DatabaseException(t.message ?: "exportBackup failed", cause = t)) },
            )
        }

    // ── Row → domain mapping ──────────────────────────────────────────────────

    private fun toDomain(row: Backups) = BackupInfo(
        id            = row.id,
        fileName      = row.file_name,
        filePath      = row.file_path,
        sizeBytes     = row.size_bytes,
        status        = runCatching { BackupStatus.valueOf(row.status) }.getOrDefault(BackupStatus.FAILED),
        createdAt     = row.created_at,
        completedAt   = row.completed_at,
        schemaVersion = row.schema_version,
        appVersion    = row.app_version,
        errorMessage  = row.error_message,
    )

    private companion object {
        const val CURRENT_SCHEMA_VERSION = 4L   // Increment when SQLDelight schema changes
        const val APP_VERSION            = "1.0.0"
    }
}
