package com.zyntasolutions.zyntapos.domain.model

/**
 * Enumerates every feature module available in ZyntaPOS.
 *
 * Each entry declares:
 * - [edition] — the minimum [ZyntaEdition] required to activate the feature.
 * - [requiredPermissions] — the [Permission]s the signed-in user must hold
 *   before the feature UI is unlocked. An empty set means any authenticated
 *   user can access the feature (subject to edition gating).
 *
 * STANDARD features are always enabled after [FeatureRegistryRepository.initDefaults].
 * PREMIUM features are enabled by default for non-STANDARD deployments.
 * ENTERPRISE features start disabled and must be explicitly activated.
 */
enum class ZyntaFeature(
    val edition: ZyntaEdition,
    val requiredPermissions: Set<Permission>,
) {
    // ── STANDARD (8) ─────────────────────────────────────────────────────────
    AUTH(ZyntaEdition.STANDARD, emptySet()),
    POS_CORE(ZyntaEdition.STANDARD, setOf(Permission.PROCESS_SALE)),
    INVENTORY_CORE(ZyntaEdition.STANDARD, setOf(Permission.MANAGE_PRODUCTS)),
    REGISTER(ZyntaEdition.STANDARD, setOf(Permission.OPEN_REGISTER)),
    SETTINGS(ZyntaEdition.STANDARD, setOf(Permission.MANAGE_USERS)),
    REPORTS_STANDARD(ZyntaEdition.STANDARD, setOf(Permission.VIEW_REPORTS)),
    DASHBOARD(ZyntaEdition.STANDARD, emptySet()),
    ONBOARDING(ZyntaEdition.STANDARD, emptySet()),

    // ── PREMIUM (8) ──────────────────────────────────────────────────────────
    POS_ADVANCED(ZyntaEdition.PREMIUM, setOf(Permission.PROCESS_SALE)),
    INVENTORY_ADVANCED(ZyntaEdition.PREMIUM, setOf(Permission.MANAGE_PRODUCTS)),
    REGISTER_ADVANCED(ZyntaEdition.PREMIUM, setOf(Permission.OPEN_REGISTER)),
    COUPONS(ZyntaEdition.PREMIUM, setOf(Permission.MANAGE_COUPONS)),
    CRM_LOYALTY(ZyntaEdition.PREMIUM, setOf(Permission.MANAGE_CUSTOMERS)),
    EXPENSES(ZyntaEdition.PREMIUM, setOf(Permission.MANAGE_EXPENSES)),
    MEDIA(ZyntaEdition.PREMIUM, setOf(Permission.MANAGE_PRODUCTS)),
    REPORTS_PREMIUM(ZyntaEdition.PREMIUM, setOf(Permission.VIEW_REPORTS)),

    // ── ENTERPRISE (7) ───────────────────────────────────────────────────────
    STAFF_HR(ZyntaEdition.ENTERPRISE, setOf(Permission.MANAGE_STAFF)),
    ACCOUNTING(ZyntaEdition.ENTERPRISE, setOf(Permission.MANAGE_ACCOUNTING)),
    E_INVOICE(ZyntaEdition.ENTERPRISE, setOf(Permission.MANAGE_ACCOUNTING)),
    ADMIN(ZyntaEdition.ENTERPRISE, setOf(Permission.ADMIN_ACCESS)),
    MULTISTORE(ZyntaEdition.ENTERPRISE, setOf(Permission.MANAGE_WAREHOUSES)),
    CUSTOM_RBAC(ZyntaEdition.ENTERPRISE, setOf(Permission.ADMIN_ACCESS)),
    REPORTS_ENTERPRISE(ZyntaEdition.ENTERPRISE, setOf(Permission.VIEW_REPORTS)),
}
