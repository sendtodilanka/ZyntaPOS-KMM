package com.zyntasolutions.zyntapos.designsystem.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zyntasolutions.zyntapos.designsystem.tokens.ZyntaElevation
import com.zyntasolutions.zyntapos.designsystem.tokens.ZyntaSpacing

// ---------------------------------------------------------------------------
// ZyntaHeatmapGrid -- Canvas-based heatmap grid in a Card container
// ---------------------------------------------------------------------------

/**
 * A single cell in the heatmap grid.
 * @param row Row index (0-based).
 * @param col Column index (0-based).
 * @param value Numeric value for color intensity.
 */
data class HeatmapCell(
    val row: Int,
    val col: Int,
    val value: Double,
)

/**
 * Aggregated heatmap data with labels and optional value range.
 *
 * @param cells Flat list of [HeatmapCell] values (sparse OK -- missing cells render as zero).
 * @param rowLabels Labels for each row (top to bottom).
 * @param columnLabels Labels for each column (left to right).
 * @param minValue Floor value for color mapping. Defaults to 0.0.
 * @param maxValue Ceiling value for color mapping. Defaults to the maximum cell value.
 */
data class HeatmapData(
    val cells: List<HeatmapCell>,
    val rowLabels: List<String>,
    val columnLabels: List<String>,
    val minValue: Double = 0.0,
    val maxValue: Double = cells.maxOfOrNull { it.value } ?: 1.0,
)

/**
 * Three-stop color gradient used by [ZyntaHeatmapGrid].
 *
 * The heatmap interpolates linearly from [low] -> [mid] for the first half
 * of the value range, then from [mid] -> [high] for the second half.
 *
 * @param low Color for the minimum value.
 * @param mid Color for the midpoint value.
 * @param high Color for the maximum value.
 */
data class HeatmapColorGradient(
    val low: Color,
    val mid: Color,
    val high: Color,
)

/**
 * Professional heatmap grid component rendered in a Material 3 Card.
 *
 * Useful for visualizing patterns across two dimensions (e.g. hourly sales
 * by day-of-week, product performance by category and time period).
 *
 * Features:
 * - Dynamic color intensity based on value range
 * - Configurable three-stop color gradient (low / mid / high)
 * - Row labels on the left, column labels on top
 * - Optional cell value text rendered on canvas
 * - Rounded cell corners with configurable spacing
 * - Color scale legend bar
 * - Touch/click callback for cell detail
 *
 * @param title Chart title.
 * @param data Aggregated [HeatmapData] containing cells, labels, and value range.
 * @param modifier Optional [Modifier].
 * @param colorGradient Three-stop color gradient. Defaults to theme surfaceVariant -> primaryContainer -> primary.
 * @param cellHeight Height of each cell in dp.
 * @param cellSpacing Gap between cells in dp.
 * @param showLegend Whether to show the color scale legend.
 * @param showCellValues Whether to draw cell values as text inside each cell.
 * @param valueFormatter Formats numeric values for display in cells and legend.
 * @param onCellClick Callback when a cell is tapped. Receives the [HeatmapCell].
 */
@Composable
fun ZyntaHeatmapGrid(
    title: String,
    data: HeatmapData,
    modifier: Modifier = Modifier,
    colorGradient: HeatmapColorGradient = HeatmapColorGradient(
        low = MaterialTheme.colorScheme.surfaceVariant,
        mid = MaterialTheme.colorScheme.primaryContainer,
        high = MaterialTheme.colorScheme.primary,
    ),
    cellHeight: Int = 28,
    cellSpacing: Int = 2,
    showLegend: Boolean = true,
    showCellValues: Boolean = false,
    valueFormatter: ((Double) -> String) = { "%.0f".format(it) },
    onCellClick: ((HeatmapCell) -> Unit)? = null,
) {
    val rowCount = data.rowLabels.size
    val colCount = data.columnLabels.size
    if (rowCount == 0 || colCount == 0) return

    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
    val cellTextColor = MaterialTheme.colorScheme.onSurface
    val textMeasurer = rememberTextMeasurer()

    // Build value lookup: (row, col) -> value
    val valueMap = remember(data.cells) {
        buildMap {
            data.cells.forEach { cell -> put(cell.row to cell.col, cell) }
        }
    }

    val minVal = data.minValue
    val maxVal = data.maxValue.coerceAtLeast(minVal + 1.0)
    val range = maxVal - minVal

    val totalGridHeight = rowCount * cellHeight + (rowCount - 1) * cellSpacing

    // Track cell layout for tap detection
    var cellLayoutInfo by remember { mutableStateOf<CellLayoutInfo?>(null) }

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
                data.columnLabels.forEach { label ->
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
                    data.rowLabels.forEach { label ->
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
                val canvasModifier = Modifier
                    .weight(1f)
                    .height(totalGridHeight.dp)
                    .let { mod ->
                        if (onCellClick != null) {
                            mod.pointerInput(data) {
                                detectTapGestures { offset ->
                                    val layout = cellLayoutInfo ?: return@detectTapGestures
                                    val col = ((offset.x - layout.originX) /
                                        (layout.cellW + layout.spacingPx)).toInt()
                                    val row = ((offset.y) /
                                        (layout.cellH + layout.spacingPx)).toInt()
                                    if (row in 0 until rowCount && col in 0 until colCount) {
                                        val cell = valueMap[row to col]
                                            ?: HeatmapCell(row, col, 0.0)
                                        onCellClick(cell)
                                    }
                                }
                            }
                        } else {
                            mod
                        }
                    }

                Canvas(modifier = canvasModifier) {
                    val cellW = (size.width - (colCount - 1) * cellSpacing.dp.toPx()) / colCount
                    val cellH = cellHeight.dp.toPx()
                    val spacing = cellSpacing.dp.toPx()
                    val corner = CornerRadius(3.dp.toPx())

                    // Store layout info for tap detection
                    cellLayoutInfo = CellLayoutInfo(
                        originX = 0f,
                        cellW = cellW,
                        cellH = cellH,
                        spacingPx = spacing,
                    )

                    for (row in 0 until rowCount) {
                        for (col in 0 until colCount) {
                            val value = valueMap[row to col]?.value ?: 0.0
                            val fraction = ((value - minVal) / range)
                                .coerceIn(0.0, 1.0).toFloat()
                            val cellColor = lerpGradient(
                                colorGradient.low,
                                colorGradient.mid,
                                colorGradient.high,
                                fraction,
                            )

                            val x = col * (cellW + spacing)
                            val y = row * (cellH + spacing)

                            drawRoundRect(
                                color = cellColor,
                                topLeft = Offset(x, y),
                                size = Size(cellW, cellH),
                                cornerRadius = corner,
                            )

                            // Draw cell value text
                            if (showCellValues) {
                                drawCellText(
                                    textMeasurer = textMeasurer,
                                    text = valueFormatter(value),
                                    topLeft = Offset(x, y),
                                    cellSize = Size(cellW, cellH),
                                    textColor = pickTextColor(
                                        cellColor,
                                        cellTextColor,
                                        labelColor,
                                    ),
                                )
                            }
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
                    Text(
                        text = valueFormatter(minVal),
                        style = MaterialTheme.typography.labelSmall,
                        color = labelColor,
                    )
                    Spacer(Modifier.width(4.dp))
                    // Gradient bar showing the three-stop color ramp
                    Canvas(
                        modifier = Modifier
                            .width(120.dp)
                            .height(10.dp),
                    ) {
                        val steps = 24
                        val stepWidth = size.width / steps
                        for (i in 0 until steps) {
                            val f = i.toFloat() / (steps - 1)
                            drawRect(
                                color = lerpGradient(
                                    colorGradient.low,
                                    colorGradient.mid,
                                    colorGradient.high,
                                    f,
                                ),
                                topLeft = Offset(i * stepWidth, 0f),
                                size = Size(stepWidth + 1f, size.height),
                            )
                        }
                    }
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = valueFormatter(maxVal),
                        style = MaterialTheme.typography.labelSmall,
                        color = labelColor,
                    )
                }
            }
        }
    }
}

/**
 * Simplified overload that accepts raw cell list and labels directly,
 * matching the original API for backward compatibility.
 *
 * @param title Chart title.
 * @param cells Flat list of [HeatmapCell] values.
 * @param rowLabels Labels for each row.
 * @param columnLabels Labels for each column.
 * @param modifier Optional [Modifier].
 * @param lowColor Color for the minimum value.
 * @param highColor Color for the maximum value.
 * @param cellHeight Height of each cell in dp.
 * @param cellSpacing Gap between cells in dp.
 * @param showLegend Whether to show the color scale legend.
 * @param valueFormatter Optional formatter for legend values.
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
    valueFormatter: ((Double) -> String)? = null,
) {
    val midColor = lerp(lowColor, highColor, 0.5f)
    ZyntaHeatmapGrid(
        title = title,
        data = HeatmapData(
            cells = cells,
            rowLabels = rowLabels,
            columnLabels = columnLabels,
        ),
        modifier = modifier,
        colorGradient = HeatmapColorGradient(
            low = lowColor,
            mid = midColor,
            high = highColor,
        ),
        cellHeight = cellHeight,
        cellSpacing = cellSpacing,
        showLegend = showLegend,
        valueFormatter = valueFormatter ?: { "%.0f".format(it) },
    )
}

// ---------------------------------------------------------------------------
// Internal helpers
// ---------------------------------------------------------------------------

/**
 * Layout dimensions cached for tap-to-cell mapping.
 */
private data class CellLayoutInfo(
    val originX: Float,
    val cellW: Float,
    val cellH: Float,
    val spacingPx: Float,
)

/**
 * Interpolates across a three-stop gradient.
 *
 * [fraction] 0.0 -> [low], 0.5 -> [mid], 1.0 -> [high].
 */
private fun lerpGradient(low: Color, mid: Color, high: Color, fraction: Float): Color {
    return if (fraction <= 0.5f) {
        lerp(low, mid, fraction * 2f)
    } else {
        lerp(mid, high, (fraction - 0.5f) * 2f)
    }
}

/**
 * Picks a readable text color based on the cell background luminance.
 * Uses a simple perceived luminance check (BT.601 coefficients).
 */
private fun pickTextColor(
    background: Color,
    darkText: Color,
    lightText: Color,
): Color {
    val luminance = 0.299f * background.red +
        0.587f * background.green +
        0.114f * background.blue
    return if (luminance > 0.5f) darkText else lightText
}

/**
 * Draws centered text inside a cell on the Canvas.
 */
private fun DrawScope.drawCellText(
    textMeasurer: TextMeasurer,
    text: String,
    topLeft: Offset,
    cellSize: Size,
    textColor: Color,
) {
    val style = TextStyle(
        fontSize = 9.sp,
        fontWeight = FontWeight.Medium,
        color = textColor,
    )
    val measured = textMeasurer.measure(
        text = text,
        style = style,
        overflow = TextOverflow.Clip,
        maxLines = 1,
        constraints = Constraints(
            maxWidth = cellSize.width.toInt(),
            maxHeight = cellSize.height.toInt(),
        ),
    )
    val textX = topLeft.x + (cellSize.width - measured.size.width) / 2f
    val textY = topLeft.y + (cellSize.height - measured.size.height) / 2f
    drawText(
        textLayoutResult = measured,
        topLeft = Offset(textX, textY),
    )
}
