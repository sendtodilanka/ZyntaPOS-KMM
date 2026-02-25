package com.zyntasolutions.zyntapos.domain.usecase.admin

import com.zyntasolutions.zyntapos.domain.model.BackupInfo
import com.zyntasolutions.zyntapos.domain.repository.BackupRepository
import kotlinx.coroutines.flow.Flow

/** Returns a reactive stream of all available backups, most recent first. */
class GetBackupsUseCase(
    private val backupRepository: BackupRepository,
) {
    operator fun invoke(): Flow<List<BackupInfo>> =
        backupRepository.getAll()
}
