package com.zyntasolutions.zyntapos.designsystem.layouts

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.zyntasolutions.zyntapos.designsystem.util.WindowSize
import com.zyntasolutions.zyntapos.designsystem.util.currentWindowSize

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

/** Width of the persistent navigation drawer in EXPANDED layout. */
private val DrawerWidth = 240.dp

/**
 * Descriptor for a single navigation destination in [ZyntaScaffold].
 *
 * @param label Human-readable label shown in nav bar / rail / drawer.
 * @param icon Icon displayed in all navigation variants.
 * @param selectedIcon Icon used when this destination is selected (defaults to [icon]).
 * @param contentDescription Accessibility description for the icon.
 */
data class ZyntaNavItem(
    val label: String,
    val icon: ImageVector,
    val selectedIcon: ImageVector = icon,
    val contentDescription: String = label,
)

/**
 * Adaptive scaffold that switches between three navigation modes based on
 * [WindowSize], wiring Material 3 navigation components to a single item list.
 *
 * The scaffold ensures content is never drawn behind the navigation chrome by
 * automatically applying the correct [PaddingValues] inside [content].
 *
 * @param items Navigation destinations to display.
 * @param selectedIndex Index of the currently selected destination.
 * @param onItemSelected Callback invoked with the tapped item index.
 * @param modifier Optional root [Modifier].
 * @param topBar Optional top app bar slot (only rendered on COMPACT/MEDIUM).
 * @param snackbarHost Snackbar host; defaults to [SnackbarHost] with a [SnackbarHostState].
 * @param windowSize Override the detected [WindowSize]; useful in previews/tests.
 * @param content Screen content lambda receiving [PaddingValues] for safe insets.
 */
@Composable
fun ZyntaScaffold(
    items: List<ZyntaNavItem>,
    selectedIndex: Int,
    onItemSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
    topBar: @Composable () -> Unit = {},
    snackbarHost: @Composable () -> Unit = {},
    windowSize: WindowSize = currentWindowSize(),
    content: @Composable (PaddingValues) -> Unit,
) {
    when (windowSize) {
        WindowSize.COMPACT -> CompactScaffold(
            items = items,
            selectedIndex = selectedIndex,
            onItemSelected = onItemSelected,
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
// EXPANDED — PermanentNavigationDrawer (240dp)
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun ExpandedScaffold(
    items: List<ZyntaNavItem>,
    selectedIndex: Int,
    onItemSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable (PaddingValues) -> Unit,
) {
    PermanentNavigationDrawer(
        modifier = modifier,
        drawerContent = {
            PermanentDrawerSheet(drawerContainerColor = MaterialTheme.colorScheme.surfaceContainerLow) {
                Spacer(Modifier.height(16.dp))
                items.forEachIndexed { index, item ->
                    NavigationDrawerItem(
                        selected = index == selectedIndex,
                        onClick = { onItemSelected(index) },
                        icon = {
                            Icon(
                                imageVector = if (index == selectedIndex) item.selectedIcon else item.icon,
                                contentDescription = item.contentDescription,
                            )
                        },
                        label = { Text(item.label) },
                        modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding),
                    )
                }
            }
        },
        content = {
            Scaffold { innerPadding ->
                content(innerPadding)
            }
        },
    )
}
