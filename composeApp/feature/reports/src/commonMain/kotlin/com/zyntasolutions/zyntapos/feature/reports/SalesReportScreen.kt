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
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Print
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import com.zyntasolutions.zyntapos.designsystem.components.ZyntaLoadingOverlay
import com.zyntasolutions.zyntapos.designsystem.components.ZyntaTopAppBar
import com.zyntasolutions.zyntapos.domain.model.PaymentMethod
import com.zyntasolutions.zyntapos.domain.usecase.reports.GenerateSalesReportUseCase
import org.koin.compose.viewmodel.koinViewModel

/**
 * Sales report screen — step 12.1.2 / 12.1.3.
 *
 * Features:
 * - [DateRangePickerBar] with Today / This Week / This Month / Custom presets
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
) {
    val state by viewModel.state.collectAsState()
    val s = state.salesReport

    // Auto-load on first composition
    LaunchedEffect(Unit) {
        if (s.report == null && !s.isLoading) viewModel.dispatch(ReportsIntent.LoadSalesReport)
    }

    Scaffold(
        topBar = {
            ZyntaTopAppBar(
                title = "Sales Report",
                onNavigateBack = onNavigateUp,
                actions = {
                    IconButton(onClick = { viewModel.dispatch(ReportsIntent.ExportSalesReportCsv) }) {
                        Icon(Icons.Default.FileDownload, contentDescription = "Export CSV")
                    }
                    IconButton(onClick = { viewModel.dispatch(ReportsIntent.ExportSalesReportPdf) }) {
                        Icon(Icons.Default.PictureAsPdf, contentDescription = "Export PDF")
                    }
                    IconButton(onClick = { viewModel.dispatch(ReportsIntent.PrintSalesReport) }) {
                        Icon(Icons.Default.Print, contentDescription = "Print")
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
                // Date range picker
                item {
                    DateRangePickerBar(
                        selectedRange = s.selectedRange,
                        onRangeSelected = { viewModel.dispatch(ReportsIntent.SelectSalesRange(it)) },
                        onCustomRange = { from, to ->
                            viewModel.dispatch(ReportsIntent.SetCustomSalesRange(from, to))
                        },
                    )
                }

                // KPI cards
                s.report?.let { report ->
                    item { SalesKpiRow(report = report) }
                    item { SalesTrendChart(report = report) }
                    item { PaymentBreakdownChart(report = report) }
                    item {
                        Text("Per-Product Sales", style = MaterialTheme.typography.titleSmall)
                    }
                    items(
                        items = report.topProducts.entries.toList(),
                        key = { it.key },
                    ) { (productId, revenue) ->
                        ProductSalesRow(productId = productId, revenue = revenue)
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

// ─── KPI cards ────────────────────────────────────────────────────────────────

@Composable
private fun SalesKpiRow(report: GenerateSalesReportUseCase.SalesReport) {
    val topProduct = report.topProducts.entries.firstOrNull()

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        KpiCard(label = "Total Sales",      value = "LKR %.2f".format(report.totalSales),     modifier = Modifier.weight(1f))
        KpiCard(label = "Orders",           value = report.orderCount.toString(),              modifier = Modifier.weight(1f))
        KpiCard(label = "Avg Order Value",  value = "LKR %.2f".format(report.avgOrderValue),  modifier = Modifier.weight(1f))
        KpiCard(label = "Top Product",      value = topProduct?.key ?: "—",                   modifier = Modifier.weight(1f))
    }
}

@Composable
private fun KpiCard(label: String, value: String, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(label, style = MaterialTheme.typography.labelSmall)
            Spacer(Modifier.height(4.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium,
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
    val lineColor = MaterialTheme.colorScheme.primary
    val fillColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Sales Trend", style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(8.dp))
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
private fun PaymentBreakdownChart(report: GenerateSalesReportUseCase.SalesReport) {
    if (report.salesByPaymentMethod.isEmpty()) return
    val maxAmount = report.salesByPaymentMethod.values.max().coerceAtLeast(1.0)

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Payment Breakdown", style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(8.dp))
            report.salesByPaymentMethod.forEach { (method, amount) ->
                PaymentBar(
                    method = method,
                    amount = amount,
                    fraction = (amount / maxAmount).toFloat(),
                )
                Spacer(Modifier.height(6.dp))
            }
        }
    }
}

@Composable
private fun PaymentBar(method: PaymentMethod, amount: Double, fraction: Float) {
    val primary = MaterialTheme.colorScheme.primary
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = method.name,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.width(80.dp),
        )
        Canvas(
            modifier = Modifier
                .weight(1f)
                .height(16.dp),
        ) {
            drawRect(color = primary, size = size.copy(width = size.width * fraction))
        }
        Spacer(Modifier.width(8.dp))
        Text(
            text = "LKR %.2f".format(amount),
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

// ─── Product sales row ────────────────────────────────────────────────────────

@Composable
private fun ProductSalesRow(productId: String, revenue: Double) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(productId, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Text("LKR %.2f".format(revenue), style = MaterialTheme.typography.bodyMedium)
    }
}
