package com.zyntasolutions.zyntapos.feature.admin

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.EventNote
import androidx.compose.material.icons.filled.MonitorHeart
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * Root screen for the Admin feature (Sprints 13–15).
 *
 * Hosts a [TabRow] with three administrative sections:
 * 1. **System Health** — live metrics, DB stats, vacuum / purge
 * 2. **Backups** — create, restore, delete database backups
 * 3. **Audit Log** — security event stream with user filter
 *
 * @param state     Current [AdminState] from [AdminViewModel].
 * @param onIntent  Dispatches intents to [AdminViewModel].
 * @param modifier  Optional modifier.
 */
@Composable
fun AdminScreen(
    state: AdminState,
    onIntent: (AdminIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxSize()) {
        TabRow(selectedTabIndex = state.activeTab.ordinal) {
            AdminTabItem.entries.forEach { item ->
                Tab(
                    selected = state.activeTab == item.tab,
                    onClick = { onIntent(AdminIntent.SwitchTab(item.tab)) },
                    text = { Text(item.label) },
                    icon = { Icon(item.icon, contentDescription = null) },
                )
            }
        }

        when (state.activeTab) {
            AdminTab.SYSTEM_HEALTH -> SystemHealthScreen(
                state = state,
                onIntent = onIntent,
                modifier = Modifier.fillMaxSize(),
            )
            AdminTab.BACKUPS -> BackupScreen(
                state = state,
                onIntent = onIntent,
                modifier = Modifier.fillMaxSize(),
            )
            AdminTab.AUDIT_LOG -> AuditLogScreen(
                state = state,
                onIntent = onIntent,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }

    // Success snackbar (non-blocking feedback)
    state.error?.let { msg ->
        LaunchedEffect(msg) { onIntent(AdminIntent.DismissError) }
    }
}

// ─── Tab metadata ─────────────────────────────────────────────────────────────

private enum class AdminTabItem(
    val tab: AdminTab,
    val label: String,
    val icon: ImageVector,
) {
    SYSTEM_HEALTH(AdminTab.SYSTEM_HEALTH, "Health", Icons.Default.MonitorHeart),
    BACKUPS(AdminTab.BACKUPS, "Backups", Icons.Default.Backup),
    AUDIT_LOG(AdminTab.AUDIT_LOG, "Audit", Icons.Default.EventNote),
}
