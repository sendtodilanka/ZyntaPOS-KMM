package com.zyntasolutions.zyntapos.feature.pos.fulfillment

import com.zyntasolutions.zyntapos.domain.repository.FulfillmentOrder

/**
 * UI state for the Click & Collect fulfillment queue (C4.4).
 */
data class FulfillmentState(
    val pickups: List<FulfillmentOrder> = emptyList(),
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    /** ID of an order currently being updated (shows inline loading indicator). */
    val updatingOrderId: String? = null,
)
