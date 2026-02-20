package com.zyntasolutions.zyntapos.designsystem.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector

// ─────────────────────────────────────────────────────────────────────────────
// ZentaTopAppBar — Adaptive, collapses on scroll via TopAppBarScrollBehavior.
// Back navigation action slot + trailing action icons slot.
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Adaptive top app bar that collapses on scroll using M3 [TopAppBarScrollBehavior].
 *
 * For simple static screens use [ZentaTopAppBar].
 * For content-heavy scroll screens pass a [scrollBehavior] obtained via
 * [TopAppBarDefaults.enterAlwaysScrollBehavior] or [TopAppBarDefaults.exitUntilCollapsedScrollBehavior].
 *
 * Example with scroll connection:
 * ```
 * val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior()
 * Scaffold(
 *     topBar = { ZentaTopAppBar(title = "Inventory", scrollBehavior = scrollBehavior) },
 *     modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection)
 * ) { ... }
 * ```
 *
 * @param title Screen title text.
 * @param modifier Optional [Modifier].
 * @param scrollBehavior Optional [TopAppBarScrollBehavior] for scroll-aware collapsing.
 * @param onNavigateBack When non-null, a back arrow is rendered and invokes this lambda.
 * @param navigationIcon Override the back arrow with a custom [ImageVector].
 * @param actions Trailing icon slot — compose additional [IconButton]s here.
 * @param colors Color overrides for the app bar (default uses M3 surface scheme).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ZentaTopAppBar(
    title: String,
    modifier: Modifier = Modifier,
    scrollBehavior: TopAppBarScrollBehavior? = null,
    onNavigateBack: (() -> Unit)? = null,
    navigationIcon: ImageVector? = null,
    actions: @Composable () -> Unit = {},
    colors: TopAppBarColors = TopAppBarDefaults.topAppBarColors(),
) {
    TopAppBar(
        title = { Text(title) },
        modifier = modifier,
        scrollBehavior = scrollBehavior,
        navigationIcon = {
            if (onNavigateBack != null) {
                IconButton(onClick = onNavigateBack) {
                    Icon(
                        imageVector = navigationIcon ?: Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Navigate back",
                    )
                }
            }
        },
        actions = { actions() },
        colors = colors,
    )
}

/**
 * Large collapsible variant of [ZentaTopAppBar] for primary screens.
 * Title is large when expanded and collapses to standard size on scroll.
 *
 * @param title Screen title.
 * @param modifier Optional [Modifier].
 * @param scrollBehavior Should be [TopAppBarDefaults.exitUntilCollapsedScrollBehavior].
 * @param onNavigateBack When non-null, a back arrow is rendered.
 * @param actions Trailing icons slot.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ZentaLargeTopAppBar(
    title: String,
    modifier: Modifier = Modifier,
    scrollBehavior: TopAppBarScrollBehavior? = null,
    onNavigateBack: (() -> Unit)? = null,
    actions: @Composable () -> Unit = {},
) {
    LargeTopAppBar(
        title = { Text(title) },
        modifier = modifier,
        scrollBehavior = scrollBehavior,
        navigationIcon = {
            if (onNavigateBack != null) {
                IconButton(onClick = onNavigateBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Navigate back",
                    )
                }
            }
        },
        actions = { actions() },
    )
}
