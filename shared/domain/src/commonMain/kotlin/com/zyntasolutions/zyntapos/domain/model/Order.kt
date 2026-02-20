package com.zyntasolutions.zyntapos.domain.model

import kotlinx.datetime.Instant

/**
 * A completed (or in-progress / held) sales transaction.
 *
 * All financial totals are stored as snapshots — they are never recalculated
 * after the order reaches [OrderStatus.COMPLETED].
 *
 * @property id Unique identifier (UUID v4).
 * @property orderNumber Human-readable sequential reference shown on receipts.
 * @property type Whether this is a sale, refund, or held cart. See [OrderType].
 * @property status Current lifecycle state. See [OrderStatus].
 * @property items The individual line items included in this order.
 * @property subtotal Sum of all line totals before order-level discount and tax adjustment.
 * @property taxAmount Total tax across all items (rounded to 2 d.p.).
 * @property discountAmount Total order-level discount applied (0.0 if none).
 * @property total Grand total the customer must pay: `subtotal + taxAmount - discountAmount`.
 * @property paymentMethod Primary [PaymentMethod]. SPLIT when [paymentSplits] is non-empty.
 * @property paymentSplits Individual legs when [paymentMethod] is [PaymentMethod.SPLIT]. Empty otherwise.
 * @property amountTendered Cash or total amount tendered by the customer.
 * @property changeAmount Amount to return to the customer: `amountTendered - total` (0 for card/mobile).
 * @property customerId Optional FK to [Customer]. Null for walk-in customers.
 * @property cashierId FK to the [User] who processed this order.
 * @property storeId FK to the store where the transaction occurred.
 * @property registerSessionId FK to the [RegisterSession] active when the order was created.
 * @property notes Free-text operator notes (e.g., special instructions).
 * @property reference External reference number (e.g., invoice or PO number).
 * @property createdAt UTC timestamp when the order was first created.
 * @property updatedAt UTC timestamp of the last modification (void, edit, etc.).
 * @property syncStatus Indicates whether this order has been pushed to the server.
 */
data class Order(
    val id: String,
    val orderNumber: String,
    val type: OrderType,
    val status: OrderStatus,
    val items: List<OrderItem>,
    val subtotal: Double,
    val taxAmount: Double,
    val discountAmount: Double,
    val total: Double,
    val paymentMethod: PaymentMethod,
    val paymentSplits: List<PaymentSplit> = emptyList(),
    val amountTendered: Double,
    val changeAmount: Double,
    val customerId: String? = null,
    val cashierId: String,
    val storeId: String,
    val registerSessionId: String,
    val notes: String? = null,
    val reference: String? = null,
    val createdAt: Instant,
    val updatedAt: Instant,
    val syncStatus: SyncStatus,
)
