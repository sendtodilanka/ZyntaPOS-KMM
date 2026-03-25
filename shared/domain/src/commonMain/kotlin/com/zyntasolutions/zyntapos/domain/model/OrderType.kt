package com.zyntasolutions.zyntapos.domain.model

/** Classifies the nature of a transaction. */
enum class OrderType {
    /** A standard forward sale to a customer. */
    SALE,

    /** A return/refund transaction reversing a previous [SALE]. */
    REFUND,

    /** A cart that has been temporarily parked to be retrieved later. */
    HOLD,

    /**
     * Buy Online, Pick up In Store (BOPIS) / Click & Collect order.
     *
     * The customer places the order remotely and collects it at a designated store.
     * Fulfillment status is tracked separately via the fulfillment workflow.
     */
    CLICK_AND_COLLECT,
}
