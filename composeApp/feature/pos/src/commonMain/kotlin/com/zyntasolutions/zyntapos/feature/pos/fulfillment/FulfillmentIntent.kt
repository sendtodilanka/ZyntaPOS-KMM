package com.zyntasolutions.zyntapos.feature.pos.fulfillment

/**
 * User intents for the Click & Collect fulfillment queue screen (C4.4).
 */
sealed class FulfillmentIntent {

    /** Load or reload the pending pickup queue for the current store. */
    data object LoadQueue : FulfillmentIntent()

    /** Advance a RECEIVED order to PREPARING state. */
    data class MarkPreparing(val orderId: String) : FulfillmentIntent()

    /** Advance a PREPARING order to READY_FOR_PICKUP state and notify the customer. */
    data class MarkReady(val orderId: String, val notifyCustomer: Boolean = true) : FulfillmentIntent()

    /** Mark an order as PICKED_UP (customer has collected). */
    data class MarkPickedUp(val orderId: String) : FulfillmentIntent()

    /** Cancel an order before pickup. */
    data class CancelOrder(val orderId: String) : FulfillmentIntent()

    /** Dismiss any visible error snack-bar. */
    data object DismissError : FulfillmentIntent()
}
