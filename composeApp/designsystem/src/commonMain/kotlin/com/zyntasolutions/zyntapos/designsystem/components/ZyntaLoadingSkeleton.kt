package com.zyntasolutions.zyntapos.designsystem.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.zyntasolutions.zyntapos.designsystem.tokens.ZyntaSpacing

// ─────────────────────────────────────────────────────────────────────────────
// ZyntaLoadingSkeleton — Shimmer-animated placeholder shapes that mirror
// the target layout. Non-blocking — chrome remains interactive.
// Show when data load exceeds 200ms (screen) or 300ms (refresh).
//
// Distinct from ZyntaLoadingOverlay which is a full-screen blocking scrim.
// ─────────────────────────────────────────────────────────────────────────────

/** Layout variant for [ZyntaLoadingSkeleton]. */
enum class SkeletonVariant {
    /** Card-shaped placeholder — 1 large rectangle. */
    Card,
    /** Row-shaped placeholder — icon circle + 2 text lines. */
    Row,
    /** Grid-shaped placeholder — 2×3 card grid. */
    Grid,
    /** Text-shaped placeholder — 3 stacked text lines of varying width. */
    Text,
}

/**
 * Shimmer-animated skeleton placeholder for loading states.
 *
 * Usage:
 * ```kotlin
 * if (isLoading) {
 *     ZyntaLoadingSkeleton(variant = SkeletonVariant.Grid)
 * } else {
 *     ActualContent()
 * }
 * ```
 *
 * @param variant Shape variant matching the expected content layout.
 * @param modifier Optional [Modifier].
 * @param itemCount Number of skeleton items for [SkeletonVariant.Row] (default 5).
 */
@Composable
fun ZyntaLoadingSkeleton(
    variant: SkeletonVariant = SkeletonVariant.Card,
    modifier: Modifier = Modifier,
    itemCount: Int = 5,
) {
    val shimmerBrush = rememberShimmerBrush()

    when (variant) {
        SkeletonVariant.Card -> {
            Column(modifier = modifier.padding(ZyntaSpacing.md), verticalArrangement = Arrangement.spacedBy(ZyntaSpacing.md)) {
                repeat(3) {
                    ShimmerBox(brush = shimmerBrush, height = 120.dp, modifier = Modifier.fillMaxWidth())
                }
            }
        }
        SkeletonVariant.Row -> {
            Column(modifier = modifier.padding(ZyntaSpacing.md), verticalArrangement = Arrangement.spacedBy(ZyntaSpacing.sm)) {
                repeat(itemCount) {
                    SkeletonRow(brush = shimmerBrush)
                }
            }
        }
        SkeletonVariant.Grid -> {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 140.dp),
                modifier = modifier.padding(ZyntaSpacing.md),
                horizontalArrangement = Arrangement.spacedBy(ZyntaSpacing.sm),
                verticalArrangement = Arrangement.spacedBy(ZyntaSpacing.sm),
                userScrollEnabled = false,
            ) {
                items(6) {
                    ShimmerBox(brush = shimmerBrush, height = 160.dp, modifier = Modifier.fillMaxWidth())
                }
            }
        }
        SkeletonVariant.Text -> {
            Column(modifier = modifier.padding(ZyntaSpacing.md), verticalArrangement = Arrangement.spacedBy(ZyntaSpacing.sm)) {
                ShimmerBox(brush = shimmerBrush, height = 16.dp, modifier = Modifier.fillMaxWidth())
                ShimmerBox(brush = shimmerBrush, height = 16.dp, modifier = Modifier.fillMaxWidth(0.85f))
                ShimmerBox(brush = shimmerBrush, height = 16.dp, modifier = Modifier.fillMaxWidth(0.6f))
            }
        }
    }
}

// ─── Internal composables ────────────────────────────────────────────────────

@Composable
private fun ShimmerBox(
    brush: Brush,
    height: Dp,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .height(height)
            .clip(RoundedCornerShape(ZyntaSpacing.sm))
            .background(brush),
    )
}

@Composable
private fun SkeletonRow(brush: Brush) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = ZyntaSpacing.xs),
        horizontalArrangement = Arrangement.spacedBy(ZyntaSpacing.sm),
    ) {
        // Circle placeholder (avatar/icon)
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(brush),
        )
        // Text lines
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(ZyntaSpacing.xs),
        ) {
            ShimmerBox(brush = brush, height = 14.dp, modifier = Modifier.fillMaxWidth(0.7f))
            ShimmerBox(brush = brush, height = 12.dp, modifier = Modifier.fillMaxWidth(0.5f))
        }
        // Trailing value
        ShimmerBox(brush = brush, height = 14.dp, modifier = Modifier.width(60.dp))
    }
}

@Composable
private fun rememberShimmerBrush(): Brush {
    val transition = rememberInfiniteTransition(label = "shimmer")
    val translateAnim by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1000f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "shimmer_translate",
    )
    val baseColor = MaterialTheme.colorScheme.surfaceVariant
    val highlightColor = MaterialTheme.colorScheme.surface
    return Brush.linearGradient(
        colors = listOf(baseColor, highlightColor, baseColor),
        start = Offset(translateAnim - 500f, 0f),
        end = Offset(translateAnim, 0f),
    )
}
