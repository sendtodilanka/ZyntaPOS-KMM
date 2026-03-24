package com.zyntasolutions.zyntapos.domain.model

import kotlinx.datetime.Instant

/**
 * A warehouse pick list for an approved inter-store transfer (P3-B1).
 *
 * Generated when a transfer is in APPROVED status so warehouse staff can
 * efficiently gather items from racks before dispatch. Items are sorted by
 * rack location to minimise travel time on the warehouse floor.
 *
 * @property transferId        The parent [StockTransfer] ID.
 * @property sourceStoreName   Display name of the source store/warehouse.
 * @property destinationStoreName Display name of the destination store/warehouse.
 * @property items             Ordered list of items to pick, sorted by rack location.
 * @property generatedAt       Timestamp when the pick list was generated.
 * @property notes             Optional transfer notes carried forward from the transfer.
 */
data class PickList(
    val transferId: String,
    val sourceStoreName: String,
    val destinationStoreName: String,
    val items: List<PickListItem>,
    val generatedAt: Instant,
    val notes: String? = null,
)

/**
 * A single line item on a warehouse [PickList].
 *
 * @property productId      FK to [Product].
 * @property productName    Human-readable product name.
 * @property sku            Product SKU for quick identification.
 * @property quantity       Number of units to pick.
 * @property rackLocation   Rack name where the product is stored (e.g. "A1", "Cold-Storage-01").
 *                          Null when the product has no assigned rack location.
 * @property binLocation    Optional sub-location within the rack (e.g. "Row-2-Bin-4").
 * @property unitOfMeasure  Display abbreviation for the unit (e.g. "pcs", "kg").
 */
data class PickListItem(
    val productId: String,
    val productName: String,
    val sku: String,
    val quantity: Double,
    val rackLocation: String? = null,
    val binLocation: String? = null,
    val unitOfMeasure: String = "pcs",
)
