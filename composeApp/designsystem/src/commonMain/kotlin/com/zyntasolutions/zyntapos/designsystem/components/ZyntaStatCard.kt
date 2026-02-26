package com.zyntasolutions.zyntapos.designsystem.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TrendingDown
import androidx.compose.material.icons.automirrored.filled.TrendingFlat
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.zyntasolutions.zyntapos.designsystem.tokens.ZyntaElevation
import com.zyntasolutions.zyntapos.designsystem.tokens.ZyntaSpacing

// ─────────────────────────────────────────────────────────────────────────────
// ZyntaStatCard — Professional KPI stat card with icon, value, trend, sparkline
// ─────────────────────────────────────────────────────────────────────────────

/** Trend direction for [ZyntaStatCard]. */
enum class TrendDirection {
    Up, Down, Flat
}

/**
 * Professional KPI stat card with:
 * - Accent-colored icon in a tonal container
 * - Large value display
 * - Descriptive label
 * - Optional trend indicator with percentage
 * - Optional mini sparkline chart
 *
 * @param icon Leading icon representing the metric.
 * @param label Metric label (e.g. "Today's Sales").
 * @param value Formatted metric value (e.g. "$1,250.50").
 * @param accentColor Primary accent color for icon tinting and sparkline.
 * @param modifier Optional [Modifier].
 * @param trend Optional trend direction (Up, Down, Flat).
 * @param trendLabel Optional trend text (e.g. "+12.5%").
 * @param sparklineData Optional list of data points for a mini sparkline.
 * @param subtitle Optional subtitle below the value.
 */
@Composable
fun ZyntaStatCard(
    icon: ImageVector,
    label: String,
    value: String,
    accentColor: Color,
    modifier: Modifier = Modifier,
    trend: TrendDirection? = null,
    trendLabel: String? = null,
    sparklineData: List<Float> = emptyList(),
    subtitle: String? = null,
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = ZyntaElevation.Level1,
        ),
        shape = MaterialTheme.shapes.medium,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(ZyntaSpacing.md),
        ) {
            // Top row: icon + sparkline
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top,
            ) {
                // Icon in tonal container
                Surface(
                    shape = MaterialTheme.shapes.small,
                    color = accentColor.copy(alpha = 0.12f),
                    modifier = Modifier.size(44.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = icon,
                            contentDescription = label,
                            tint = accentColor,
                            modifier = Modifier.size(24.dp),
                        )
                    }
                }

                // Mini sparkline
                if (sparklineData.size >= 2) {
                    MiniSparkline(
                        data = sparklineData,
                        color = accentColor,
                        modifier = Modifier
                            .width(72.dp)
                            .height(32.dp),
                    )
                }
            }

            Spacer(Modifier.height(ZyntaSpacing.sm))

            // Label
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(Modifier.height(2.dp))

            // Value
            Text(
                text = value,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )

            // Subtitle
            if (subtitle != null) {
                Spacer(Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            // Trend indicator
            if (trend != null && trendLabel != null) {
                Spacer(Modifier.height(ZyntaSpacing.xs))
                TrendIndicator(trend = trend, label = trendLabel)
            }
        }
    }
}

/**
 * Compact stat card variant for smaller spaces (e.g. horizontal scroll rows).
 */
@Composable
fun ZyntaCompactStatCard(
    icon: ImageVector,
    label: String,
    value: String,
    accentColor: Color,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.width(160.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = ZyntaElevation.Level1),
        shape = MaterialTheme.shapes.medium,
    ) {
        Column(modifier = Modifier.padding(ZyntaSpacing.sm)) {
            Surface(
                shape = MaterialTheme.shapes.small,
                color = accentColor.copy(alpha = 0.12f),
                modifier = Modifier.size(36.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = icon,
                        contentDescription = label,
                        tint = accentColor,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
            Spacer(Modifier.height(ZyntaSpacing.sm))
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = value,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

// ── Internal sub-composables ─────────────────────────────────────────────────

@Composable
private fun TrendIndicator(trend: TrendDirection, label: String) {
    val (icon, color) = when (trend) {
        TrendDirection.Up -> Icons.AutoMirrored.Filled.TrendingUp to MaterialTheme.colorScheme.tertiary
        TrendDirection.Down -> Icons.AutoMirrored.Filled.TrendingDown to MaterialTheme.colorScheme.error
        TrendDirection.Flat -> Icons.AutoMirrored.Filled.TrendingFlat to MaterialTheme.colorScheme.onSurfaceVariant
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = color,
            modifier = Modifier.size(14.dp),
        )
        Spacer(Modifier.width(4.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            color = color,
        )
    }
}

/**
 * Mini sparkline chart drawn with Canvas.
 * Renders a smooth line from data points with a gradient fill below.
 */
@Composable
internal fun MiniSparkline(
    data: List<Float>,
    color: Color,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier) {
        if (data.size < 2) return@Canvas
        val maxVal = data.max().coerceAtLeast(1f)
        val minVal = data.min()
        val range = (maxVal - minVal).coerceAtLeast(1f)

        val stepX = size.width / (data.size - 1)
        val paddingY = 4f

        val points = data.mapIndexed { index, value ->
            Offset(
                x = index * stepX,
                y = size.height - paddingY - ((value - minVal) / range) * (size.height - paddingY * 2),
            )
        }

        // Draw gradient fill
        val fillPath = Path().apply {
            moveTo(points.first().x, size.height)
            lineTo(points.first().x, points.first().y)
            for (i in 1 until points.size) {
                lineTo(points[i].x, points[i].y)
            }
            lineTo(points.last().x, size.height)
            close()
        }
        drawPath(
            path = fillPath,
            brush = Brush.verticalGradient(
                colors = listOf(color.copy(alpha = 0.3f), color.copy(alpha = 0.0f)),
                startY = 0f,
                endY = size.height,
            ),
        )

        // Draw line
        val linePath = Path().apply {
            moveTo(points.first().x, points.first().y)
            for (i in 1 until points.size) {
                lineTo(points[i].x, points[i].y)
            }
        }
        drawPath(
            path = linePath,
            color = color,
            style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round),
        )
    }
}

// ── Preview ───────────────────────────────────────────────────────────────────

@androidx.compose.ui.tooling.preview.Preview
@androidx.compose.runtime.Composable
private fun ZyntaStatCardPreview() {
    com.zyntasolutions.zyntapos.designsystem.theme.ZyntaTheme {
        ZyntaStatCard(
            icon = Icons.AutoMirrored.Filled.TrendingUp,
            label = "Today's Sales",
            value = "Rs 45,200",
            accentColor = androidx.compose.ui.graphics.Color(0xFF4CAF50),
        )
    }
}
