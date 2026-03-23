package com.zyntasolutions.zyntapos.designsystem.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.zyntasolutions.zyntapos.designsystem.tokens.ZyntaElevation
import com.zyntasolutions.zyntapos.designsystem.tokens.ZyntaSpacing

// ---------------------------------------------------------------------------
// ZyntaBarChart -- Canvas-based vertical bar chart in a Card container
// ---------------------------------------------------------------------------

/**
 * A single bar entry for [ZyntaBarChart].
 * @param label X-axis label (category name).
 * @param value Y-axis value.
 * @param color Optional per-bar color override. Falls back to the chart-level [ZyntaBarChart.barColor].
 */
data class BarChartEntry(
    val label: String,
    val value: Float,
    val color: Color? = null,
)

/**
 * A grouped bar series for multi-series [ZyntaBarChart].
 * @param name Legend name.
 * @param values Values (one per category, must match [ZyntaBarChart] labels length).
 * @param color Series color.
 */
data class BarChartSeries(
    val name: String,
    val values: List<Float>,
    val color: Color,
)

/**
 * Professional vertical bar chart rendered in a Material 3 Card.
 *
 * Supports two modes:
 * - **Single series:** pass [entries] with per-bar labels & values.
 * - **Multi-series (grouped):** pass [labels] + [barSeries] for side-by-side bars.
 *
 * Features:
 * - Gradient-filled rounded bars
 * - Y-axis grid lines with value labels
 * - X-axis category labels
 * - Multi-series legend
 *
 * @param title Chart title.
 * @param entries Single-series bar entries (used when [barSeries] is empty).
 * @param barColor Default bar color for single-series mode.
 * @param labels X-axis labels for multi-series mode.
 * @param barSeries Grouped bar series (overrides [entries] when non-empty).
 * @param modifier Optional [Modifier].
 * @param chartHeight Height of the chart canvas area in dp.
 * @param showGrid Whether to show horizontal grid lines.
 * @param showLegend Whether to show the series legend.
 * @param yAxisFormatter Optional formatter for Y-axis labels.
 */
@Composable
fun ZyntaBarChart(
    title: String,
    modifier: Modifier = Modifier,
    entries: List<BarChartEntry> = emptyList(),
    barColor: Color = MaterialTheme.colorScheme.primary,
    labels: List<String> = emptyList(),
    barSeries: List<BarChartSeries> = emptyList(),
    chartHeight: Int = 200,
    showGrid: Boolean = true,
    showLegend: Boolean = true,
    yAxisFormatter: ((Float) -> String)? = null,
) {
    val gridColor = MaterialTheme.colorScheme.outlineVariant
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant

    // Determine mode: grouped or single
    val isGrouped = barSeries.isNotEmpty() && labels.isNotEmpty()
    val categoryLabels = if (isGrouped) labels else entries.map { it.label }
    val categoryCount = categoryLabels.size
    if (categoryCount == 0) return

    val maxValue = if (isGrouped) {
        barSeries.flatMap { it.values }.maxOrNull()?.coerceAtLeast(1f) ?: 1f
    } else {
        entries.maxOfOrNull { it.value }?.coerceAtLeast(1f) ?: 1f
    }

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
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(chartHeight.dp),
            ) {
                val leftPadding = 48f
                val bottomPadding = 4f
                val topPadding = 8f
                val chartWidth = size.width - leftPadding
                val chartAreaHeight = size.height - bottomPadding - topPadding

                // Y-axis grid lines
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

                // Bar geometry
                val groupWidth = chartWidth / categoryCount
                val barGap = (groupWidth * 0.2f).coerceAtLeast(4f)
                val cornerRadius = CornerRadius(4.dp.toPx())

                if (isGrouped) {
                    val seriesCount = barSeries.size
                    val totalBarSpace = groupWidth - barGap
                    val singleBarWidth = (totalBarSpace / seriesCount).coerceAtLeast(4f)

                    for (catIndex in 0 until categoryCount) {
                        val groupStartX = leftPadding + catIndex * groupWidth + barGap / 2f
                        barSeries.forEachIndexed { seriesIdx, series ->
                            val value = series.values.getOrElse(catIndex) { 0f }
                            val barHeight = (value / maxValue) * chartAreaHeight
                            val barX = groupStartX + seriesIdx * singleBarWidth

                            drawRoundRect(
                                brush = Brush.verticalGradient(
                                    colors = listOf(series.color, series.color.copy(alpha = 0.7f)),
                                    startY = topPadding + chartAreaHeight - barHeight,
                                    endY = topPadding + chartAreaHeight,
                                ),
                                topLeft = Offset(barX, topPadding + chartAreaHeight - barHeight),
                                size = Size(singleBarWidth, barHeight),
                                cornerRadius = cornerRadius,
                            )
                        }
                    }
                } else {
                    val singleBarWidth = (groupWidth - barGap).coerceAtLeast(4f)
                    entries.forEachIndexed { index, entry ->
                        val barHeight = (entry.value / maxValue) * chartAreaHeight
                        val barX = leftPadding + index * groupWidth + barGap / 2f
                        val color = entry.color ?: barColor

                        drawRoundRect(
                            brush = Brush.verticalGradient(
                                colors = listOf(color, color.copy(alpha = 0.7f)),
                                startY = topPadding + chartAreaHeight - barHeight,
                                endY = topPadding + chartAreaHeight,
                            ),
                            topLeft = Offset(barX, topPadding + chartAreaHeight - barHeight),
                            size = Size(singleBarWidth, barHeight),
                            cornerRadius = cornerRadius,
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
                categoryLabels.forEach { label ->
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelSmall,
                        color = labelColor,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                    )
                }
            }

            // Legend (multi-series only)
            if (showLegend && isGrouped && barSeries.size > 1) {
                Spacer(Modifier.height(ZyntaSpacing.sm))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                ) {
                    barSeries.forEach { s ->
                        BarLegendItem(color = s.color, label = s.name)
                        Spacer(Modifier.width(ZyntaSpacing.md))
                    }
                }
            }
        }
    }
}

@Composable
private fun BarLegendItem(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Canvas(modifier = Modifier.width(12.dp).height(8.dp)) {
            drawRoundRect(
                color = color,
                cornerRadius = CornerRadius(2.dp.toPx()),
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
