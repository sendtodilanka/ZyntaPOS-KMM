package com.zyntasolutions.zyntapos.designsystem.layouts

import androidx.compose.animation.*
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.zyntasolutions.zyntapos.designsystem.util.WindowSize
import com.zyntasolutions.zyntapos.designsystem.util.currentWindowSize

// ─────────────────────────────────────────────────────────────────────────────
// ZyntaListDetailLayout — Master list + detail pane (EXPANDED); single-pane on COMPACT
//
// Per PLAN_PHASE1.md Sprint 9–10 Step 6.3.4:
//   EXPANDED → Shows master list on left + detail on right simultaneously.
//              Uses configurable [listWeight] / [detailWeight] split (default 35/65).
//   MEDIUM   → Same two-pane layout as EXPANDED (tablet landscape).
//   COMPACT  → Single-pane: shows either the list or the detail based on
//              [detailVisible]. Caller drives navigation between the two panes.
//
// The layout is navigation-agnostic: the caller owns the "selected item" state
// and provides both [listContent] and [detailContent] as composable lambdas.
// On COMPACT with [detailVisible]=true, [detailContent] is shown; otherwise
// [listContent] is shown.
// ─────────────────────────────────────────────────────────────────────────────

/** Divider width between list and detail panes. */
private val PaneDividerWidth = 1.dp

/**
 * Adaptive master–detail layout for item-based screens (orders, inventory, customers).
 *
 * **Two-pane (MEDIUM/EXPANDED)**:
 * ```
 * ┌──────────────────────────────────────────┐
 * │   List pane (35%)  │   Detail pane (65%) │
 * │   [listContent]    │   [detailContent]   │
 * └──────────────────────────────────────────┘
 * ```
 *
 * **Single-pane (COMPACT)**:
 * - When [detailVisible] = `false` → renders [listContent] full-screen.
 * - When [detailVisible] = `true`  → renders [detailContent] full-screen.
 *
 * ```kotlin
 * ZyntaListDetailLayout(
 *     detailVisible  = selectedOrder != null,
 *     listContent    = { OrderList(orders, onSelect = { selectedOrder = it }) },
 *     detailContent  = { OrderDetail(selectedOrder, onBack = { selectedOrder = null }) },
 * )
 * ```
 *
 * @param listContent Composable for the master list pane.
 * @param detailContent Composable for the detail pane. Receives a [BoxScope].
 * @param modifier Optional root [Modifier].
 * @param detailVisible On COMPACT, drives which single pane is visible.
 *        Has no effect on MEDIUM/EXPANDED (both panes always shown).
 * @param listWeight Width weight of the list pane in two-pane mode (default 0.35f).
 * @param showDivider Whether to show a 1dp divider between panes in two-pane mode.
 * @param windowSize Override the detected [WindowSize]; useful in previews/tests.
 */
@Composable
fun ZyntaListDetailLayout(
    listContent: @Composable () -> Unit,
    detailContent: @Composable BoxScope.() -> Unit,
    modifier: Modifier = Modifier,
    detailVisible: Boolean = false,
    listWeight: Float = 0.35f,
    showDivider: Boolean = true,
    windowSize: WindowSize = currentWindowSize(),
) {
    require(listWeight in 0.1f..0.9f) {
        "listWeight must be between 0.1 and 0.9, got $listWeight"
    }

    val detailWeight = 1f - listWeight
    val isTwoPane = windowSize != WindowSize.COMPACT

    if (isTwoPane) {
        // ── Two-pane (MEDIUM / EXPANDED) ─────────────────────────────────────
        Row(modifier = modifier.fillMaxSize()) {
            // List pane
            Box(
                modifier = Modifier
                    .weight(listWeight)
                    .fillMaxHeight(),
            ) {
                listContent()
            }

            // Divider
            if (showDivider) {
                androidx.compose.material3.HorizontalDivider(
                    modifier = Modifier
                        .fillMaxHeight()
                        .width(PaneDividerWidth),
                    thickness = PaneDividerWidth,
                )
            }

            // Detail pane
            Box(
                modifier = Modifier
                    .weight(detailWeight)
                    .fillMaxHeight(),
                content = detailContent,
            )
        }
    } else {
        // ── Single-pane (COMPACT) ─────────────────────────────────────────────
        Box(modifier = modifier.fillMaxSize()) {
            // Animate between list and detail — slide transition for natural feel
            AnimatedContent(
                targetState = detailVisible,
                transitionSpec = {
                    if (targetState) {
                        // Navigate to detail: slide in from right
                        slideInHorizontally { width -> width } + fadeIn() togetherWith
                            slideOutHorizontally { width -> -width } + fadeOut()
                    } else {
                        // Back to list: slide in from left
                        slideInHorizontally { width -> -width } + fadeIn() togetherWith
                            slideOutHorizontally { width -> width } + fadeOut()
                    }
                },
                label = "ZyntaListDetailTransition",
            ) { showDetail ->
                if (showDetail) {
                    Box(modifier = Modifier.fillMaxSize(), content = detailContent)
                } else {
                    listContent()
                }
            }
        }
    }
}
