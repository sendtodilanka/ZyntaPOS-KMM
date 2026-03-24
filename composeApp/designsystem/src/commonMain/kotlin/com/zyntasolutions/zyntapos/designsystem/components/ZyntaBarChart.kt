package com.zyntasolutions.zyntapos.designsystem.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zyntasolutions.zyntapos.designsystem.tokens.ZyntaElevation
import com.zyntasolutions.zyntapos.designsystem.tokens.ZyntaSpacing

// ---------------------------------------------------------------------------
// ZyntaBarChart -- Canvas-based vertical bar chart in a Card container
// ---------------------------------------------------------------------------

/**
 * A single bar entry for [ZyntaBarChart].
 * @param label X-axis label (category name).
 * @param value Y-axis value.
 * @param color Optional per-bar color override. Falls back to the chart-level barColor.
 */
data class BarChartEntry(
    val label: String,
    val value: Float,
    val color: Color? = null,
)

/**
 * A grouped bar series for multi-series [ZyntaBarChart].
 * @param name Legend name.
 * @param values Values (one per category, must match labels length).
 * @param color Series color.
 */
data class BarChartSeries(
    val name: String,
    val values: List<Float>,
    val color: Color,
)

/**
 * Rendering mode for multi-series bar charts.
 */
enum class BarChartMode {
    /** Bars rendered side-by-side within each category. */
    GROUPED,

    /** Bars stacked on top of each other within each category. */
    STACKED,
}

/**
 * Professional vertical bar chart rendered in a Material 3 Card.
 *
 * Supports three modes:
 * - **Single series:** pass [entries] with per-bar labels & values.
 * - **Multi-series (grouped):** pass [labels] + [barSeries] with [mode] = [BarChartMode.GROUPED].
 * - **Multi-series (stacked):** pass [labels] + [barSeries] with [mode] = [BarChartMode.STACKED].
 *
 * Features:
 * - Gradient-filled rounded bars with animated height on initial render
 * - Y-axis grid lines with value labels
 * - X-axis category labels
 * - Optional value labels on top of bars
 * - Multi-series legend
 * - Responsive sizing (fills available width)
 *
 * @param title Chart title.
 * @param entries Single-series bar entries (used when [barSeries] is empty).
 * @param barColor Default bar color for single-series mode.
 * @param labels X-axis labels for multi-series mode.
 * @param barSeries Grouped/stacked bar series (overrides [entries] when non-empty).
 * @param mode Rendering mode for multi-series charts.
 * @param modifier Optional [Modifier].
 * @param chartHeight Height of the chart canvas area in dp.
 * @param showGrid Whether to show horizontal grid lines.
 * @param showLegend Whether to show the series legend.
 * @param showValueLabels Whether to show value labels on top of bars.
 * @param animate Whether to animate bar height on initial render.
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
    mode: BarChartMode = BarChartMode.GROUPED,
    chartHeight: Int = 200,
    showGrid: Boolean = true,
    showLegend: Boolean = true,
    showValueLabels: Boolean = false,
    animate: Boolean = true,
    yAxisFormatter: ((Float) -> String)? = null,
) {
    val gridColor = MaterialTheme.colorScheme.outlineVariant
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
    val textMeasurer = rememberTextMeasurer()

    // Determine mode: multi-series or single
    val isMultiSeries = barSeries.isNotEmpty() && labels.isNotEmpty()
    val isStacked = isMultiSeries && mode == BarChartMode.STACKED
    val categoryLabels = if (isMultiSeries) labels else entries.map { it.label }
    val categoryCount = categoryLabels.size
    if (categoryCount == 0) return

    val maxValue = when {
        isStacked -> {
            // For stacked bars, max is the sum of all series at any category
            (0 until categoryCount).maxOf { catIndex ->
                barSeries.sumOf { series ->
                    series.values.getOrElse(catIndex) { 0f }.toDouble()
                }.toFloat()
            }.coerceAtLeast(1f)
        }

        isMultiSeries -> {
            barSeries.flatMap { it.values }.maxOrNull()?.coerceAtLeast(1f) ?: 1f
        }

        else -> {
            entries.maxOfOrNull { it.value }?.coerceAtLeast(1f) ?: 1f
        }
    }

    // Animation
    val animationProgress = remember { Animatable(if (animate) 0f else 1f) }
    LaunchedEffect(entries, barSeries) {
        if (animate) {
            animationProgress.snapTo(0f)
            animationProgress.animateTo(
                targetValue = 1f,
                animationSpec = tween(durationMillis = 600),
            )
        }
    }

    val formatter = yAxisFormatter ?: { value ->
        if (value >= 1000f) {
            val k = value / 1000f
            if (k == k.toLong().toFloat()) "${k.toLong()}k" else "%.1fk".format(k)
        } else if (value == value.toLong().toFloat()) {
            value.toLong().toString()
        } else {
            "%.1f".format(value)
        }
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
            val yAxisLabelStyle = TextStyle(
                fontSize = 10.sp,
                color = labelColor,
            )
            val valueLabelStyle = TextStyle(
                fontSize = 9.sp,
                fontWeight = FontWeight.Medium,
                color = labelColor,
            )
            val progress = animationProgress.value

            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(chartHeight.dp),
            ) {
                val leftPadding = 48f
                val bottomPadding = 4f
                val topPadding = if (showValueLabels) 20f else 8f
                val chartWidth = size.width - leftPadding
                val chartAreaHeight = size.height - bottomPadding - topPadding

                // Y-axis grid lines + value labels
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

                        // Y-axis value label
                        val gridValue = maxValue * (1f - i.toFloat() / gridLines)
                        val labelText = formatter(gridValue)
                        val measured = textMeasurer.measure(
                            text = labelText,
                            style = yAxisLabelStyle,
                        )
                        drawText(
                            textLayoutResult = measured,
                            topLeft = Offset(
                                x = leftPadding - measured.size.width - 4f,
                                y = y - measured.size.height / 2f,
                            ),
                        )
                    }
                }

                // Bar geometry
                val groupWidth = chartWidth / categoryCount
                val barGap = (groupWidth * 0.2f).coerceAtLeast(4f)
                val cornerRadius = CornerRadius(4.dp.toPx())

                if (isStacked) {
                    val singleBarWidth = (groupWidth - barGap).coerceAtLeast(4f)

                    for (catIndex in 0 until categoryCount) {
                        val barX = leftPadding + catIndex * groupWidth + barGap / 2f
                        var cumulativeHeight = 0f

                        barSeries.forEach { series ->
                            val value = series.values.getOrElse(catIndex) { 0f }
                            val segmentHeight = (value / maxValue) * chartAreaHeight * progress
                            val segmentTop =
                                topPadding + chartAreaHeight - cumulativeHeight - segmentHeight

                            drawRoundRect(
                                brush = Brush.verticalGradient(
                                    colors = listOf(
                                        series.color,
                                        series.color.copy(alpha = 0.7f),
                                    ),
                                    startY = segmentTop,
                                    endY = segmentTop + segmentHeight,
                                ),
                                topLeft = Offset(barX, segmentTop),
                                size = Size(singleBarWidth, segmentHeight),
                                cornerRadius = if (cumulativeHeight == 0f) {
                                    CornerRadius.Zero
                                } else {
                                    CornerRadius.Zero
                                },
                            )
                            cumulativeHeight += segmentHeight
                        }

                        // Round the top corners of the topmost segment
                        if (cumulativeHeight > 0f) {
                            val topY = topPadding + chartAreaHeight - cumulativeHeight
                            val topSeries = barSeries.last { series ->
                                series.values.getOrElse(catIndex) { 0f } > 0f
                            }
                            val topSegmentHeight =
                                (topSeries.values.getOrElse(catIndex) { 0f } / maxValue) *
                                    chartAreaHeight * progress
                            drawRoundRect(
                                brush = Brush.verticalGradient(
                                    colors = listOf(
                                        topSeries.color,
                                        topSeries.color.copy(alpha = 0.7f),
                                    ),
                                    startY = topY,
                                    endY = topY + topSegmentHeight,
                                ),
                                topLeft = Offset(barX, topY),
                                size = Size(singleBarWidth, topSegmentHeight),
                                cornerRadius = cornerRadius,
                            )
                        }

                        // Value label for stacked total
                        if (showValueLabels && progress > 0.8f) {
                            val totalValue = barSeries.sumOf { series ->
                                series.values.getOrElse(catIndex) { 0f }.toDouble()
                            }.toFloat()
                            val totalTop = topPadding + chartAreaHeight - cumulativeHeight
                            drawBarValueLabel(
                                textMeasurer = textMeasurer,
                                style = valueLabelStyle,
                                value = totalValue,
                                formatter = formatter,
                                barX = barX,
                                barWidth = singleBarWidth,
                                barTop = totalTop,
                            )
                        }
                    }
                } else if (isMultiSeries) {
                    val seriesCount = barSeries.size
                    val totalBarSpace = groupWidth - barGap
                    val singleBarWidth = (totalBarSpace / seriesCount).coerceAtLeast(4f)

                    for (catIndex in 0 until categoryCount) {
                        val groupStartX = leftPadding + catIndex * groupWidth + barGap / 2f
                        barSeries.forEachIndexed { seriesIdx, series ->
                            val value = series.values.getOrElse(catIndex) { 0f }
                            val barHeight = (value / maxValue) * chartAreaHeight * progress
                            val barX = groupStartX + seriesIdx * singleBarWidth

                            drawRoundRect(
                                brush = Brush.verticalGradient(
                                    colors = listOf(
                                        series.color,
                                        series.color.copy(alpha = 0.7f),
                                    ),
                                    startY = topPadding + chartAreaHeight - barHeight,
                                    endY = topPadding + chartAreaHeight,
                                ),
                                topLeft = Offset(
                                    barX,
                                    topPadding + chartAreaHeight - barHeight,
                                ),
                                size = Size(singleBarWidth, barHeight),
                                cornerRadius = cornerRadius,
                            )

                            // Value label per bar
                            if (showValueLabels && progress > 0.8f) {
                                drawBarValueLabel(
                                    textMeasurer = textMeasurer,
                                    style = valueLabelStyle,
                                    value = value,
                                    formatter = formatter,
                                    barX = barX,
                                    barWidth = singleBarWidth,
                                    barTop = topPadding + chartAreaHeight - barHeight,
                                )
                            }
                        }
                    }
                } else {
                    val singleBarWidth = (groupWidth - barGap).coerceAtLeast(4f)
                    entries.forEachIndexed { index, entry ->
                        val barHeight = (entry.value / maxValue) * chartAreaHeight * progress
                        val barX = leftPadding + index * groupWidth + barGap / 2f
                        val color = entry.color ?: barColor

                        drawRoundRect(
                            brush = Brush.verticalGradient(
                                colors = listOf(color, color.copy(alpha = 0.7f)),
                                startY = topPadding + chartAreaHeight - barHeight,
                                endY = topPadding + chartAreaHeight,
                            ),
                            topLeft = Offset(
                                barX,
                                topPadding + chartAreaHeight - barHeight,
                            ),
                            size = Size(singleBarWidth, barHeight),
                            cornerRadius = cornerRadius,
                        )

                        // Value label per bar
                        if (showValueLabels && progress > 0.8f) {
                            drawBarValueLabel(
                                textMeasurer = textMeasurer,
                                style = valueLabelStyle,
                                value = entry.value,
                                formatter = formatter,
                                barX = barX,
                                barWidth = singleBarWidth,
                                barTop = topPadding + chartAreaHeight - barHeight,
                            )
                        }
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
            if (showLegend && isMultiSeries && barSeries.size > 1) {
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

/**
 * Draws a formatted value label centered above a bar.
 */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawBarValueLabel(
    textMeasurer: TextMeasurer,
    style: TextStyle,
    value: Float,
    formatter: (Float) -> String,
    barX: Float,
    barWidth: Float,
    barTop: Float,
) {
    val labelText = formatter(value)
    val measured = textMeasurer.measure(
        text = labelText,
        style = style,
        overflow = TextOverflow.Clip,
        maxLines = 1,
    )
    val labelX = barX + (barWidth - measured.size.width) / 2f
    val labelY = barTop - measured.size.height - 2f
    drawText(
        textLayoutResult = measured,
        topLeft = Offset(
            x = labelX.coerceAtLeast(0f),
            y = labelY.coerceAtLeast(0f),
        ),
    )
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
