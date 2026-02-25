package com.zyntasolutions.zyntapos.domain.repository

import com.zyntasolutions.zyntapos.core.result.Result
import com.zyntasolutions.zyntapos.domain.model.BackupInfo
import kotlinx.coroutines.flow.Flow

/**
 * Contract for database backup and restore operations.
 */
interface BackupRepository {

    /** Emits the list of available backups, most recent first. Re-emits on change. */
    fun getAll(): Flow<List<BackupInfo>>

    /** Returns a single backup record by [id]. */
    suspend fun getById(id: String): Result<BackupInfo>

    /**
     * Creates a full database backup.
     *
     * The backup is written to the platform-specific backup directory.
     * Progress is tracked via the returned [BackupInfo] in the [getAll] flow.
     *
     * @return [BackupInfo] with status CREATING initially, updated to SUCCESS/FAILED.
     */
    suspend fun createBackup(backupId: String, timestamp: Long): Result<BackupInfo>

    /**
     * Restores the database from a backup identified by [backupId].
     *
     * WARNING: This replaces the current database. The app should restart after restore.
     */
    suspend fun restoreBackup(backupId: String): Result<Unit>

    /** Deletes a backup file and its metadata record. */
    suspend fun deleteBackup(id: String): Result<Unit>

    /** Exports a backup to an external location (e.g., cloud storage, USB). */
    suspend fun exportBackup(id: String, exportPath: String): Result<Unit>
}
