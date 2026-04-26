package com.zyntasolutions.zyntapos.domain.usecase.audit

import com.zyntasolutions.zyntapos.domain.model.AuditPolicy

/**
 * Key namespace for [AuditPolicy] entries in the generic `settings` table.
 *
 * Storing the audit policy as individual `audit.{category}.enabled` rows
 * rather than a single JSON blob keeps the stable cross-platform shape
 * shared by every other settings entry; it also lets `observe(key)`
 * reactivity be added later for any single category without a custom
 * encoding.
 */
internal object AuditPolicyKeys {

    fun keyFor(category: AuditPolicy.Category): String = when (category) {
        AuditPolicy.Category.LOGIN -> "audit.login.enabled"
        AuditPolicy.Category.PRODUCT -> "audit.product.enabled"
        AuditPolicy.Category.ORDER -> "audit.order.enabled"
        AuditPolicy.Category.CUSTOMER -> "audit.customer.enabled"
        AuditPolicy.Category.SETTINGS -> "audit.settings.enabled"
        AuditPolicy.Category.PAYROLL -> "audit.payroll.enabled"
        AuditPolicy.Category.BACKUP -> "audit.backup.enabled"
        AuditPolicy.Category.ROLE_CHANGES -> "audit.role.enabled"
    }
}
