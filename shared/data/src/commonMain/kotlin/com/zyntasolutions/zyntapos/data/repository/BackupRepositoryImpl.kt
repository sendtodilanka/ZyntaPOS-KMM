package com.zyntasolutions.zyntapos.data.repository

import com.zyntasolutions.zyntapos.core.result.DatabaseException
import com.zyntasolutions.zyntapos.core.result.Result
import com.zyntasolutions.zyntapos.db.ZyntaDatabase
import com.zyntasolutions.zyntapos.domain.model.BackupInfo
import com.zyntasolutions.zyntapos.domain.model.BackupStatus
import com.zyntasolutions.zyntapos.domain.repository.BackupRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.datetime.Clock

/**
 * [BackupRepository] implementation — in-memory backup registry backed by [ZyntaDatabase].
 *
 * Phase 3 Sprint 5-7: The actual file-copy backup mechanism requires platform-specific
 * file I/O (`expect/actual`) which is implemented in Sprint 13-15. This implementation
 * tracks backup metadata in-memory (survives the session) and provides the reactive
 * [getAll] flow needed by the admin feature UI.
 *
 * The persistence of backup metadata will be moved to a `backups` SQLDelight table
 * in Sprint 13 when the admin feature module is scaffolded.
 */
class BackupRepositoryImpl(
    @Suppress("UNUSED_PARAMETER") private val db: ZyntaDatabase,
) : BackupRepository {

    private val _backups = MutableStateFlow<List<BackupInfo>>(emptyList())

    override fun getAll(): Flow<List<BackupInfo>> =
        _backups.asStateFlow().map { list -> list.sortedByDescending { it.createdAt } }

    override suspend fun getById(id: String): Result<BackupInfo> {
        val backup = _backups.value.find { it.id == id }
            ?: return Result.Error(DatabaseException("Backup not found: $id"))
        return Result.Success(backup)
    }

    override suspend fun createBackup(backupId: String, timestamp: Long): Result<BackupInfo> {
        return runCatching {
            val creating = BackupInfo(
                id = backupId,
                fileName = "zyntapos-backup-${timestamp}.db",
                filePath = "",          // Platform-specific path resolved in Sprint 13
                sizeBytes = 0L,
                status = BackupStatus.CREATING,
                createdAt = timestamp,
                completedAt = null,
                schemaVersion = 4L,     // Current DB schema version
                appVersion = "1.0.0",
                errorMessage = null,
            )
            _backups.value = _backups.value + creating

            // Phase 3 stub — mark SUCCESS immediately (file copy is Sprint 13)
            val completed = creating.copy(
                status = BackupStatus.SUCCESS,
                completedAt = Clock.System.now().toEpochMilliseconds(),
                sizeBytes = 1024L,      // Placeholder size
            )
            _backups.value = _backups.value.map { if (it.id == backupId) completed else it }
            completed
        }.fold(
            onSuccess = { Result.Success(it) },
            onFailure = { t -> Result.Error(DatabaseException(t.message ?: "createBackup failed", cause = t)) },
        )
    }

    override suspend fun restoreBackup(backupId: String): Result<Unit> {
        val backup = _backups.value.find { it.id == backupId }
            ?: return Result.Error(DatabaseException("Backup not found: $backupId"))
        if (backup.status != BackupStatus.SUCCESS) {
            return Result.Error(DatabaseException("Cannot restore backup with status ${backup.status}"))
        }
        // Full file-level restore implemented in Sprint 13 (platform expect/actual)
        return Result.Success(Unit)
    }

    override suspend fun deleteBackup(id: String): Result<Unit> {
        return runCatching {
            _backups.value = _backups.value.filter { it.id != id }
        }.fold(
            onSuccess = { Result.Success(Unit) },
            onFailure = { t -> Result.Error(DatabaseException(t.message ?: "deleteBackup failed", cause = t)) },
        )
    }

    override suspend fun exportBackup(id: String, exportPath: String): Result<Unit> {
        _backups.value.find { it.id == id }
            ?: return Result.Error(DatabaseException("Backup not found: $id"))
        // Platform-specific file export implemented in Sprint 13
        return Result.Success(Unit)
    }
}
