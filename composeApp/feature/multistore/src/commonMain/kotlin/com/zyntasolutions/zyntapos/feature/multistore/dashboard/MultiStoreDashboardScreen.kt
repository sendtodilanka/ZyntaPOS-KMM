package com.zyntasolutions.zyntapos.feature.multistore.dashboard

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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.AttachMoney
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.Store
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.zyntasolutions.zyntapos.designsystem.components.StoreItem
import com.zyntasolutions.zyntapos.designsystem.components.ZyntaEmptyState
import com.zyntasolutions.zyntapos.designsystem.components.ZyntaStoreSelectorCompact
import com.zyntasolutions.zyntapos.designsystem.layouts.ZyntaPageScaffold
import com.zyntasolutions.zyntapos.domain.model.report.StoreSalesData

/**
 * Multi-store global dashboard screen (C3.3).
 *
 * Displays aggregated KPIs across all accessible stores with per-store
 * comparison cards and a store selector in the top bar for context switching.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MultiStoreDashboardScreen(
    viewModel: MultiStoreDashboardViewModel,
    onNavigateBack: () -> Unit,
) {
    val state by viewModel.state.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is MultiStoreDashboardEffect.ShowError ->
                    snackbarHostState.showSnackbar(effect.message)
                is MultiStoreDashboardEffect.NavigateToStoreDashboard -> { /* handled by parent */ }
                is MultiStoreDashboardEffect.StoreSwitched ->
                    snackbarHostState.showSnackbar("Switched to ${effect.storeName}")
            }
        }
    }

    ZyntaPageScaffold(
        title = "Multi-Store Dashboard",
        onNavigateBack = onNavigateBack,
        snackbarHostState = snackbarHostState,
        actions = {
            if (state.stores.size > 1) {
                ZyntaStoreSelectorCompact(
                    currentStore = state.activeStore?.let {
                        StoreItem(id = it.id, name = it.name, address = it.address)
                    },
                    availableStores = state.stores.map {
                        StoreItem(id = it.id, name = it.name, address = it.address)
                    },
                    onStoreSelected = { item ->
                        state.stores.find { it.id == item.id }?.let { store ->
                            viewModel.dispatch(MultiStoreDashboardIntent.SwitchStore(store))
                        }
                    },
                )
            }
        },
    ) { padding ->
        MultiStoreDashboardContent(
            state = state,
            onPeriodSelected = { viewModel.dispatch(MultiStoreDashboardIntent.SelectPeriod(it)) },
            modifier = Modifier.padding(padding),
        )
    }
}

@Composable
private fun MultiStoreDashboardContent(
    state: MultiStoreDashboardState,
    onPeriodSelected: (DashboardPeriod) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (state.isLoading && state.storeComparison.isEmpty()) {
        Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // Period filter chips
        item {
            PeriodFilterRow(
                selectedPeriod = state.selectedPeriod,
                onPeriodSelected = onPeriodSelected,
            )
        }

        // Aggregate KPI cards
        item {
            AggregateKPIRow(
                totalRevenue = state.totalRevenue,
                totalOrders = state.totalOrders,
                averageOrderValue = state.overallAOV,
                storeCount = state.stores.size,
            )
        }

        // Loading indicator for refresh
        if (state.isLoading) {
            item {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
        }

        // Per-store comparison section
        if (state.storeComparison.isEmpty() && !state.isLoading) {
            item {
                ZyntaEmptyState(
                    icon = Icons.Default.Store,
                    title = "No store data available",
                    subtitle = "Store comparison data will appear here once orders are synced.",
                )
            }
        } else {
            item {
                Text(
                    text = "Store Comparison",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
            }
            items(state.storeComparison) { storeData ->
                StoreComparisonCard(
                    data = storeData,
                    totalRevenue = state.totalRevenue,
                )
            }
        }

        // Error display
        state.error?.let { error ->
            item {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                    ),
                ) {
                    Text(
                        text = error,
                        modifier = Modifier.padding(16.dp),
                        color = MaterialTheme.colorScheme.onErrorContainer,
                    )
                }
            }
        }
    }
}

@Composable
private fun PeriodFilterRow(
    selectedPeriod: DashboardPeriod,
    onPeriodSelected: (DashboardPeriod) -> Unit,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        DashboardPeriod.entries.forEach { period ->
            FilterChip(
                selected = period == selectedPeriod,
                onClick = { onPeriodSelected(period) },
                label = { Text(period.label) },
            )
        }
    }
}

@Composable
private fun AggregateKPIRow(
    totalRevenue: Double,
    totalOrders: Int,
    averageOrderValue: Double,
    storeCount: Int,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        KPICard(
            title = "Total Revenue",
            value = formatCurrency(totalRevenue),
            icon = Icons.Default.AttachMoney,
            modifier = Modifier.weight(1f),
        )
        KPICard(
            title = "Total Orders",
            value = totalOrders.toString(),
            icon = Icons.Default.ShoppingCart,
            modifier = Modifier.weight(1f),
        )
        KPICard(
            title = "Avg Order Value",
            value = formatCurrency(averageOrderValue),
            icon = Icons.Default.Receipt,
            modifier = Modifier.weight(1f),
        )
        KPICard(
            title = "Active Stores",
            value = storeCount.toString(),
            icon = Icons.Default.Store,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun KPICard(
    title: String,
    value: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
        ),
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = title,
                modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = title,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f),
            )
        }
    }
}

@Composable
private fun StoreComparisonCard(
    data: StoreSalesData,
    totalRevenue: Double,
) {
    val revenueShare = if (totalRevenue > 0) (data.totalRevenue / totalRevenue).toFloat() else 0f

    Card(
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Store,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = data.storeName,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                    )
                }
                Text(
                    text = formatCurrency(data.totalRevenue),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Revenue share progress bar
            LinearProgressIndicator(
                progress = { revenueShare },
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surfaceVariant,
            )

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = "${data.orderCount} orders",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = "AOV: ${formatCurrency(data.averageOrderValue)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = "${(revenueShare * 100).toInt()}% share",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

private fun formatCurrency(amount: Double): String {
    val formatted = String.format("%.2f", amount)
    return "LKR $formatted"
}
