package com.zyntasolutions.zyntapos.feature.inventory

/**
 * One-shot side-effect events emitted by [InventoryViewModel].
 *
 * Collected in inventory screens via `LaunchedEffect(Unit) { viewModel.effects.collect { … } }`.
 * Each subtype is consumed once and discarded.
 */
sealed interface InventoryEffect {

    /**
     * Navigate to the product detail/edit screen.
     *
     * @param productId The product ID to load; null for new product creation.
     */
    data class NavigateToDetail(val productId: String?) : InventoryEffect

    /** Navigate back to the product list from the detail view. */
    data object NavigateToList : InventoryEffect

    /**
     * Show a transient error message via Snackbar.
     *
     * @param msg User-visible error text.
     */
    data class ShowError(val msg: String) : InventoryEffect

    /**
     * Show a transient success message via Snackbar.
     *
     * @param msg User-visible success text.
     */
    data class ShowSuccess(val msg: String) : InventoryEffect

    /**
     * Signal the HAL printer to print a barcode label.
     *
     * @param barcode The barcode string to print.
     * @param productName Product name for label context.
     */
    data class PrintBarcode(val barcode: String, val productName: String) : InventoryEffect

    /**
     * Bulk import completed — refresh the product list.
     *
     * @param importedCount Number of products successfully imported.
     * @param errorCount Number of rows that failed import.
     */
    data class BulkImportComplete(val importedCount: Int, val errorCount: Int) : InventoryEffect
}
