package com.zyntasolutions.zyntapos.designsystem.layouts

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import com.zyntasolutions.zyntapos.designsystem.components.ZyntaSnackbarHost
import com.zyntasolutions.zyntapos.designsystem.components.ZyntaTopAppBar

// ─────────────────────────────────────────────────────────────────────────────
// ZyntaPageScaffold — Standard page scaffold for sub-screens.
//
// Wraps Material 3 Scaffold with consistent use of ZyntaTopAppBar and
// ZyntaSnackbarHost. Feature screens should use this instead of bare Scaffold
// to ensure design-system compliance.
//
// For the main navigation shell (with NavigationBar/Rail/Drawer), use
// ZyntaScaffold instead.
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Standard page-level scaffold that enforces consistent use of design system
 * components: [ZyntaTopAppBar] for the top bar and [ZyntaSnackbarHost] for
 * snackbar feedback.
 *
 * @param title Screen title displayed in the top app bar.
 * @param modifier Optional root [Modifier].
 * @param onNavigateBack When non-null, a back arrow is rendered in the top bar.
 * @param navigationIcon Override the default back arrow with a custom [ImageVector].
 * @param snackbarHostState When non-null, a [ZyntaSnackbarHost] is rendered.
 * @param actions Trailing icon slot for the top app bar.
 * @param floatingActionButton Optional FAB slot.
 * @param bottomBar Optional bottom bar slot.
 * @param content Screen content receiving [PaddingValues] for safe insets.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ZyntaPageScaffold(
    title: String,
    modifier: Modifier = Modifier,
    onNavigateBack: (() -> Unit)? = null,
    navigationIcon: ImageVector? = null,
    snackbarHostState: SnackbarHostState? = null,
    actions: @Composable () -> Unit = {},
    floatingActionButton: @Composable () -> Unit = {},
    bottomBar: @Composable () -> Unit = {},
    content: @Composable (PaddingValues) -> Unit,
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            ZyntaTopAppBar(
                title = title,
                onNavigateBack = onNavigateBack,
                navigationIcon = navigationIcon,
                actions = actions,
            )
        },
        snackbarHost = {
            if (snackbarHostState != null) {
                ZyntaSnackbarHost(snackbarHostState)
            }
        },
        floatingActionButton = floatingActionButton,
        bottomBar = bottomBar,
        content = content,
    )
}
