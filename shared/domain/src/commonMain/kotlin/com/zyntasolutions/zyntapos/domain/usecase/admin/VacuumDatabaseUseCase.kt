package com.zyntasolutions.zyntapos.domain.usecase.admin

import com.zyntasolutions.zyntapos.core.result.Result
import com.zyntasolutions.zyntapos.domain.model.PurgeResult
import com.zyntasolutions.zyntapos.domain.repository.SystemRepository

/**
 * Compacts the SQLite database file by running VACUUM.
 *
 * This operation can be slow (several seconds) on large databases and should
 * only be triggered by an admin explicitly, never automatically during business hours.
 */
class VacuumDatabaseUseCase(
    private val systemRepository: SystemRepository,
) {
    suspend operator fun invoke(): Result<PurgeResult> =
        systemRepository.vacuumDatabase()
}
