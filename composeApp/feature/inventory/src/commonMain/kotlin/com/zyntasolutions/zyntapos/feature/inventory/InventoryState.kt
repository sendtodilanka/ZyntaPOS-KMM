package com.zyntasolutions.zyntapos.feature.inventory

import androidx.compose.runtime.Immutable
import com.zyntasolutions.zyntapos.domain.model.Category
import com.zyntasolutions.zyntapos.domain.model.Product
import com.zyntasolutions.zyntapos.domain.model.ProductVariant
import com.zyntasolutions.zyntapos.domain.model.StockAdjustment
import com.zyntasolutions.zyntapos.domain.model.Supplier
import com.zyntasolutions.zyntapos.domain.model.TaxGroup
import com.zyntasolutions.zyntapos.domain.model.UnitOfMeasure

/**
 * Immutable UI state for the Inventory management screens (Sprint 18, Step 10.1).
 *
 * This is the **single source of truth** consumed by `ProductListScreen`,
 * `ProductDetailScreen`, and all inventory dialogs. All fields are read-only
 * snapshots emitted by [InventoryViewModel] via its `StateFlow<InventoryState>`.
 *
 * ### Sub-states
 * - **List view:** [products], [categories], [searchQuery], [selectedCategoryId],
 *   [stockFilter], [viewMode], [sortColumn], [sortDirection]
 * - **Detail view:** [selectedProduct], [editFormState], [productVariants]
 * - **Dialogs:** [stockAdjustmentTarget], [bulkImportState], [barcodeGeneratorTarget]
 *
 * @property products Pre-filtered product list matching current search + filters.
 * @property categories All active categories for the filter chip row.
 * @property searchQuery Live text from the product search bar.
 * @property selectedCategoryId Active category filter; null means "All".
 * @property stockFilter Active stock status filter.
 * @property viewMode Current list/grid toggle state.
 * @property sortColumn Current sort column key for ZyntaTable.
 * @property sortDirection Current sort direction.
 * @property selectedProduct Product loaded for detail/edit view; null = list mode.
 * @property editFormState Mutable form fields for create/edit product.
 * @property productVariants Variants of the currently selected product.
 * @property allTaxGroups Available tax groups for the selector.
 * @property allUnits Available units of measure for the selector.
 * @property stockAdjustmentTarget Product targeted for stock adjustment dialog; null = hidden.
 * @property bulkImportState State for the bulk import dialog.
 * @property barcodeGeneratorTarget Product targeted for barcode generation; null = hidden.
 * @property isLoading True while an async operation is in-flight.
 * @property error Transient user-visible error message; null = no error.
 * @property successMessage Transient success message; null = no message.
 */
@Immutable
data class InventoryState(
    // ── List View ──────────────────────────────────────────────────────────
    val products: List<Product> = emptyList(),
    val categories: List<Category> = emptyList(),
    val searchQuery: String = "",
    val selectedCategoryId: String? = null,
    val stockFilter: StockFilter = StockFilter.ALL,
    val viewMode: ViewMode = ViewMode.LIST,
    val sortColumn: String? = "name",
    val sortDirection: SortDir = SortDir.ASC,

    // ── Detail / Edit View ────────────────────────────────────────────────
    val selectedProduct: Product? = null,
    val editFormState: ProductFormState = ProductFormState(),
    val productVariants: List<ProductVariant> = emptyList(),

    // ── Reference Data ────────────────────────────────────────────────────
    val allTaxGroups: List<TaxGroup> = emptyList(),
    val allUnits: List<UnitOfMeasure> = emptyList(),

    // ── Dialog States ─────────────────────────────────────────────────────
    val stockAdjustmentTarget: Product? = null,
    val bulkImportState: BulkImportState = BulkImportState(),
    val barcodeGeneratorTarget: Product? = null,

    // ── Barcode Scanner ──────────────────────────────────────────────────
    /** True while the HAL barcode scanner is actively listening. */
    val isScannerActive: Boolean = false,

    // ── Pagination ──────────────────────────────────────────────────────────
    /** Total number of products matching current filters (for pagination). */
    val totalProductCount: Long = 0,
    /** Whether more pages of products are available. */
    val hasMoreProducts: Boolean = false,
    /** True while an additional page is being fetched (infinite scroll). */
    val isLoadingMore: Boolean = false,

    // ── Global ────────────────────────────────────────────────────────────
    val isLoading: Boolean = false,
    val error: String? = null,
    val successMessage: String? = null,

    // ── Sprint 19: Category Management ───────────────────────────────────
    /** All categories (flat) for the CategoryListScreen tree view. */
    val allCategoriesFlat: List<Category> = emptyList(),
    /** Category currently being edited; null = create new. */
    val selectedCategory: Category? = null,
    /** Whether the CategoryDetail screen is visible. */
    val showCategoryDetail: Boolean = false,

    // ── Sprint 19: Supplier Management ───────────────────────────────────
    /** All active suppliers for the SupplierListScreen. */
    val suppliers: List<Supplier> = emptyList(),
    /** Supplier currently being edited; null = create new. */
    val selectedSupplier: Supplier? = null,
    /** Whether the SupplierDetail screen is visible. */
    val showSupplierDetail: Boolean = false,
    /** Read-only purchase order history for the selected supplier. */
    val supplierPurchaseHistory: List<PurchaseOrderSummary> = emptyList(),

    // ── Sprint 19: Tax Group Management ──────────────────────────────────
    /** Whether the TaxGroupScreen is displayed as a standalone modal. */
    val showTaxGroupManagement: Boolean = false,

    // ── Sprint 19: Unit Management ────────────────────────────────────────
    /** Unit groups with their units for UnitManagementScreen. */
    val unitGroups: List<UnitGroup> = emptyList(),
    /** Whether the UnitManagementScreen is displayed. */
    val showUnitManagement: Boolean = false,

    // ── Sprint 19: Low Stock ──────────────────────────────────────────────
    /** Count of products with stockQty < minStockQty for the alert banner. */
    val lowStockCount: Int = 0,

    // ── INV-7: Batch Product Selection ───────────────────────────────────
    /** True when the list is in multi-select mode. */
    val isSelectionMode: Boolean = false,
    /** IDs of products currently selected for batch operations. */
    val selectedProductIds: Set<String> = emptySet(),
)

/** Stock status filter options for the product list. */
enum class StockFilter {
    /** Show all products regardless of stock level. */
    ALL,
    /** Show only products with stockQty > 0. */
    IN_STOCK,
    /** Show only products with stockQty <= minStockQty and stockQty > 0. */
    LOW_STOCK,
    /** Show only products with stockQty <= 0. */
    OUT_OF_STOCK,
}

/** Product list display mode toggle. */
enum class ViewMode {
    /** Tabular ZyntaTable view (Expanded/Medium). */
    LIST,
    /** Card grid view (all breakpoints). */
    GRID,
}

/** Sort direction for table columns. */
enum class SortDir { ASC, DESC }

/**
 * Mutable form fields mirroring [Product] for create/edit operations.
 *
 * Separate from [Product] to support partial editing, validation error tracking,
 * and draft state before persistence.
 */
data class ProductFormState(
    val id: String? = null,
    val name: String = "",
    val barcode: String = "",
    val sku: String = "",
    val categoryId: String = "",
    val unitId: String = "",
    val price: String = "",
    val costPrice: String = "",
    val taxGroupId: String? = null,
    val stockQty: String = "0",
    val minStockQty: String = "0",
    val description: String = "",
    val imageUrl: String? = null,
    val isActive: Boolean = true,
    val isEditing: Boolean = false,
    val validationErrors: Map<String, String> = emptyMap(),
)

/**
 * State for the bulk CSV import dialog.
 */
data class BulkImportState(
    val isVisible: Boolean = false,
    val fileName: String? = null,
    val parsedRows: List<Map<String, String>> = emptyList(),
    val columnMapping: Map<String, String> = emptyMap(),
    val availableColumns: List<String> = emptyList(),
    val importProgress: Float = 0f,
    val isImporting: Boolean = false,
    val importErrors: List<String> = emptyList(),
)
