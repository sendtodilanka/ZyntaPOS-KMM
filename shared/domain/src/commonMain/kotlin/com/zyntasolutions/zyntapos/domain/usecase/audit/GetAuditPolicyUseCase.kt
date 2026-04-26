package com.zyntasolutions.zyntapos.domain.usecase.audit

import com.zyntasolutions.zyntapos.domain.model.AuditPolicy
import com.zyntasolutions.zyntapos.domain.repository.SettingsRepository

/**
 * Loads the current [AuditPolicy] from the generic settings store.
 *
 * Each category resolves to a `String?` row; missing rows fall back to the
 * default (`true` — every category audited). `roleChanges` always reads as
 * `true` regardless of the stored value to enforce the invariant defined
 * on [AuditPolicy].
 *
 * @param settingsRepository Generic key-value persistence backing.
 */
class GetAuditPolicyUseCase(private val settingsRepository: SettingsRepository) {

    suspend operator fun invoke(): AuditPolicy {
        suspend fun read(category: AuditPolicy.Category): Boolean {
            val raw = settingsRepository.get(AuditPolicyKeys.keyFor(category)) ?: return true
            // Treat anything other than an explicit "false" as enabled — keeps
            // forward-compat with future serialisation changes (e.g. "1" / "on").
            return !raw.equals("false", ignoreCase = true)
        }
        return AuditPolicy(
            login = read(AuditPolicy.Category.LOGIN),
            product = read(AuditPolicy.Category.PRODUCT),
            order = read(AuditPolicy.Category.ORDER),
            customer = read(AuditPolicy.Category.CUSTOMER),
            settings = read(AuditPolicy.Category.SETTINGS),
            payroll = read(AuditPolicy.Category.PAYROLL),
            backup = read(AuditPolicy.Category.BACKUP),
            roleChanges = true,
        )
    }
}
