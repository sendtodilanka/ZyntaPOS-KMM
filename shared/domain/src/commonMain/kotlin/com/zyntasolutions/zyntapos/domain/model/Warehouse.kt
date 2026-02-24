package com.zyntasolutions.zyntapos.domain.model

/**
 * A physical warehouse or store location that holds stock.
 *
 * @property id Unique identifier (UUID v4).
 * @property storeId The parent store this warehouse belongs to.
 * @property name Display name (e.g., "Main Floor", "Back Storage").
 * @property managerId FK to the manager [User]. Null = no assigned manager.
 * @property isActive Whether the warehouse is operational.
 * @property isDefault If true this warehouse is pre-selected for new stock operations.
 * @property address Optional physical address description.
 */
data class Warehouse(
    val id: String,
    val storeId: String,
    val name: String,
    val managerId: String? = null,
    val isActive: Boolean = true,
    val isDefault: Boolean = false,
    val address: String? = null,
)

/**
 * Records the movement of stock from one [Warehouse] to another.
 *
 * @property id Unique identifier (UUID v4).
 * @property sourceWarehouseId FK to the warehouse stock is taken from.
 * @property destWarehouseId FK to the warehouse stock is moved to.
 * @property productId FK to the product being transferred.
 * @property quantity Number of units transferred.
 * @property status Two-phase commit status.
 * @property notes Optional transfer notes.
 * @property transferredBy User ID of the staff who confirmed the transfer.
 * @property transferredAt Epoch millis when the transfer was committed.
 */
data class StockTransfer(
    val id: String,
    val sourceWarehouseId: String,
    val destWarehouseId: String,
    val productId: String,
    val quantity: Double,
    val status: Status = Status.PENDING,
    val notes: String? = null,
    val transferredBy: String? = null,
    val transferredAt: Long? = null,
) {
    enum class Status { PENDING, COMMITTED, CANCELLED }

    init {
        require(quantity > 0.0) { "Transfer quantity must be positive" }
        require(sourceWarehouseId != destWarehouseId) { "Source and destination must differ" }
    }
}
