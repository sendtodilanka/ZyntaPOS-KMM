package com.zyntasolutions.zyntapos.debug.tabs

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.zyntasolutions.zyntapos.debug.mvi.DebugIntent
import com.zyntasolutions.zyntapos.debug.mvi.DebugState
import com.zyntasolutions.zyntapos.designsystem.components.ZyntaButton
import com.zyntasolutions.zyntapos.designsystem.components.ZyntaButtonVariant

/**
 * Database tab — table counts, reset, vacuum, export.
 *
 * All destructive actions are gated behind [ConfirmDestructiveDialog].
 */
@Composable
fun DatabaseTab(
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

        // ── Table row counts ───────────────────────────────────────────────────
        Text("Table Row Counts", style = MaterialTheme.typography.titleSmall)

        if (state.tableRowCounts.isEmpty()) {
            ZyntaButton(
                text = "Load Table Counts",
                onClick = { onIntent(DebugIntent.LoadTableCounts) },
                variant = ZyntaButtonVariant.Secondary,
                modifier = Modifier.fillMaxWidth(),
            )
        } else {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    state.tableRowCounts.entries.forEachIndexed { index, (table, count) ->
                        if (index > 0) HorizontalDivider()
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(table, style = MaterialTheme.typography.bodySmall)
                            Text(
                                count.toString(),
                                style = MaterialTheme.typography.labelMedium,
                                color = if (count > 0) MaterialTheme.colorScheme.primary
                                        else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
            ZyntaButton(
                text = "Refresh Counts",
                onClick = { onIntent(DebugIntent.LoadTableCounts) },
                variant = ZyntaButtonVariant.Ghost,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        // ── DB stats ──────────────────────────────────────────────────────────
        if (state.dbFileSizeKb > 0) {
            Text(
                "DB file size: ${state.dbFileSizeKb} KB",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        // ── Safe actions ──────────────────────────────────────────────────────
        Text("Maintenance", style = MaterialTheme.typography.titleSmall)

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ZyntaButton(
                text = "VACUUM",
                onClick = { onIntent(DebugIntent.VacuumDatabase) },
                variant = ZyntaButtonVariant.Secondary,
                isLoading = state.isLoading,
                modifier = Modifier.weight(1f),
            )
            ZyntaButton(
                text = "Export DB",
                onClick = { onIntent(DebugIntent.ExportDatabase) },
                variant = ZyntaButtonVariant.Secondary,
                modifier = Modifier.weight(1f),
            )
        }

        // ── Destructive actions ───────────────────────────────────────────────
        Text("Destructive", style = MaterialTheme.typography.titleSmall)
        Text(
            "These operations cannot be undone. A typed-word confirmation is required.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        ZyntaButton(
            text = "Reset Database (DROP ALL)",
            onClick = { onIntent(DebugIntent.RequestResetDatabase) },
            variant = ZyntaButtonVariant.Danger,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
