package com.zyntasolutions.zyntapos.feature.reports

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.Group
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.zyntasolutions.zyntapos.designsystem.components.ZyntaEmptyState
import com.zyntasolutions.zyntapos.designsystem.components.ZyntaLoadingSkeleton
import com.zyntasolutions.zyntapos.designsystem.layouts.ZyntaPageScaffold
import com.zyntasolutions.zyntapos.designsystem.tokens.ZyntaSpacing
import com.zyntasolutions.zyntapos.domain.model.Customer
import com.zyntasolutions.zyntapos.domain.usecase.reports.GenerateCustomerReportUseCase
import org.koin.compose.viewmodel.koinViewModel

/**
 * Customer report screen — snapshot of the full customer base.
 *
 * Shows:
 * - KPI cards: Total, Registered, Walk-In, Credit Enabled, Total Loyalty Points
 * - Top 10 customers by loyalty points
 * - Breakdown by customer group
 *
 * The report is a live snapshot (no date range filter) — it reflects the current
 * state of all active customer records. Reload is triggered automatically on first
 * composition; the user can re-trigger via the toolbar refresh icon.
 *
 * @param onNavigateUp Back navigation handler.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomerReportScreen(
    onNavigateUp: () -> Unit,
    viewModel: ReportsViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val s = state.customerReport

    LaunchedEffect(Unit) {
        if (s.report == null && !s.isLoading) {
            viewModel.dispatch(ReportsIntent.LoadCustomerReport)
        }
    }

    ZyntaPageScaffold(
        title = "Customer Report",
        onNavigateBack = onNavigateUp,
        actions = {
            IconButton(onClick = { viewModel.dispatch(ReportsIntent.ExportCustomerReportCsv) }) {
                Icon(Icons.Default.FileDownload, contentDescription = "Export CSV")
            }
        },
    ) { paddingValues ->
        Box(modifier = Modifier.fillMaxSize()) {
            when {
                s.report == null && !s.isLoading && s.error == null -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        ZyntaEmptyState(
                            icon = Icons.Default.Group,
                            title = "No Customer Data",
                            subtitle = "No customer records found.",
                            modifier = Modifier.fillMaxWidth().padding(ZyntaSpacing.xl),
                        )
                    }
                }

                s.error != null -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = s.error,
                            color = MaterialTheme.colorScheme.error,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth().padding(ZyntaSpacing.xl),
                        )
                    }
                }

                s.report != null -> {
                    CustomerReportContent(
                        report = s.report,
                        paddingValues = paddingValues,
                    )
                }
            }

            if (s.isLoading) ZyntaLoadingSkeleton(modifier = Modifier.fillMaxSize().padding(16.dp))
        }
    }
}

@Composable
private fun CustomerReportContent(
    report: GenerateCustomerReportUseCase.CustomerReport,
    paddingValues: PaddingValues,
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(paddingValues),
        contentPadding = PaddingValues(ZyntaSpacing.md),
        verticalArrangement = Arrangement.spacedBy(ZyntaSpacing.md),
    ) {
        item { CustomerKpiGrid(report = report) }

        item {
            Text(
                text = "Top Customers by Loyalty Points",
                style = MaterialTheme.typography.titleSmall,
            )
        }

        if (report.topByLoyaltyPoints.isEmpty()) {
            item {
                Text(
                    text = "No customers yet",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            items(items = report.topByLoyaltyPoints, key = { it.id }) { customer ->
                CustomerLoyaltyRow(customer = customer)
            }
        }

        if (report.byGroup.isNotEmpty()) {
            item {
                Spacer(Modifier.height(ZyntaSpacing.sm))
                Text(
                    text = "By Customer Group",
                    style = MaterialTheme.typography.titleSmall,
                )
                Spacer(Modifier.height(ZyntaSpacing.xs))
            }
            items(
                items = report.byGroup.entries.toList().sortedByDescending { it.value },
                key = { it.key ?: "__none__" },
            ) { (groupId, count) ->
                GroupCountRow(groupId = groupId, count = count)
            }
        }
    }
}

// ─── KPI grid ────────────────────────────────────────────────────────────────

@Composable
private fun CustomerKpiGrid(report: GenerateCustomerReportUseCase.CustomerReport) {
    Column(verticalArrangement = Arrangement.spacedBy(ZyntaSpacing.sm)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(ZyntaSpacing.sm),
        ) {
            CustomerKpiCard(
                label = "Total Customers",
                value = report.totalCustomers.toString(),
                isPrimary = true,
                modifier = Modifier.weight(1f),
            )
            CustomerKpiCard(
                label = "Registered",
                value = report.registeredCustomers.toString(),
                modifier = Modifier.weight(1f),
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(ZyntaSpacing.sm),
        ) {
            CustomerKpiCard(
                label = "Walk-In",
                value = report.walkInCustomers.toString(),
                modifier = Modifier.weight(1f),
            )
            CustomerKpiCard(
                label = "Credit Enabled",
                value = report.creditEnabledCustomers.toString(),
                modifier = Modifier.weight(1f),
            )
        }
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            ),
        ) {
            Column(
                modifier = Modifier.padding(ZyntaSpacing.md).fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    "Total Loyalty Points",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(ZyntaSpacing.xs))
                Text(
                    text = report.totalLoyaltyPoints.toString(),
                    style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

@Composable
private fun CustomerKpiCard(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    isPrimary: Boolean = false,
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
    ) {
        Column(
            modifier = Modifier.padding(ZyntaSpacing.md),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(ZyntaSpacing.xs))
            Text(
                text = value,
                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                color = if (isPrimary) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
            )
        }
    }
}

// ─── Top loyalty row ──────────────────────────────────────────────────────────

@Composable
private fun CustomerLoyaltyRow(customer: Customer) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = ZyntaSpacing.xs),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(customer.name, style = MaterialTheme.typography.bodyMedium)
            Text(
                customer.phone,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            text = "${customer.loyaltyPoints} pts",
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
            color = MaterialTheme.colorScheme.primary,
        )
    }
    HorizontalDivider()
}

// ─── Group count row ─────────────────────────────────────────────────────────

@Composable
private fun GroupCountRow(groupId: String?, count: Int) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = ZyntaSpacing.xs),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = groupId ?: "No Group",
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = count.toString(),
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
        )
    }
    HorizontalDivider()
}
