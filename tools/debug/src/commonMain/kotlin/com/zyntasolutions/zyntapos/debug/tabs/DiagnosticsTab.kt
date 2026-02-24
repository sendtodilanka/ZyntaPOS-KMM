package com.zyntasolutions.zyntapos.debug.tabs

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
 * Diagnostics tab — audit log, system health snapshot, log viewer.
 */
@Composable
fun DiagnosticsTab(
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

        // ── System health ──────────────────────────────────────────────────────
        Text("System Health", style = MaterialTheme.typography.titleSmall)

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                HealthRow("DB file size", if (state.dbFileSizeKb > 0) "${state.dbFileSizeKb} KB" else "Unknown")
                HealthRow("Pending sync ops", state.pendingOpsCount.toString())
                HealthRow("Audit entries", state.auditEntries.size.toString())
            }
        }

        ZyntaButton(
            text = "Refresh Health",
            onClick = { onIntent(DebugIntent.LoadSystemHealth) },
            variant = ZyntaButtonVariant.Ghost,
            modifier = Modifier.fillMaxWidth(),
        )

        // ── Audit log ─────────────────────────────────────────────────────────
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text("Audit Log (${state.auditEntries.size})", style = MaterialTheme.typography.titleSmall)
            ZyntaButton(
                text = "Reload",
                onClick = { onIntent(DebugIntent.LoadAuditLog) },
                variant = ZyntaButtonVariant.Ghost,
            )
        }

        if (state.auditEntries.isEmpty()) {
            Text(
                "No audit entries found.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(vertical = 4.dp)) {
                    state.auditEntries.take(50).forEachIndexed { index, entry ->
                        if (index > 0) HorizontalDivider(modifier = Modifier.padding(horizontal = 12.dp))
                        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Text(
                                    entry.eventType.name,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = if (entry.success) MaterialTheme.colorScheme.primary
                                            else MaterialTheme.colorScheme.error,
                                )
                                Text(
                                    entry.createdAt.toString().take(19),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Text(
                                "User: ${entry.userId}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            if (entry.payload.isNotBlank()) {
                                Text(
                                    entry.payload,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                    if (state.auditEntries.size > 50) {
                        Text(
                            "… and ${state.auditEntries.size - 50} more entries",
                            modifier = Modifier.padding(12.dp),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }

        // ── Log viewer ────────────────────────────────────────────────────────
        Text("Session Logs", style = MaterialTheme.typography.titleSmall)

        ZyntaButton(
            text = "Export Logs",
            onClick = { onIntent(DebugIntent.ExportLogs) },
            variant = ZyntaButtonVariant.Secondary,
            modifier = Modifier.fillMaxWidth(),
        )

        if (state.logLines.isNotEmpty()) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(12.dp)) {
                    state.logLines.takeLast(30).forEach { line ->
                        Text(
                            line,
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun HealthRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodySmall)
    }
}
