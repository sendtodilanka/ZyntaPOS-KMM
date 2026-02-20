package com.zyntasolutions.zyntapos.domain.model

/**
 * Defines the roles a user can hold within the ZyntaPOS system.
 *
 * Roles are ordered from highest privilege (ADMIN) to lowest (STOCK_MANAGER).
 * Role-to-permission mappings are defined in [Permission.rolePermissions].
 */
enum class Role {
    /** Full system access — manage users, settings, reports, and all operations. */
    ADMIN,

    /** Store-level management — inventory, staff oversight, reports. */
    STORE_MANAGER,

    /** Front-of-house POS operations — process sales, handle payments. */
    CASHIER,

    /** Read-only financial reporting and export access. */
    ACCOUNTANT,

    /** Inventory management — stock adjustments, supplier management. */
    STOCK_MANAGER,
}
