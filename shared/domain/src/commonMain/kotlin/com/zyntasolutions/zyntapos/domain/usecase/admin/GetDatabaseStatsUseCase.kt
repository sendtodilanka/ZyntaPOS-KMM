package com.zyntasolutions.zyntapos.domain.usecase.admin

import com.zyntasolutions.zyntapos.core.result.Result
import com.zyntasolutions.zyntapos.domain.model.DatabaseStats
import com.zyntasolutions.zyntapos.domain.repository.SystemRepository

/** Returns row counts and size information for all database tables. */
class GetDatabaseStatsUseCase(
    private val systemRepository: SystemRepository,
) {
    suspend operator fun invoke(): Result<DatabaseStats> =
        systemRepository.getDatabaseStats()
}
