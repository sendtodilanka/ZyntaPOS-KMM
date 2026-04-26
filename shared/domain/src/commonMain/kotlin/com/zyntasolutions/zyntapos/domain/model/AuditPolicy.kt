package com.zyntasolutions.zyntapos.domain.model

/**
 * Which audit log categories are currently enabled (Sprint 23 task 23.9).
 *
 * Persisted as eight string-typed boolean keys in the `settings` table —
 * see `domain.usecase.audit.AuditPolicyKeys`. The defaults below are used
 * when a key is absent (all on; matches the Sprint 23 spec).
 *
 * ### `roleChanges` invariant
 * `roleChanges` is always `true`. The audit-policy use cases reject any
 * attempt to disable it; the toggle is rendered disabled in the UI. This
 * keeps the audit trail forensically sound — a malicious operator can't
 * silently amend their own permissions without leaving a trace.
 *
 * @property login        User login / logout events.
 * @property product      Product CRUD operations.
 * @property order        Order create / void / refund operations.
 * @property customer     Customer CRUD operations.
 * @property settings     Settings / configuration changes.
 * @property payroll      Payroll generation and payment.
 * @property backup       Backup and restore operations.
 * @property roleChanges  Role create / edit / delete (always `true`).
 */
data class AuditPolicy(
    val login: Boolean = true,
    val product: Boolean = true,
    val order: Boolean = true,
    val customer: Boolean = true,
    val settings: Boolean = true,
    val payroll: Boolean = true,
    val backup: Boolean = true,
    val roleChanges: Boolean = true,
) {
    /** Audit category enum — the toggle surface displayed in the UI. */
    enum class Category { LOGIN, PRODUCT, ORDER, CUSTOMER, SETTINGS, PAYROLL, BACKUP, ROLE_CHANGES }

    /** Returns the current enabled state for [category]. */
    fun isEnabled(category: Category): Boolean = when (category) {
        Category.LOGIN -> login
        Category.PRODUCT -> product
        Category.ORDER -> order
        Category.CUSTOMER -> customer
        Category.SETTINGS -> settings
        Category.PAYROLL -> payroll
        Category.BACKUP -> backup
        Category.ROLE_CHANGES -> roleChanges
    }

    /** Returns a new [AuditPolicy] with [category] flipped to [enabled]. */
    fun with(category: Category, enabled: Boolean): AuditPolicy = when (category) {
        Category.LOGIN -> copy(login = enabled)
        Category.PRODUCT -> copy(product = enabled)
        Category.ORDER -> copy(order = enabled)
        Category.CUSTOMER -> copy(customer = enabled)
        Category.SETTINGS -> copy(settings = enabled)
        Category.PAYROLL -> copy(payroll = enabled)
        Category.BACKUP -> copy(backup = enabled)
        Category.ROLE_CHANGES -> copy(roleChanges = enabled)
    }
}
