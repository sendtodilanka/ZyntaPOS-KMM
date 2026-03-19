package com.zyntasolutions.zyntapos.domain.model

/**
 * A supplier purchase order created to replenish inventory (C1.3 / C1.5).
 *
 * Lifecycle: PENDING → PARTIAL → RECEIVED | CANCELLED
 *
 * @property id Unique identifier (UUID v4).
 * @property supplierId FK to the supplier providing the goods.
 * @property orderNumber Human-readable PO reference (e.g. "PO-2026-001").
 * @property status Current fulfilment status.
 * @property orderDate Epoch millis when the PO was placed.
 * @property expectedDate Epoch millis when goods are expected to arrive (nullable).
 * @property receivedDate Epoch millis when goods were fully received (nullable).
 * @property totalAmount Total cost of all line items.
 * @property currency ISO 4217 currency code (e.g. "LKR").
 * @property notes Optional free-text notes for this PO.
 * @property createdBy User ID of the staff member who created the PO.
 * @property items Line items included in this PO.
 */
data class PurchaseOrder(
    val id: String,
    val supplierId: String,
    val orderNumber: String,
    val status: Status = Status.PENDING,
    val orderDate: Long,
    val expectedDate: Long? = null,
    val receivedDate: Long? = null,
    val totalAmount: Double = 0.0,
    val currency: String = "LKR",
    val notes: String? = null,
    val createdBy: String,
    val items: List<PurchaseOrderItem> = emptyList(),
) {
    enum class Status {
        /** Order placed with supplier — awaiting delivery. */
        PENDING,
        /** Some items received — partial fulfilment. */
        PARTIAL,
        /** All items received — PO closed. */
        RECEIVED,
        /** PO cancelled before any goods were delivered. */
        CANCELLED;

        val isTerminal: Boolean get() = this == RECEIVED || this == CANCELLED
    }
}

/**
 * A single line item within a [PurchaseOrder].
 *
 * @property id Unique identifier (UUID v4).
 * @property purchaseOrderId FK to the parent [PurchaseOrder].
 * @property productId FK to the product being ordered.
 * @property quantityOrdered Number of units ordered.
 * @property quantityReceived Number of units already received (updated on partial receipt).
 * @property unitCost Cost per unit at time of order.
 * @property lineTotal quantityOrdered × unitCost.
 * @property notes Optional line-item note.
 */
data class PurchaseOrderItem(
    val id: String,
    val purchaseOrderId: String,
    val productId: String,
    val quantityOrdered: Double,
    val quantityReceived: Double = 0.0,
    val unitCost: Double,
    val lineTotal: Double,
    val notes: String? = null,
) {
    val remainingQuantity: Double get() = (quantityOrdered - quantityReceived).coerceAtLeast(0.0)
    val isFullyReceived: Boolean get() = quantityReceived >= quantityOrdered
}
