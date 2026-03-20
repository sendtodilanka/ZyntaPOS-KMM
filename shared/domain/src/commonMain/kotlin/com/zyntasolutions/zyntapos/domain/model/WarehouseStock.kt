package com.zyntasolutions.zyntapos.domain.model

/**
 * Per-warehouse stock level for a single product.
 *
 * Replaces the single global [Product.stockQty] for multi-warehouse deployments.
 * Each (warehouseId, productId) pair has exactly one [WarehouseStock] row.
 *
 * @property id Unique identifier (UUID v4).
 * @property warehouseId FK to the [Warehouse] this stock entry belongs to.
 * @property productId FK to the [Product] tracked at this warehouse.
 * @property quantity Current on-hand quantity at this warehouse.
 * @property minQuantity Reorder threshold — triggers a low-stock alert when
 *   [quantity] drops to or below this value. Zero means no threshold set.
 * @property updatedAt Epoch millis of last modification.
 * @property productName Display name of the product (denormalised for list UIs).
 * @property productSku SKU of the product (may be null).
 * @property productBarcode Barcode of the product (may be null).
 * @property productImageUrl Image URL of the product (may be null).
 * @property warehouseName Display name of the warehouse (populated in cross-warehouse queries).
 */
data class WarehouseStock(
    val id: String,
    val warehouseId: String,
    val productId: String,
    val quantity: Double,
    val minQuantity: Double = 0.0,
    val updatedAt: Long = 0L,
    // Denormalised fields — populated by JOIN queries; null when queried by single key.
    val productName: String? = null,
    val productSku: String? = null,
    val productBarcode: String? = null,
    val productImageUrl: String? = null,
    val warehouseName: String? = null,
) {
    /** True when this entry is below or at its reorder threshold. */
    val isLowStock: Boolean get() = minQuantity > 0.0 && quantity <= minQuantity

    /** Shortfall below reorder point (negative means surplus). */
    val stockShortfall: Double get() = minQuantity - quantity
}
