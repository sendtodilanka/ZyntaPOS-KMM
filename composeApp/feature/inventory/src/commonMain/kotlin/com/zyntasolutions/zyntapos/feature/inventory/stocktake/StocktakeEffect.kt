package com.zyntasolutions.zyntapos.feature.inventory.stocktake

/** One-shot side-effect events emitted by [StocktakeViewModel]. */
sealed interface StocktakeEffect {

    /** A product was successfully scanned and its count updated. */
    data class ScanSuccess(val productName: String, val qty: Int) : StocktakeEffect

    /** The scanned barcode was not found in the product catalogue. */
    data class ScanNotFound(val barcode: String) : StocktakeEffect

    /** The session was completed; [varianceCount] products had non-zero variances. */
    data class StocktakeCompleted(val varianceCount: Int) : StocktakeEffect

    /** Navigate back after session cancellation. */
    data object SessionCancelled : StocktakeEffect

    /** Show an error message. */
    data class ShowError(val message: String) : StocktakeEffect
}
