package com.zyntasolutions.zyntapos.domain.model

import kotlinx.datetime.Instant

/**
 * Records a manual change to a [Product]'s on-hand stock quantity.
 *
 * Adjustments are append-only — existing records are never modified.
 * They form a full audit trail for inventory discrepancy investigations.
 *
 * @property id Unique identifier (UUID v4).
 * @property productId FK to the [Product] whose stock is being adjusted.
 * @property type The nature of the adjustment. See [Type].
 * @property quantity Absolute change in stock units. Always positive.
 *                    The sign is determined by [type] (INCREASE adds, DECREASE subtracts).
 * @property reason Operator-supplied explanation for the adjustment.
 * @property adjustedBy FK to the [User] who performed the adjustment.
 * @property timestamp UTC timestamp when the adjustment was recorded.
 * @property syncStatus Whether this record has been synced to the server.
 */
data class StockAdjustment(
    val id: String,
    val productId: String,
    val type: Type,
    val quantity: Double,
    val reason: String,
    val adjustedBy: String,
    val timestamp: Instant,
    val syncStatus: SyncStatus,
) {
    init {
        require(quantity > 0.0) { "Adjustment quantity must be positive, got $quantity" }
    }

    /** The direction and nature of the stock change. */
    enum class Type {
        /** Add units to on-hand stock (e.g., received delivery, found surplus). */
        INCREASE,

        /** Remove units from on-hand stock (e.g., damaged, expired, shrinkage). */
        DECREASE,

        /** Move stock between locations (records net change at source/destination). */
        TRANSFER,
    }
}
