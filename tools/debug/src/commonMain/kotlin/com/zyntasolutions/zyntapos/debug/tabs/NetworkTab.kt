package com.zyntasolutions.zyntapos.debug.tabs

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.zyntasolutions.zyntapos.debug.mvi.DebugIntent
import com.zyntasolutions.zyntapos.debug.mvi.DebugState
import com.zyntasolutions.zyntapos.designsystem.components.ZyntaButton
import com.zyntasolutions.zyntapos.designsystem.components.ZyntaButtonVariant

/**
 * Network tab — offline mode toggle, sync queue depth, force sync, clear queue.
 */
@Composable
fun NetworkTab(
    state: DebugState,
    onIntent: (DebugIntent) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {

        // ── Offline mode toggle ────────────────────────────────────────────────
        Text("Connectivity", style = MaterialTheme.typography.titleSmall)

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column {
                        Text("Force Offline Mode", style = MaterialTheme.typography.bodyMedium)
                        Text(
                            "UI flag only — full network interception in Phase 2",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(
                        checked = state.isOfflineModeForced,
                        onCheckedChange = { onIntent(DebugIntent.SetOfflineModeForced(it)) },
                    )
                }
            }
        }

        // ── Sync queue ────────────────────────────────────────────────────────
        Text("Sync Queue", style = MaterialTheme.typography.titleSmall)

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text("Pending operations", style = MaterialTheme.typography.bodyMedium)
                    Text(
                        state.pendingOpsCount.toString(),
                        style = MaterialTheme.typography.titleMedium,
                        color = if (state.pendingOpsCount > 0) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ZyntaButton(
                text = "Refresh Queue",
                onClick = { onIntent(DebugIntent.LoadSyncQueueDepth) },
                variant = ZyntaButtonVariant.Secondary,
                modifier = Modifier.weight(1f),
            )
            ZyntaButton(
                text = "Force Sync",
                onClick = { onIntent(DebugIntent.ForceSyncNow) },
                isLoading = state.isLoading,
                modifier = Modifier.weight(1f),
            )
        }

        // ── Destructive ───────────────────────────────────────────────────────
        Text("Destructive", style = MaterialTheme.typography.titleSmall)
        Text(
            "Marks all pending operations as synced without actually pushing to server.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        ZyntaButton(
            text = "Clear Sync Queue",
            onClick = { onIntent(DebugIntent.RequestClearSyncQueue) },
            variant = ZyntaButtonVariant.Danger,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
