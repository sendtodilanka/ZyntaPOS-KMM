package com.zyntasolutions.zyntapos.domain.usecase.admin

import com.zyntasolutions.zyntapos.core.result.Result
import com.zyntasolutions.zyntapos.domain.model.BackupInfo
import com.zyntasolutions.zyntapos.domain.repository.BackupRepository

/**
 * Creates a full database backup.
 *
 * @param backupId Unique identifier for this backup (UUID v4).
 * @param timestamp Epoch millis when the backup is initiated.
 */
class CreateBackupUseCase(
    private val backupRepository: BackupRepository,
) {
    suspend operator fun invoke(backupId: String, timestamp: Long): Result<BackupInfo> =
        backupRepository.createBackup(backupId, timestamp)
}
