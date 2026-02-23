package com.zyntasolutions.zyntapos.feature.reports

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
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

/**
 * Stock report screen — step 12.1.4 / 12.1.5.
 *
 * Features:
 * - Current stock levels ZyntaTable (product, category, qty, value, status badge)
 * - Low stock section: items where qty < minStockQty (highlighted amber)
 * - Dead stock section: items with no movement in 30 days (highlighted gray)
 * - Category filter FilterChip row
 * - Export CSV / PDF actions
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
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentPadding = PaddingValues(ZyntaSpacing.md),
                verticalArrangement = Arrangement.spacedBy(ZyntaSpacing.md),
            ) {
                // Category filter chips
                if (categories.isNotEmpty()) {
                    item {
                        CategoryFilterRow(
                            categories = categories,
                            selectedCategory = s.selectedCategory,
                            onCategorySelected = {
                                viewModel.dispatch(ReportsIntent.FilterStockByCategory(it))
                            },
                        )
                    }
                }

                // ── Low stock section ──────────────────────────────────────
                if (s.lowStockItems.isNotEmpty()) {
                    item {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(ZyntaSpacing.xs),
                        ) {
                            Icon(
                                Icons.Default.Warning,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.secondary,
                            )
                            Text(
                                text = "Low Stock",
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.secondary,
                            )
                        }
                    }
                    items(s.lowStockItems, key = { "low-${it.id}" }) { product ->
                        StockProductRow(product = product, highlight = StockHighlight.LOW, formatter = formatter)
                    }
                    item { HorizontalDivider() }
                }

                // ── Dead stock section ─────────────────────────────────────
                if (s.deadStockItems.isNotEmpty()) {
                    item {
                        Text(
                            text = "Dead Stock (no movement in 30 days)",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                        )
                    }
                    items(s.deadStockItems, key = { "dead-${it.id}" }) { product ->
                        StockProductRow(product = product, highlight = StockHighlight.DEAD, formatter = formatter)
                    }
                    item { HorizontalDivider() }
                }

                // ── All products table ────────────────────────────────────
                if (filteredProducts.isNotEmpty()) {
                    item {
                        Text("All Products (${filteredProducts.size})", style = MaterialTheme.typography.titleSmall)
                    }
                    item {
                        StockTableHeader(
                            sortColumn = s.sortColumn,
                            sortAscending = s.sortAscending,
                            onSort = { col ->
                                val asc = if (s.sortColumn == col) !s.sortAscending else true
                                viewModel.dispatch(ReportsIntent.SortStock(col, asc))
                            },
                        )
                    }
                    items(filteredProducts, key = { it.id }) { product ->
                        StockProductRow(product = product, highlight = StockHighlight.NONE, formatter = formatter)
                    }
                }

                // Empty state
                if (s.allProducts.isEmpty() && !s.isLoading && s.error == null) {
                    item {
                        ZyntaEmptyState(
                            icon = Icons.Default.Inventory2,
                            title = "No Stock Data",
                            subtitle = "Stock data will appear here once products are added to inventory.",
                            modifier = Modifier.fillMaxWidth().padding(ZyntaSpacing.xl),
                        )
                    }
                }

                // Error
                s.error?.let { error ->
                    item {
                        Text(
                            text = error,
                            color = MaterialTheme.colorScheme.error,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }

            if (s.isLoading) ZyntaLoadingOverlay(isLoading = true)
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

@Composable
private fun StockProductRow(product: Product, highlight: StockHighlight, formatter: CurrencyFormatter) {
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

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = ZyntaSpacing.xs),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(modifier = Modifier.weight(2f)) {
            Text(product.name, style = MaterialTheme.typography.bodyMedium)
            Text(product.categoryId, style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text("%.0f".format(product.stockQty), style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f), textAlign = TextAlign.End)
        Text(formatter.format(product.stockQty * product.price), style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f), textAlign = TextAlign.End)
        ZyntaStatusBadge(label = statusLabel, customColor = statusColor, modifier = Modifier.weight(1f))
    }
    HorizontalDivider()
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
