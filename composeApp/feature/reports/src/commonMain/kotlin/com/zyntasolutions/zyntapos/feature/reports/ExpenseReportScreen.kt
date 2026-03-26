package com.zyntasolutions.zyntapos.feature.reports

import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.Receipt
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
import com.zyntasolutions.zyntapos.core.i18n.StringResource
import com.zyntasolutions.zyntapos.core.utils.CurrencyFormatter
import com.zyntasolutions.zyntapos.designsystem.components.LocalStrings
import com.zyntasolutions.zyntapos.designsystem.components.ZyntaEmptyState
import com.zyntasolutions.zyntapos.designsystem.components.ZyntaLoadingSkeleton
import com.zyntasolutions.zyntapos.designsystem.layouts.ZyntaPageScaffold
import com.zyntasolutions.zyntapos.designsystem.tokens.ZyntaSpacing
import com.zyntasolutions.zyntapos.domain.usecase.reports.GenerateExpenseReportUseCase
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel

/**
 * Expense report screen — period-based expense summary.
 *
 * Features:
 * - [DateRangePickerBar] matching the sales report (Today / This Week / This Month / Custom)
 * - KPI cards: Total Approved, Total Pending, Total Rejected
 * - Horizontal bar chart of approved spend per category
 * - Category breakdown table
 *
 * @param onNavigateUp Back navigation handler.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExpenseReportScreen(
    onNavigateUp: () -> Unit,
    viewModel: ReportsViewModel = koinViewModel(),
    formatter: CurrencyFormatter = koinInject(),
) {
    val strings = LocalStrings.current
    val state by viewModel.state.collectAsState()
    val s = state.expenseReport

    LaunchedEffect(Unit) {
        if (s.report == null && !s.isLoading) {
            viewModel.dispatch(ReportsIntent.LoadExpenseReport)
        }
    }

    ZyntaPageScaffold(
        title = strings[StringResource.REPORTS_EXPENSE_REPORT],
        onNavigateBack = onNavigateUp,
        actions = {
            IconButton(onClick = { viewModel.dispatch(ReportsIntent.ExportExpenseReportCsv) }) {
                Icon(Icons.Default.FileDownload, contentDescription = strings[StringResource.REPORTS_EXPORT_CSV_CD])
            }
        },
    ) { paddingValues ->
        Box(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
            ) {
                // ── Date range picker ────────────────────────────────────────
                DateRangePickerBar(
                    selectedRange = s.selectedRange,
                    onRangeSelected = { viewModel.dispatch(ReportsIntent.SelectExpenseRange(it)) },
                    onCustomRange = { from, to ->
                        viewModel.dispatch(ReportsIntent.SetCustomExpenseRange(from, to))
                    },
                    modifier = Modifier.padding(
                        start = ZyntaSpacing.md,
                        end = ZyntaSpacing.md,
                        top = ZyntaSpacing.md,
                        bottom = ZyntaSpacing.sm,
                    ),
                )

                HorizontalDivider()

                // ── Content area ─────────────────────────────────────────────
                when {
                    s.report == null && !s.isLoading && s.error == null -> {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center,
                        ) {
                            ZyntaEmptyState(
                                icon = Icons.Default.Receipt,
                                title = strings[StringResource.REPORTS_NO_EXPENSE_DATA],
                                subtitle = strings[StringResource.REPORTS_EXPENSE_EMPTY_HINT],
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
                        ExpenseReportContent(
                            report = s.report,
                            formatter = formatter,
                        )
                    }
                }
            }

            if (s.isLoading) ZyntaLoadingSkeleton(modifier = Modifier.fillMaxSize().padding(16.dp))
        }
    }
}

@Composable
private fun ExpenseReportContent(
    report: GenerateExpenseReportUseCase.ExpenseReport,
    formatter: CurrencyFormatter,
) {
    LazyColumn(
        contentPadding = PaddingValues(ZyntaSpacing.md),
        verticalArrangement = Arrangement.spacedBy(ZyntaSpacing.md),
    ) {
        item { ExpenseKpiRow(report = report, formatter = formatter) }

        if (report.byCategory.isNotEmpty()) {
            item {
                ExpenseCategoryChart(report = report, formatter = formatter)
            }
            item {
                val s = LocalStrings.current
                Text(
                    s[StringResource.REPORTS_CATEGORY_BREAKDOWN],
                    style = MaterialTheme.typography.titleSmall,
                )
            }
            items(
                items = report.byCategory.entries
                    .sortedByDescending { it.value }
                    .toList(),
                key = { it.key ?: "__none__" },
            ) { (categoryId, amount) ->
                ExpenseCategoryRow(categoryId = categoryId, amount = amount, formatter = formatter)
            }
        }
    }
}

// ─── KPI row ─────────────────────────────────────────────────────────────────

@Composable
private fun ExpenseKpiRow(
    report: GenerateExpenseReportUseCase.ExpenseReport,
    formatter: CurrencyFormatter,
) {
    val s = LocalStrings.current
    Column(verticalArrangement = Arrangement.spacedBy(ZyntaSpacing.sm)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(ZyntaSpacing.sm),
        ) {
            ExpenseKpiCard(
                label = s[StringResource.REPORTS_APPROVED],
                value = formatter.format(report.totalApproved),
                count = report.approvedCount,
                isPrimary = true,
                modifier = Modifier.weight(1f),
            )
            ExpenseKpiCard(
                label = s[StringResource.REPORTS_PENDING],
                value = formatter.format(report.totalPending),
                count = report.pendingCount,
                modifier = Modifier.weight(1f),
            )
        }
        ExpenseKpiCard(
            label = s[StringResource.REPORTS_REJECTED],
            value = formatter.format(report.totalRejected),
            count = report.rejectedCount,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun ExpenseKpiCard(
    label: String,
    value: String,
    count: Int,
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
            val s = LocalStrings.current
            Text(
                text = s[StringResource.REPORTS_RECORDS_COUNT, count],
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

// ─── Category horizontal bar chart ───────────────────────────────────────────

@Composable
private fun ExpenseCategoryChart(
    report: GenerateExpenseReportUseCase.ExpenseReport,
    formatter: CurrencyFormatter,
) {
    if (report.byCategory.isEmpty()) return
    val maxAmount = report.byCategory.values.max().coerceAtLeast(1.0)

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        val s = LocalStrings.current
        Column(modifier = Modifier.padding(ZyntaSpacing.md)) {
            Text(s[StringResource.REPORTS_SPEND_BY_CATEGORY], style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(ZyntaSpacing.sm))
            report.byCategory.entries
                .sortedByDescending { it.value }
                .take(8)
                .forEach { (categoryId, amount) ->
                    ExpenseCategoryBar(
                        label = categoryId ?: s[StringResource.REPORTS_UNCATEGORISED],
                        amount = amount,
                        fraction = (amount / maxAmount).toFloat(),
                        formatter = formatter,
                    )
                    Spacer(Modifier.height(ZyntaSpacing.xs))
                }
        }
    }
}

@Composable
private fun ExpenseCategoryBar(
    label: String,
    amount: Double,
    fraction: Float,
    formatter: CurrencyFormatter,
) {
    val primary = MaterialTheme.colorScheme.tertiary
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = label.take(14),
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.width(100.dp),
        )
        Canvas(
            modifier = Modifier
                .weight(1f)
                .height(ZyntaSpacing.md),
        ) {
            drawRect(color = primary, size = size.copy(width = size.width * fraction))
        }
        Spacer(Modifier.width(ZyntaSpacing.sm))
        Text(
            text = formatter.format(amount),
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

// ─── Category table row ───────────────────────────────────────────────────────

@Composable
private fun ExpenseCategoryRow(
    categoryId: String?,
    amount: Double,
    formatter: CurrencyFormatter,
) {
    val s = LocalStrings.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = ZyntaSpacing.xs),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = categoryId ?: s[StringResource.REPORTS_UNCATEGORISED],
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = formatter.format(amount),
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
        )
    }
    HorizontalDivider()
}
