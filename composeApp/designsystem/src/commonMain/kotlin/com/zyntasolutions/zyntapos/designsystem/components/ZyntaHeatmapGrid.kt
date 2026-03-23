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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.zyntasolutions.zyntapos.designsystem.tokens.ZyntaElevation
import com.zyntasolutions.zyntapos.designsystem.tokens.ZyntaSpacing

// ---------------------------------------------------------------------------
// ZyntaHeatmapGrid -- Canvas-based heatmap grid in a Card container
// ---------------------------------------------------------------------------

/**
 * A single cell in the heatmap grid.
 * @param row Row index (0-based).
 * @param column Column index (0-based).
 * @param value Numeric value for color intensity.
 */
data class HeatmapCell(
    val row: Int,
    val column: Int,
    val value: Float,
)

/**
 * Professional heatmap grid component rendered in a Material 3 Card.
 *
 * Useful for visualizing patterns across two dimensions (e.g. hourly sales
 * by day-of-week, product performance by category and time period).
 *
 * Features:
 * - Dynamic color intensity based on value range
 * - Configurable color ramp (low → high)
 * - Row and column labels
 * - Rounded cell corners
 * - Color scale legend
 *
 * @param title Chart title.
 * @param cells Flat list of [HeatmapCell] values (sparse OK — missing cells render as zero).
 * @param rowLabels Labels for each row (top to bottom).
 * @param columnLabels Labels for each column (left to right).
 * @param modifier Optional [Modifier].
 * @param lowColor Color for the minimum value.
 * @param highColor Color for the maximum value.
 * @param cellHeight Height of each cell in dp.
 * @param cellSpacing Gap between cells in dp.
 * @param showLegend Whether to show the color scale legend.
 * @param valueFormatter Optional formatter for tooltip/accessibility values.
 */
@Composable
fun ZyntaHeatmapGrid(
    title: String,
    cells: List<HeatmapCell>,
    rowLabels: List<String>,
    columnLabels: List<String>,
    modifier: Modifier = Modifier,
    lowColor: Color = MaterialTheme.colorScheme.surfaceVariant,
    highColor: Color = MaterialTheme.colorScheme.primary,
    cellHeight: Int = 28,
    cellSpacing: Int = 2,
    showLegend: Boolean = true,
    valueFormatter: ((Float) -> String)? = null,
) {
    val rowCount = rowLabels.size
    val colCount = columnLabels.size
    if (rowCount == 0 || colCount == 0) return

    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant

    // Build value lookup: (row, col) -> value
    val valueMap = mutableMapOf<Pair<Int, Int>, Float>()
    cells.forEach { cell -> valueMap[cell.row to cell.column] = cell.value }

    val maxValue = cells.maxOfOrNull { it.value }?.coerceAtLeast(1f) ?: 1f
    val minValue = cells.minOfOrNull { it.value } ?: 0f

    val totalGridHeight = rowCount * cellHeight + (rowCount - 1) * cellSpacing

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

            // Column labels row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 56.dp),
            ) {
                columnLabels.forEach { label ->
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

            Spacer(Modifier.height(4.dp))

            // Heatmap grid: row labels + canvas
            Row(modifier = Modifier.fillMaxWidth()) {
                // Row labels column
                Column(
                    modifier = Modifier.width(52.dp),
                    verticalArrangement = Arrangement.SpaceEvenly,
                ) {
                    rowLabels.forEach { label ->
                        Text(
                            text = label,
                            style = MaterialTheme.typography.labelSmall,
                            color = labelColor,
                            textAlign = TextAlign.End,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(cellHeight.dp)
                                .padding(end = 4.dp),
                            maxLines = 1,
                        )
                        if (cellSpacing > 0) {
                            Spacer(Modifier.height(cellSpacing.dp))
                        }
                    }
                }

                Spacer(Modifier.width(4.dp))

                // Canvas grid
                Canvas(
                    modifier = Modifier
                        .weight(1f)
                        .height(totalGridHeight.dp),
                ) {
                    val cellW = (size.width - (colCount - 1) * cellSpacing.dp.toPx()) / colCount
                    val cellH = cellHeight.dp.toPx()
                    val spacing = cellSpacing.dp.toPx()
                    val corner = CornerRadius(3.dp.toPx())
                    val range = (maxValue - minValue).coerceAtLeast(1f)

                    for (row in 0 until rowCount) {
                        for (col in 0 until colCount) {
                            val value = valueMap[row to col] ?: 0f
                            val fraction = ((value - minValue) / range).coerceIn(0f, 1f)
                            val cellColor = lerp(lowColor, highColor, fraction)

                            val x = col * (cellW + spacing)
                            val y = row * (cellH + spacing)

                            drawRoundRect(
                                color = cellColor,
                                topLeft = Offset(x, y),
                                size = Size(cellW, cellH),
                                cornerRadius = corner,
                            )
                        }
                    }
                }
            }

            // Color scale legend
            if (showLegend) {
                Spacer(Modifier.height(ZyntaSpacing.sm))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 56.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                ) {
                    val formatter = valueFormatter ?: { "%.0f".format(it) }
                    Text(
                        text = formatter(minValue),
                        style = MaterialTheme.typography.labelSmall,
                        color = labelColor,
                    )
                    Spacer(Modifier.width(4.dp))
                    // Gradient bar showing the color ramp
                    Canvas(
                        modifier = Modifier
                            .width(100.dp)
                            .height(10.dp),
                    ) {
                        val steps = 20
                        val stepWidth = size.width / steps
                        for (i in 0 until steps) {
                            val f = i.toFloat() / (steps - 1)
                            drawRect(
                                color = lerp(lowColor, highColor, f),
                                topLeft = Offset(i * stepWidth, 0f),
                                size = Size(stepWidth + 1f, size.height),
                            )
                        }
                    }
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = formatter(maxValue),
                        style = MaterialTheme.typography.labelSmall,
                        color = labelColor,
                    )
                }
            }
        }
    }
}
