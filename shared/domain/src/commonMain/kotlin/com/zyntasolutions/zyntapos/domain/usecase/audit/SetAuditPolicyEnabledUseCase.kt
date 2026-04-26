package com.zyntasolutions.zyntapos.domain.usecase.audit

import com.zyntasolutions.zyntapos.core.result.Result
import com.zyntasolutions.zyntapos.core.result.ValidationException
import com.zyntasolutions.zyntapos.domain.model.AuditPolicy
import com.zyntasolutions.zyntapos.domain.repository.SettingsRepository

/**
 * Toggles a single audit-policy [AuditPolicy.Category] on or off.
 *
 * ### Validation
 * Disabling [AuditPolicy.Category.ROLE_CHANGES] is rejected with a
 * [ValidationException] so the audit trail for permission edits cannot
 * be silenced — see the invariant doc on [AuditPolicy].
 *
 * @param settingsRepository Generic key-value persistence backing.
 */
class SetAuditPolicyEnabledUseCase(private val settingsRepository: SettingsRepository) {

    suspend operator fun invoke(category: AuditPolicy.Category, enabled: Boolean): Result<Unit> {
        if (category == AuditPolicy.Category.ROLE_CHANGES && !enabled) {
            return Result.Error(
                ValidationException(
                    "Audit logging for role changes cannot be disabled.",
                    field = "category",
                    rule = "ROLE_CHANGES_LOCKED",
                ),
            )
        }
        return settingsRepository.set(AuditPolicyKeys.keyFor(category), enabled.toString())
    }
}
