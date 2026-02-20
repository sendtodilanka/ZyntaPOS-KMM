package com.zyntasolutions.zyntapos.domain.model

import kotlinx.datetime.Instant

/**
 * A saleable item within the ZyntaPOS catalogue.
 *
 * @property id Unique identifier (UUID v4).
 * @property name Display name shown on receipts, POS grid, and reports.
 * @property barcode EAN-13 / Code128 barcode value. Unique — null if not assigned.
 * @property sku Internal stock-keeping unit code. Unique — null if not assigned.
 * @property categoryId FK to [Category].
 * @property unitId FK to [UnitOfMeasure] (e.g., "pcs", "kg").
 * @property price Selling price (exclusive of tax if [TaxGroup.isInclusive] is false).
 * @property costPrice Purchase cost — used in margin/profit reports. Never shown to customers.
 * @property taxGroupId FK to [TaxGroup]. Null means no tax applied.
 * @property stockQty Current on-hand quantity. Updated atomically on each sale or adjustment.
 * @property minStockQty Threshold that triggers a low-stock alert. Default 0 = no alert.
 * @property imageUrl Remote or local URI for the product image. Loaded via Coil.
 * @property description Optional rich-text description.
 * @property isActive If false the product is hidden from the POS grid and search.
 * @property createdAt UTC timestamp of record creation.
 * @property updatedAt UTC timestamp of the last modification.
 */
data class Product(
    val id: String,
    val name: String,
    val barcode: String? = null,
    val sku: String? = null,
    val categoryId: String,
    val unitId: String,
    val price: Double,
    val costPrice: Double = 0.0,
    val taxGroupId: String? = null,
    val stockQty: Double = 0.0,
    val minStockQty: Double = 0.0,
    val imageUrl: String? = null,
    val description: String? = null,
    val isActive: Boolean = true,
    val createdAt: Instant,
    val updatedAt: Instant,
)
