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
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import com.zyntasolutions.zyntapos.designsystem.components.LocalDrawerAvailableStores
import com.zyntasolutions.zyntapos.designsystem.components.LocalDrawerCurrentStore
import com.zyntasolutions.zyntapos.designsystem.components.LocalDrawerOnStoreSelected
import com.zyntasolutions.zyntapos.designsystem.components.SyncDisplayStatus
import com.zyntasolutions.zyntapos.designsystem.components.ZyntaStoreSelector
import com.zyntasolutions.zyntapos.designsystem.components.ZyntaSyncStatusIndicator
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

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
 * `null` in all other layout variants (MEDIUM mini drawer, EXPANDED permanent drawer).
 */
val LocalZyntaDrawerController = staticCompositionLocalOf<(() -> Unit)?> { null }

// ─────────────────────────────────────────────────────────────────────────────
// ZyntaNavigationDrawer — Hierarchical collapsible permanent drawer
//
// Used by the EXPANDED layout variant (window width > 840 dp) and by
// the MEDIUM layout variant with [startMini] = true (72 dp icons-only mode
// that can be expanded by the user).
//
// Layout:
//   Row(fillMaxSize) {
//     Surface(animatedWidth, fillMaxHeight, color = surfaceContainerLow) {
//       DrawerColumnContent — Header + LazyColumn items + DrawerFooter
//     }
//     Box(weight 1f) — screen content wrapped in a Scaffold
//   }
//
// Dimensions:
//   Full width  = 260 dp
//   Mini width  = 72 dp   (icons only, labels hidden)
//   Expand/collapse animation = tween(200 ms)
// ─────────────────────────────────────────────────────────────────────────────

private val DrawerFullWidth = 260.dp
private val DrawerMiniWidth = 72.dp
private const val DrawerAnimationMs = 200
private val ChildStartBorderWidth = 4.dp
private val ChildExtraStartPadding = 16.dp

/**
 * Hierarchical collapsible permanent navigation drawer for EXPANDED and MEDIUM layouts.
 *
 * Parent items with [ZyntaNavItem.children] render an expand/collapse chevron.
 * Child items are indented and show a primary-colour left border when active.
 *
 * The drawer can be collapsed to a 72 dp icon-only mini variant via the toggle
 * button in the header. Pass [startMini] = true to begin in the collapsed state
 * (used by the MEDIUM layout).
 *
 * @param items Full list of navigation destinations including any child items.
 * @param selectedIndex Active parent index within [items].
 * @param onItemSelected Callback with the tapped parent index.
 * @param modifier Optional root [Modifier].
 * @param startMini When true the drawer starts in 72 dp mini mode (MEDIUM layout default).
 * @param selectedChildIndex Active child index within the selected parent (-1 = none).
 * @param onChildSelected Callback with (parentIndex, childIndex) when a child is tapped.
 * @param groups Optional section-header descriptors for the scrollable item area.
 * @param drawerUserName Full name of the logged-in user for the footer tile.
 * @param drawerUserInitials Pre-computed initials (e.g. "JS") for the footer avatar.
 * @param drawerUserRole Human-readable role label (e.g. "Admin") for the footer tile.
 * @param content Screen content composable receiving inner [PaddingValues].
 */
@Composable
fun ZyntaNavigationDrawer(
    items: List<ZyntaNavItem>,
    selectedIndex: Int,
    onItemSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
    startMini: Boolean = false,
    selectedChildIndex: Int = -1,
    onChildSelected: (parentIndex: Int, childIndex: Int) -> Unit = { _, _ -> },
    groups: List<ZyntaNavGroup> = emptyList(),
    drawerUserName: String? = null,
    drawerUserInitials: String? = null,
    drawerUserRole: String? = null,
    syncStatus: SyncDisplayStatus? = null,
    syncPendingCount: Int = 0,
    content: @Composable (PaddingValues) -> Unit,
) {
    // ── Collapse / expand toggle state ────────────────────────────────────────
    var isMini by rememberSaveable { mutableStateOf(startMini) }

    val drawerWidth by animateDpAsState(
        targetValue = if (isMini) DrawerMiniWidth else DrawerFullWidth,
        animationSpec = tween(durationMillis = DrawerAnimationMs),
        label = "DrawerWidth",
    )

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
                DrawerColumnContent(
                    items = items,
                    selectedIndex = selectedIndex,
                    onItemSelected = onItemSelected,
                    selectedChildIndex = selectedChildIndex,
                    onChildSelected = onChildSelected,
                    groups = groups,
                    isMini = isMini,
                    onToggleMini = { isMini = !isMini },
                    drawerUserName = drawerUserName,
                    drawerUserInitials = drawerUserInitials,
                    drawerUserRole = drawerUserRole,
                    syncStatus = syncStatus,
                    syncPendingCount = syncPendingCount,
                )
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
// DrawerColumnContent — shared drawer body (header + items + footer)
//
// Extracted as `internal` so that CompactScaffold in ZyntaScaffold.kt can
// reuse the same rendering logic inside a ModalDrawerSheet without duplication.
//
// The permanent drawer (ZyntaNavigationDrawer) provides onToggleMini to show
// the collapse/expand toggle button.  The modal drawer (CompactScaffold) passes
// null so no toggle button is rendered.
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Internal composable that renders the full drawer column: header, scrollable
 * nav item list, and sticky footer.
 *
 * Used by both [ZyntaNavigationDrawer] (permanent, EXPANDED/MEDIUM) and the
 * modal [ModalDrawerSheet] (COMPACT). Pass `onToggleMini = null` when there is
 * no mini/full toggle (i.e., the modal drawer is always full-width when visible).
 */
@Composable
internal fun DrawerColumnContent(
    items: List<ZyntaNavItem>,
    selectedIndex: Int,
    onItemSelected: (Int) -> Unit,
    selectedChildIndex: Int = -1,
    onChildSelected: (parentIndex: Int, childIndex: Int) -> Unit = { _, _ -> },
    groups: List<ZyntaNavGroup> = emptyList(),
    isMini: Boolean = false,
    onToggleMini: (() -> Unit)? = null,
    drawerUserName: String? = null,
    drawerUserInitials: String? = null,
    drawerUserRole: String? = null,
    syncStatus: SyncDisplayStatus? = null,
    syncPendingCount: Int = 0,
) {
    // ── Mini-mode hover popout state ─────────────────────────────────────────
    var hoveredMiniIndex by remember { mutableStateOf(-1) }
    var dismissJob: Job? by remember { mutableStateOf(null) }
    val popoutScope = rememberCoroutineScope()

    // ── Parent expand-state map (index → expanded) ────────────────────────────
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

    Column(modifier = Modifier.fillMaxHeight()) {

        // ── Header ─────────────────────────────────────────────────────────────
        DrawerHeader(
            isMini = isMini,
            onToggle = onToggleMini,
        )

        // ── Scrollable item list ───────────────────────────────────────────────
        LazyColumn(modifier = Modifier.weight(1f)) {
            itemsIndexed(items) { index, item ->
                val isParentSelected = index == selectedIndex
                val isExpanded = expandedState[index] ?: false
                val hasChildren = item.children.isNotEmpty()

                Box(
                    modifier = if (isMini) {
                        Modifier.pointerInput(index) {
                            awaitPointerEventScope {
                                while (true) {
                                    val event = awaitPointerEvent()
                                    when (event.type) {
                                        PointerEventType.Enter -> {
                                            dismissJob?.cancel()
                                            hoveredMiniIndex = index
                                        }
                                        PointerEventType.Exit -> {
                                            dismissJob = popoutScope.launch {
                                                delay(150)
                                                if (hoveredMiniIndex == index) hoveredMiniIndex = -1
                                            }
                                        }
                                        else -> {}
                                    }
                                }
                            }
                        }
                    } else Modifier,
                ) {

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

                // Mini-mode hover popout
                if (isMini && hoveredMiniIndex == index) {
                    MiniItemPopout(
                        item = item,
                        selectedChildIndex = if (isParentSelected) selectedChildIndex else -1,
                        onItemSelected = {
                            dismissJob?.cancel()
                            hoveredMiniIndex = -1
                            onItemSelected(index)
                        },
                        onChildSelected = { childIdx ->
                            dismissJob?.cancel()
                            hoveredMiniIndex = -1
                            onChildSelected(index, childIdx)
                        },
                        onHoverEnter = { dismissJob?.cancel() },
                        onHoverExit = {
                            dismissJob = popoutScope.launch {
                                delay(100)
                                hoveredMiniIndex = -1
                            }
                        },
                    )
                }
                } // end Box
            }
        }

        // ── Sticky footer — user info tile + sync status ──────────────────────
        DrawerFooter(
            isMini = isMini,
            userName = drawerUserName,
            userInitials = drawerUserInitials,
            userRole = drawerUserRole,
            syncStatus = syncStatus,
            syncPendingCount = syncPendingCount,
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Internal composables
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Drawer header containing the app icon, app name label, and the optional toggle button.
 *
 * In mini mode the app name text is hidden and the icon is centred.
 * When [onToggle] is null (modal drawer) the toggle button is omitted entirely.
 */
@Composable
private fun DrawerHeader(
    isMini: Boolean,
    onToggle: (() -> Unit)?,
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
        if (onToggle != null) {
            IconButton(onClick = onToggle) {
                Icon(
                    imageVector = Icons.Filled.Menu,
                    contentDescription = if (isMini) "Expand navigation drawer" else "Collapse navigation drawer",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/**
 * Sticky footer tile showing the authenticated user's avatar, name, and role.
 *
 * In mini mode only the avatar circle is shown (no name or role text).
 * The footer is omitted entirely when both [userName] and [userInitials] are null.
 */
@Composable
private fun DrawerFooter(
    isMini: Boolean,
    userName: String?,
    userInitials: String?,
    userRole: String?,
    syncStatus: SyncDisplayStatus? = null,
    syncPendingCount: Int = 0,
) {
    if (userName == null && userInitials == null && syncStatus == null) {
        Spacer(modifier = Modifier.height(16.dp))
        return
    }

    HorizontalDivider(
        modifier = Modifier.padding(horizontal = 8.dp),
        color = MaterialTheme.colorScheme.outlineVariant,
    )

    // ── Store selector (multi-store only, hidden in mini mode) ───────────────
    val drawerAvailableStores = LocalDrawerAvailableStores.current
    val drawerCurrentStore = LocalDrawerCurrentStore.current
    val drawerOnStoreSelected = LocalDrawerOnStoreSelected.current
    if (!isMini && drawerAvailableStores.size > 1) {
        ZyntaStoreSelector(
            currentStore = drawerCurrentStore,
            availableStores = drawerAvailableStores,
            onStoreSelected = drawerOnStoreSelected,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 4.dp),
        )
    }

    // ── Sync status row (above user info) ────────────────────────────────────
    if (syncStatus != null) {
        ZyntaSyncStatusIndicator(
            status = syncStatus,
            isMini = isMini,
            pendingCount = syncPendingCount,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 4.dp),
        )
    }

    if (userName != null || userInitials != null) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Avatar circle
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.size(36.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = userInitials ?: userName?.take(1)?.uppercase() ?: "?",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
            }

            // Name + role (hidden in mini mode)
            if (!isMini) {
                Spacer(modifier = Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = userName ?: "",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (!userRole.isNullOrBlank()) {
                        Text(
                            text = userRole,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                        )
                    }
                }
            }
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
            label = { if (!isMini) Text(item.label) },
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

/**
 * Floating popout menu shown to the right of the mini drawer when the user
 * hovers over an item in mini mode (desktop only).
 *
 * Shows the item label as a header. If the item has children they are listed
 * as [NavigationDrawerItem] rows below a divider. Leaf items show a single
 * selectable row with icon + label.
 *
 * The popout dismisses when the mouse leaves via [onHoverExit].
 */
@Composable
private fun MiniItemPopout(
    item: ZyntaNavItem,
    selectedChildIndex: Int,
    onItemSelected: () -> Unit,
    onChildSelected: (childIndex: Int) -> Unit,
    onHoverEnter: () -> Unit,
    onHoverExit: () -> Unit,
) {
    Popup(
        alignment = Alignment.CenterEnd,
        offset = IntOffset(8, 0),
        properties = PopupProperties(focusable = false),
        onDismissRequest = {},
    ) {
        Surface(
            modifier = Modifier
                .width(200.dp)
                .pointerInput(Unit) {
                    awaitPointerEventScope {
                        while (true) {
                            val event = awaitPointerEvent()
                            when (event.type) {
                                PointerEventType.Enter -> onHoverEnter()
                                PointerEventType.Exit -> onHoverExit()
                                else -> {}
                            }
                        }
                    }
                },
            shape = MaterialTheme.shapes.medium,
            color = MaterialTheme.colorScheme.surface,
            shadowElevation = 8.dp,
            tonalElevation = 2.dp,
        ) {
            Column(modifier = Modifier.padding(vertical = 4.dp)) {
                // Item label header
                Text(
                    text = item.label,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                )
                HorizontalDivider(modifier = Modifier.padding(horizontal = 8.dp))

                if (item.children.isNotEmpty()) {
                    // List all children
                    item.children.forEachIndexed { childIdx, child ->
                        NavigationDrawerItem(
                            selected = selectedChildIndex == childIdx,
                            onClick = { onChildSelected(childIdx) },
                            label = {
                                Text(
                                    child.label,
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                            },
                            modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding),
                        )
                    }
                } else {
                    // Leaf item — single selectable row
                    NavigationDrawerItem(
                        selected = selectedChildIndex < 0,
                        onClick = onItemSelected,
                        icon = { Icon(item.selectedIcon, contentDescription = null) },
                        label = {
                            Text(
                                item.label,
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        },
                        modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding),
                    )
                }
            }
        }
    }
}
