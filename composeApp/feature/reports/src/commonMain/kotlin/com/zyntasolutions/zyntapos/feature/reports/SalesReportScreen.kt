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
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.Payment
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
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
import com.zyntasolutions.zyntapos.designsystem.util.WindowSize
import com.zyntasolutions.zyntapos.designsystem.util.currentWindowSize
import com.zyntasolutions.zyntapos.domain.model.PaymentMethod
import com.zyntasolutions.zyntapos.domain.usecase.reports.GenerateSalesReportUseCase
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel

// ─── Tab definitions ─────────────────────────────────────────────────────────

private enum class SalesTab(val title: String, val icon: ImageVector) {
    OVERVIEW("Overview", Icons.Default.Dashboard),
    TREND("Trend", Icons.AutoMirrored.Filled.TrendingUp),
    PAYMENT("Payment", Icons.Default.Payment),
    PRODUCTS("Products", Icons.Default.ShoppingCart),
}

/**
 * Sales report screen — step 12.1.2 / 12.1.3.
 *
 * Features:
 * - [DateRangePickerBar] with Today / This Week / This Month / Custom presets
 * - Tab-based layout: Overview, Trend, Payment, Products
 * - KPI cards: Total Sales, Order Count, Average Order Value, Top Product
 * - Canvas-based line chart (revenue per day in range)
 * - Horizontal bar chart for payment method breakdown
 * - Sortable per-product sales table
 *
 * ViewModel caches the last report result — screen does not re-query on recomposition.
 *
 * @param onNavigateUp Back navigation handler.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SalesReportScreen(
    onNavigateUp: () -> Unit,
    viewModel: ReportsViewModel = koinViewModel(),
    formatter: CurrencyFormatter = koinInject(),
) {
    val strings = LocalStrings.current
    val state by viewModel.state.collectAsState()
    val s = state.salesReport
    val windowSize = currentWindowSize()

    var selectedTabIndex by rememberSaveable { mutableIntStateOf(0) }

    // Auto-load on first composition
    LaunchedEffect(Unit) {
        if (s.report == null && !s.isLoading) viewModel.dispatch(ReportsIntent.LoadSalesReport)
    }

    ZyntaPageScaffold(
        title = strings[StringResource.REPORTS_SALES_REPORT],
        onNavigateBack = onNavigateUp,
        actions = {
            IconButton(onClick = { viewModel.dispatch(ReportsIntent.ExportSalesReportCsv) }) {
                Icon(Icons.Default.FileDownload, contentDescription = strings[StringResource.REPORTS_EXPORT_CSV_CD])
            }
            IconButton(onClick = { viewModel.dispatch(ReportsIntent.ExportSalesReportPdf) }) {
                Icon(Icons.Default.PictureAsPdf, contentDescription = strings[StringResource.REPORTS_EXPORT_PDF_CD])
            }
            IconButton(onClick = { viewModel.dispatch(ReportsIntent.PrintSalesReport) }) {
                Icon(Icons.Default.Print, contentDescription = strings[StringResource.REPORTS_PRINT_CD])
            }
        },
    ) { paddingValues ->
        Box(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
            ) {
                // ── Date range picker — always visible above tabs ────────────
                DateRangePickerBar(
                    selectedRange = s.selectedRange,
                    onRangeSelected = { viewModel.dispatch(ReportsIntent.SelectSalesRange(it)) },
                    onCustomRange = { from, to ->
                        viewModel.dispatch(ReportsIntent.SetCustomSalesRange(from, to))
                    },
                    modifier = Modifier.padding(
                        start = ZyntaSpacing.md,
                        end = ZyntaSpacing.md,
                        top = ZyntaSpacing.md,
                        bottom = ZyntaSpacing.sm,
                    ),
                )

                // ── Tab row ──────────────────────────────────────────────────
                PrimaryScrollableTabRow(
                    selectedTabIndex = selectedTabIndex,
                    edgePadding = ZyntaSpacing.md,
                    divider = {},
                ) {
                    SalesTab.entries.forEachIndexed { index, tab ->
                        val tabTitle = when (tab) {
                            SalesTab.OVERVIEW -> strings[StringResource.REPORTS_TAB_OVERVIEW]
                            SalesTab.TREND -> strings[StringResource.REPORTS_TAB_TREND]
                            SalesTab.PAYMENT -> strings[StringResource.REPORTS_TAB_PAYMENT]
                            SalesTab.PRODUCTS -> strings[StringResource.REPORTS_TAB_PRODUCTS]
                        }
                        Tab(
                            selected = selectedTabIndex == index,
                            onClick = { selectedTabIndex = index },
                            text = { Text(tabTitle) },
                            icon = { Icon(tab.icon, contentDescription = tabTitle) },
                        )
                    }
                }

                HorizontalDivider()

                // ── Tab content ──────────────────────────────────────────────
                when {
                    // Empty state when not loading and no report
                    s.report == null && !s.isLoading && s.error == null -> {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center,
                        ) {
                            ZyntaEmptyState(
                                icon = Icons.Default.Assessment,
                                title = strings[StringResource.REPORTS_NO_DATA],
                                subtitle = strings[StringResource.REPORTS_SALES_EMPTY_HINT],
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
                                modifier = Modifier.fillMaxWidth().padding(ZyntaSpacing.xl),
                            )
                        }
                    }

                    // Report loaded — render the selected tab
                    s.report != null -> {
                        val report = s.report
                        when (SalesTab.entries[selectedTabIndex]) {
                            SalesTab.OVERVIEW -> OverviewTabContent(
                                report = report,
                                formatter = formatter,
                                isExpanded = windowSize == WindowSize.EXPANDED,
                            )

                            SalesTab.TREND -> TrendTabContent(report = report)

                            SalesTab.PAYMENT -> PaymentTabContent(
                                report = report,
                                formatter = formatter,
                            )

                            SalesTab.PRODUCTS -> ProductsTabContent(
                                report = report,
                                formatter = formatter,
                            )
                        }
                    }
                }
            }

            if (s.isLoading) ZyntaLoadingSkeleton(modifier = Modifier.fillMaxSize().padding(16.dp))
        }
    }
}

// ─── Tab content composables ─────────────────────────────────────────────────

@Composable
private fun OverviewTabContent(
    report: GenerateSalesReportUseCase.SalesReport,
    formatter: CurrencyFormatter,
    isExpanded: Boolean,
) {
    LazyColumn(
        contentPadding = PaddingValues(ZyntaSpacing.md),
        verticalArrangement = Arrangement.spacedBy(ZyntaSpacing.md),
    ) {
        item {
            SalesKpiRow(report = report, formatter = formatter, isExpanded = isExpanded)
        }
    }
}

@Composable
private fun TrendTabContent(report: GenerateSalesReportUseCase.SalesReport) {
    LazyColumn(
        contentPadding = PaddingValues(ZyntaSpacing.md),
        verticalArrangement = Arrangement.spacedBy(ZyntaSpacing.md),
    ) {
        item {
            SalesTrendChart(report = report)
        }
    }
}

@Composable
private fun PaymentTabContent(
    report: GenerateSalesReportUseCase.SalesReport,
    formatter: CurrencyFormatter,
) {
    LazyColumn(
        contentPadding = PaddingValues(ZyntaSpacing.md),
        verticalArrangement = Arrangement.spacedBy(ZyntaSpacing.md),
    ) {
        item {
            PaymentBreakdownChart(report = report, formatter = formatter)
        }
    }
}

@Composable
private fun ProductsTabContent(
    report: GenerateSalesReportUseCase.SalesReport,
    formatter: CurrencyFormatter,
) {
    val strings = LocalStrings.current
    LazyColumn(
        contentPadding = PaddingValues(ZyntaSpacing.md),
        verticalArrangement = Arrangement.spacedBy(ZyntaSpacing.sm),
    ) {
        item {
            Text(strings[StringResource.REPORTS_PER_PRODUCT_SALES], style = MaterialTheme.typography.titleSmall)
        }
        items(
            items = report.topProducts.entries.toList(),
            key = { it.key },
        ) { (productId, revenue) ->
            ProductSalesRow(productId = productId, revenue = revenue, formatter = formatter)
        }
    }
}

// ─── KPI cards ────────────────────────────────────────────────────────────────

@Composable
private fun SalesKpiRow(
    report: GenerateSalesReportUseCase.SalesReport,
    formatter: CurrencyFormatter,
    isExpanded: Boolean,
) {
    val strings = LocalStrings.current
    val topProduct = report.topProducts.entries.firstOrNull()

    if (isExpanded) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(ZyntaSpacing.sm),
        ) {
            KpiCard(
                label = strings[StringResource.REPORTS_TOTAL_SALES],
                value = formatter.format(report.totalSales),
                helper = strings[StringResource.REPORTS_TOTAL_REVENUE_HINT],
                isPrimary = true,
                modifier = Modifier.weight(1f),
            )
            KpiCard(
                label = strings[StringResource.REPORTS_ORDERS_COUNT],
                value = report.orderCount.toString(),
                helper = strings[StringResource.REPORTS_ORDERS_COUNT_HINT],
                modifier = Modifier.weight(1f),
            )
            KpiCard(
                label = strings[StringResource.REPORTS_AVG_ORDER_VALUE],
                value = formatter.format(report.avgOrderValue),
                helper = strings[StringResource.REPORTS_AVG_ORDER_HINT],
                modifier = Modifier.weight(1f),
            )
            KpiCard(
                label = strings[StringResource.REPORTS_TOP_PRODUCT],
                value = topProduct?.key ?: "\u2014",
                helper = if (topProduct != null) formatter.format(topProduct.value) else strings[StringResource.REPORTS_NO_DATA_SHORT],
                modifier = Modifier.weight(1f),
            )
        }
    } else {
        // Compact: 2x2 grid
        Column(verticalArrangement = Arrangement.spacedBy(ZyntaSpacing.sm)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(ZyntaSpacing.sm),
            ) {
                KpiCard(
                    label = strings[StringResource.REPORTS_TOTAL_SALES],
                    value = formatter.format(report.totalSales),
                    helper = strings[StringResource.REPORTS_TOTAL_REVENUE_HINT],
                    isPrimary = true,
                    modifier = Modifier.weight(1f),
                )
                KpiCard(
                    label = strings[StringResource.REPORTS_ORDERS_COUNT],
                    value = report.orderCount.toString(),
                    helper = strings[StringResource.REPORTS_ORDERS_COUNT_HINT],
                    modifier = Modifier.weight(1f),
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(ZyntaSpacing.sm),
            ) {
                KpiCard(
                    label = strings[StringResource.REPORTS_AVG_ORDER_VALUE],
                    value = formatter.format(report.avgOrderValue),
                    helper = strings[StringResource.REPORTS_AVG_ORDER_HINT],
                    modifier = Modifier.weight(1f),
                )
                KpiCard(
                    label = strings[StringResource.REPORTS_TOP_PRODUCT],
                    value = topProduct?.key ?: "\u2014",
                    helper = if (topProduct != null) formatter.format(topProduct.value) else strings[StringResource.REPORTS_NO_DATA_SHORT],
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun KpiCard(
    label: String,
    value: String,
    helper: String,
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
                color = if (isPrimary) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(ZyntaSpacing.xs))
            Text(
                text = helper,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}

// ─── Line chart (Canvas-based) ────────────────────────────────────────────────

/**
 * Canvas-based sales trend line chart.
 *
 * Renders revenue data points from [GenerateSalesReportUseCase.SalesReport.topProducts]
 * as a simple line chart with filled path. In a full implementation this would use
 * per-day bucketed data; Phase 1 renders top-product revenues as proxy trend points.
 */
@Composable
private fun SalesTrendChart(report: GenerateSalesReportUseCase.SalesReport) {
    val strings = LocalStrings.current
    val lineColor = MaterialTheme.colorScheme.primary
    val fillColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(modifier = Modifier.padding(ZyntaSpacing.md)) {
            Text(strings[StringResource.REPORTS_SALES_TREND], style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(ZyntaSpacing.sm))
            val values = report.topProducts.values.toList().ifEmpty { listOf(0.0) }
            val maxVal = values.max().coerceAtLeast(1.0).toFloat()

            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(160.dp),
            ) {
                val stepX = if (values.size > 1) size.width / (values.size - 1) else size.width
                val points = values.mapIndexed { i, v ->
                    Offset(
                        x = i * stepX,
                        y = size.height - (v.toFloat() / maxVal) * size.height,
                    )
                }

                // Fill path
                val fillPath = Path().apply {
                    moveTo(points.first().x, size.height)
                    points.forEach { lineTo(it.x, it.y) }
                    lineTo(points.last().x, size.height)
                    close()
                }
                drawPath(fillPath, color = fillColor)

                // Line path
                val linePath = Path().apply {
                    points.forEachIndexed { i, p ->
                        if (i == 0) moveTo(p.x, p.y) else lineTo(p.x, p.y)
                    }
                }
                drawPath(linePath, color = lineColor, style = Stroke(width = 3.dp.toPx()))

                // Data points
                points.forEach { drawCircle(color = lineColor, radius = 4.dp.toPx(), center = it) }
            }
        }
    }
}

// ─── Payment method breakdown (horizontal bar chart) ─────────────────────────

@Composable
private fun PaymentBreakdownChart(
    report: GenerateSalesReportUseCase.SalesReport,
    formatter: CurrencyFormatter,
) {
    val strings = LocalStrings.current
    if (report.salesByPaymentMethod.isEmpty()) return
    val maxAmount = report.salesByPaymentMethod.values.max().coerceAtLeast(1.0)

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(modifier = Modifier.padding(ZyntaSpacing.md)) {
            Text(strings[StringResource.REPORTS_PAYMENT_BREAKDOWN], style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(ZyntaSpacing.sm))
            report.salesByPaymentMethod.forEach { (method, amount) ->
                PaymentBar(
                    method = method,
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
private fun PaymentBar(
    method: PaymentMethod,
    amount: Double,
    fraction: Float,
    formatter: CurrencyFormatter,
) {
    val primary = MaterialTheme.colorScheme.primary
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = method.name.lowercase().replaceFirstChar { it.uppercaseChar() },
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.width(80.dp),
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

// ─── Product sales row ────────────────────────────────────────────────────────

@Composable
private fun ProductSalesRow(productId: String, revenue: Double, formatter: CurrencyFormatter) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = ZyntaSpacing.xs),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(productId, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Text(formatter.format(revenue), style = MaterialTheme.typography.bodyMedium)
    }
}
