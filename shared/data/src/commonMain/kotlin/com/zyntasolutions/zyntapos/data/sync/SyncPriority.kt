package com.zyntasolutions.zyntapos.data.sync

import com.zyntasolutions.zyntapos.domain.model.SyncOperation

/**
 * Sync priority tier definitions.
 *
 * Maps entity types to integer priority tiers (lower = higher priority).
 * The SQL CASE expression in `getEligibleOperations` mirrors these tiers.
 *
 * ## Priority Tiers
 * | Tier | Value | Entity Types | Rationale |
 * |------|-------|-------------|-----------|
 * | CRITICAL | 0 | order, cash_movement, register_session | Revenue-impacting; must reach server ASAP |
 * | HIGH | 1 | product, stock_adjustment, customer | Core catalog/inventory; visible to all devices |
 * | NORMAL | 2 | category, supplier, user | Supporting data; less time-sensitive |
 * | LOW | 3 | Everything else (settings, media, accounting) | Background data; can wait |
 */
object SyncPriority {
    const val CRITICAL = 0
    const val HIGH = 1
    const val NORMAL = 2
    const val LOW = 3

    private val TIER_MAP = mapOf(
        SyncOperation.EntityType.ORDER to CRITICAL,
        SyncOperation.EntityType.CASH_MOVEMENT to CRITICAL,
        SyncOperation.EntityType.REGISTER_SESSION to CRITICAL,

        SyncOperation.EntityType.PRODUCT to HIGH,
        SyncOperation.EntityType.STOCK_ADJUSTMENT to HIGH,
        SyncOperation.EntityType.CUSTOMER to HIGH,

        SyncOperation.EntityType.CATEGORY to NORMAL,
        SyncOperation.EntityType.SUPPLIER to NORMAL,
        SyncOperation.EntityType.USER to NORMAL,
    )

    /** Returns the sync priority tier for the given [entityType]. Defaults to [LOW]. */
    fun tierFor(entityType: String): Int = TIER_MAP[entityType] ?: LOW
}
