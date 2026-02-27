package com.zyntasolutions.zyntapos.feature.inventory.stocktake

/** All user actions and system events that can mutate [StocktakeState]. */
sealed interface StocktakeIntent {

    /** Start a new stocktake session for the currently logged-in user. */
    data object StartSession : StocktakeIntent

    /** A barcode was scanned — look up the product and update the count. */
    data class ScanItem(val barcode: String) : StocktakeIntent

    /** Manually set the counted quantity for a specific product. */
    data class ManualAdjustCount(val productId: String, val qty: Int) : StocktakeIntent

    /** Remove a product entry from the count list. */
    data class RemoveCount(val productId: String) : StocktakeIntent

    /** Toggle scanner active / inactive. */
    data class SetScannerActive(val active: Boolean) : StocktakeIntent

    /** Complete the session — apply variances and create stock adjustments. */
    data object CompleteStocktake : StocktakeIntent

    /** Cancel the session without applying any adjustments. */
    data object CancelStocktake : StocktakeIntent

    /** Dismiss the current error message. */
    data object DismissError : StocktakeIntent
}
