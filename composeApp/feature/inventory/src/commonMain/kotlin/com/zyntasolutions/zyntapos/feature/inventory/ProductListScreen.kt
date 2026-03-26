package com.zyntasolutions.zyntapos.feature.inventory

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.zyntasolutions.zyntapos.core.utils.CurrencyFormatter
import com.zyntasolutions.zyntapos.designsystem.components.SortDirection
import com.zyntasolutions.zyntapos.designsystem.components.ZyntaProductCard
import com.zyntasolutions.zyntapos.designsystem.components.ZyntaSearchBar
import com.zyntasolutions.zyntapos.designsystem.components.ZyntaTable
import com.zyntasolutions.zyntapos.designsystem.components.ZyntaTableColumn
import org.koin.compose.koinInject
import com.zyntasolutions.zyntapos.designsystem.components.StockIndicator
import com.zyntasolutions.zyntapos.designsystem.components.ZyntaEmptyState
import com.zyntasolutions.zyntapos.designsystem.tokens.ZyntaSpacing
import com.zyntasolutions.zyntapos.designsystem.util.WindowSize
import com.zyntasolutions.zyntapos.designsystem.util.currentWindowSize
import com.zyntasolutions.zyntapos.designsystem.components.LocalStrings
import com.zyntasolutions.zyntapos.core.i18n.StringResource
import com.zyntasolutions.zyntapos.domain.model.Category
import com.zyntasolutions.zyntapos.domain.model.Product

/**
 * Product list/catalogue screen — the main entry point for inventory management.
 *
 * ### Layout Topography (per UI/UX plan §9.1)
 * - **Expanded:** Master-detail split. Left (40%): searchable, filterable product table.
 *   Right (60%): selected product detail/edit form.
 * - **Medium:** Full-width product table. Row tap → navigate to product detail screen.
 * - **Compact:** Card list (single column). Tap → full-screen detail.
 *
 * ### Features
 * - ZyntaTable (list) + grid toggle button
 * - ZyntaSearchBar with FTS5 search via [SearchProductsUseCase]
 * - Category [FilterChip] row for additive filtering
 * - Stock status filter chips (All, In Stock, Low Stock, Out of Stock)
 * - FAB → ProductDetail(productId=null) for new product
 * - Sortable table columns (name, SKU, price, stock qty)
 *
 * @param state           Current [InventoryState] snapshot from [InventoryViewModel].
 * @param onIntent        Dispatches [InventoryIntent] to the ViewModel.
 * @param onNavigateToDetail Navigation callback for product detail screen.
 * @param modifier        Optional root [Modifier].
 */
@Composable
fun ProductListScreen(
    state: InventoryState,
    onIntent: (InventoryIntent) -> Unit,
    onNavigateToDetail: (String?) -> Unit,
    onNavigateToPrintLabels: () -> Unit = {},
    onNavigateToStocktake: () -> Unit = {},
    modifier: Modifier = Modifier,
    currencyFormatter: CurrencyFormatter = koinInject(),
) {
    val s = LocalStrings.current
    val windowSize = currentWindowSize()

    LaunchedEffect(Unit) {
        onIntent(InventoryIntent.LoadProducts)
    }

    // Wire Bulk Import dialog — visibility is controlled by BulkImportState.isVisible
    BulkImportDialog(state = state.bulkImportState, onIntent = onIntent)

    // INV-7: Confirm batch delete dialog
    var showBatchDeleteDialog by remember { mutableStateOf(false) }
    if (showBatchDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showBatchDeleteDialog = false },
            title = { Text(s[StringResource.INVENTORY_BATCH_DELETE_TITLE]) },
            text = { Text(s[StringResource.INVENTORY_BATCH_DELETE_MESSAGE]) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showBatchDeleteDialog = false
                        onIntent(InventoryIntent.BatchDeleteSelectedProducts)
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                ) { Text(s[StringResource.COMMON_DELETE]) }
            },
            dismissButton = {
                TextButton(onClick = { showBatchDeleteDialog = false }) { Text(s[StringResource.COMMON_CANCEL]) }
            },
        )
    }

    Scaffold(
        modifier = modifier,
        floatingActionButton = {
            if (!state.isSelectionMode) {
                ExtendedFloatingActionButton(
                    onClick = { onNavigateToDetail(null) },
                    icon = { Icon(Icons.Default.Add, contentDescription = s[StringResource.INVENTORY_ADD_PRODUCT]) },
                    text = { Text(s[StringResource.INVENTORY_ADD_PRODUCT]) },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                )
            }
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = ZyntaSpacing.md),
        ) {
            // ── INV-7: Batch Action Toolbar (replaces search bar in selection mode) ──
            if (state.isSelectionMode) {
                BatchActionToolbar(
                    selectedCount = state.selectedProductIds.size,
                    totalCount = state.products.size,
                    onSelectAll = { onIntent(InventoryIntent.SelectAllProducts) },
                    onDelete = { showBatchDeleteDialog = true },
                    onCancel = { onIntent(InventoryIntent.ExitSelectionMode) },
                )
            } else {
                // ── Search Bar ────────────────────────────────────────────
                Spacer(Modifier.height(ZyntaSpacing.sm))
                ZyntaSearchBar(
                    query = state.searchQuery,
                    onQueryChange = { onIntent(InventoryIntent.SearchQueryChanged(it)) },
                    onClear = { onIntent(InventoryIntent.SearchQueryChanged("")) },
                    onScanToggle = {
                        if (state.isScannerActive) {
                            onIntent(InventoryIntent.StopBarcodeScanner)
                        } else {
                            onIntent(InventoryIntent.StartBarcodeScanner)
                        }
                    },
                    isScanActive = state.isScannerActive,
                    placeholder = s[StringResource.INVENTORY_SEARCH_PLACEHOLDER],
                )
            }

            Spacer(Modifier.height(ZyntaSpacing.sm))

            // ── Filter Row: Categories + Stock Filters + View Toggle ────
            if (!state.isSelectionMode) {
                ProductFilterRow(
                    categories = state.categories,
                    selectedCategoryId = state.selectedCategoryId,
                    stockFilter = state.stockFilter,
                    viewMode = state.viewMode,
                    onSelectCategory = { onIntent(InventoryIntent.SelectCategory(it)) },
                    onSetStockFilter = { onIntent(InventoryIntent.SetStockFilter(it)) },
                    onToggleViewMode = { onIntent(InventoryIntent.ToggleViewMode) },
                    onBulkImport = { onIntent(InventoryIntent.OpenBulkImport) },
                    onPrintLabels = onNavigateToPrintLabels,
                    onStocktake = onNavigateToStocktake,
                    onEnterSelectionMode = { onIntent(InventoryIntent.EnterSelectionMode) },
                )
            }

            // INV-8: Search result count
            if (!state.isSelectionMode &&
                (state.searchQuery.isNotBlank() || state.selectedCategoryId != null || state.stockFilter != StockFilter.ALL)
            ) {
                Text(
                    text = s[StringResource.INVENTORY_PRODUCTS_FOUND],
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = ZyntaSpacing.xs),
                )
            }

            Spacer(Modifier.height(ZyntaSpacing.sm))

            // ── Product List / Grid ─────────────────────────────────────
            when (state.viewMode) {
                ViewMode.LIST -> ProductTableView(
                    products = state.products,
                    categories = state.categories,
                    isLoading = state.isLoading,
                    sortColumn = state.sortColumn,
                    sortDirection = state.sortDirection,
                    onSort = { onIntent(InventoryIntent.SortByColumn(it)) },
                    onProductClick = { product ->
                        if (state.isSelectionMode) {
                            onIntent(InventoryIntent.ToggleProductSelection(product.id))
                        } else {
                            onNavigateToDetail(product.id)
                        }
                    },
                    onStockAdjust = { onIntent(InventoryIntent.OpenStockAdjustment(it)) },
                    windowSize = windowSize,
                    currencyFormatter = currencyFormatter,
                    isSelectionMode = state.isSelectionMode,
                    selectedProductIds = state.selectedProductIds,
                )
                ViewMode.GRID -> ProductGridView(
                    products = state.products,
                    isLoading = state.isLoading,
                    onProductClick = { product ->
                        if (state.isSelectionMode) {
                            onIntent(InventoryIntent.ToggleProductSelection(product.id))
                        } else {
                            onNavigateToDetail(product.id)
                        }
                    },
                    windowSize = windowSize,
                    currencyFormatter = currencyFormatter,
                    isSelectionMode = state.isSelectionMode,
                    selectedProductIds = state.selectedProductIds,
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Sub-composables: Filter Row, Table View, Grid View
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Horizontal scrollable filter row with category chips, stock status chips,
 * and a list/grid view toggle button.
 *
 * @param categories         All active categories from [InventoryState.categories].
 * @param selectedCategoryId Currently selected category ID; null = "All".
 * @param stockFilter        Current stock status filter.
 * @param viewMode           Current view mode (LIST or GRID).
 * @param onSelectCategory   Category selection callback.
 * @param onSetStockFilter   Stock filter selection callback.
 * @param onToggleViewMode   View mode toggle callback.
 */
/**
 * Top bar shown when the product list is in multi-select mode (INV-7).
 */
@Composable
private fun BatchActionToolbar(
    selectedCount: Int,
    totalCount: Int,
    onSelectAll: () -> Unit,
    onDelete: () -> Unit,
    onCancel: () -> Unit,
) {
    val s = LocalStrings.current
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.primaryContainer,
        tonalElevation = 2.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = ZyntaSpacing.sm, vertical = ZyntaSpacing.xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onCancel) {
                Icon(Icons.Default.Close, contentDescription = s[StringResource.INVENTORY_EXIT_SELECTION])
            }
            Text(
                text = s[StringResource.INVENTORY_SELECTED_COUNT],
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onSelectAll) { Text(s[StringResource.COMMON_SELECT_ALL]) }
            IconButton(onClick = onDelete, enabled = selectedCount > 0) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = s[StringResource.INVENTORY_DELETE_SELECTED],
                    tint = if (selectedCount > 0) MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProductFilterRow(
    categories: List<Category>,
    selectedCategoryId: String?,
    stockFilter: StockFilter,
    viewMode: ViewMode,
    onSelectCategory: (String?) -> Unit,
    onSetStockFilter: (StockFilter) -> Unit,
    onToggleViewMode: () -> Unit,
    onBulkImport: () -> Unit,
    onPrintLabels: () -> Unit = {},
    onStocktake: () -> Unit = {},
    onEnterSelectionMode: () -> Unit = {},
) {
    val s = LocalStrings.current
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // ── Category chips ──────────────────────────────────────────
        @OptIn(ExperimentalLayoutApi::class)
        FlowRow(
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.spacedBy(ZyntaSpacing.xs),
        ) {
            FilterChip(
                selected = selectedCategoryId == null,
                onClick = { onSelectCategory(null) },
                label = { Text(s[StringResource.INVENTORY_FILTER_ALL]) },
            )
            categories.forEach { cat ->
                FilterChip(
                    selected = selectedCategoryId == cat.id,
                    onClick = { onSelectCategory(cat.id) },
                    label = { Text(cat.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                )
            }

            // ── Stock status chips ──────────────────────────────────
            Spacer(Modifier.width(ZyntaSpacing.md))
            StockFilter.entries.forEach { filter ->
                FilterChip(
                    selected = stockFilter == filter,
                    onClick = { onSetStockFilter(filter) },
                    label = { Text(filter.displayLabel()) },
                )
            }
        }

        // ── Stocktake + Print Labels + Bulk Import + Select + View toggle ─
        IconButton(onClick = onStocktake) {
            Icon(
                imageVector = Icons.Default.Inventory,
                contentDescription = s[StringResource.INVENTORY_STOCKTAKE],
            )
        }
        IconButton(onClick = onPrintLabels) {
            Icon(
                imageVector = Icons.Default.Print,
                contentDescription = s[StringResource.INVENTORY_PRINT_LABELS],
            )
        }
        IconButton(onClick = onBulkImport) {
            Icon(
                imageVector = Icons.Default.Upload,
                contentDescription = s[StringResource.INVENTORY_BULK_IMPORT],
            )
        }
        // INV-7: Enter multi-select mode
        IconButton(onClick = onEnterSelectionMode) {
            Icon(
                imageVector = Icons.Default.Checklist,
                contentDescription = s[StringResource.INVENTORY_SELECT_PRODUCTS],
            )
        }
        IconButton(onClick = onToggleViewMode) {
            Icon(
                imageVector = if (viewMode == ViewMode.LIST) Icons.Default.GridView else Icons.AutoMirrored.Filled.ViewList,
                contentDescription = if (viewMode == ViewMode.LIST) s[StringResource.INVENTORY_SWITCH_GRID] else s[StringResource.INVENTORY_SWITCH_LIST],
            )
        }
    }
}

/**
 * Tabular product list using [ZyntaTable] with sortable columns.
 *
 * Columns: Image (thumb), Name, SKU, Category, Price, Stock Qty, Status.
 * Desktop: Right-click context menu support (Phase 2).
 *
 * @param products       Pre-filtered product list.
 * @param categories     All categories for name resolution.
 * @param isLoading      Whether the data is still loading.
 * @param sortColumn     Current sort column key.
 * @param sortDirection  Current sort direction.
 * @param onSort         Sort callback with column key.
 * @param onProductClick Product row tap callback.
 * @param onStockAdjust  Stock adjustment shortcut callback.
 * @param windowSize     Current responsive window size.
 */
@Composable
private fun ProductTableView(
    products: List<Product>,
    categories: List<Category>,
    isLoading: Boolean,
    sortColumn: String?,
    sortDirection: SortDir,
    onSort: (String) -> Unit,
    onProductClick: (Product) -> Unit,
    onStockAdjust: (Product) -> Unit,
    windowSize: WindowSize,
    currencyFormatter: CurrencyFormatter,
    isSelectionMode: Boolean = false,
    selectedProductIds: Set<String> = emptySet(),
) {
    val s = LocalStrings.current
    val categoryMap = remember(categories) { categories.associateBy { it.id } }

    val columns = buildList {
        if (isSelectionMode) add(ZyntaTableColumn("select", "", weight = 0.5f, sortable = false))
        add(ZyntaTableColumn("name", s[StringResource.INVENTORY_PRODUCT_NAME], weight = 2.5f))
        if (windowSize != WindowSize.COMPACT) {
            add(ZyntaTableColumn("sku", s[StringResource.INVENTORY_SKU], weight = 1.2f))
            add(ZyntaTableColumn("category", s[StringResource.INVENTORY_CATEGORY], weight = 1.5f))
        }
        add(ZyntaTableColumn("price", s[StringResource.COMMON_PRICE], weight = 1f))
        add(ZyntaTableColumn("stockQty", s[StringResource.INVENTORY_STOCK_LEVEL], weight = 1f))
        if (windowSize == WindowSize.EXPANDED) {
            add(ZyntaTableColumn("status", s[StringResource.COMMON_STATUS], weight = 1f, sortable = false))
        }
    }

    val tableSortDir = when (sortDirection) {
        SortDir.ASC -> SortDirection.Ascending
        SortDir.DESC -> SortDirection.Descending
    }

    ZyntaTable(
        columns = columns,
        items = products,
        sortColumnKey = sortColumn,
        sortDirection = tableSortDir,
        onSort = onSort,
        isLoading = isLoading,
        modifier = Modifier.fillMaxSize(),
        rowKey = { it.id },
        emptyContent = {
            ZyntaEmptyState(
                title = s[StringResource.INVENTORY_NO_PRODUCTS],
                icon = Icons.Default.Inventory2,
                subtitle = s[StringResource.INVENTORY_NO_PRODUCTS_SUBTITLE],
                modifier = Modifier.fillMaxWidth().padding(ZyntaSpacing.xl),
            )
        },
        rowContent = { product: Product ->
        // INV-7: Checkbox column in selection mode
        if (isSelectionMode) {
            Checkbox(
                checked = product.id in selectedProductIds,
                onCheckedChange = { onProductClick(product) },
                modifier = Modifier.weight(0.5f),
            )
        }
        // Name column
        Text(
            text = product.name,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .weight(2.5f)
                .clickable { onProductClick(product) },
        )
        // SKU column (Medium/Expanded only)
        if (windowSize != WindowSize.COMPACT) {
            Text(
                text = product.sku ?: "—",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.weight(1.2f),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = categoryMap[product.categoryId]?.name ?: "—",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.weight(1.5f),
            )
        }
        // Price
        Text(
            text = currencyFormatter.format(product.price),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f),
        )
        // Stock
        Text(
            text = product.stockQty.toStockDisplay(),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f),
            color = product.stockColor(),
        )
        // Status badge (Expanded only)
        if (windowSize == WindowSize.EXPANDED) {
            Box(modifier = Modifier.weight(1f)) {
                val indicator = product.toStockIndicator()
                AssistChip(
                    onClick = { onStockAdjust(product) },
                    label = { Text(indicator.label, style = MaterialTheme.typography.labelSmall) },
                    leadingIcon = {
                        Icon(
                            imageVector = indicator.icon,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
                        )
                    },
                    colors = AssistChipDefaults.assistChipColors(
                        containerColor = indicator.chipColor(),
                    ),
                )
            }
        }
    },
)
}

/**
 * Responsive grid view of products using cards.
 *
 * Column rules (per UI/UX plan §9.1):
 * - COMPACT  → 2 columns
 * - MEDIUM   → 3–4 columns
 * - EXPANDED → 4–6 columns
 *
 * @param products       Pre-filtered product list.
 * @param isLoading      Loading state.
 * @param onProductClick Product card tap callback.
 * @param windowSize     Current responsive window size.
 */
@Composable
private fun ProductGridView(
    products: List<Product>,
    isLoading: Boolean,
    onProductClick: (Product) -> Unit,
    windowSize: WindowSize,
    currencyFormatter: CurrencyFormatter,
    isSelectionMode: Boolean = false,
    selectedProductIds: Set<String> = emptySet(),
) {
    if (isLoading) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    val s = LocalStrings.current
    if (products.isEmpty()) {
        ZyntaEmptyState(
            title = s[StringResource.INVENTORY_NO_PRODUCTS],
            icon = Icons.Default.Inventory2,
            subtitle = s[StringResource.INVENTORY_NO_PRODUCTS_SUBTITLE],
            modifier = Modifier.fillMaxSize(),
        )
        return
    }

    val columns = when (windowSize) {
        WindowSize.COMPACT -> 2
        WindowSize.MEDIUM -> 3
        WindowSize.EXPANDED -> 5
    }

    LazyVerticalGrid(
        columns = GridCells.Fixed(columns),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(ZyntaSpacing.sm),
        horizontalArrangement = Arrangement.spacedBy(ZyntaSpacing.sm),
        verticalArrangement = Arrangement.spacedBy(ZyntaSpacing.sm),
    ) {
        items(products, key = { it.id }) { product ->
            val isSelected = isSelectionMode && product.id in selectedProductIds
            ZyntaProductCard(
                name = product.name,
                price = currencyFormatter.format(product.price),
                imageUrl = product.imageUrl,
                stockIndicator = product.toStockIndicator(),
                onClick = { onProductClick(product) },
                isSelected = isSelected,
            )
        }
    }
}

// ── Private helpers ───────────────────────────────────────────────────────────

/** Display label for [StockFilter] enum values. */
private fun StockFilter.displayLabel(): String = when (this) {
    StockFilter.ALL -> "All"
    StockFilter.IN_STOCK -> "In Stock"
    StockFilter.LOW_STOCK -> "Low Stock"
    StockFilter.OUT_OF_STOCK -> "Out of Stock"
}

/** Converts stock quantity to a display string. */
private fun Double.toStockDisplay(): String =
    if (this == this.toLong().toDouble()) this.toLong().toString() else "%.1f".format(this)

/** Maps product to stock indicator for card badges. */
private fun Product.toStockIndicator(): StockIndicator = when {
    stockQty <= 0.0 -> StockIndicator.OutOfStock
    stockQty <= minStockQty.coerceAtLeast(1.0) -> StockIndicator.LowStock
    else -> StockIndicator.InStock
}

/** Returns appropriate color for stock quantity display. */
@Composable
private fun Product.stockColor() = when {
    stockQty <= 0.0 -> MaterialTheme.colorScheme.error
    stockQty <= minStockQty.coerceAtLeast(1.0) -> MaterialTheme.colorScheme.tertiary
    else -> MaterialTheme.colorScheme.onSurface
}

/** Maps [StockIndicator] to a chip container color. */
@Composable
private fun StockIndicator.chipColor() = when (this) {
    StockIndicator.InStock -> MaterialTheme.colorScheme.tertiaryContainer
    StockIndicator.LowStock -> MaterialTheme.colorScheme.secondaryContainer
    StockIndicator.OutOfStock -> MaterialTheme.colorScheme.errorContainer
}

/** Display label for [StockIndicator]. */
private val StockIndicator.label: String get() = when (this) {
    StockIndicator.InStock -> "In Stock"
    StockIndicator.LowStock -> "Low"
    StockIndicator.OutOfStock -> "Out"
}

/** Icon for [StockIndicator] (accessibility — do not rely on color alone). */
private val StockIndicator.icon get() = when (this) {
    StockIndicator.InStock -> Icons.Default.CheckCircle
    StockIndicator.LowStock -> Icons.Default.Warning
    StockIndicator.OutOfStock -> Icons.Default.Error
}
