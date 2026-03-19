package com.zyntasolutions.zyntapos.domain.model

import kotlinx.datetime.Instant

/**
 * Per-store price and stock overrides for a [MasterProduct].
 *
 * When a master product is assigned to a store, this record holds the
 * store-specific configuration. Null price fields mean "use the master
 * product's base value".
 *
 * @property id Unique identifier (UUID v4).
 * @property masterProductId FK to the parent [MasterProduct].
 * @property storeId FK to the store this override belongs to.
 * @property localPrice Store-specific selling price. Null = use [MasterProduct.basePrice].
 * @property localCostPrice Store-specific cost price. Null = use [MasterProduct.costPrice].
 * @property localStockQty Current on-hand quantity at this store.
 * @property minStockQty Threshold that triggers a low-stock alert for this store.
 * @property isActive If false the product is hidden from this store's POS.
 * @property createdAt UTC timestamp of record creation.
 * @property updatedAt UTC timestamp of the last modification.
 */
data class StoreProductOverride(
    val id: String,
    val masterProductId: String,
    val storeId: String,
    val localPrice: Double? = null,
    val localCostPrice: Double? = null,
    val localStockQty: Double = 0.0,
    val minStockQty: Double = 0.0,
    val isActive: Boolean = true,
    val createdAt: Instant,
    val updatedAt: Instant,
)
