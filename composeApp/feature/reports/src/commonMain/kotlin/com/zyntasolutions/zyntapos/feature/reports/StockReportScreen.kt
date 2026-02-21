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
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.PictureAsPdf
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
import androidx.compose.ui.unit.dp
import com.zyntasolutions.zyntapos.designsystem.components.ZentaBadge
import com.zyntasolutions.zyntapos.designsystem.components.ZentaLoadingOverlay
import com.zyntasolutions.zyntapos.designsystem.components.ZentaTopAppBar
import com.zyntasolutions.zyntapos.designsystem.layouts.ZentaScaffold
import com.zyntasolutions.zyntapos.domain.model.Product
import org.koin.compose.viewmodel.koinViewModel

/**
 * Stock report screen — step 12.1.4 / 12.1.5.
 *
 * Features:
 * - Current stock levels ZentaTable (product, category, qty, value, status badge)
 * - Low stock section: items where qty < minStockQty (highlighted amber)
 * - Dead stock section: items with no movement in 30 days (highlighted gray)
 * - Category filter FilterChip row
 * - Export CSV / PDF actions
 *
 * Uses paged SQLDelight query via [GenerateStockReportUseCase] to handle 10K+ products.
 *
 * @param onNavigateUp Back navigation handler.
 */
@Composable
fun StockReportScreen(
    onNavigateUp: () -> Unit,
    viewModel: ReportsViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val s = state.stockReport

    // Auto-load on first composition
    LaunchedEffect(Unit) {
        if (s.allProducts.isEmpty() && !s.isLoading) viewModel.onIntent(ReportsIntent.LoadStockReport)
    }

    // Derive distinct categories for filter chips
    val categories = s.allProducts.map { it.categoryId }.distinct()

    // Apply category filter and sort
    val filteredProducts = s.allProducts
        .filter { s.selectedCategory == null || it.categoryId == s.selectedCategory }
        .sortedWith(stockComparator(s.sortColumn, s.sortAscending))

    ZentaScaffold(
        topBar = {
            ZentaTopAppBar(
                title = "Stock Report",
                onNavigateUp = onNavigateUp,
                actions = {
                    IconButton(onClick = { viewModel.onIntent(ReportsIntent.ExportStockReportCsv) }) {
                        Icon(Icons.Default.FileDownload, contentDescription = "Export CSV")
                    }
                    IconButton(onClick = { viewModel.onIntent(ReportsIntent.ExportStockReportPdf) }) {
                        Icon(Icons.Default.PictureAsPdf, contentDescription = "Export PDF")
                    }
                },
            )
        },
    ) { paddingValues ->
        Box(modifier = Modifier.fillMaxSize()) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                // Category filter chips
                if (categories.isNotEmpty()) {
                    item {
                        CategoryFilterRow(
                            categories = categories,
                            selectedCategory = s.selectedCategory,
                            onCategorySelected = {
                                viewModel.onIntent(ReportsIntent.FilterStockByCategory(it))
                            },
                        )
                    }
                }

                // ── Low stock section ──────────────────────────────────────
                if (s.lowStockItems.isNotEmpty()) {
                    item {
                        SectionHeader(title = "⚠️ Low Stock", color = androidx.compose.ui.graphics.Color(0xFFF59E0B))
                    }
                    items(s.lowStockItems, key = { "low-${it.id}" }) { product ->
                        StockProductRow(product = product, highlight = StockHighlight.LOW)
                    }
                    item { HorizontalDivider() }
                }

                // ── Dead stock section ─────────────────────────────────────
                if (s.deadStockItems.isNotEmpty()) {
                    item {
                        SectionHeader(title = "☠️ Dead Stock (no movement in 30 days)", color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f))
                    }
                    items(s.deadStockItems, key = { "dead-${it.id}" }) { product ->
                        StockProductRow(product = product, highlight = StockHighlight.DEAD)
                    }
                    item { HorizontalDivider() }
                }

                // ── All products table ────────────────────────────────────
                item {
                    Text("All Products (${filteredProducts.size})", style = MaterialTheme.typography.titleSmall)
                }
                item {
                    StockTableHeader(
                        sortColumn = s.sortColumn,
                        sortAscending = s.sortAscending,
                        onSort = { col ->
                            val asc = if (s.sortColumn == col) !s.sortAscending else true
                            viewModel.onIntent(ReportsIntent.SortStock(col, asc))
                        },
                    )
                }
                items(filteredProducts, key = { it.id }) { product ->
                    StockProductRow(product = product, highlight = StockHighlight.NONE)
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

            if (s.isLoading) ZentaLoadingOverlay()
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
        horizontalArrangement = Arrangement.spacedBy(8.dp),
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
private fun SectionHeader(title: String, color: androidx.compose.ui.graphics.Color) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        color = color,
    )
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
    val indicator = if (current == column) if (ascending) " ▲" else " ▼" else ""
    Text(
        text = "$label$indicator",
        style = MaterialTheme.typography.labelSmall,
        modifier = modifier.then(
            Modifier.padding(end = 4.dp)
        ),
        color = if (current == column) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurface,
    )
}

@Composable
private fun StockProductRow(product: Product, highlight: StockHighlight) {
    val bgColor = when (highlight) {
        StockHighlight.LOW  -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)
        StockHighlight.DEAD -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        StockHighlight.NONE -> androidx.compose.ui.graphics.Color.Transparent
    }
    val statusLabel = when {
        product.stockQty <= 0              -> "Out"
        product.stockQty < product.minStockQty -> "Low"
        else                               -> "OK"
    }
    val statusColor = when (statusLabel) {
        "Out" -> MaterialTheme.colorScheme.error
        "Low" -> androidx.compose.ui.graphics.Color(0xFFF59E0B)
        else  -> androidx.compose.ui.graphics.Color(0xFF22C55E)
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(modifier = Modifier.weight(2f)) {
            Text(product.name, style = MaterialTheme.typography.bodyMedium)
            Text(product.categoryId, style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text("%.0f".format(product.stockQty), style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f), textAlign = TextAlign.End)
        Text("LKR %.2f".format(product.stockQty * product.price), style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f), textAlign = TextAlign.End)
        ZentaBadge(label = statusLabel, color = statusColor, modifier = Modifier.weight(1f))
    }
    HorizontalDivider(thickness = 0.5.dp)
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
