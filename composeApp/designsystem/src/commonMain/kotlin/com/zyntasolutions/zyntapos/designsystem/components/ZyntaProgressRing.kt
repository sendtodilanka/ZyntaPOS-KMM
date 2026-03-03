package com.zyntasolutions.zyntapos.designsystem.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

// ─────────────────────────────────────────────────────────────────────────────
// ZyntaProgressRing — Circular arc indicator for daily target progress
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Circular progress ring that animates from 0 to [progress] on first composition.
 *
 * Uses [FastOutSlowInEasing] with a 400 ms entry delay so it plays after the
 * parent card's entrance animation settles.
 *
 * @param progress Value between `0.0f` (0 %) and `1.0f` (100 %).
 *   Values >1 are clamped to 1.
 * @param modifier Optional [Modifier].
 * @param size Outer diameter of the ring (default 120 dp).
 * @param strokeWidth Width of the track and progress arc (default 8 dp).
 * @param trackColor Background track color (default [MaterialTheme.colorScheme.surfaceVariant]).
 * @param progressColor Arc color (default [MaterialTheme.colorScheme.primary]).
 * @param centerContent Composable slotted at the centre of the ring (e.g. a percentage label).
 */
@Composable
fun ZyntaProgressRing(
    progress: Float,
    modifier: Modifier = Modifier,
    size: Dp = 120.dp,
    strokeWidth: Dp = 8.dp,
    trackColor: Color = MaterialTheme.colorScheme.surfaceVariant,
    progressColor: Color = MaterialTheme.colorScheme.primary,
    centerContent: @Composable () -> Unit = {},
) {
    var animTarget by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(progress) { animTarget = progress }

    val animatedProgress by animateFloatAsState(
        targetValue = animTarget,
        animationSpec = tween(
            durationMillis = 1000,
            delayMillis = 400,
            easing = FastOutSlowInEasing,
        ),
        label = "progress-ring",
    )

    Box(
        modifier = modifier.size(size),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.size(size)) {
            val strokePx = strokeWidth.toPx()
            val inset = strokePx / 2f
            val arcSize = this.size.minDimension - strokePx

            // Background track
            drawArc(
                color = trackColor,
                startAngle = -90f,
                sweepAngle = 360f,
                useCenter = false,
                style = Stroke(width = strokePx, cap = StrokeCap.Round),
                topLeft = Offset(inset, inset),
                size = Size(arcSize, arcSize),
            )

            // Filled progress arc
            val sweep = 360f * animatedProgress.coerceIn(0f, 1f)
            if (sweep > 0f) {
                drawArc(
                    color = progressColor,
                    startAngle = -90f,
                    sweepAngle = sweep,
                    useCenter = false,
                    style = Stroke(width = strokePx, cap = StrokeCap.Round),
                    topLeft = Offset(inset, inset),
                    size = Size(arcSize, arcSize),
                )
            }
        }
        centerContent()
    }
}
