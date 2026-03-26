package com.zyntasolutions.zyntapos.feature.inventory

import com.zyntasolutions.zyntapos.domain.model.Category
import com.zyntasolutions.zyntapos.domain.model.Product
import com.zyntasolutions.zyntapos.domain.model.StockAdjustment
import com.zyntasolutions.zyntapos.domain.model.Supplier
import com.zyntasolutions.zyntapos.domain.model.TaxGroup
import com.zyntasolutions.zyntapos.domain.model.UnitOfMeasure

/**
 * All user interactions and system events that can mutate [InventoryState].
 *
 * [InventoryIntent] is the single entry point into [InventoryViewModel].
 * The Composable layer dispatches intents and observes the resulting state.
 *
 * ### Intent Categories
 * - **List navigation:** [LoadProducts], [SearchQueryChanged], [SelectCategory],
 *   [SetStockFilter], [ToggleViewMode], [SortByColumn]
 * - **Product CRUD:** [SelectProduct], [CreateProduct], [UpdateProduct],
 *   [DeleteProduct], [UpdateFormField], [ClearForm]
 * - **Variants:** [AddVariant], [RemoveVariant], [UpdateVariant]
 * - **Stock:** [OpenStockAdjustment], [SubmitStockAdjustment], [DismissStockAdjustment]
 * - **Barcode:** [OpenBarcodeGenerator], [DismissBarcodeGenerator]
 * - **Bulk import:** [OpenBulkImport], [SetImportFile], [SetColumnMapping],
 *   [ConfirmBulkImport], [DismissBulkImport]
 * - **UI:** [DismissError], [DismissSuccess]
 */
sealed interface InventoryIntent {

    // ─── List Navigation ───────────────────────────────────────────────────

    /** Triggers initial or refresh load of products, categories, tax groups, units. */
    data object LoadProducts : InventoryIntent

    /** Updates the search query; ViewModel debounces before querying. */
    data class SearchQueryChanged(val query: String) : InventoryIntent

    /** Applies a category filter. Pass null for "All". */
    data class SelectCategory(val categoryId: String?) : InventoryIntent

    /** Applies a stock status filter. */
    data class SetStockFilter(val filter: StockFilter) : InventoryIntent

    /** Toggles between list and grid view. */
    data object ToggleViewMode : InventoryIntent

    /** Loads the next page of products for infinite scroll. */
    data object LoadMoreProducts : InventoryIntent

    /** Sorts the table by the given column key; toggles direction if same column. */
    data class SortByColumn(val columnKey: String) : InventoryIntent

    // ─── Product CRUD ──────────────────────────────────────────────────────

    /** Loads a product by ID into the detail view. Pass null for new product form. */
    data class SelectProduct(val productId: String?) : InventoryIntent

    /** Clears the detail view and returns to the list. */
    data object BackToList : InventoryIntent

    /** Updates a single form field by name and value. */
    data class UpdateFormField(val field: String, val value: String) : InventoryIntent

    /** Toggles the isActive flag on the form. */
    data object ToggleFormActive : InventoryIntent

    /** Clears all form fields to defaults. */
    data object ClearForm : InventoryIntent

    /** Validates and persists the product form (create or update based on form.id). */
    data object SaveProduct : InventoryIntent

    /** Soft-deletes the product with the given ID. */
    data class DeleteProduct(val productId: String) : InventoryIntent

    // ─── Variants ──────────────────────────────────────────────────────────

    /** Adds a new empty variant row to the form. */
    data object AddVariant : InventoryIntent

    /** Removes a variant by its local index in the form list. */
    data class RemoveVariant(val index: Int) : InventoryIntent

    /** Updates a variant field at the given index. */
    data class UpdateVariant(val index: Int, val field: String, val value: String) : InventoryIntent

    // ─── Stock Adjustment Dialog ───────────────────────────────────────────

    /** Opens the stock adjustment dialog for the given product. */
    data class OpenStockAdjustment(val product: Product) : InventoryIntent

    /** Submits a stock adjustment from the dialog. */
    data class SubmitStockAdjustment(
        val type: StockAdjustment.Type,
        val quantity: Double,
        val reason: String,
    ) : InventoryIntent

    /** Closes the stock adjustment dialog. */
    data object DismissStockAdjustment : InventoryIntent

    // ─── Barcode Generator Dialog ──────────────────────────────────────────

    /** Opens the barcode generator for the given product. */
    data class OpenBarcodeGenerator(val product: Product) : InventoryIntent

    /** Closes the barcode generator dialog. */
    data object DismissBarcodeGenerator : InventoryIntent

    // ─── Bulk Import Dialog ────────────────────────────────────────────────

    /** Opens the bulk import dialog. */
    data object OpenBulkImport : InventoryIntent

    /** Sets the selected CSV file name and parsed rows. */
    data class SetImportFile(
        val fileName: String,
        val columns: List<String>,
        val rows: List<Map<String, String>>,
    ) : InventoryIntent

    /** Updates a column mapping (CSV column → product field). */
    data class SetColumnMapping(val csvColumn: String, val productField: String) : InventoryIntent

    /** Confirms and executes the bulk import. */
    data object ConfirmBulkImport : InventoryIntent

    /** Closes the bulk import dialog. */
    data object DismissBulkImport : InventoryIntent

    // ─── Barcode Scanner ──────────────────────────────────────────────────

    /** Starts the HAL barcode scanner and begins collecting scan events. */
    data object StartBarcodeScanner : InventoryIntent

    /** Stops the HAL barcode scanner. */
    data object StopBarcodeScanner : InventoryIntent

    /**
     * A barcode was scanned by the HAL scanner.
     * Fills the barcode form field on the product detail screen.
     */
    data class BarcodeScanResult(val barcode: String) : InventoryIntent

    // ─── UI Feedback ───────────────────────────────────────────────────────

    /** Dismisses the current error message. */
    data object DismissError : InventoryIntent

    /** Dismisses the current success message. */
    data object DismissSuccess : InventoryIntent

    // ─── Sprint 19: Category Management ───────────────────────────────────

    /** Loads all categories from the repository. */
    data object LoadCategories : InventoryIntent

    /** Opens the CategoryDetail screen. Null = create new. */
    data class OpenCategoryDetail(val categoryId: String?) : InventoryIntent

    /** Persists a category (insert or update based on id). */
    data class SaveCategory(val category: Category) : InventoryIntent

    /** Soft-deletes the category with the given ID. */
    data class DeleteCategory(val categoryId: String) : InventoryIntent

    /** Closes the CategoryDetail screen. */
    data object CloseCategoryDetail : InventoryIntent

    // ─── Sprint 19: Supplier Management ───────────────────────────────────

    /** Loads all suppliers from the repository. */
    data object LoadSuppliers : InventoryIntent

    /** Opens the SupplierDetail screen. Null = create new. */
    data class OpenSupplierDetail(val supplierId: String?) : InventoryIntent

    /** Persists a supplier (insert or update). */
    data class SaveSupplier(val supplier: Supplier) : InventoryIntent

    /** Soft-deletes the supplier with the given ID. */
    data class DeleteSupplier(val supplierId: String) : InventoryIntent

    /** Closes the SupplierDetail screen. */
    data object CloseSupplierDetail : InventoryIntent

    // ─── Sprint 19: Tax Group Management ──────────────────────────────────

    /** Opens the TaxGroupScreen. */
    data object OpenTaxGroupManagement : InventoryIntent

    /** Closes the TaxGroupScreen. */
    data object CloseTaxGroupManagement : InventoryIntent

    /** Persists a tax group (insert or update). */
    data class SaveTaxGroup(val taxGroup: TaxGroup) : InventoryIntent

    /** Deletes the tax group with the given ID. */
    data class DeleteTaxGroup(val taxGroupId: String) : InventoryIntent

    // ─── Sprint 19: Unit Management ───────────────────────────────────────

    /** Opens the UnitManagementScreen. */
    data object OpenUnitManagement : InventoryIntent

    /** Closes the UnitManagementScreen. */
    data object CloseUnitManagement : InventoryIntent

    /** Persists a unit of measure (insert or update). */
    data class SaveUnit(val groupId: String, val unit: UnitOfMeasure) : InventoryIntent

    /** Deletes the unit with the given ID. */
    data class DeleteUnit(val unitId: String) : InventoryIntent

    /** Saves (rename) a UnitGroup. */
    data class SaveUnitGroup(val group: UnitGroup) : InventoryIntent

    // ─── INV-7: Batch Product Selection ───────────────────────────────────

    /** Enters multi-select mode for batch operations. */
    data object EnterSelectionMode : InventoryIntent

    /** Exits multi-select mode and clears selection. */
    data object ExitSelectionMode : InventoryIntent

    /** Toggles the selection state of a product by ID. */
    data class ToggleProductSelection(val productId: String) : InventoryIntent

    /** Selects all currently visible products. */
    data object SelectAllProducts : InventoryIntent

    /** Deletes all currently selected products. */
    data object BatchDeleteSelectedProducts : InventoryIntent
}
