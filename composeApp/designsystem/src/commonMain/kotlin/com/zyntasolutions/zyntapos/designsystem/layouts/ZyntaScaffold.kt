package com.zyntasolutions.zyntapos.designsystem.layouts

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.zyntasolutions.zyntapos.designsystem.components.SyncDisplayStatus
import com.zyntasolutions.zyntapos.designsystem.util.WindowSize
import com.zyntasolutions.zyntapos.designsystem.util.currentWindowSize
import kotlinx.coroutines.launch

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
// Per UI/UX §2.1 & TODO-005:
//   COMPACT   → ModalNavigationDrawer (hamburger in TopAppBar, no bottom bar)
//   MEDIUM    → ZyntaNavigationDrawer (mini 72dp, expandable to 260dp)
//   EXPANDED  → ZyntaNavigationDrawer (full 260dp, collapsible to 72dp)
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Descriptor for a single navigation destination in [ZyntaScaffold].
 *
 * @param label Human-readable label shown in nav bar / rail / drawer.
 * @param icon Icon displayed in all navigation variants.
 * @param selectedIcon Icon used when this destination is selected (defaults to [icon]).
 * @param contentDescription Accessibility description for the icon.
 * @param children Optional child destinations shown as an expandable sub-list in the
 *   EXPANDED drawer.  Ignored in COMPACT (modal drawer) and MEDIUM (mini drawer).
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
 * [WindowSize], wiring the design-system navigation drawer to a single item list.
 *
 * ### Navigation modes
 * - **COMPACT** (<600 dp): No bottom bar. A modal overlay drawer is opened by the
 *   hamburger icon injected into the [ZyntaTopAppBar] via [LocalZyntaDrawerController].
 * - **MEDIUM** (600–840 dp): Mini [ZyntaNavigationDrawer] (72 dp icons-only) that
 *   expands to 260 dp when the user taps the toggle button.
 * - **EXPANDED** (>840 dp): Full [ZyntaNavigationDrawer] (260 dp), collapsible to
 *   72 dp mini mode.
 *
 * ### Tiered navigation
 * [compactItems] and related parameters are retained for backwards-compatibility
 * but are no longer used in the COMPACT layout (modal drawer uses the full [items]
 * list). They may be removed in a future refactor.
 *
 * @param items Full navigation destinations — used for all three layout variants.
 * @param selectedIndex Selected index within [items].
 * @param onItemSelected Callback with the tapped index within [items].
 * @param modifier Optional root [Modifier].
 * @param compactItems Retained for API compatibility; not used in COMPACT modal drawer.
 * @param compactSelectedIndex Retained for API compatibility.
 * @param onCompactItemSelected Retained for API compatibility.
 * @param groups Optional section groups rendered as headers in the drawer.
 * @param selectedChildIndex Selected child index within the active parent (-1 = no child active).
 * @param onChildSelected Callback with (parentIndex, childIndex) when a child is tapped.
 * @param topBar Retained for API compatibility; not rendered in the new nav model.
 * @param snackbarHost Retained for API compatibility.
 * @param drawerUserName Full name of the logged-in user for the drawer footer.
 * @param drawerUserInitials Pre-computed initials (e.g. "JS") for the footer avatar.
 * @param drawerUserRole Human-readable role label (e.g. "Admin") for the footer.
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
    drawerUserName: String? = null,
    drawerUserInitials: String? = null,
    drawerUserRole: String? = null,
    syncStatus: SyncDisplayStatus? = null,
    syncPendingCount: Int = 0,
    windowSize: WindowSize = currentWindowSize(),
    content: @Composable (PaddingValues) -> Unit,
) {
    when (windowSize) {
        WindowSize.COMPACT -> CompactScaffold(
            items = items,
            selectedIndex = selectedIndex,
            onItemSelected = onItemSelected,
            modifier = modifier,
            groups = groups,
            selectedChildIndex = selectedChildIndex,
            onChildSelected = onChildSelected,
            drawerUserName = drawerUserName,
            drawerUserInitials = drawerUserInitials,
            drawerUserRole = drawerUserRole,
            syncStatus = syncStatus,
            syncPendingCount = syncPendingCount,
            content = content,
        )
        WindowSize.MEDIUM -> MediumScaffold(
            items = items,
            selectedIndex = selectedIndex,
            onItemSelected = onItemSelected,
            modifier = modifier,
            groups = groups,
            selectedChildIndex = selectedChildIndex,
            onChildSelected = onChildSelected,
            drawerUserName = drawerUserName,
            drawerUserInitials = drawerUserInitials,
            drawerUserRole = drawerUserRole,
            syncStatus = syncStatus,
            syncPendingCount = syncPendingCount,
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
            drawerUserName = drawerUserName,
            drawerUserInitials = drawerUserInitials,
            drawerUserRole = drawerUserRole,
            syncStatus = syncStatus,
            syncPendingCount = syncPendingCount,
            content = content,
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// COMPACT — ModalNavigationDrawer (hamburger trigger in TopAppBar)
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun CompactScaffold(
    items: List<ZyntaNavItem>,
    selectedIndex: Int,
    onItemSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
    groups: List<ZyntaNavGroup> = emptyList(),
    selectedChildIndex: Int = -1,
    onChildSelected: (parentIndex: Int, childIndex: Int) -> Unit = { _, _ -> },
    drawerUserName: String? = null,
    drawerUserInitials: String? = null,
    drawerUserRole: String? = null,
    syncStatus: SyncDisplayStatus? = null,
    syncPendingCount: Int = 0,
    content: @Composable (PaddingValues) -> Unit,
) {
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    // Provide the open-drawer lambda so ZyntaTopAppBar can render the hamburger icon
    CompositionLocalProvider(
        LocalZyntaDrawerController provides { scope.launch { drawerState.open() } },
    ) {
        ModalNavigationDrawer(
            modifier = modifier,
            drawerState = drawerState,
            drawerContent = {
                ModalDrawerSheet(
                    drawerContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                ) {
                    DrawerColumnContent(
                        items = items,
                        selectedIndex = selectedIndex,
                        onItemSelected = { idx ->
                            onItemSelected(idx)
                            scope.launch { drawerState.close() }
                        },
                        selectedChildIndex = selectedChildIndex,
                        onChildSelected = { parentIdx, childIdx ->
                            onChildSelected(parentIdx, childIdx)
                            scope.launch { drawerState.close() }
                        },
                        groups = groups,
                        isMini = false,
                        onToggleMini = null, // modal drawer is always full-width; no toggle needed
                        drawerUserName = drawerUserName,
                        drawerUserInitials = drawerUserInitials,
                        drawerUserRole = drawerUserRole,
                        syncStatus = syncStatus,
                        syncPendingCount = syncPendingCount,
                    )
                }
            },
        ) {
            Scaffold(content = content)
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// MEDIUM — ZyntaNavigationDrawer (mini 72dp, expandable to 260dp)
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun MediumScaffold(
    items: List<ZyntaNavItem>,
    selectedIndex: Int,
    onItemSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
    groups: List<ZyntaNavGroup> = emptyList(),
    selectedChildIndex: Int = -1,
    onChildSelected: (parentIndex: Int, childIndex: Int) -> Unit = { _, _ -> },
    drawerUserName: String? = null,
    drawerUserInitials: String? = null,
    drawerUserRole: String? = null,
    syncStatus: SyncDisplayStatus? = null,
    syncPendingCount: Int = 0,
    content: @Composable (PaddingValues) -> Unit,
) {
    ZyntaNavigationDrawer(
        items = items,
        selectedIndex = selectedIndex,
        onItemSelected = onItemSelected,
        modifier = modifier,
        startMini = true, // MEDIUM starts in 72dp mini mode; user can expand
        selectedChildIndex = selectedChildIndex,
        onChildSelected = onChildSelected,
        groups = groups,
        drawerUserName = drawerUserName,
        drawerUserInitials = drawerUserInitials,
        drawerUserRole = drawerUserRole,
        syncStatus = syncStatus,
        syncPendingCount = syncPendingCount,
        content = content,
    )
}

// ─────────────────────────────────────────────────────────────────────────────
// EXPANDED — ZyntaNavigationDrawer (full 260dp, collapsible to 72dp mini)
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
    drawerUserName: String? = null,
    drawerUserInitials: String? = null,
    drawerUserRole: String? = null,
    syncStatus: SyncDisplayStatus? = null,
    syncPendingCount: Int = 0,
    content: @Composable (PaddingValues) -> Unit,
) {
    ZyntaNavigationDrawer(
        items = items,
        selectedIndex = selectedIndex,
        onItemSelected = onItemSelected,
        modifier = modifier,
        startMini = false, // EXPANDED starts full-width
        selectedChildIndex = selectedChildIndex,
        onChildSelected = onChildSelected,
        groups = groups,
        drawerUserName = drawerUserName,
        drawerUserInitials = drawerUserInitials,
        drawerUserRole = drawerUserRole,
        syncStatus = syncStatus,
        syncPendingCount = syncPendingCount,
        content = content,
    )
}
