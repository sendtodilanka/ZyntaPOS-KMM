package com.zyntasolutions.zyntapos.feature.reports

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.Store
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.zyntasolutions.zyntapos.core.utils.CurrencyFormatter
import com.zyntasolutions.zyntapos.domain.model.report.StoreSalesData
import org.koin.compose.koinInject

/**
 * Store Comparison Report screen (C5.2).
 *
 * Displays a ranked list of stores by revenue with comparison metrics:
 * revenue, order count, AOV, and revenue share percentage.
 *
 * Data sourced from [GenerateMultiStoreComparisonReportUseCase] which queries
 * the local `orders` table grouped by `store_id`.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StoreComparisonReportScreen(
    state: ReportsState.StoreComparisonState,
    onIntent: (ReportsIntent) -> Unit,
    onNavigateUp: () -> Unit,
    currencyFormatter: CurrencyFormatter = koinInject(),
) {
    LaunchedEffect(Unit) {
        onIntent(ReportsIntent.LoadStoreComparison)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Store Comparison") },
                navigationIcon = {
                    IconButton(onClick = onNavigateUp) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(
                        onClick = { onIntent(ReportsIntent.ExportStoreComparisonCsv) },
                        enabled = !state.isExporting && state.stores.isNotEmpty(),
                    ) {
                        Icon(Icons.Default.FileDownload, contentDescription = "Export CSV")
                    }
                },
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
        ) {
            // ── Period filter chips ──────────────────────────────────────────
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                DateRange.entries.filter { it != DateRange.CUSTOM }.forEach { range ->
                    FilterChip(
                        selected = state.selectedRange == range,
                        onClick = { onIntent(ReportsIntent.SelectStoreComparisonRange(range)) },
                        label = {
                            Text(
                                when (range) {
                                    DateRange.TODAY -> "Today"
                                    DateRange.THIS_WEEK -> "This Week"
                                    DateRange.THIS_MONTH -> "This Month"
                                    DateRange.CUSTOM -> "Custom"
                                },
                            )
                        },
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

            // ── Summary KPIs ────────────────────────────────────────────────
            if (!state.isLoading && state.stores.isNotEmpty()) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    SummaryCard(
                        label = "Total Revenue",
                        value = currencyFormatter.format(state.totalRevenue),
                        modifier = Modifier.weight(1f),
                    )
                    SummaryCard(
                        label = "Total Orders",
                        value = state.totalOrders.toString(),
                        modifier = Modifier.weight(1f),
                    )
                    SummaryCard(
                        label = "Stores",
                        value = state.stores.size.toString(),
                        modifier = Modifier.weight(1f),
                    )
                }
                Spacer(Modifier.height(16.dp))
            }

            // ── Content ─────────────────────────────────────────────────────
            when {
                state.isLoading -> {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.CenterHorizontally))
                }

                state.error != null -> {
                    Text(
                        text = state.error,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }

                state.stores.isEmpty() -> {
                    Text(
                        text = "No store data available for the selected period.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                else -> {
                    Text(
                        text = "Ranked by Revenue",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Spacer(Modifier.height(8.dp))

                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        itemsIndexed(state.stores) { index, store ->
                            StoreComparisonCard(
                                rank = index + 1,
                                store = store,
                                maxRevenue = state.totalRevenue,
                                currencyFormatter = currencyFormatter,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SummaryCard(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
        ),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f),
            )
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        }
    }
}

@Composable
private fun StoreComparisonCard(
    rank: Int,
    store: StoreSalesData,
    maxRevenue: Double,
    currencyFormatter: CurrencyFormatter,
) {
    val revenueShare = if (maxRevenue > 0) (store.totalRevenue / maxRevenue).toFloat() else 0f

    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Rank badge
            Text(
                text = "#$rank",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(end = 12.dp),
            )

            Column(modifier = Modifier.weight(1f)) {
                // Store name
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.Store,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(end = 4.dp),
                    )
                    Text(
                        text = store.storeName,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                }

                Spacer(Modifier.height(4.dp))

                // Revenue share bar
                LinearProgressIndicator(
                    progress = { revenueShare },
                    modifier = Modifier.fillMaxWidth().height(6.dp),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant,
                )

                Spacer(Modifier.height(6.dp))

                // Metrics row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    MetricLabel("Revenue", currencyFormatter.format(store.totalRevenue))
                    MetricLabel("Orders", store.orderCount.toString())
                    MetricLabel("AOV", currencyFormatter.format(store.averageOrderValue))
                    MetricLabel("Share", "${(revenueShare * 100).toInt()}%")
                }
            }
        }
    }
}

@Composable
private fun MetricLabel(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Medium,
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

