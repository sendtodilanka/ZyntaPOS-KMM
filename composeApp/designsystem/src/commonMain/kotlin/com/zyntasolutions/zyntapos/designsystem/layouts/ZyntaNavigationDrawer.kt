package com.zyntasolutions.zyntapos.designsystem.layouts

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.PointOfSale
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

// ─────────────────────────────────────────────────────────────────────────────
// LocalZyntaDrawerController — CompositionLocal for opening the modal drawer
//
// In the COMPACT layout a bottom sheet / modal drawer may be opened
// programmatically (e.g., from a Menu icon in the TopAppBar). This
// CompositionLocal provides the lambda that triggers that open action.
//
// For the EXPANDED permanent drawer this is always `null` — the drawer is
// always visible and cannot be "opened".
// ─────────────────────────────────────────────────────────────────────────────

/**
 * CompositionLocal providing a lambda to open the modal drawer (COMPACT only).
 * `null` in all other layout variants (MEDIUM rail, EXPANDED permanent drawer).
 */
val LocalZyntaDrawerController = staticCompositionLocalOf<(() -> Unit)?> { null }

// ─────────────────────────────────────────────────────────────────────────────
// ZyntaNavigationDrawer — Hierarchical collapsible permanent drawer
//
// Used exclusively by the EXPANDED layout variant (window width > 840 dp).
// COMPACT and MEDIUM variants remain unchanged (NavigationBar / NavigationRail).
//
// Layout:
//   Row(fillMaxSize) {
//     Surface(animatedWidth, fillMaxHeight, color = surfaceContainerLow) {
//       Column {
//         Header  — App icon + "ZyntaPOS" label (hidden in mini) + toggle button
//         LazyColumn — scrollable item list: group headers, parent rows, child rows
//         Footer  — reserved for Phase 2 user-info tile
//       }
//     }
//     Box(weight 1f) — screen content wrapped in a Scaffold
//   }
//
// Dimensions:
//   Full width  = 260 dp
//   Mini width  = 72 dp   (icons only, labels hidden)
//   Expand/collapse animation = tween(200 ms)
//
// Selection model:
//   [selectedIndex]      — index in [items] for the active top-level parent
//   [selectedChildIndex] — index within the active parent's children (-1 = none)
//
// Expand state:
//   Persisted per parent index via rememberSaveable + SnapshotStateMap so that
//   the open/closed state survives recomposition but resets on process death.
//   On first composition the active parent is auto-expanded.
// ─────────────────────────────────────────────────────────────────────────────

private val DrawerFullWidth = 260.dp
private val DrawerMiniWidth = 72.dp
private val DrawerAnimationMs = 200
private val ChildStartBorderWidth = 4.dp
private val ChildExtraStartPadding = 16.dp

/**
 * Hierarchical collapsible permanent navigation drawer for the EXPANDED layout.
 *
 * Parent items with [ZyntaNavItem.children] render an expand/collapse chevron.
 * Child items are indented and show a primary-colour left border when active.
 *
 * The drawer can be collapsed to a 72 dp icon-only mini variant via the toggle
 * button in the header.
 *
 * @param items Full list of navigation destinations including any child items.
 * @param selectedIndex Active parent index within [items].
 * @param onItemSelected Callback with the tapped parent index.
 * @param modifier Optional root [Modifier].
 * @param selectedChildIndex Active child index within the selected parent (-1 = none).
 * @param onChildSelected Callback with (parentIndex, childIndex) when a child is tapped.
 * @param groups Optional section-header descriptors for the scrollable item area.
 * @param content Screen content composable receiving inner [PaddingValues].
 */
@Composable
fun ZyntaNavigationDrawer(
    items: List<ZyntaNavItem>,
    selectedIndex: Int,
    onItemSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
    selectedChildIndex: Int = -1,
    onChildSelected: (parentIndex: Int, childIndex: Int) -> Unit = { _, _ -> },
    groups: List<ZyntaNavGroup> = emptyList(),
    content: @Composable (PaddingValues) -> Unit,
) {
    // ── Collapse / expand toggle state ────────────────────────────────────────
    var isMini by rememberSaveable { mutableStateOf(false) }

    val drawerWidth by animateDpAsState(
        targetValue = if (isMini) DrawerMiniWidth else DrawerFullWidth,
        animationSpec = tween(durationMillis = DrawerAnimationMs),
        label = "DrawerWidth",
    )

    // ── Parent expand-state map (index → expanded) ────────────────────────────
    // Persisted across recompositions via SnapshotStateMap.
    // The active parent is auto-expanded on first composition and whenever
    // selectedIndex changes (e.g., the user navigates to a new section).
    val expandedState = remember {
        mutableStateMapOf<Int, Boolean>().also { map ->
            if (selectedIndex >= 0) map[selectedIndex] = true
        }
    }

    // Auto-expand the newly selected parent whenever selectedIndex changes
    LaunchedEffect(selectedIndex) {
        if (selectedIndex >= 0 && items.getOrNull(selectedIndex)?.children?.isNotEmpty() == true) {
            expandedState[selectedIndex] = true
        }
    }

    // Build group-header index map once
    val groupHeaderAt: Map<Int, String> = remember(groups) {
        if (groups.isNotEmpty()) buildMap { groups.forEach { g -> put(g.startIndex, g.title) } }
        else emptyMap()
    }

    // Provide null for LocalZyntaDrawerController — permanent drawer is always visible
    CompositionLocalProvider(LocalZyntaDrawerController provides null) {
        Row(modifier = modifier.fillMaxSize()) {

            // ── Drawer surface ─────────────────────────────────────────────────
            Surface(
                modifier = Modifier
                    .width(drawerWidth)
                    .fillMaxHeight(),
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                tonalElevation = 0.dp,
            ) {
                Column(modifier = Modifier.fillMaxHeight()) {

                    // ── Header ─────────────────────────────────────────────────
                    DrawerHeader(
                        isMini = isMini,
                        onToggle = { isMini = !isMini },
                    )

                    // ── Scrollable item list ───────────────────────────────────
                    LazyColumn(modifier = Modifier.weight(1f)) {
                        itemsIndexed(items) { index, item ->
                            val isParentSelected = index == selectedIndex
                            val isExpanded = expandedState[index] ?: false
                            val hasChildren = item.children.isNotEmpty()

                            // Section group header
                            groupHeaderAt[index]?.let { title ->
                                if (index > 0) {
                                    HorizontalDivider(
                                        modifier = Modifier.padding(
                                            start = 16.dp, end = 16.dp, top = 8.dp, bottom = 4.dp,
                                        ),
                                    )
                                }
                                if (!isMini) {
                                    Text(
                                        text = title.uppercase(),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(
                                            start = 28.dp, bottom = 4.dp, top = 4.dp,
                                        ),
                                    )
                                }
                            }

                            // Parent item row
                            ParentDrawerItem(
                                item = item,
                                index = index,
                                isSelected = isParentSelected && selectedChildIndex < 0,
                                isExpanded = isExpanded,
                                isMini = isMini,
                                hasChildren = hasChildren,
                                onItemSelected = { idx ->
                                    onItemSelected(idx)
                                    if (hasChildren) {
                                        expandedState[idx] = !(expandedState[idx] ?: false)
                                    }
                                },
                            )

                            // Child item rows (visible only when expanded and not in mini mode)
                            if (hasChildren && isExpanded && !isMini) {
                                item.children.forEachIndexed { childIndex, child ->
                                    val isChildSelected =
                                        isParentSelected && selectedChildIndex == childIndex
                                    ChildDrawerItem(
                                        child = child,
                                        isSelected = isChildSelected,
                                        onClick = { onChildSelected(index, childIndex) },
                                    )
                                }
                            }
                        }
                    }

                    // ── Footer (Phase 2: user info tile) ──────────────────────
                    Spacer(modifier = Modifier.height(16.dp))
                }
            }

            // ── Content area ───────────────────────────────────────────────────
            Box(modifier = Modifier.weight(1f)) {
                Scaffold { innerPadding ->
                    content(innerPadding)
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Internal composables
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Drawer header containing the app icon, app name label, and the toggle button.
 *
 * In mini mode the app name text is hidden and the icon is centred.
 */
@Composable
private fun DrawerHeader(
    isMini: Boolean,
    onToggle: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(64.dp)
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (!isMini) {
            Icon(
                imageVector = Icons.Filled.PointOfSale,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .size(24.dp)
                    .padding(start = 4.dp),
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "ZyntaPOS",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
        } else {
            Spacer(modifier = Modifier.weight(1f))
        }
        IconButton(onClick = onToggle) {
            Icon(
                imageVector = Icons.Filled.Menu,
                contentDescription = if (isMini) "Expand navigation drawer" else "Collapse navigation drawer",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * A parent-level drawer item row.
 *
 * When the item has children a chevron icon is rendered on the trailing end and
 * rotates 90° when the section is expanded.  In mini mode the label and chevron
 * are hidden and the icon is centred.
 *
 * Selected state (no child active):
 *   The [NavigationDrawerItem] renders with a 4 dp primary-colour left border.
 */
@Composable
private fun ParentDrawerItem(
    item: ZyntaNavItem,
    index: Int,
    isSelected: Boolean,
    isExpanded: Boolean,
    isMini: Boolean,
    hasChildren: Boolean,
    onItemSelected: (Int) -> Unit,
) {
    val chevronRotation by animateFloatAsState(
        targetValue = if (isExpanded) 90f else 0f,
        animationSpec = tween(durationMillis = DrawerAnimationMs),
        label = "ChevronRotation[$index]",
    )

    // Left border highlight for selected parent (no active child)
    val leftBorderModifier = if (isSelected) {
        Modifier.background(
            color = MaterialTheme.colorScheme.primary,
        )
    } else {
        Modifier
    }

    Row(modifier = Modifier.fillMaxWidth()) {
        // 4 dp primary colour left border strip for selected parent
        Box(
            modifier = Modifier
                .width(ChildStartBorderWidth)
                .height(48.dp)
                .then(leftBorderModifier),
        )
        NavigationDrawerItem(
            selected = isSelected,
            onClick = { onItemSelected(index) },
            icon = {
                Icon(
                    imageVector = if (isSelected) item.selectedIcon else item.icon,
                    contentDescription = item.contentDescription,
                )
            },
            label = if (!isMini) {
                { Text(item.label) }
            } else {
                null
            },
            badge = if (hasChildren && !isMini) {
                {
                    Icon(
                        imageVector = Icons.Filled.ChevronRight,
                        contentDescription = null,
                        modifier = Modifier.rotate(chevronRotation),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                null
            },
            modifier = Modifier
                .weight(1f)
                .padding(NavigationDrawerItemDefaults.ItemPadding),
        )
    }
}

/**
 * A child-level drawer item row, always indented under its parent.
 *
 * Selected state:
 *   - 4 dp primary-colour left border strip
 *   - Row background set to [MaterialTheme.colorScheme.primaryContainer]
 */
@Composable
private fun ChildDrawerItem(
    child: ZyntaNavChildItem,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    val rowBackground = if (isSelected) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        Color.Transparent
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(rowBackground)
            .clickable(onClick = onClick)
            .padding(end = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // 4 dp left border strip
        Box(
            modifier = Modifier
                .width(ChildStartBorderWidth)
                .height(40.dp)
                .background(
                    color = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent,
                ),
        )
        // Extra indent so the child label is visually nested under the parent icon
        Spacer(modifier = Modifier.width(ChildExtraStartPadding + 40.dp)) // 40 dp ≈ icon width
        Text(
            text = child.label,
            style = MaterialTheme.typography.bodyMedium,
            color = if (isSelected) {
                MaterialTheme.colorScheme.onPrimaryContainer
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
            modifier = Modifier
                .weight(1f)
                .padding(vertical = 10.dp),
        )
    }
}
