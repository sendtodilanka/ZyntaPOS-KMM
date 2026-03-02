package com.zyntasolutions.zyntapos.designsystem.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight

// ─────────────────────────────────────────────────────────────────────────────
// AnimatedCounter — Smooth number counter with FastOutSlowIn easing
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Animated number counter that counts up from 0 to [targetValue] on first
 * composition, then re-animates whenever [targetValue] changes.
 *
 * Uses [FastOutSlowInEasing] for a snappy, professional feel.
 *
 * @param targetValue Numeric value to animate towards.
 * @param modifier Optional [Modifier].
 * @param durationMillis Animation duration (default 800 ms).
 * @param delayMillis Delay before animation starts (default 0 ms).
 * @param style Text style for the rendered value.
 * @param fontWeight Font weight override.
 * @param color Text color.
 * @param formatter Maps the current animated [Float] to its display [String].
 *   Defaults to truncating to a whole integer.
 */
@Composable
fun AnimatedCounter(
    targetValue: Float,
    modifier: Modifier = Modifier,
    durationMillis: Int = 800,
    delayMillis: Int = 0,
    style: TextStyle = MaterialTheme.typography.headlineSmall,
    fontWeight: FontWeight = FontWeight.Bold,
    color: Color = MaterialTheme.colorScheme.onSurface,
    formatter: (Float) -> String = { it.toLong().toString() },
) {
    var animationTarget by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(targetValue) { animationTarget = targetValue }

    val animatedValue by animateFloatAsState(
        targetValue = animationTarget,
        animationSpec = tween(
            durationMillis = durationMillis,
            delayMillis = delayMillis,
            easing = FastOutSlowInEasing,
        ),
        label = "animated-counter",
    )

    Text(
        text = formatter(animatedValue),
        style = style,
        fontWeight = fontWeight,
        color = color,
        modifier = modifier,
    )
}
