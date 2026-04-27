package com.zyntasolutions.zyntapos.domain.usecase.settings

import com.zyntasolutions.zyntapos.domain.model.DataRetentionPolicy
import com.zyntasolutions.zyntapos.domain.repository.SettingsRepository

/**
 * Loads the current [DataRetentionPolicy] from the generic settings store.
 *
 * Missing or out-of-spec stored values fall back to the defaults defined on
 * [DataRetentionPolicy] — same defensive read pattern as
 * [GetSecurityPolicyUseCase].
 *
 * @param settingsRepository Generic key-value persistence backing.
 */
class GetDataRetentionPolicyUseCase(private val settingsRepository: SettingsRepository) {

    suspend operator fun invoke(): DataRetentionPolicy {
        val default = DataRetentionPolicy()

        val auditLog = settingsRepository.get(DataRetentionPolicyKeys.AUDIT_LOG_DAYS)
            ?.toIntOrNull()
            ?.takeIf { it in DataRetentionPolicy.ALLOWED_AUDIT_LOG_DAYS }
            ?: default.auditLogRetentionDays

        val syncQueue = settingsRepository.get(DataRetentionPolicyKeys.SYNC_QUEUE_DAYS)
            ?.toIntOrNull()
            ?.takeIf { it in DataRetentionPolicy.ALLOWED_SYNC_QUEUE_DAYS }
            ?: default.syncQueueRetentionDays

        val reports = settingsRepository.get(DataRetentionPolicyKeys.REPORT_MONTHS)
            ?.toIntOrNull()
            ?.takeIf { it in DataRetentionPolicy.ALLOWED_REPORT_MONTHS }
            ?: default.reportRetentionMonths

        return DataRetentionPolicy(
            auditLogRetentionDays = auditLog,
            syncQueueRetentionDays = syncQueue,
            reportRetentionMonths = reports,
        )
    }
}
