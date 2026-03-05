package com.zyntasolutions.zyntapos.designsystem.layouts

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.zyntasolutions.zyntapos.designsystem.util.WindowSize
import com.zyntasolutions.zyntapos.designsystem.util.currentWindowSize

// ─────────────────────────────────────────────────────────────────────────────
// ZyntaSplitPane — Horizontal two-pane layout with configurable weight ratio
//
// Per PLAN_PHASE1.md Sprint 9–10 Step 6.3.2:
//   • Default split is 40% primary / 60% secondary (configurable via [primaryWeight])
//   • collapsible=true collapses the secondary pane on COMPACT — primary fills full width
//   • A 1dp divider line separates the two panes when both are visible
//   • windowSize override supports previews and tests
// ─────────────────────────────────────────────────────────────────────────────

/** Minimum primary pane width. Prevents primary from becoming unusably narrow. */
private val _PrimaryMinWidth = 200.dp

/** Width of the visual divider between panes. */
private val DividerThickness: Dp = 1.dp

/**
 * Horizontal two-pane split layout used for master-detail and POS checkout screens.
 *
 * When [collapsible] is `true` and the window is [WindowSize.COMPACT], the
 * secondary pane is hidden and the primary pane fills the entire width — which
 * enables single-pane mode for small screens without requiring a different route.
 *
 * ```kotlin
 * ZyntaSplitPane(
 *     primaryContent  = { ProductListPane() },
 *     secondaryContent = { CartPane() },
 *     primaryWeight   = 0.4f,
 *     collapsible     = true,
 * )
 * ```
 *
 * @param primaryContent Left/primary pane composable (e.g., product list).
 * @param secondaryContent Right/secondary pane composable (e.g., cart / detail).
 * @param modifier Optional root [Modifier].
 * @param primaryWeight Weight of the primary pane [0f–1f]; default 0.4f (40%).
 *        The secondary pane receives `1f - primaryWeight`.
 * @param collapsible When `true`, hides the secondary pane on [WindowSize.COMPACT].
 * @param showDivider When `true` (default), renders a 1dp divider between panes.
 * @param windowSize Override the detected [WindowSize]; useful in previews/tests.
 */
@Composable
fun ZyntaSplitPane(
    primaryContent: @Composable BoxScope.() -> Unit,
    secondaryContent: @Composable BoxScope.() -> Unit,
    modifier: Modifier = Modifier,
    primaryWeight: Float = 0.4f,
    collapsible: Boolean = true,
    showDivider: Boolean = true,
    windowSize: WindowSize = currentWindowSize(),
) {
    require(primaryWeight in 0.01f..0.99f) {
        "primaryWeight must be between 0.01 and 0.99, got $primaryWeight"
    }

    val secondaryWeight = 1f - primaryWeight
    val isCompact = windowSize == WindowSize.COMPACT
    val showSecondary = !(collapsible && isCompact)

    Row(modifier = modifier.fillMaxSize()) {
        // ── Primary pane ───────────────────────────────────────────────────
        Box(
            modifier = Modifier
                .weight(if (showSecondary) primaryWeight else 1f)
                .fillMaxHeight(),
            content = primaryContent,
        )

        // ── Divider ────────────────────────────────────────────────────────
        if (showDivider && showSecondary) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(DividerThickness)
                    .background(MaterialTheme.colorScheme.outlineVariant),
            )
        }

        // ── Secondary pane ─────────────────────────────────────────────────
        AnimatedVisibility(
            visible = showSecondary,
            enter = expandHorizontally(),
            exit = shrinkHorizontally(),
        ) {
            Box(
                modifier = Modifier
                    .weight(secondaryWeight)
                    .fillMaxHeight(),
                content = secondaryContent,
            )
        }
    }
}
