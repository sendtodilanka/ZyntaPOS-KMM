package com.zyntasolutions.zyntapos.domain.usecase.settings

import com.zyntasolutions.zyntapos.core.result.Result
import com.zyntasolutions.zyntapos.core.result.ValidationException
import com.zyntasolutions.zyntapos.domain.model.DataRetentionPolicy
import com.zyntasolutions.zyntapos.domain.repository.SettingsRepository

/**
 * Persists the three retention windows of a [DataRetentionPolicy].
 *
 * Validates each field against its allow-list up-front (returns
 * [ValidationException] without writing) and then performs three
 * sequential writes; first repo error short-circuits.
 */
class SaveDataRetentionPolicyUseCase(private val settingsRepository: SettingsRepository) {

    suspend operator fun invoke(policy: DataRetentionPolicy): Result<Unit> {
        validate(policy)?.let { return Result.Error(it) }

        val writes: List<Pair<String, String>> = listOf(
            DataRetentionPolicyKeys.AUDIT_LOG_DAYS to policy.auditLogRetentionDays.toString(),
            DataRetentionPolicyKeys.SYNC_QUEUE_DAYS to policy.syncQueueRetentionDays.toString(),
            DataRetentionPolicyKeys.REPORT_MONTHS to policy.reportRetentionMonths.toString(),
        )
        for ((key, value) in writes) {
            when (val outcome = settingsRepository.set(key, value)) {
                is Result.Success -> { /* keep writing */ }
                is Result.Error -> return Result.Error(outcome.exception)
                Result.Loading -> { /* never returned by this repo */ }
            }
        }
        return Result.Success(Unit)
    }

    private fun validate(policy: DataRetentionPolicy): ValidationException? {
        if (policy.auditLogRetentionDays !in DataRetentionPolicy.ALLOWED_AUDIT_LOG_DAYS) {
            return ValidationException(
                "Audit log retention must be one of ${DataRetentionPolicy.ALLOWED_AUDIT_LOG_DAYS} days.",
                field = "auditLogRetentionDays",
                rule = "ENUM",
            )
        }
        if (policy.syncQueueRetentionDays !in DataRetentionPolicy.ALLOWED_SYNC_QUEUE_DAYS) {
            return ValidationException(
                "Sync queue retention must be one of ${DataRetentionPolicy.ALLOWED_SYNC_QUEUE_DAYS} days.",
                field = "syncQueueRetentionDays",
                rule = "ENUM",
            )
        }
        if (policy.reportRetentionMonths !in DataRetentionPolicy.ALLOWED_REPORT_MONTHS) {
            return ValidationException(
                "Report retention must be one of ${DataRetentionPolicy.ALLOWED_REPORT_MONTHS} months.",
                field = "reportRetentionMonths",
                rule = "ENUM",
            )
        }
        return null
    }
}
