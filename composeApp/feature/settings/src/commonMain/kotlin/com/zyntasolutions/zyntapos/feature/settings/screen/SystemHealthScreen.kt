package com.zyntasolutions.zyntapos.feature.settings.screen

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SdStorage
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.zyntasolutions.zyntapos.core.health.DatabaseStatus
import com.zyntasolutions.zyntapos.core.health.HealthSnapshot
import com.zyntasolutions.zyntapos.core.health.OverallStatus
import com.zyntasolutions.zyntapos.core.health.SystemHealthTracker
import com.zyntasolutions.zyntapos.designsystem.layouts.ZyntaPageScaffold
import com.zyntasolutions.zyntapos.designsystem.tokens.ZyntaSpacing
import org.koin.compose.koinInject

// ─────────────────────────────────────────────────────────────────────────────
// SystemHealthScreen — Real-time system diagnostics dashboard
//
// Displays memory, disk, database, and runtime health indicators.
// Auto-refreshes every 30 seconds while visible; stops on dispose.
// ─────────────────────────────────────────────────────────────────────────────

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun SystemHealthScreen(
    onBack: () -> Unit,
    healthTracker: SystemHealthTracker = koinInject(),
) {
    val snapshot by healthTracker.snapshot.collectAsState()
    val scope = rememberCoroutineScope()

    DisposableEffect(healthTracker) {
        healthTracker.startAutoRefresh(30_000L)
        onDispose { healthTracker.stopAutoRefresh() }
    }

    ZyntaPageScaffold(
        title = "System Health",
        onNavigateBack = onBack,
        actions = {
            IconButton(onClick = { scope.launch { healthTracker.refresh() } }) {
                Icon(Icons.Filled.Refresh, contentDescription = "Refresh")
            }
        },
    ) { innerPadding ->
        LazyColumn(
            contentPadding = PaddingValues(
                start = ZyntaSpacing.md,
                end = ZyntaSpacing.md,
                top = innerPadding.calculateTopPadding() + ZyntaSpacing.md,
                bottom = innerPadding.calculateBottomPadding() + ZyntaSpacing.md,
            ),
            verticalArrangement = Arrangement.spacedBy(ZyntaSpacing.md),
            modifier = Modifier.fillMaxSize(),
        ) {
            // ── Overall Status Banner ────────────────────────────────────────
            item { OverallStatusBanner(snapshot.overallStatus) }

            // ── Memory Card ──────────────────────────────────────────────────
            item { MemoryHealthCard(snapshot) }

            // ── Disk Card ────────────────────────────────────────────────────
            item { DiskHealthCard(snapshot) }

            // ── Database Card ────────────────────────────────────────────────
            item { DatabaseHealthCard(snapshot) }

            // ── Runtime Info Card ────────────────────────────────────────────
            item { RuntimeInfoCard(snapshot) }
        }
    }
}

// ─── Overall Status Banner ───────────────────────────────────────────────────

@Composable
private fun OverallStatusBanner(status: OverallStatus) {
    val (icon, label, containerColor, contentColor) = when (status) {
        OverallStatus.HEALTHY -> StatusStyle(
            Icons.Filled.CheckCircle, "All Systems Healthy",
            MaterialTheme.colorScheme.primaryContainer,
            MaterialTheme.colorScheme.onPrimaryContainer,
        )
        OverallStatus.WARNING -> StatusStyle(
            Icons.Filled.Warning, "Performance Warning",
            MaterialTheme.colorScheme.tertiaryContainer,
            MaterialTheme.colorScheme.onTertiaryContainer,
        )
        OverallStatus.CRITICAL -> StatusStyle(
            Icons.Filled.Error, "Critical Issue Detected",
            MaterialTheme.colorScheme.errorContainer,
            MaterialTheme.colorScheme.onErrorContainer,
        )
        OverallStatus.UNKNOWN -> StatusStyle(
            Icons.Filled.Warning, "Collecting Metrics...",
            MaterialTheme.colorScheme.surfaceContainerHigh,
            MaterialTheme.colorScheme.onSurface,
        )
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = containerColor),
        modifier = Modifier.fillMaxWidth().animateContentSize(),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(ZyntaSpacing.lg),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = contentColor,
                modifier = Modifier.size(32.dp),
            )
            Spacer(Modifier.width(ZyntaSpacing.md))
            Column {
                Text(
                    text = label,
                    style = MaterialTheme.typography.titleMedium,
                    color = contentColor,
                )
                Text(
                    text = statusSubtitle(status),
                    style = MaterialTheme.typography.bodySmall,
                    color = contentColor.copy(alpha = 0.8f),
                )
            }
        }
    }
}

private fun statusSubtitle(status: OverallStatus): String = when (status) {
    OverallStatus.HEALTHY -> "Memory, disk, and database are operating normally."
    OverallStatus.WARNING -> "Some metrics are approaching their limits."
    OverallStatus.CRITICAL -> "Immediate attention required. Check details below."
    OverallStatus.UNKNOWN -> "Waiting for the first health check to complete..."
}

// ─── Memory Health Card ──────────────────────────────────────────────────────

@Composable
private fun MemoryHealthCard(snapshot: HealthSnapshot) {
    HealthSectionCard(
        title = "Memory",
        icon = Icons.Filled.Memory,
    ) {
        UsageBar(
            label = "Heap Usage",
            usedBytes = snapshot.heapUsedBytes,
            totalBytes = snapshot.heapMaxBytes,
            percent = snapshot.heapUsagePercent,
        )
        HorizontalDivider(modifier = Modifier.padding(horizontal = ZyntaSpacing.md))
        MetricRow("Used", formatBytes(snapshot.heapUsedBytes))
        HorizontalDivider(modifier = Modifier.padding(horizontal = ZyntaSpacing.md))
        MetricRow("Maximum", formatBytes(snapshot.heapMaxBytes))
    }
}

// ─── Disk Health Card ────────────────────────────────────────────────────────

@Composable
private fun DiskHealthCard(snapshot: HealthSnapshot) {
    HealthSectionCard(
        title = "Disk Storage",
        icon = Icons.Filled.SdStorage,
    ) {
        UsageBar(
            label = "Disk Usage",
            usedBytes = snapshot.diskTotalBytes - snapshot.diskFreeBytes,
            totalBytes = snapshot.diskTotalBytes,
            percent = snapshot.diskUsagePercent,
        )
        HorizontalDivider(modifier = Modifier.padding(horizontal = ZyntaSpacing.md))
        MetricRow("Free Space", formatBytes(snapshot.diskFreeBytes))
        HorizontalDivider(modifier = Modifier.padding(horizontal = ZyntaSpacing.md))
        MetricRow("Total Space", formatBytes(snapshot.diskTotalBytes))
    }
}

// ─── Database Health Card ────────────────────────────────────────────────────

@Composable
private fun DatabaseHealthCard(snapshot: HealthSnapshot) {
    HealthSectionCard(
        title = "Database",
        icon = Icons.Filled.Storage,
    ) {
        ListItem(
            headlineContent = {
                Text("Status", style = MaterialTheme.typography.bodyMedium)
            },
            trailingContent = {
                val (statusText, statusColor) = when (snapshot.databaseStatus) {
                    DatabaseStatus.HEALTHY -> "Healthy" to MaterialTheme.colorScheme.primary
                    DatabaseStatus.DEGRADED -> "Degraded" to MaterialTheme.colorScheme.tertiary
                    DatabaseStatus.ERROR -> "Error" to MaterialTheme.colorScheme.error
                    DatabaseStatus.UNKNOWN -> "Unknown" to MaterialTheme.colorScheme.onSurfaceVariant
                }
                Text(
                    text = statusText,
                    style = MaterialTheme.typography.labelLarge,
                    color = statusColor,
                )
            },
        )
        HorizontalDivider(modifier = Modifier.padding(horizontal = ZyntaSpacing.md))
        MetricRow("Database Size", formatBytes(snapshot.databaseSizeBytes))
    }
}

// ─── Runtime Info Card ───────────────────────────────────────────────────────

@Composable
private fun RuntimeInfoCard(snapshot: HealthSnapshot) {
    HealthSectionCard(
        title = "Runtime",
        icon = Icons.Filled.Timer,
    ) {
        MetricRow("Platform", snapshot.platformDescription.ifBlank { "—" })
        HorizontalDivider(modifier = Modifier.padding(horizontal = ZyntaSpacing.md))
        MetricRow("Uptime", formatDuration(snapshot.uptimeMillis))
        HorizontalDivider(modifier = Modifier.padding(horizontal = ZyntaSpacing.md))
        MetricRow("CPU Cores", "${snapshot.availableProcessors}")
        HorizontalDivider(modifier = Modifier.padding(horizontal = ZyntaSpacing.md))
        MetricRow("Network", if (snapshot.isNetworkAvailable) "Connected" else "Offline")
    }
}

// ─── Shared sub-composables ──────────────────────────────────────────────────

private data class StatusStyle(
    val icon: ImageVector,
    val label: String,
    val containerColor: Color,
    val contentColor: Color,
)

@Composable
private fun HealthSectionCard(
    title: String,
    icon: ImageVector,
    content: @Composable () -> Unit,
) {
    Column {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(start = ZyntaSpacing.xs, bottom = ZyntaSpacing.xs),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(ZyntaSpacing.xs))
            Text(
                text = title.uppercase(),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        Card(
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            ),
            modifier = Modifier.fillMaxWidth(),
        ) {
            content()
        }
    }
}

@Composable
private fun UsageBar(
    label: String,
    usedBytes: Long,
    totalBytes: Long,
    percent: Float,
) {
    Column(modifier = Modifier.padding(ZyntaSpacing.md)) {
        Row(
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                text = "${formatBytes(usedBytes)} / ${formatBytes(totalBytes)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(ZyntaSpacing.xs))
        LinearProgressIndicator(
            progress = { (percent / 100f).coerceIn(0f, 1f) },
            modifier = Modifier.fillMaxWidth().height(8.dp),
            color = progressColor(percent),
            trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
        )
        Spacer(Modifier.height(ZyntaSpacing.xs))
        Text(
            text = "${"%.1f".format(percent)}% used",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun progressColor(percent: Float): Color = when {
    percent > 90f -> MaterialTheme.colorScheme.error
    percent > 75f -> MaterialTheme.colorScheme.tertiary
    else -> MaterialTheme.colorScheme.primary
}

@Composable
private fun MetricRow(label: String, value: String) {
    ListItem(
        headlineContent = {
            Text(label, style = MaterialTheme.typography.bodyMedium)
        },
        trailingContent = {
            Text(
                text = value,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
    )
}

// ─── Formatting helpers ──────────────────────────────────────────────────────

private fun formatBytes(bytes: Long): String {
    if (bytes <= 0) return "0 B"
    val units = arrayOf("B", "KB", "MB", "GB", "TB")
    var value = bytes.toDouble()
    var unitIndex = 0
    while (value >= 1024 && unitIndex < units.lastIndex) {
        value /= 1024
        unitIndex++
    }
    return if (unitIndex == 0) "$bytes B"
    else "${"%.1f".format(value)} ${units[unitIndex]}"
}

private fun formatDuration(millis: Long): String {
    if (millis <= 0) return "—"
    val seconds = millis / 1000
    val minutes = seconds / 60
    val hours = minutes / 60
    val days = hours / 24
    return buildString {
        if (days > 0) append("${days}d ")
        if (hours % 24 > 0) append("${hours % 24}h ")
        if (minutes % 60 > 0) append("${minutes % 60}m ")
        if (days == 0L) append("${seconds % 60}s")
    }.trim()
}
