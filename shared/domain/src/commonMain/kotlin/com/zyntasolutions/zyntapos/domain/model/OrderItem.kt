package com.zyntasolutions.zyntapos.domain.model

/**
 * A single line item within a persisted [Order].
 *
 * All monetary values are snapshots taken at the time of the sale and are
 * **never** recalculated retrospectively when product prices change.
 *
 * @property id Unique identifier (UUID v4).
 * @property orderId FK to the parent [Order].
 * @property productId FK to the originating [Product] (for stock reversal on void).
 * @property productName Snapshot of [Product.name] at the time of sale.
 * @property unitPrice Snapshot of the selling price per unit at the time of sale.
 * @property quantity Number of units sold. Must be > 0.
 * @property discount Discount applied to this line (interpreted via [discountType]).
 * @property discountType Whether [discount] is a fixed amount or a percentage.
 * @property taxRate Effective tax rate percentage applied to this line (0.0–100.0).
 * @property taxAmount Calculated tax amount for this line (rounded to 2 d.p.).
 * @property lineTotal Final line amount after discount and tax (rounded to 2 d.p.).
 */
data class OrderItem(
    val id: String,
    val orderId: String,
    val productId: String,
    val productName: String,
    val unitPrice: Double,
    val quantity: Double,
    val discount: Double = 0.0,
    val discountType: DiscountType = DiscountType.FIXED,
    val taxRate: Double = 0.0,
    val taxAmount: Double = 0.0,
    val lineTotal: Double,
)
