package com.zyntasolutions.zyntapos.domain.usecase.admin

import com.zyntasolutions.zyntapos.core.result.Result
import com.zyntasolutions.zyntapos.domain.model.SystemHealth
import com.zyntasolutions.zyntapos.domain.repository.SystemRepository

/** Returns a current snapshot of system health metrics. */
class GetSystemHealthUseCase(
    private val systemRepository: SystemRepository,
) {
    suspend operator fun invoke(): Result<SystemHealth> =
        systemRepository.getSystemHealth()
}
