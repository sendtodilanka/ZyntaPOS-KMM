package com.zyntasolutions.zyntapos.domain.usecase.reports.enterprise

import com.zyntasolutions.zyntapos.domain.model.BackupInfo
import com.zyntasolutions.zyntapos.domain.repository.BackupRepository
import kotlinx.coroutines.flow.Flow

/**
 * Generates a backup history report listing all available database backups.
 *
 * Returns all [BackupInfo] records ordered by creation time (most recent first),
 * including backup status, file size, schema version, and completion timestamps.
 * Useful for administrators auditing backup compliance and storage usage.
 *
 * @param backupRepository Source for backup history records.
 */
class GenerateBackupHistoryReportUseCase(
    private val backupRepository: BackupRepository,
) {
    /**
     * @return A [Flow] emitting the list of [BackupInfo] records, most recent first.
     *         Re-emits whenever a new backup is created or an existing one changes status.
     */
    operator fun invoke(): Flow<List<BackupInfo>> = backupRepository.getAll()
}
