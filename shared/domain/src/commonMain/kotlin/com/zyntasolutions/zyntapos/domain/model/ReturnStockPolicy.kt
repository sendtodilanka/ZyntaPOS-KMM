package com.zyntasolutions.zyntapos.domain.model

/**
 * Configurable policy determining where returned stock goes during a
 * cross-store refund (C4.2).
 *
 * Each [Store] carries a [returnStockPolicy] that controls inventory
 * routing when a customer returns a product at a different store than
 * where the original sale was made.
 */
enum class ReturnStockPolicy {
    /**
     * Returned stock is added to the store that is processing the return.
     * This is the default — simplest operationally because the physical
     * item is already on-site.
     */
    RETURN_TO_CURRENT_STORE,

    /**
     * Returned stock is routed back to the store where the original sale
     * was made. This keeps each store's inventory aligned with its sales
     * history but requires an inter-store transfer to move the physical item.
     */
    RETURN_TO_ORIGINAL_STORE,
}
