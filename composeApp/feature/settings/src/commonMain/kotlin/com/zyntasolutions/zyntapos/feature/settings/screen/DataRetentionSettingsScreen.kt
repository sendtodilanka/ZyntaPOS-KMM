package com.zyntasolutions.zyntapos.feature.settings.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.HistoryToggleOff
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import com.zyntasolutions.zyntapos.core.i18n.StringResource
import com.zyntasolutions.zyntapos.designsystem.components.LocalStrings
import com.zyntasolutions.zyntapos.designsystem.layouts.ZyntaPageScaffold
import com.zyntasolutions.zyntapos.designsystem.tokens.ZyntaSpacing

/**
 * Data Retention read-only shell (Phase 3 Sprint 23).
 *
 * Displays current retention windows for audit log, sync queue, and report
 * data. The "Run Purge Now" affordance is present but disabled — wiring to
 * `PurgeExpiredDataUseCase` is Sprint 24 follow-up work.
 */
@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun DataRetentionSettingsScreen(onBack: () -> Unit) {
    val s = LocalStrings.current
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
                        value = "90 days",
                        icon = Icons.Default.HistoryToggleOff,
                    )
                    RetentionRow(
                        label = s[StringResource.SETTINGS_SYNC_RETENTION],
                        value = "14 days",
                        icon = Icons.Default.Sync,
                    )
                    RetentionRow(
                        label = s[StringResource.SETTINGS_REPORT_RETENTION],
                        value = "12 months",
                        icon = Icons.Default.Assessment,
                    )
                }
            }
            item {
                Button(
                    onClick = {},
                    enabled = false,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(
                        Icons.Default.CleaningServices,
                        contentDescription = null,
                        modifier = Modifier.padding(end = ZyntaSpacing.xs),
                    )
                    Text(s[StringResource.SETTINGS_PURGE_NOW])
                }
            }
            item {
                Text(
                    text = s[StringResource.COMMON_READ_ONLY],
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = ZyntaSpacing.xs),
                )
            }
        }
    }
}

@Composable
private fun RetentionRow(label: String, value: String, icon: ImageVector) {
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
        modifier = Modifier.fillMaxWidth(),
    )
}
