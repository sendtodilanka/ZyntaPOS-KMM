package com.zyntasolutions.zyntapos.domain.model

/**
 * All granular actions that can be authorized within the ZyntaPOS system.
 *
 * Each permission maps to a specific business operation. The [rolePermissions]
 * map defines the default set of permissions granted to each [Role]. Use
 * [com.zyntasolutions.zyntapos.domain.usecase.auth.CheckPermissionUseCase]
 * at runtime to evaluate access for a logged-in user.
 */
enum class Permission {
    // ── Reporting ─────────────────────────────────────────────────────────────
    /** View sales, stock, and financial reports. */
    VIEW_REPORTS,

    /** Export reports to CSV / PDF. */
    EXPORT_REPORTS,

    // ── POS Operations ────────────────────────────────────────────────────────
    /** Process a new sale transaction. */
    PROCESS_SALE,

    /** Void (cancel) a completed order. */
    VOID_ORDER,

    /** Apply an item-level or order-level discount. */
    APPLY_DISCOUNT,

    /** Place an order on hold for later retrieval. */
    HOLD_ORDER,

    /** Issue a refund / return transaction. */
    PROCESS_REFUND,

    // ── Register ──────────────────────────────────────────────────────────────
    /** Open a new cash-register session. */
    OPEN_REGISTER,

    /** Close the current cash-register session and generate Z-Report. */
    CLOSE_REGISTER,

    /** Record petty-cash IN / OUT movements within a session. */
    RECORD_CASH_MOVEMENT,

    // ── Inventory ─────────────────────────────────────────────────────────────
    /** Create, edit, and delete products. */
    MANAGE_PRODUCTS,

    /** Create, edit, and delete product categories. */
    MANAGE_CATEGORIES,

    /** Perform manual stock adjustments (increase / decrease / transfer). */
    ADJUST_STOCK,

    /** Manage suppliers and purchase-order records. */
    MANAGE_SUPPLIERS,

    // ── User & Settings ───────────────────────────────────────────────────────
    /** Create, edit, deactivate user accounts and assign roles. */
    MANAGE_USERS,

    /** Access and modify application-wide settings. */
    MANAGE_SETTINGS,

    /** Manage tax groups and tax rates. */
    MANAGE_TAX,

    /** Configure printer and hardware settings. */
    MANAGE_HARDWARE,

    // ── Customer ──────────────────────────────────────────────────────────────
    /** View, create, and edit customer profiles. */
    MANAGE_CUSTOMERS,

    // ── Security / Audit ──────────────────────────────────────────────────────
    /** View the security audit log. */
    VIEW_AUDIT_LOG,

    /** Trigger manual data backup or restore. */
    MANAGE_BACKUP,
    ;

    companion object {
        /**
         * Default permission set granted to each [Role].
         *
         * This map is the single source of truth for role-based access control.
         * It is consumed by [com.zyntasolutions.zyntapos.domain.security.RbacEngine].
         *
         * Permissions are additive — higher roles include all lower-role permissions
         * plus additional ones.
         */
        val rolePermissions: Map<Role, Set<Permission>> = mapOf(
            Role.ADMIN to entries.toSet(), // all permissions

            Role.STORE_MANAGER to setOf(
                VIEW_REPORTS, EXPORT_REPORTS,
                PROCESS_SALE, VOID_ORDER, APPLY_DISCOUNT, HOLD_ORDER, PROCESS_REFUND,
                OPEN_REGISTER, CLOSE_REGISTER, RECORD_CASH_MOVEMENT,
                MANAGE_PRODUCTS, MANAGE_CATEGORIES, ADJUST_STOCK, MANAGE_SUPPLIERS,
                MANAGE_USERS, MANAGE_SETTINGS, MANAGE_TAX, MANAGE_HARDWARE,
                MANAGE_CUSTOMERS,
                VIEW_AUDIT_LOG,
            ),

            Role.CASHIER to setOf(
                PROCESS_SALE, VOID_ORDER, APPLY_DISCOUNT, HOLD_ORDER, PROCESS_REFUND,
                OPEN_REGISTER, CLOSE_REGISTER, RECORD_CASH_MOVEMENT,
                MANAGE_CUSTOMERS,
            ),

            Role.ACCOUNTANT to setOf(
                VIEW_REPORTS, EXPORT_REPORTS,
                VIEW_AUDIT_LOG,
            ),

            Role.STOCK_MANAGER to setOf(
                MANAGE_PRODUCTS, MANAGE_CATEGORIES, ADJUST_STOCK, MANAGE_SUPPLIERS,
                VIEW_REPORTS,
            ),
        )
    }
}
