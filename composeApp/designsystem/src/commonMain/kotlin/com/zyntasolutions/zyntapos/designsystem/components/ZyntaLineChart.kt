package com.zyntasolutions.zyntapos.designsystem.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.zyntasolutions.zyntapos.designsystem.tokens.ZyntaElevation
import com.zyntasolutions.zyntapos.designsystem.tokens.ZyntaSpacing

// ─────────────────────────────────────────────────────────────────────────────
// ZyntaLineChart — Canvas-based line chart in a Card container
// ─────────────────────────────────────────────────────────────────────────────

/**
 * A data point for [ZyntaLineChart].
 * @param label X-axis label (e.g. "Mon", "Jan").
 * @param value Y-axis value.
 */
data class ChartDataPoint(
    val label: String,
    val value: Float,
)

/**
 * A line series for [ZyntaLineChart].
 * @param name Series name for legend.
 * @param data Data points (must be same length across all series).
 * @param color Line and fill color.
 */
data class ChartSeries(
    val name: String,
    val data: List<ChartDataPoint>,
    val color: Color,
)

/**
 * Professional line chart component rendered in a Material 3 Card.
 *
 * Features:
 * - Multi-series support with gradient fills
 * - X-axis labels
 * - Y-axis grid lines with value labels
 * - Legend row below the chart
 * - Smooth line rendering with data point markers
 *
 * @param title Chart title.
 * @param series List of [ChartSeries] to plot.
 * @param modifier Optional [Modifier].
 * @param chartHeight Height of the chart canvas area.
 * @param showGrid Whether to show horizontal grid lines.
 * @param showLegend Whether to show the series legend.
 * @param yAxisFormatter Optional formatter for Y-axis labels.
 */
@Composable
fun ZyntaLineChart(
    title: String,
    series: List<ChartSeries>,
    modifier: Modifier = Modifier,
    chartHeight: Int = 200,
    showGrid: Boolean = true,
    showLegend: Boolean = true,
    yAxisFormatter: ((Float) -> String)? = null,
) {
    val gridColor = MaterialTheme.colorScheme.outlineVariant
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
    val markerInnerColor = MaterialTheme.colorScheme.surface

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = ZyntaElevation.Level1),
        shape = MaterialTheme.shapes.medium,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(ZyntaSpacing.md),
        ) {
            // Title
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
            )

            Spacer(Modifier.height(ZyntaSpacing.md))

            // Chart canvas
            if (series.isNotEmpty() && series.first().data.isNotEmpty()) {
                val allValues = series.flatMap { s -> s.data.map { it.value } }
                val maxValue = allValues.max().coerceAtLeast(1f)
                val labels = series.first().data.map { it.label }

                Canvas(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(chartHeight.dp),
                ) {
                    val leftPadding = 48f
                    val bottomPadding = 24f
                    val topPadding = 8f
                    val chartWidth = size.width - leftPadding
                    val chartAreaHeight = size.height - bottomPadding - topPadding

                    // Grid lines
                    if (showGrid) {
                        val gridLines = 4
                        for (i in 0..gridLines) {
                            val y = topPadding + (chartAreaHeight / gridLines) * i
                            drawLine(
                                color = gridColor.copy(alpha = 0.3f),
                                start = Offset(leftPadding, y),
                                end = Offset(size.width, y),
                                strokeWidth = 1f,
                            )
                        }
                    }

                    // Draw each series
                    series.forEach { s ->
                        if (s.data.size < 2) return@forEach
                        val stepX = chartWidth / (s.data.size - 1)

                        val points = s.data.mapIndexed { index, dp ->
                            Offset(
                                x = leftPadding + index * stepX,
                                y = topPadding + chartAreaHeight - (dp.value / maxValue) * chartAreaHeight,
                            )
                        }

                        // Gradient fill
                        val fillPath = Path().apply {
                            moveTo(points.first().x, topPadding + chartAreaHeight)
                            lineTo(points.first().x, points.first().y)
                            for (i in 1 until points.size) {
                                lineTo(points[i].x, points[i].y)
                            }
                            lineTo(points.last().x, topPadding + chartAreaHeight)
                            close()
                        }
                        drawPath(
                            path = fillPath,
                            brush = Brush.verticalGradient(
                                colors = listOf(
                                    s.color.copy(alpha = 0.25f),
                                    s.color.copy(alpha = 0.02f),
                                ),
                                startY = topPadding,
                                endY = topPadding + chartAreaHeight,
                            ),
                        )

                        // Line
                        val linePath = Path().apply {
                            moveTo(points.first().x, points.first().y)
                            for (i in 1 until points.size) {
                                lineTo(points[i].x, points[i].y)
                            }
                        }
                        drawPath(
                            path = linePath,
                            color = s.color,
                            style = Stroke(
                                width = 2.5.dp.toPx(),
                                cap = StrokeCap.Round,
                                join = StrokeJoin.Round,
                            ),
                        )

                        // Data point markers
                        points.forEach { point ->
                            drawCircle(
                                color = s.color,
                                radius = 4.dp.toPx(),
                                center = point,
                            )
                            drawCircle(
                                color = markerInnerColor,
                                radius = 2.dp.toPx(),
                                center = point,
                            )
                        }
                    }
                }

                // X-axis labels
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 48.dp),
                ) {
                    labels.forEachIndexed { index, label ->
                        Text(
                            text = label,
                            style = MaterialTheme.typography.labelSmall,
                            color = labelColor,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }

            // Legend
            if (showLegend && series.size > 1) {
                Spacer(Modifier.height(ZyntaSpacing.sm))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
                ) {
                    series.forEach { s ->
                        ChartLegendItem(color = s.color, label = s.name)
                        Spacer(Modifier.width(ZyntaSpacing.md))
                    }
                }
            }
        }
    }
}

/**
 * Horizontal bar chart component for displaying category breakdowns.
 */
@Composable
fun ZyntaHorizontalBarChart(
    title: String,
    items: List<Pair<String, Float>>,
    color: Color,
    modifier: Modifier = Modifier,
    valueFormatter: ((Float) -> String) = { "%.0f".format(it) },
) {
    val maxValue = items.maxOfOrNull { it.second }?.coerceAtLeast(1f) ?: 1f

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = ZyntaElevation.Level1),
        shape = MaterialTheme.shapes.medium,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(ZyntaSpacing.md),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(ZyntaSpacing.md))

            items.forEach { (label, value) ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.width(80.dp),
                    )
                    Box(modifier = Modifier.weight(1f).height(20.dp)) {
                        Canvas(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(20.dp),
                        ) {
                            // Background bar
                            drawRoundRect(
                                color = color.copy(alpha = 0.1f),
                                size = size.copy(height = size.height),
                                cornerRadius = androidx.compose.ui.geometry.CornerRadius(4.dp.toPx()),
                            )
                            // Value bar
                            val barWidth = (value / maxValue) * size.width
                            drawRoundRect(
                                brush = Brush.horizontalGradient(
                                    colors = listOf(color.copy(alpha = 0.8f), color),
                                ),
                                size = size.copy(width = barWidth, height = size.height),
                                cornerRadius = androidx.compose.ui.geometry.CornerRadius(4.dp.toPx()),
                            )
                        }
                    }
                    Spacer(Modifier.width(ZyntaSpacing.sm))
                    Text(
                        text = valueFormatter(value),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.width(56.dp),
                        textAlign = TextAlign.End,
                    )
                }
            }
        }
    }
}

@Composable
private fun ChartLegendItem(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Canvas(modifier = Modifier.width(12.dp).height(3.dp)) {
            drawRoundRect(
                color = color,
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(2.dp.toPx()),
            )
        }
        Spacer(Modifier.width(4.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

// ── Preview ───────────────────────────────────────────────────────────────────

@androidx.compose.ui.tooling.preview.Preview
@androidx.compose.runtime.Composable
private fun ZyntaLineChartPreview() {
    com.zyntasolutions.zyntapos.designsystem.theme.ZyntaTheme {
        ZyntaLineChart(
            title = "Daily Revenue",
            series = listOf(
                ChartSeries(
                    name = "Sales",
                    data = listOf(
                        ChartDataPoint(label = "Mon", value = 100f),
                        ChartDataPoint(label = "Tue", value = 200f),
                        ChartDataPoint(label = "Wed", value = 150f),
                        ChartDataPoint(label = "Thu", value = 300f),
                        ChartDataPoint(label = "Fri", value = 250f),
                    ),
                    color = androidx.compose.ui.graphics.Color(0xFF4CAF50),
                ),
            ),
        )
    }
}
