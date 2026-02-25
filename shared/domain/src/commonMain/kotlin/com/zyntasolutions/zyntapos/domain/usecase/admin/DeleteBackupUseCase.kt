package com.zyntasolutions.zyntapos.domain.usecase.admin

import com.zyntasolutions.zyntapos.core.result.Result
import com.zyntasolutions.zyntapos.domain.repository.BackupRepository

/** Permanently deletes a backup file and its metadata record. */
class DeleteBackupUseCase(
    private val backupRepository: BackupRepository,
) {
    suspend operator fun invoke(id: String): Result<Unit> =
        backupRepository.deleteBackup(id)
}
