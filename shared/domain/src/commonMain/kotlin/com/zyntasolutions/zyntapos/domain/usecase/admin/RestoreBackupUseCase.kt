package com.zyntasolutions.zyntapos.domain.usecase.admin

import com.zyntasolutions.zyntapos.core.result.Result
import com.zyntasolutions.zyntapos.domain.repository.BackupRepository

/**
 * Restores the database from a previously created backup.
 *
 * WARNING: This operation replaces the live database. The application must be
 * restarted after a successful restore. Caller is responsible for prompting the user.
 *
 * @param backupId ID of the backup to restore from.
 */
class RestoreBackupUseCase(
    private val backupRepository: BackupRepository,
) {
    suspend operator fun invoke(backupId: String): Result<Unit> =
        backupRepository.restoreBackup(backupId)
}
