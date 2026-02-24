package com.zyntasolutions.zyntapos.debug

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.zyntasolutions.zyntapos.debug.components.ActionResultBanner
import com.zyntasolutions.zyntapos.debug.components.ConfirmDestructiveDialog
import com.zyntasolutions.zyntapos.debug.mvi.DebugEffect
import com.zyntasolutions.zyntapos.debug.mvi.DebugIntent
import com.zyntasolutions.zyntapos.debug.mvi.DebugTab
import com.zyntasolutions.zyntapos.debug.tabs.AuthTab
import com.zyntasolutions.zyntapos.debug.tabs.DatabaseTab
import com.zyntasolutions.zyntapos.debug.tabs.DiagnosticsTab
import com.zyntasolutions.zyntapos.debug.tabs.NetworkTab
import com.zyntasolutions.zyntapos.debug.tabs.SeedsTab
import com.zyntasolutions.zyntapos.debug.tabs.UiUxTab
import org.koin.compose.viewmodel.koinViewModel

/**
 * Root composable for the Debug Console.
 *
 * **Access control:** This screen must only be reachable when:
 *  - `AppInfoProvider.isDebug == true` (gated in [ZyntaNavGraph])
 *  - The current user holds [Role.ADMIN] (gated in the nav back-stack entry)
 *
 * Layout:
 * ```
 * ┌─ TopAppBar (title + back) ─────────────────┐
 * ├─ ScrollableTabRow (6 tabs) ─────────────────┤
 * ├─ ActionResultBanner (success/error) ────────┤
 * └─ Tab content (scrollable per tab) ──────────┘
 * ```
 *
 * Destructive actions surface [ConfirmDestructiveDialog] as an overlay.
 *
 * @param onNavigateUp Called when the back button is tapped or session is cleared.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DebugScreen(
    onNavigateUp: () -> Unit,
    viewModel: DebugViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsState()

    // Collect one-shot effects
    LaunchedEffect(viewModel.effects) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is DebugEffect.NavigateUp   -> onNavigateUp()
                is DebugEffect.ShowSnackbar -> { /* handled via ActionResultBanner in state */ }
                is DebugEffect.ShareFile    -> { /* platform-specific file sharing — Phase 2 */ }
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {

        // ── Top App Bar ────────────────────────────────────────────────────────
        TopAppBar(
            title = { Text("Debug Console") },
            navigationIcon = {
                IconButton(onClick = onNavigateUp) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                    )
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.errorContainer,
                titleContentColor = MaterialTheme.colorScheme.onErrorContainer,
                navigationIconContentColor = MaterialTheme.colorScheme.onErrorContainer,
            ),
            actions = {
                Text(
                    text = "ADMIN",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    modifier = Modifier.padding(end = 16.dp),
                )
            },
        )

        // ── Scrollable Tab Row ─────────────────────────────────────────────────
        val tabs = DebugTab.entries
        val selectedIndex = tabs.indexOf(state.activeTab)

        ScrollableTabRow(
            selectedTabIndex = selectedIndex,
            modifier = Modifier.fillMaxWidth(),
            edgePadding = 0.dp,
        ) {
            tabs.forEachIndexed { index, tab ->
                Tab(
                    selected = index == selectedIndex,
                    onClick = { viewModel.dispatch(DebugIntent.SelectTab(tab)) },
                    text = { Text(tab.label, style = MaterialTheme.typography.labelMedium) },
                )
            }
        }

        // ── Action result banner ───────────────────────────────────────────────
        ActionResultBanner(
            result = state.actionResult,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
        )

        // ── Tab content ────────────────────────────────────────────────────────
        Box(modifier = Modifier.fillMaxSize().weight(1f)) {
            when (state.activeTab) {
                DebugTab.Seeds       -> SeedsTab(state = state, onIntent = viewModel::dispatch)
                DebugTab.Database    -> DatabaseTab(state = state, onIntent = viewModel::dispatch)
                DebugTab.Auth        -> AuthTab(state = state, onIntent = viewModel::dispatch)
                DebugTab.Network     -> NetworkTab(state = state, onIntent = viewModel::dispatch)
                DebugTab.Diagnostics -> DiagnosticsTab(state = state, onIntent = viewModel::dispatch)
                DebugTab.UiUx        -> UiUxTab(state = state, onIntent = viewModel::dispatch)
            }
        }
    }

    // ── Destructive confirmation dialog overlay ────────────────────────────────
    state.pendingDestructiveIntent?.let { pendingIntent ->
        val (title, message, word) = destructiveDialogProps(pendingIntent)
        ConfirmDestructiveDialog(
            title = title,
            message = message,
            confirmWord = word,
            onConfirm = {
                viewModel.dispatch(DebugIntent.DismissDestructiveDialog)
                viewModel.dispatch(pendingIntent)
            },
            onDismiss = { viewModel.dispatch(DebugIntent.DismissDestructiveDialog) },
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Destructive dialog properties helper
// ─────────────────────────────────────────────────────────────────────────────

private data class DestructiveDialogProps(
    val title: String,
    val message: String,
    val confirmWord: String,
)

private fun destructiveDialogProps(intent: DebugIntent): DestructiveDialogProps =
    when (intent) {
        is DebugIntent.ConfirmResetDatabase ->
            DestructiveDialogProps(
                title = "Reset Database",
                message = "This will DROP ALL TABLES and recreate the schema. " +
                    "All users, products, orders, and audit logs will be permanently erased. " +
                    "You will be logged out after the reset.",
                confirmWord = "RESET",
            )
        is DebugIntent.ConfirmClearSeedData ->
            DestructiveDialogProps(
                title = "Clear Seed Data",
                message = "All seed-generated records will be removed from the database.",
                confirmWord = "CLEAR",
            )
        is DebugIntent.ConfirmClearSyncQueue ->
            DestructiveDialogProps(
                title = "Clear Sync Queue",
                message = "All pending sync operations will be marked as synced without pushing to the server. " +
                    "This may cause data inconsistency with the backend.",
                confirmWord = "CLEAR",
            )
        is DebugIntent.ConfirmClearSession ->
            DestructiveDialogProps(
                title = "Clear Session",
                message = "Your current session will be ended and you will be redirected to the login screen.",
                confirmWord = "LOGOUT",
            )
        is DebugIntent.ConfirmClearTable ->
            DestructiveDialogProps(
                title = "Clear Table",
                message = "All records in '${intent.tableName}' will be permanently deleted.",
                confirmWord = "DELETE",
            )
        else ->
            DestructiveDialogProps(
                title = "Confirm Action",
                message = "This action cannot be undone.",
                confirmWord = "CONFIRM",
            )
    }
