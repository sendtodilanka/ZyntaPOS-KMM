package com.zyntasolutions.zyntapos.feature.settings.screen

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.HistoryToggleOff
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import com.zyntasolutions.zyntapos.core.i18n.StringResource
import com.zyntasolutions.zyntapos.designsystem.components.LocalStrings
import com.zyntasolutions.zyntapos.designsystem.layouts.ZyntaPageScaffold
import com.zyntasolutions.zyntapos.designsystem.tokens.ZyntaSpacing
import com.zyntasolutions.zyntapos.domain.model.DataRetentionPolicy
import com.zyntasolutions.zyntapos.feature.settings.DataRetentionIntent
import com.zyntasolutions.zyntapos.feature.settings.DataRetentionState

/**
 * Data Retention settings screen (Sprint 23 task 23.9 — persistence slice 3/3).
 *
 * Renders three rows backed by [DataRetentionState]:
 *   1. Audit log retention — int dropdown {30, 90, 180, 365} days
 *   2. Sync queue retention — int dropdown {7, 14, 30} days
 *   3. Report data retention — int dropdown {6, 12, 24} months
 *
 * Each tap dispatches a `DataRetentionIntent.Apply` carrying the next
 * snapshot. The "Run Purge Now" action is rendered disabled — wiring it
 * to a real `PurgeExpiredDataUseCase` is the next step (the use case
 * does not exist yet).
 *
 * @param state    Current loaded policy from
 *                 [com.zyntasolutions.zyntapos.feature.settings.DataRetentionViewModel].
 * @param onIntent Pipe back to `viewModel.dispatch`.
 * @param onBack   Back navigation handler.
 */
@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun DataRetentionSettingsScreen(
    state: DataRetentionState,
    onIntent: (DataRetentionIntent) -> Unit,
    onBack: () -> Unit,
) {
    val s = LocalStrings.current
    var openDialog by remember { mutableStateOf<DialogKind?>(null) }

    ZyntaPageScaffold(
        title = s[StringResource.SETTINGS_DATA_RETENTION],
        onNavigateBack = onBack,
    ) { innerPadding ->
        LazyColumn(
            contentPadding = PaddingValues(
                start = ZyntaSpacing.md,
                end = ZyntaSpacing.md,
                top = innerPadding.calculateTopPadding() + ZyntaSpacing.md,
                bottom = innerPadding.calculateBottomPadding() + ZyntaSpacing.md,
            ),
            verticalArrangement = Arrangement.spacedBy(ZyntaSpacing.md),
        ) {
            item {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    RetentionRow(
                        label = s[StringResource.SETTINGS_AUDIT_RETENTION],
                        value = "${state.policy.auditLogRetentionDays} days",
                        icon = Icons.Default.HistoryToggleOff,
                        enabled = !state.isLoading,
                        onClick = { openDialog = DialogKind.AUDIT_LOG },
                    )
                    RetentionRow(
                        label = s[StringResource.SETTINGS_SYNC_RETENTION],
                        value = "${state.policy.syncQueueRetentionDays} days",
                        icon = Icons.Default.Sync,
                        enabled = !state.isLoading,
                        onClick = { openDialog = DialogKind.SYNC_QUEUE },
                    )
                    RetentionRow(
                        label = s[StringResource.SETTINGS_REPORT_RETENTION],
                        value = "${state.policy.reportRetentionMonths} months",
                        icon = Icons.Default.Assessment,
                        enabled = !state.isLoading,
                        onClick = { openDialog = DialogKind.REPORTS },
                    )
                }
            }
            // Run Purge Now button — temporarily removed to bisect Step[1] failure
            state.error?.let { msg ->
                item {
                    Text(
                        text = msg,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        }
    }

    when (openDialog) {
        DialogKind.AUDIT_LOG -> RetentionChoiceDialog(
            title = s[StringResource.SETTINGS_AUDIT_RETENTION],
            options = DataRetentionPolicy.ALLOWED_AUDIT_LOG_DAYS,
            current = state.policy.auditLogRetentionDays,
            unitSuffix = " days",
            onSelect = { value ->
                onIntent(DataRetentionIntent.Apply(state.policy.copy(auditLogRetentionDays = value)))
                openDialog = null
            },
            onDismiss = { openDialog = null },
        )
        DialogKind.SYNC_QUEUE -> RetentionChoiceDialog(
            title = s[StringResource.SETTINGS_SYNC_RETENTION],
            options = DataRetentionPolicy.ALLOWED_SYNC_QUEUE_DAYS,
            current = state.policy.syncQueueRetentionDays,
            unitSuffix = " days",
            onSelect = { value ->
                onIntent(DataRetentionIntent.Apply(state.policy.copy(syncQueueRetentionDays = value)))
                openDialog = null
            },
            onDismiss = { openDialog = null },
        )
        DialogKind.REPORTS -> RetentionChoiceDialog(
            title = s[StringResource.SETTINGS_REPORT_RETENTION],
            options = DataRetentionPolicy.ALLOWED_REPORT_MONTHS,
            current = state.policy.reportRetentionMonths,
            unitSuffix = " months",
            onSelect = { value ->
                onIntent(DataRetentionIntent.Apply(state.policy.copy(reportRetentionMonths = value)))
                openDialog = null
            },
            onDismiss = { openDialog = null },
        )
        null -> Unit
    }
}

private enum class DialogKind { AUDIT_LOG, SYNC_QUEUE, REPORTS }

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun RetentionRow(
    label: String,
    value: String,
    icon: ImageVector,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    ListItem(
        headlineContent = { Text(label, style = MaterialTheme.typography.bodyLarge) },
        trailingContent = {
            Text(
                value,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
            )
        },
        leadingContent = {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        },
        colors = ListItemDefaults.colors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onClick),
    )
}

@Composable
private fun RetentionChoiceDialog(
    title: String,
    options: List<Int>,
    current: Int,
    unitSuffix: String,
    onSelect: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val s = LocalStrings.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                options.forEach { value ->
                    ListItem(
                        headlineContent = {
                            Text(
                                "$value$unitSuffix",
                                style = MaterialTheme.typography.bodyLarge,
                                color = if (value == current)
                                    MaterialTheme.colorScheme.primary
                                else
                                    MaterialTheme.colorScheme.onSurface,
                            )
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(value) },
                    )
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(s[StringResource.COMMON_CANCEL]) }
        },
    )
}
