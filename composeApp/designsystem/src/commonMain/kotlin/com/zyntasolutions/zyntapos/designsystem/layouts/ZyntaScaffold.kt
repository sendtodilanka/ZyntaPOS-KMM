package com.zyntasolutions.zyntapos.designsystem.layouts

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.zyntasolutions.zyntapos.designsystem.util.WindowSize
import com.zyntasolutions.zyntapos.designsystem.util.currentWindowSize

/**
 * A labelled section within [ZyntaScaffold]'s expanded navigation drawer.
 *
 * @param title  Section header shown above the first item in this group.
 * @param startIndex  Index (within the full `items` list) of the first item in this group.
 * @param itemCount   Number of consecutive items belonging to this group.
 */
data class ZyntaNavGroup(
    val title: String,
    val startIndex: Int,
    val itemCount: Int,
)

// ─────────────────────────────────────────────────────────────────────────────
// ZyntaScaffold — Adaptive navigation container
//
// Per UI/UX §2.1 & PLAN_PHASE1.md Sprint 9–10 Step 6.3.1:
//   COMPACT   → NavigationBar  (bottom, Material 3 standard)
//   MEDIUM    → NavigationRail (left, 72dp, icons + labels)
//   EXPANDED  → PermanentNavigationDrawer (240dp, icons + full labels)
//
// Usage:
//   ZyntaScaffold(
//       items = navItems,
//       selectedIndex = currentIndex,
//       onItemSelected = { index -> /* navigate */ },
//   ) { innerPadding -> ScreenContent(Modifier.padding(innerPadding)) }
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Descriptor for a single navigation destination in [ZyntaScaffold].
 *
 * @param label Human-readable label shown in nav bar / rail / drawer.
 * @param icon Icon displayed in all navigation variants.
 * @param selectedIcon Icon used when this destination is selected (defaults to [icon]).
 * @param contentDescription Accessibility description for the icon.
 * @param children Optional child destinations shown as an expandable sub-list in the
 *   EXPANDED drawer.  Ignored in COMPACT (bottom bar) and MEDIUM (rail) variants.
 */
data class ZyntaNavItem(
    val label: String,
    val icon: ImageVector,
    val selectedIcon: ImageVector = icon,
    val contentDescription: String = label,
    val children: List<ZyntaNavChildItem> = emptyList(),
)

/**
 * Descriptor for a child navigation item nested under a [ZyntaNavItem] in the
 * EXPANDED hierarchical drawer.
 *
 * @param label Human-readable label for the child destination.
 * @param contentDescription Accessibility description (defaults to [label]).
 */
data class ZyntaNavChildItem(
    val label: String,
    val contentDescription: String = label,
)

/**
 * Adaptive scaffold that switches between three navigation modes based on
 * [WindowSize], wiring Material 3 navigation components to a single item list.
 *
 * The scaffold ensures content is never drawn behind the navigation chrome by
 * automatically applying the correct [PaddingValues] inside [content].
 *
 * ### Tiered navigation
 * To avoid bottom-bar overflow on small screens, supply a shorter list via
 * [compactItems] (recommended max 5 items).  When non-empty it is used
 * **exclusively** for the COMPACT `NavigationBar`; the full [items] list is
 * still used for the MEDIUM rail and EXPANDED drawer.
 *
 * [compactSelectedIndex] must be the pre-computed selection position within
 * [compactItems].  When [compactItems] is empty it falls back to [selectedIndex].
 *
 * The EXPANDED drawer can optionally render labelled section headers by
 * providing [groups].  Each [ZyntaNavGroup] spans a contiguous range of [items].
 *
 * @param items Full navigation destinations — used for MEDIUM rail & EXPANDED drawer.
 * @param selectedIndex Selected index within [items].
 * @param onItemSelected Callback with the tapped index within [items].
 * @param modifier Optional root [Modifier].
 * @param compactItems Subset shown in the COMPACT bottom bar (max 5). Empty = use [items].
 * @param compactSelectedIndex Selected index within [compactItems].
 * @param onCompactItemSelected Tap callback for [compactItems]. Null = use [onItemSelected].
 * @param groups Optional section groups rendered as headers in the EXPANDED drawer.
 * @param selectedChildIndex Selected child index within the active parent item (-1 = no child
 *   active). Only consumed by the EXPANDED drawer; ignored in COMPACT and MEDIUM variants.
 * @param onChildSelected Callback with the parent index and the tapped child index within that
 *   parent's [ZyntaNavItem.children] list.  Only fired from the EXPANDED drawer.
 * @param topBar Optional top app bar slot (only rendered on COMPACT/MEDIUM).
 * @param snackbarHost Snackbar host.
 * @param windowSize Override the detected [WindowSize]; useful in previews/tests.
 * @param content Screen content lambda receiving [PaddingValues] for safe insets.
 */
@Composable
fun ZyntaScaffold(
    items: List<ZyntaNavItem>,
    selectedIndex: Int,
    onItemSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
    compactItems: List<ZyntaNavItem> = emptyList(),
    compactSelectedIndex: Int = selectedIndex,
    onCompactItemSelected: ((Int) -> Unit)? = null,
    groups: List<ZyntaNavGroup> = emptyList(),
    selectedChildIndex: Int = -1,
    onChildSelected: (parentIndex: Int, childIndex: Int) -> Unit = { _, _ -> },
    topBar: @Composable () -> Unit = {},
    snackbarHost: @Composable () -> Unit = {},
    windowSize: WindowSize = currentWindowSize(),
    content: @Composable (PaddingValues) -> Unit,
) {
    val effectiveCompactItems = compactItems.ifEmpty { items }
    val effectiveCompactSelected = if (compactItems.isEmpty()) selectedIndex else compactSelectedIndex
    val effectiveCompactCallback = onCompactItemSelected ?: onItemSelected

    when (windowSize) {
        WindowSize.COMPACT -> CompactScaffold(
            items = effectiveCompactItems,
            selectedIndex = effectiveCompactSelected,
            onItemSelected = effectiveCompactCallback,
            modifier = modifier,
            topBar = topBar,
            snackbarHost = snackbarHost,
            content = content,
        )
        WindowSize.MEDIUM -> MediumScaffold(
            items = items,
            selectedIndex = selectedIndex,
            onItemSelected = onItemSelected,
            modifier = modifier,
            topBar = topBar,
            snackbarHost = snackbarHost,
            content = content,
        )
        WindowSize.EXPANDED -> ExpandedScaffold(
            items = items,
            selectedIndex = selectedIndex,
            onItemSelected = onItemSelected,
            modifier = modifier,
            groups = groups,
            selectedChildIndex = selectedChildIndex,
            onChildSelected = onChildSelected,
            content = content,
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// COMPACT — NavigationBar (bottom)
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun CompactScaffold(
    items: List<ZyntaNavItem>,
    selectedIndex: Int,
    onItemSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
    topBar: @Composable () -> Unit = {},
    snackbarHost: @Composable () -> Unit = {},
    content: @Composable (PaddingValues) -> Unit,
) {
    Scaffold(
        modifier = modifier,
        topBar = topBar,
        snackbarHost = snackbarHost,
        bottomBar = {
            NavigationBar {
                items.forEachIndexed { index, item ->
                    NavigationBarItem(
                        selected = index == selectedIndex,
                        onClick = { onItemSelected(index) },
                        icon = {
                            Icon(
                                imageVector = if (index == selectedIndex) item.selectedIcon else item.icon,
                                contentDescription = item.contentDescription,
                            )
                        },
                        label = { Text(item.label) },
                    )
                }
            }
        },
        content = content,
    )
}

// ─────────────────────────────────────────────────────────────────────────────
// MEDIUM — NavigationRail (left, 72dp)
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun MediumScaffold(
    items: List<ZyntaNavItem>,
    selectedIndex: Int,
    onItemSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
    topBar: @Composable () -> Unit = {},
    snackbarHost: @Composable () -> Unit = {},
    content: @Composable (PaddingValues) -> Unit,
) {
    Scaffold(
        modifier = modifier,
        topBar = topBar,
        snackbarHost = snackbarHost,
        content = { innerPadding ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
            ) {
                NavigationRail {
                    Spacer(Modifier.height(8.dp))
                    items.forEachIndexed { index, item ->
                        NavigationRailItem(
                            selected = index == selectedIndex,
                            onClick = { onItemSelected(index) },
                            icon = {
                                Icon(
                                    imageVector = if (index == selectedIndex) item.selectedIcon else item.icon,
                                    contentDescription = item.contentDescription,
                                )
                            },
                            label = { Text(item.label) },
                        )
                    }
                }
                // Content fills remaining width
                Box(modifier = Modifier.weight(1f)) {
                    content(PaddingValues())
                }
            }
        },
    )
}

// ─────────────────────────────────────────────────────────────────────────────
// EXPANDED — Hierarchical collapsible navigation drawer (260dp / 72dp mini)
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun ExpandedScaffold(
    items: List<ZyntaNavItem>,
    selectedIndex: Int,
    onItemSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
    groups: List<ZyntaNavGroup> = emptyList(),
    selectedChildIndex: Int = -1,
    onChildSelected: (parentIndex: Int, childIndex: Int) -> Unit = { _, _ -> },
    content: @Composable (PaddingValues) -> Unit,
) {
    ZyntaNavigationDrawer(
        items = items,
        selectedIndex = selectedIndex,
        onItemSelected = onItemSelected,
        modifier = modifier,
        selectedChildIndex = selectedChildIndex,
        onChildSelected = onChildSelected,
        groups = groups,
        content = content,
    )
}
