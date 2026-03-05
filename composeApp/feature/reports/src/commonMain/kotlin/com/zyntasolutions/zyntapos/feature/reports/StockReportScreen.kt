package com.zyntasolutions.zyntapos.feature.reports

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import com.zyntasolutions.zyntapos.core.utils.CurrencyFormatter
import com.zyntasolutions.zyntapos.designsystem.components.ZyntaEmptyState
import com.zyntasolutions.zyntapos.designsystem.components.ZyntaLoadingOverlay
import com.zyntasolutions.zyntapos.designsystem.components.ZyntaStatusBadge
import com.zyntasolutions.zyntapos.designsystem.layouts.ZyntaPageScaffold
import com.zyntasolutions.zyntapos.designsystem.tokens.ZyntaSpacing
import com.zyntasolutions.zyntapos.domain.model.Product
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel

// ─── Tab definitions ─────────────────────────────────────────────────────────

private enum class StockTab(val title: String, val icon: ImageVector) {
    ALL("All Stock", Icons.Default.Inventory2),
    LOW("Low Stock", Icons.Default.Warning),
    DEAD("Dead Stock", Icons.Default.Block),
}

/**
 * Stock report screen — step 12.1.4 / 12.1.5.
 *
 * Features:
 * - Current stock levels ZyntaTable (product, category, qty, value, status badge)
 * - Low stock section: items where qty < minStockQty (highlighted amber)
 * - Dead stock section: items with no movement in 30 days (highlighted gray)
 * - Category filter FilterChip row
 * - Export CSV / PDF actions
 * - Tab-based layout: All Stock, Low Stock, Dead Stock
 * - Summary card with total, low stock, and dead stock counts
 *
 * Uses paged SQLDelight query via [GenerateStockReportUseCase] to handle 10K+ products.
 *
 * @param onNavigateUp Back navigation handler.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StockReportScreen(
    onNavigateUp: () -> Unit,
    viewModel: ReportsViewModel = koinViewModel(),
    formatter: CurrencyFormatter = koinInject(),
) {
    val state by viewModel.state.collectAsState()
    val s = state.stockReport

    var selectedTabIndex by rememberSaveable { mutableIntStateOf(0) }

    // Auto-load on first composition
    LaunchedEffect(Unit) {
        if (s.allProducts.isEmpty() && !s.isLoading) viewModel.dispatch(ReportsIntent.LoadStockReport)
    }

    // Derive distinct categories for filter chips
    val categories = s.allProducts.map { it.categoryId }.distinct()

    // Apply category filter and sort
    val filteredProducts = s.allProducts
        .filter { s.selectedCategory == null || it.categoryId == s.selectedCategory }
        .sortedWith(stockComparator(s.sortColumn, s.sortAscending))

    ZyntaPageScaffold(
        title = "Stock Report",
        onNavigateBack = onNavigateUp,
        actions = {
            IconButton(onClick = { viewModel.dispatch(ReportsIntent.ExportStockReportCsv) }) {
                Icon(Icons.Default.FileDownload, contentDescription = "Export CSV")
            }
            IconButton(onClick = { viewModel.dispatch(ReportsIntent.ExportStockReportPdf) }) {
                Icon(Icons.Default.PictureAsPdf, contentDescription = "Export PDF")
            }
        },
    ) { paddingValues ->
        Box(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
            ) {
                // ── Summary card ──────────────────────────────────────────────
                if (s.allProducts.isNotEmpty()) {
                    StockSummaryCard(
                        totalProducts = s.allProducts.size,
                        lowStockCount = s.lowStockItems.size,
                        deadStockCount = s.deadStockItems.size,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(
                                start = ZyntaSpacing.md,
                                end = ZyntaSpacing.md,
                                top = ZyntaSpacing.md,
                                bottom = ZyntaSpacing.sm,
                            ),
                    )
                }

                // ── Tab row ──────────────────────────────────────────────────
                PrimaryScrollableTabRow(
                    selectedTabIndex = selectedTabIndex,
                    edgePadding = ZyntaSpacing.md,
                    divider = {},
                ) {
                    StockTab.entries.forEachIndexed { index, tab ->
                        val badgeCount = when (tab) {
                            StockTab.LOW -> s.lowStockItems.size
                            StockTab.DEAD -> s.deadStockItems.size
                            else -> 0
                        }
                        Tab(
                            selected = selectedTabIndex == index,
                            onClick = { selectedTabIndex = index },
                            text = { Text(tab.title) },
                            icon = {
                                if (badgeCount > 0) {
                                    BadgedBox(
                                        badge = {
                                            Badge { Text(badgeCount.toString()) }
                                        },
                                    ) {
                                        Icon(tab.icon, contentDescription = tab.title)
                                    }
                                } else {
                                    Icon(tab.icon, contentDescription = tab.title)
                                }
                            },
                        )
                    }
                }

                HorizontalDivider()

                // ── Tab content ──────────────────────────────────────────────
                when {
                    // Empty state
                    s.allProducts.isEmpty() && !s.isLoading && s.error == null -> {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center,
                        ) {
                            ZyntaEmptyState(
                                icon = Icons.Default.Inventory2,
                                title = "No Stock Data",
                                subtitle = "Stock data will appear here once products are added to inventory.",
                                modifier = Modifier.fillMaxWidth().padding(ZyntaSpacing.xl),
                            )
                        }
                    }

                    // Error
                    s.error != null -> {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = s.error,
                                color = MaterialTheme.colorScheme.error,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth().padding(ZyntaSpacing.md),
                            )
                        }
                    }

                    // Tab content
                    else -> {
                        when (StockTab.entries[selectedTabIndex]) {
                            StockTab.ALL -> AllStockTabContent(
                                categories = categories,
                                selectedCategory = s.selectedCategory,
                                onCategorySelected = {
                                    viewModel.dispatch(ReportsIntent.FilterStockByCategory(it))
                                },
                                filteredProducts = filteredProducts,
                                sortColumn = s.sortColumn,
                                sortAscending = s.sortAscending,
                                onSort = { col ->
                                    val asc = if (s.sortColumn == col) !s.sortAscending else true
                                    viewModel.dispatch(ReportsIntent.SortStock(col, asc))
                                },
                                formatter = formatter,
                            )

                            StockTab.LOW -> LowStockTabContent(
                                lowStockItems = s.lowStockItems,
                                formatter = formatter,
                            )

                            StockTab.DEAD -> DeadStockTabContent(
                                deadStockItems = s.deadStockItems,
                                formatter = formatter,
                            )
                        }
                    }
                }
            }

            if (s.isLoading) ZyntaLoadingOverlay(isLoading = true)
        }
    }
}

// ─── Summary card ────────────────────────────────────────────────────────────

@Composable
private fun StockSummaryCard(
    totalProducts: Int,
    lowStockCount: Int,
    deadStockCount: Int,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(ZyntaSpacing.md),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            SummaryMetric(
                label = "Total Products",
                value = totalProducts.toString(),
                modifier = Modifier.weight(1f),
            )
            SummaryMetric(
                label = "Low Stock",
                value = lowStockCount.toString(),
                valueColor = if (lowStockCount > 0) MaterialTheme.colorScheme.secondary else null,
                modifier = Modifier.weight(1f),
            )
            SummaryMetric(
                label = "Dead Stock",
                value = deadStockCount.toString(),
                valueColor = if (deadStockCount > 0) MaterialTheme.colorScheme.onSurfaceVariant else null,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun SummaryMetric(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    valueColor: androidx.compose.ui.graphics.Color? = null,
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(ZyntaSpacing.xs))
        Text(
            text = value,
            style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
            color = valueColor ?: MaterialTheme.colorScheme.onSurface,
        )
    }
}

// ─── Tab content composables ─────────────────────────────────────────────────

@Composable
private fun AllStockTabContent(
    categories: List<String>,
    selectedCategory: String?,
    onCategorySelected: (String?) -> Unit,
    filteredProducts: List<Product>,
    sortColumn: StockSortColumn,
    sortAscending: Boolean,
    onSort: (StockSortColumn) -> Unit,
    formatter: CurrencyFormatter,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(ZyntaSpacing.md),
        verticalArrangement = Arrangement.spacedBy(ZyntaSpacing.sm),
    ) {
        // Category filter chips
        if (categories.isNotEmpty()) {
            item {
                CategoryFilterRow(
                    categories = categories,
                    selectedCategory = selectedCategory,
                    onCategorySelected = onCategorySelected,
                )
            }
        }

        if (filteredProducts.isNotEmpty()) {
            item {
                Text(
                    "All Products (${filteredProducts.size})",
                    style = MaterialTheme.typography.titleSmall,
                )
            }
            item {
                StockTableHeader(
                    sortColumn = sortColumn,
                    sortAscending = sortAscending,
                    onSort = onSort,
                )
            }
            items(filteredProducts, key = { it.id }) { product ->
                StockProductCard(
                    product = product,
                    highlight = StockHighlight.NONE,
                    formatter = formatter,
                )
            }
        } else {
            item {
                ZyntaEmptyState(
                    icon = Icons.Default.Inventory2,
                    title = "No Products Found",
                    subtitle = "Try adjusting the category filter.",
                    modifier = Modifier.fillMaxWidth().padding(ZyntaSpacing.xl),
                )
            }
        }
    }
}

@Composable
private fun LowStockTabContent(
    lowStockItems: List<Product>,
    formatter: CurrencyFormatter,
) {
    if (lowStockItems.isEmpty()) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            ZyntaEmptyState(
                icon = Icons.Default.Warning,
                title = "No Low Stock Items",
                subtitle = "All products are above their minimum stock threshold.",
                modifier = Modifier.fillMaxWidth().padding(ZyntaSpacing.xl),
            )
        }
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(ZyntaSpacing.md),
            verticalArrangement = Arrangement.spacedBy(ZyntaSpacing.sm),
        ) {
            item {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(ZyntaSpacing.xs),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.Default.Warning,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.secondary,
                    )
                    Text(
                        text = "Low Stock (${lowStockItems.size} items)",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.secondary,
                    )
                }
            }
            items(lowStockItems, key = { "low-${it.id}" }) { product ->
                StockProductCard(
                    product = product,
                    highlight = StockHighlight.LOW,
                    formatter = formatter,
                )
            }
        }
    }
}

@Composable
private fun DeadStockTabContent(
    deadStockItems: List<Product>,
    formatter: CurrencyFormatter,
) {
    if (deadStockItems.isEmpty()) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            ZyntaEmptyState(
                icon = Icons.Default.Block,
                title = "No Dead Stock",
                subtitle = "All products have had movement in the last 30 days.",
                modifier = Modifier.fillMaxWidth().padding(ZyntaSpacing.xl),
            )
        }
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(ZyntaSpacing.md),
            verticalArrangement = Arrangement.spacedBy(ZyntaSpacing.sm),
        ) {
            item {
                Text(
                    text = "Dead Stock (${deadStockItems.size} items — no movement in 30 days)",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                )
            }
            items(deadStockItems, key = { "dead-${it.id}" }) { product ->
                StockProductCard(
                    product = product,
                    highlight = StockHighlight.DEAD,
                    formatter = formatter,
                )
            }
        }
    }
}

// ─── Internal composables ─────────────────────────────────────────────────────

private enum class StockHighlight { NONE, LOW, DEAD }

@Composable
private fun CategoryFilterRow(
    categories: List<String>,
    selectedCategory: String?,
    onCategorySelected: (String?) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(ZyntaSpacing.sm),
    ) {
        FilterChip(
            selected = selectedCategory == null,
            onClick = { onCategorySelected(null) },
            label = { Text("All") },
        )
        categories.forEach { cat ->
            FilterChip(
                selected = selectedCategory == cat,
                onClick = { onCategorySelected(cat) },
                label = { Text(cat) },
            )
        }
    }
}

@Composable
private fun StockTableHeader(
    sortColumn: StockSortColumn,
    sortAscending: Boolean,
    onSort: (StockSortColumn) -> Unit,
) {
    Row(modifier = Modifier.fillMaxWidth()) {
        SortableHeader("Product", StockSortColumn.NAME, sortColumn, sortAscending, onSort, Modifier.weight(2f))
        SortableHeader("Qty",     StockSortColumn.QTY, sortColumn, sortAscending, onSort, Modifier.weight(1f))
        SortableHeader("Value",   StockSortColumn.VALUE, sortColumn, sortAscending, onSort, Modifier.weight(1f))
        Text("Status", style = MaterialTheme.typography.labelSmall, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun SortableHeader(
    label: String,
    column: StockSortColumn,
    current: StockSortColumn,
    ascending: Boolean,
    onSort: (StockSortColumn) -> Unit,
    modifier: Modifier = Modifier,
) {
    val indicator = if (current == column) if (ascending) " \u25B2" else " \u25BC" else ""
    Text(
        text = "$label$indicator",
        style = MaterialTheme.typography.labelSmall,
        modifier = modifier.then(
            Modifier.padding(end = ZyntaSpacing.xs)
        ),
        color = if (current == column) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurface,
    )
}

/**
 * Enhanced stock product row wrapped in a [Card] with surfaceContainerLow background,
 * status badge with color coding, and category helper text.
 */
@Composable
private fun StockProductCard(product: Product, highlight: StockHighlight, formatter: CurrencyFormatter) {
    val statusLabel = when {
        product.stockQty <= 0              -> "Out"
        product.stockQty < product.minStockQty -> "Low"
        else                               -> "OK"
    }
    val statusColor = when (statusLabel) {
        "Out" -> MaterialTheme.colorScheme.error
        "Low" -> MaterialTheme.colorScheme.secondary
        else  -> MaterialTheme.colorScheme.tertiary
    }

    val containerColor = when (highlight) {
        StockHighlight.LOW -> MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.3f)
        StockHighlight.DEAD -> MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.5f)
        StockHighlight.NONE -> MaterialTheme.colorScheme.surfaceContainerLow
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = containerColor),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = ZyntaSpacing.md, vertical = ZyntaSpacing.sm),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(2f)) {
                Text(product.name, style = MaterialTheme.typography.bodyMedium)
                Text(
                    product.categoryId,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                "%.0f".format(product.stockQty),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f),
                textAlign = TextAlign.End,
            )
            Text(
                formatter.format(product.stockQty * product.price),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f),
                textAlign = TextAlign.End,
            )
            ZyntaStatusBadge(
                label = statusLabel,
                customColor = statusColor,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

/**
 * Legacy row composable retained for backward compatibility.
 * Delegates to the enhanced [StockProductCard].
 */
@Suppress("UnusedPrivateMember")
@Composable
private fun StockProductRow(product: Product, highlight: StockHighlight, formatter: CurrencyFormatter) {
    StockProductCard(product = product, highlight = highlight, formatter = formatter)
}

// ─── Sort comparator ──────────────────────────────────────────────────────────

private fun stockComparator(column: StockSortColumn, ascending: Boolean): Comparator<Product> {
    val base: Comparator<Product> = when (column) {
        StockSortColumn.NAME     -> compareBy { it.name }
        StockSortColumn.CATEGORY -> compareBy { it.categoryId }
        StockSortColumn.QTY      -> compareBy { it.stockQty }
        StockSortColumn.VALUE    -> compareBy { it.stockQty * it.price }
        StockSortColumn.STATUS   -> compareBy { it.stockQty }
    }
    return if (ascending) base else base.reversed()
}
