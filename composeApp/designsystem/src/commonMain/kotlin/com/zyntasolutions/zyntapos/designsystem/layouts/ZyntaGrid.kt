package com.zyntasolutions.zyntapos.designsystem.layouts

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.zyntasolutions.zyntapos.designsystem.tokens.ZyntaSpacing
import com.zyntasolutions.zyntapos.designsystem.util.WindowSize
import com.zyntasolutions.zyntapos.designsystem.util.currentWindowSize

// ─────────────────────────────────────────────────────────────────────────────
// ZyntaGrid — LazyVerticalGrid with WindowSizeClass-driven column count
//
// Per UI/UX §2.3 Product Grid Column Rules & PLAN_PHASE1.md Sprint 9–10 Step 6.3.3:
//   COMPACT  → 2 columns  (min card width ~140dp)
//   MEDIUM   → 3–4 columns (min card width ~150dp); uses adaptive for fluid layout
//   EXPANDED → 4–6 columns (min card width ~160dp); uses adaptive for fluid layout
//
// The [key] parameter is mandatory for stable recomposition (prevents full-grid
// redraws when only individual items change — critical for 200ms barcode scan SLA).
// ─────────────────────────────────────────────────────────────────────────────

// ── Column count constants per WindowSize ─────────────────────────────────────

/** Fixed column count for COMPACT windows. */
private const val CompactColumns = 2

/**
 * Minimum adaptive card width for MEDIUM windows.
 * Results in 3–4 columns depending on actual screen width within 600–840dp.
 */
private val MediumMinItemWidth: Dp = 150.dp

/**
 * Minimum adaptive card width for EXPANDED windows.
 * Results in 4–6 columns depending on actual screen width > 840dp.
 */
private val ExpandedMinItemWidth: Dp = 160.dp

/**
 * Responsive lazy vertical grid that automatically adjusts its column count
 * based on [WindowSize].
 *
 * The grid uses `GridCells.Fixed(2)` on COMPACT for a predictable two-column
 * layout, and `GridCells.Adaptive` on MEDIUM/EXPANDED to let Material 3
 * calculate the optimal count given the available width.
 *
 * **Stable keys are required** — pass a unique identifier per item via [key]
 * to prevent full-grid recomposition on every state change.
 *
 * ```kotlin
 * ZyntaGrid(
 *     items = products,
 *     key   = { it.id },
 * ) { product ->
 *     ZyntaProductCard(product = product)
 * }
 * ```
 *
 * @param T The type of each data item.
 * @param items List of items to render.
 * @param key Stable key extractor — **required** for efficient recomposition.
 * @param modifier Optional root [Modifier].
 * @param contentPadding Padding around the full grid content area.
 * @param verticalArrangement Vertical spacing between rows.
 * @param horizontalArrangement Horizontal spacing between columns.
 * @param windowSize Override the detected [WindowSize]; useful in previews/tests.
 * @param header Optional sticky header composable placed before item rows.
 * @param itemContent Composable slot for each item.
 */
@Composable
fun <T : Any> ZyntaGrid(
    items: List<T>,
    key: (T) -> Any,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(ZyntaSpacing.md),
    verticalArrangement: Arrangement.Vertical = Arrangement.spacedBy(ZyntaSpacing.sm),
    horizontalArrangement: Arrangement.Horizontal = Arrangement.spacedBy(ZyntaSpacing.sm),
    windowSize: WindowSize = currentWindowSize(),
    header: (LazyGridScope.() -> Unit)? = null,
    itemContent: @Composable LazyGridItemScope.(item: T) -> Unit,
) {
    val columns: GridCells = when (windowSize) {
        WindowSize.COMPACT -> GridCells.Fixed(CompactColumns)
        WindowSize.MEDIUM -> GridCells.Adaptive(minSize = MediumMinItemWidth)
        WindowSize.EXPANDED -> GridCells.Adaptive(minSize = ExpandedMinItemWidth)
    }

    LazyVerticalGrid(
        columns = columns,
        modifier = modifier,
        contentPadding = contentPadding,
        verticalArrangement = verticalArrangement,
        horizontalArrangement = horizontalArrangement,
    ) {
        // Optional sticky header (e.g., category chips row)
        header?.invoke(this)

        items(
            items = items,
            key = { item -> key(item) },
            itemContent = itemContent,
        )
    }
}

