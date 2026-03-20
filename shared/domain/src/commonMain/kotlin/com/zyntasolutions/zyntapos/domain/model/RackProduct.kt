package com.zyntasolutions.zyntapos.domain.model

/**
 * Maps a product to a specific rack within a warehouse, with an optional bin location.
 *
 * Multiple products can occupy the same rack; a product may appear in multiple racks
 * (with different [binLocation] slots). This table enables the warehouse inventory
 * report (`warehouseInventory` in `reports.sq`) to show exactly where each product
 * is stored on the warehouse floor.
 *
 * @property id          Unique identifier (UUID v4).
 * @property rackId      FK to [WarehouseRack].
 * @property productId   FK to [Product].
 * @property quantity    Number of units stored at this exact rack/bin location.
 * @property binLocation Optional alphanumeric bin slot within the rack (e.g. "A3", "Row-2-Bin-4").
 * @property updatedAt   Epoch millis of last modification.
 * @property productName Denormalised product name (populated by JOIN queries).
 * @property productSku  Denormalised SKU (populated by JOIN queries).
 * @property productBarcode Denormalised barcode (populated by JOIN queries).
 */
data class RackProduct(
    val id: String,
    val rackId: String,
    val productId: String,
    val quantity: Double,
    val binLocation: String? = null,
    val updatedAt: Long = 0L,
    // Denormalised — populated by JOIN queries; null when queried by exact key.
    val productName: String? = null,
    val productSku: String? = null,
    val productBarcode: String? = null,
)
