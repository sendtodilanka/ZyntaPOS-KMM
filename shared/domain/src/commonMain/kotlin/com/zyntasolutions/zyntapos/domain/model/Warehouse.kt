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
 * Multi-step IST workflow (C1.3):
 *   PENDING → APPROVED → IN_TRANSIT → RECEIVED
 * Legacy two-phase commit (warehouse-level):
 *   PENDING → COMMITTED | CANCELLED
 *
 * @property id Unique identifier (UUID v4).
 * @property sourceWarehouseId FK to the warehouse stock is taken from.
 * @property destWarehouseId FK to the warehouse stock is moved to.
 * @property productId FK to the product being transferred.
 * @property quantity Number of units transferred.
 * @property status Current workflow status.
 * @property notes Optional transfer notes.
 * @property createdBy User ID of the staff who created the transfer request.
 * @property approvedBy User ID of the manager who approved the transfer.
 * @property approvedAt Epoch millis when the transfer was approved.
 * @property dispatchedBy User ID of the staff who dispatched the transfer.
 * @property dispatchedAt Epoch millis when the transfer was dispatched.
 * @property receivedBy User ID of the staff who received the transfer.
 * @property receivedAt Epoch millis when the transfer was received.
 * @property transferredBy User ID of the staff who committed (legacy field).
 * @property transferredAt Epoch millis when committed (legacy field).
 */
data class StockTransfer(
    val id: String,
    val sourceWarehouseId: String,
    val destWarehouseId: String,
    val productId: String,
    val quantity: Double,
    val status: Status = Status.PENDING,
    val notes: String? = null,
    val createdBy: String? = null,
    val approvedBy: String? = null,
    val approvedAt: Long? = null,
    val dispatchedBy: String? = null,
    val dispatchedAt: Long? = null,
    val receivedBy: String? = null,
    val receivedAt: Long? = null,
    val transferredBy: String? = null,
    val transferredAt: Long? = null,
) {
    /**
     * IST multi-step workflow statuses.
     * COMMITTED and CANCELLED are retained for backward-compatible warehouse-level transfers.
     */
    enum class Status {
        /** Initial state — awaiting manager approval. */
        PENDING,
        /** Manager has approved — ready to dispatch. */
        APPROVED,
        /** Goods dispatched from source warehouse — in transit. */
        IN_TRANSIT,
        /** Goods received at destination warehouse — stock updated. */
        RECEIVED,
        /** Legacy: atomic warehouse-to-warehouse commit (single-step). */
        COMMITTED,
        /** Transfer cancelled — no stock movement. */
        CANCELLED;

        /** True when the transfer can still be cancelled. */
        val isCancellable: Boolean get() = this == PENDING || this == APPROVED

        /** True when the transfer has reached a terminal state. */
        val isTerminal: Boolean get() = this == RECEIVED || this == COMMITTED || this == CANCELLED
    }

    init {
        require(quantity > 0.0) { "Transfer quantity must be positive" }
        require(sourceWarehouseId != destWarehouseId) { "Source and destination must differ" }
    }
}
