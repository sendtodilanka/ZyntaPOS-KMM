package com.zyntasolutions.zyntapos.feature.admin

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.zyntasolutions.zyntapos.core.i18n.StringResource
import com.zyntasolutions.zyntapos.designsystem.components.LocalStrings
import com.zyntasolutions.zyntapos.designsystem.tokens.ZyntaSpacing
import com.zyntasolutions.zyntapos.domain.model.DatabaseStats
import com.zyntasolutions.zyntapos.domain.model.SystemHealth

/**
 * System health dashboard screen (Sprint 13).
 *
 * Displays real-time metrics (memory, DB size, sync status, connectivity)
 * and provides maintenance actions (VACUUM, purge expired data).
 *
 * @param state     Current [AdminState].
 * @param onIntent  Dispatches intents to [AdminViewModel].
 * @param modifier  Optional modifier.
 */
@Composable
fun SystemHealthScreen(
    state: AdminState,
    onIntent: (AdminIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val s = LocalStrings.current
    var showPurgeDialog by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = ZyntaSpacing.md),
        verticalArrangement = Arrangement.spacedBy(ZyntaSpacing.sm),
        contentPadding = PaddingValues(vertical = ZyntaSpacing.md),
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(s[StringResource.ADMIN_SYSTEM_HEALTH_TITLE], style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                IconButton(onClick = {
                    onIntent(AdminIntent.RefreshSystemHealth)
                    onIntent(AdminIntent.RefreshDatabaseStats)
                }) {
                    Icon(Icons.Default.Refresh, contentDescription = s[StringResource.ADMIN_REFRESH_CD])
                }
            }
        }

        if (state.isLoading && state.systemHealth == null) {
            item {
                Box(Modifier.fillMaxWidth().height(120.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
        }

        state.systemHealth?.let { health ->
            item { ConnectivityCard(health) }
            item { MemoryCard(health) }
            item { SyncCard(health) }
            item { AppVersionCard(health) }
        }

        state.databaseStats?.let { stats ->
            item { DatabaseSizeCard(stats) }
            item { TableStatsCard(stats) }
        }

        // ── Maintenance actions ────────────────────────────────────────
        item {
            Text(
                s[StringResource.ADMIN_MAINTENANCE],
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.padding(top = ZyntaSpacing.sm),
            )
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(ZyntaSpacing.sm)) {
                OutlinedButton(
                    onClick = { onIntent(AdminIntent.RunVacuum) },
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(Icons.Default.CleaningServices, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(s[StringResource.ADMIN_VACUUM_DB])
                }
                OutlinedButton(
                    onClick = { showPurgeDialog = true },
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(Icons.Default.DeleteSweep, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(s[StringResource.ADMIN_PURGE_OLD_DATA])
                }
            }
        }

        // Last vacuum / purge results
        state.lastVacuumResult?.let { res ->
            item {
                ResultBanner(
                    label = s[StringResource.ADMIN_LAST_VACUUM],
                    message = s[StringResource.ADMIN_KB_FREED, res.bytesFreed / 1024, res.durationMs],
                    success = res.success,
                )
            }
        }
        state.lastPurgeResult?.let { res ->
            item {
                ResultBanner(
                    label = s[StringResource.ADMIN_LAST_PURGE],
                    message = s[StringResource.ADMIN_ROWS_REMOVED, res.bytesFreed, res.durationMs],
                    success = res.success,
                )
            }
        }

        item { Spacer(Modifier.height(80.dp)) }
    }

    if (showPurgeDialog) {
        PurgeConfirmDialog(
            onConfirm = { days ->
                onIntent(AdminIntent.PurgeExpiredData(days))
                showPurgeDialog = false
            },
            onDismiss = { showPurgeDialog = false },
        )
    }
}

// ─── Private composables ──────────────────────────────────────────────────────

@Composable
private fun ConnectivityCard(health: SystemHealth) {
    val s = LocalStrings.current
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(ZyntaSpacing.md),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = if (health.isOnline) Icons.Default.Wifi else Icons.Default.WifiOff,
                    contentDescription = null,
                    tint = if (health.isOnline) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                )
                Spacer(Modifier.width(ZyntaSpacing.sm))
                Text(
                    text = if (health.isOnline) s[StringResource.ADMIN_ONLINE] else s[StringResource.ADMIN_OFFLINE],
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                )
            }
            SyncStatusChip(health)
        }
    }
}

@Composable
private fun SyncStatusChip(health: SystemHealth) {
    val s = LocalStrings.current
    val isHealthy = health.isSyncHealthy
    AssistChip(
        onClick = {},
        label = {
            Text(
                text = if (isHealthy) s[StringResource.ADMIN_SYNC_OK] else s[StringResource.ADMIN_SYNC_PENDING, health.pendingSyncCount],
                style = MaterialTheme.typography.labelSmall,
            )
        },
        leadingIcon = {
            Icon(
                imageVector = if (isHealthy) Icons.Default.CloudDone else Icons.Default.CloudSync,
                contentDescription = null,
                modifier = Modifier.size(14.dp),
            )
        },
        colors = AssistChipDefaults.assistChipColors(
            containerColor = if (isHealthy) MaterialTheme.colorScheme.secondaryContainer
            else MaterialTheme.colorScheme.errorContainer,
        ),
    )
}

@Composable
private fun MemoryCard(health: SystemHealth) {
    val s = LocalStrings.current
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(ZyntaSpacing.md)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(s[StringResource.ADMIN_MEMORY], style = MaterialTheme.typography.titleSmall)
                Text(
                    "%.1f%%".format(health.memoryUsagePercent),
                    style = MaterialTheme.typography.bodySmall,
                    color = if (health.memoryUsagePercent > 80)
                        MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.primary,
                )
            }
            Spacer(Modifier.height(4.dp))
            LinearProgressIndicator(
                progress = { (health.memoryUsagePercent / 100.0).toFloat().coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth(),
                color = if (health.memoryUsagePercent > 80) MaterialTheme.colorScheme.error
                else MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "${health.usedMemoryBytes / (1024 * 1024)} MB / ${health.totalMemoryBytes / (1024 * 1024)} MB",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun SyncCard(health: SystemHealth) {
    val s = LocalStrings.current
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(ZyntaSpacing.md),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column {
                Text(s[StringResource.ADMIN_SYNC_QUEUE], style = MaterialTheme.typography.titleSmall)
                Text(
                    text = s[StringResource.ADMIN_PENDING_OPS, health.pendingSyncCount],
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                text = health.pendingSyncCount.toString(),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = if (health.pendingSyncCount > 0) MaterialTheme.colorScheme.error
                else MaterialTheme.colorScheme.primary,
            )
        }
    }
}

@Composable
private fun AppVersionCard(health: SystemHealth) {
    val s = LocalStrings.current
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(ZyntaSpacing.md),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(s[StringResource.ADMIN_APP_VERSION], style = MaterialTheme.typography.titleSmall)
            Text(
                text = "${health.appVersion} (build ${health.buildNumber})",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun DatabaseSizeCard(stats: DatabaseStats) {
    val s = LocalStrings.current
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(ZyntaSpacing.md)) {
            Text(s[StringResource.ADMIN_DATABASE], style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(4.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                MetricItem("Size", "%.2f MB".format(stats.sizeBytes / (1024.0 * 1024.0)))
                MetricItem("WAL", "%.2f MB".format(stats.walSizeBytes / (1024.0 * 1024.0)))
                MetricItem("Rows", stats.totalRows.toString())
            }
        }
    }
}

@Composable
private fun MetricItem(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun TableStatsCard(stats: DatabaseStats) {
    val s = LocalStrings.current
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(ZyntaSpacing.md)) {
            Text(s[StringResource.ADMIN_TABLE_ROW_COUNTS], style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(ZyntaSpacing.sm))
            stats.tables.forEach { table ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        table.tableName,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        table.rowCount.toString(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun ResultBanner(label: String, message: String, success: Boolean) {
    Surface(
        color = if (success) MaterialTheme.colorScheme.secondaryContainer
        else MaterialTheme.colorScheme.errorContainer,
        shape = MaterialTheme.shapes.small,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(ZyntaSpacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = if (success) Icons.Default.CheckCircle else Icons.Default.Error,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = if (success) MaterialTheme.colorScheme.onSecondaryContainer
                else MaterialTheme.colorScheme.onErrorContainer,
            )
            Spacer(Modifier.width(4.dp))
            Text(
                text = "$label: $message",
                style = MaterialTheme.typography.bodySmall,
                color = if (success) MaterialTheme.colorScheme.onSecondaryContainer
                else MaterialTheme.colorScheme.onErrorContainer,
            )
        }
    }
}

@Composable
private fun PurgeConfirmDialog(
    onConfirm: (days: Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val s = LocalStrings.current
    var days by remember { mutableStateOf("30") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(s[StringResource.ADMIN_PURGE_DIALOG_TITLE]) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(ZyntaSpacing.sm)) {
                Text(
                    s[StringResource.ADMIN_PURGE_DIALOG_MSG],
                    style = MaterialTheme.typography.bodyMedium,
                )
                OutlinedTextField(
                    value = days,
                    onValueChange = { if (it.all { c -> c.isDigit() }) days = it },
                    label = { Text(s[StringResource.ADMIN_OLDER_THAN_DAYS]) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(days.toIntOrNull() ?: 30) }) { Text(s[StringResource.ADMIN_PURGE_ACTION]) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(s[StringResource.COMMON_CANCEL]) }
        },
    )
}
