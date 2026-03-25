package com.zyntasolutions.zyntapos.domain.model

/**
 * Fulfillment lifecycle state for [OrderType.CLICK_AND_COLLECT] (BOPIS) orders.
 *
 * Tracks the in-store pickup flow independently from the payment lifecycle ([OrderStatus]).
 * Only applicable when [Order.type] is [OrderType.CLICK_AND_COLLECT].
 *
 * Normal progression:
 * [RECEIVED] → [PREPARING] → [READY_FOR_PICKUP] → [PICKED_UP]
 *
 * Exception path:
 * Any state → [EXPIRED] (if not picked up within the configured timeout window)
 * [RECEIVED] / [PREPARING] → [CANCELLED] (if order is voided before preparation)
 */
enum class FulfillmentStatus {
    /** Order has been received and is queued for preparation. */
    RECEIVED,

    /** Staff are actively preparing the order for pickup. */
    PREPARING,

    /** Order is ready and waiting for the customer to collect at the store. */
    READY_FOR_PICKUP,

    /** Customer has collected the order. Terminal state. */
    PICKED_UP,

    /** Order was not collected within the timeout window and has been automatically cancelled. */
    EXPIRED,

    /** Order was explicitly cancelled before the customer arrived. */
    CANCELLED,
}
