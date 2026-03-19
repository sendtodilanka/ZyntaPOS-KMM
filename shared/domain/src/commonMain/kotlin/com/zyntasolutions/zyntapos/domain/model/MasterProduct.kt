package com.zyntasolutions.zyntapos.domain.model

import kotlinx.datetime.Instant

/**
 * A centrally-managed product template shared across all stores.
 *
 * Master products live in the global catalog (admin-panel-only writes).
 * POS devices receive them via sync pull as read-only data.
 * Stores can override [basePrice] and [costPrice] locally via [StoreProductOverride].
 *
 * @property id Unique identifier (UUID v4).
 * @property sku Global stock-keeping unit code. Unique across all stores.
 * @property barcode EAN-13 / Code128 barcode value. Unique globally.
 * @property name Display name shown on receipts, POS grid, and reports.
 * @property description Optional rich-text description.
 * @property basePrice Default selling price. Stores may override this locally.
 * @property costPrice Default purchase cost. Never shown to customers.
 * @property categoryId FK to [Category].
 * @property unitId FK to [UnitOfMeasure] (e.g., "pcs", "kg").
 * @property taxGroupId FK to [TaxGroup]. Null means no tax applied.
 * @property imageUrl Remote or local URI for the product image.
 * @property isActive If false the product is hidden from the catalog.
 * @property createdAt UTC timestamp of record creation.
 * @property updatedAt UTC timestamp of the last modification.
 */
data class MasterProduct(
    val id: String,
    val sku: String? = null,
    val barcode: String? = null,
    val name: String,
    val description: String? = null,
    val basePrice: Double,
    val costPrice: Double = 0.0,
    val categoryId: String? = null,
    val unitId: String? = null,
    val taxGroupId: String? = null,
    val imageUrl: String? = null,
    val isActive: Boolean = true,
    val createdAt: Instant,
    val updatedAt: Instant,
)
