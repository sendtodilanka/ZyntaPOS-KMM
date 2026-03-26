package com.zyntasolutions.zyntapos.feature.multistore

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.zyntasolutions.zyntapos.core.i18n.StringResource
import com.zyntasolutions.zyntapos.designsystem.components.LocalStrings
import com.zyntasolutions.zyntapos.designsystem.components.ZyntaEmptyState
import com.zyntasolutions.zyntapos.designsystem.tokens.ZyntaSpacing
import com.zyntasolutions.zyntapos.domain.model.WarehouseStock

/**
 * Per-warehouse stock level screen (C1.2).
 *
 * Displays two tabs:
 * - **All Stock** — full product list with current quantity and reorder threshold.
 * - **Low Stock** — only items at or below their reorder threshold.
 *
 * A FAB triggers the [WarehouseStockEntryScreen] to set stock for a product.
 *
 * @param state       Current [WarehouseState].
 * @param onIntent    Dispatches intents to [WarehouseViewModel].
 * @param warehouseId ID of the warehouse being viewed.
 * @param modifier    Optional modifier.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WarehouseStockScreen(
    state: WarehouseState,
    onIntent: (WarehouseIntent) -> Unit,
    warehouseId: String,
    modifier: Modifier = Modifier,
) {
    LaunchedEffect(warehouseId) {
        onIntent(WarehouseIntent.LoadWarehouseStock(warehouseId))
    }

    val s = LocalStrings.current
    var selectedTab by remember { mutableIntStateOf(0) }

    Scaffold(
        modifier = modifier,
        floatingActionButton = {
            FloatingActionButton(
                onClick = { onIntent(WarehouseIntent.OpenStockEntry(warehouseId, null)) },
            ) {
                Icon(Icons.Default.Add, contentDescription = s[StringResource.MULTISTORE_SET_STOCK])
            }
        },
    ) { innerPadding ->
        Column(Modifier.fillMaxSize().padding(innerPadding)) {

            // ── Search bar ────────────────────────────────────────────────────
            OutlinedTextField(
                value = state.stockSearchQuery,
                onValueChange = { onIntent(WarehouseIntent.SearchStock(it)) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = ZyntaSpacing.md, vertical = ZyntaSpacing.sm),
                placeholder = { Text(s[StringResource.MULTISTORE_SEARCH_STOCK_PLACEHOLDER]) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                singleLine = true,
            )

            // ── Tabs ──────────────────────────────────────────────────────────
            TabRow(selectedTabIndex = selectedTab) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = { Text(s[StringResource.MULTISTORE_ALL_STOCK_TAB_FORMAT, state.warehouseStock.size]) },
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = {
                        BadgedBox(
                            badge = {
                                if (state.lowStockItems.isNotEmpty()) {
                                    Badge { Text(state.lowStockItems.size.toString()) }
                                }
                            },
                        ) {
                            Text(s[StringResource.MULTISTORE_LOW_STOCK])
                        }
                    },
                )
            }

            when (selectedTab) {
                0 -> StockList(
                    items = state.warehouseStock,
                    isLoading = state.isLoading,
                    onItemClick = { entry ->
                        onIntent(WarehouseIntent.OpenStockEntry(warehouseId, entry.productId))
                    },
                )
                1 -> StockList(
                    items = state.lowStockItems,
                    isLoading = state.isLoading,
                    isLowStockTab = true,
                    onItemClick = { entry ->
                        onIntent(WarehouseIntent.OpenStockEntry(warehouseId, entry.productId))
                    },
                )
            }
        }
    }
}

@Composable
private fun StockList(
    items: List<WarehouseStock>,
    isLoading: Boolean,
    isLowStockTab: Boolean = false,
    onItemClick: (WarehouseStock) -> Unit,
) {
    val s = LocalStrings.current
    when {
        isLoading && items.isEmpty() -> Box(
            Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) { CircularProgressIndicator() }

        items.isEmpty() -> ZyntaEmptyState(
            title = if (isLowStockTab) s[StringResource.MULTISTORE_NO_LOW_STOCK] else s[StringResource.MULTISTORE_NO_STOCK_YET],
            icon = if (isLowStockTab) Icons.Default.Warning else Icons.Default.Inventory2,
            modifier = Modifier.fillMaxSize(),
        )

        else -> LazyColumn(Modifier.fillMaxSize()) {
            items(items, key = { it.id }) { entry ->
                StockItemRow(entry = entry, onClick = { onItemClick(entry) })
                HorizontalDivider()
            }
        }
    }
}

@Composable
private fun StockItemRow(
    entry: WarehouseStock,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val s = LocalStrings.current
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = ZyntaSpacing.md, vertical = ZyntaSpacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Product name + SKU column
        Column(Modifier.weight(1f)) {
            Text(
                text = entry.productName ?: entry.productId,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
            )
            if (!entry.productSku.isNullOrBlank()) {
                Text(
                    text = s[StringResource.MULTISTORE_SKU_FORMAT, entry.productSku!!],
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        Spacer(Modifier.width(ZyntaSpacing.md))

        // Quantity column
        Column(horizontalAlignment = Alignment.End) {
            val qtyText = if (entry.quantity == entry.quantity.toLong().toDouble()) {
                entry.quantity.toLong().toString()
            } else {
                entry.quantity.toString()
            }
            Text(
                text = qtyText,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = if (entry.isLowStock) MaterialTheme.colorScheme.error
                        else MaterialTheme.colorScheme.onSurface,
            )
            if (entry.minQuantity > 0.0) {
                val minText = if (entry.minQuantity == entry.minQuantity.toLong().toDouble()) {
                    entry.minQuantity.toLong().toString()
                } else {
                    entry.minQuantity.toString()
                }
                Text(
                    text = s[StringResource.MULTISTORE_MIN_QTY_FORMAT, minText],
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        if (entry.isLowStock) {
            Spacer(Modifier.width(ZyntaSpacing.sm))
            Icon(
                imageVector = Icons.Default.Warning,
                contentDescription = s[StringResource.MULTISTORE_LOW_STOCK],
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(16.dp),
            )
        }
    }
}
