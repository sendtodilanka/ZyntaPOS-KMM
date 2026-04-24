package com.zyntasolutions.zyntapos.feature.settings.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Inventory
import androidx.compose.material.icons.filled.Login
import androidx.compose.material.icons.filled.Paid
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import com.zyntasolutions.zyntapos.core.i18n.StringResource
import com.zyntasolutions.zyntapos.designsystem.components.LocalStrings
import com.zyntasolutions.zyntapos.designsystem.layouts.ZyntaPageScaffold
import com.zyntasolutions.zyntapos.designsystem.tokens.ZyntaSpacing

/**
 * Audit Policy read-only shell (Phase 3 Sprint 23).
 *
 * Renders the list of audited action categories with their current on/off
 * state. Switches are present but disabled — toggling writes to the settings
 * store is Sprint 24 work. "Role changes" is pinned on and always disabled.
 */
@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun AuditPolicySettingsScreen(onBack: () -> Unit) {
    val s = LocalStrings.current
    val rows = listOf(
        AuditPolicyRow(s[StringResource.SETTINGS_AUDIT_CAT_LOGIN], Icons.Default.Login, enabled = true),
        AuditPolicyRow(s[StringResource.SETTINGS_AUDIT_CAT_PRODUCT], Icons.Default.Inventory, enabled = true),
        AuditPolicyRow(s[StringResource.SETTINGS_AUDIT_CAT_ORDER], Icons.Default.Receipt, enabled = true),
        AuditPolicyRow(s[StringResource.SETTINGS_AUDIT_CAT_CUSTOMER], Icons.Default.People, enabled = true),
        AuditPolicyRow(s[StringResource.SETTINGS_AUDIT_CAT_SETTINGS], Icons.Default.Tune, enabled = true),
        AuditPolicyRow(s[StringResource.SETTINGS_AUDIT_CAT_PAYROLL], Icons.Default.Paid, enabled = true),
        AuditPolicyRow(s[StringResource.SETTINGS_AUDIT_CAT_BACKUP], Icons.Default.Restore, enabled = true),
        AuditPolicyRow(s[StringResource.SETTINGS_AUDIT_CAT_ROLE], Icons.Default.Security, enabled = true),
    )
    ZyntaPageScaffold(
        title = s[StringResource.SETTINGS_AUDIT_POLICY],
        onNavigateBack = onBack,
    ) { innerPadding ->
        LazyColumn(
            contentPadding = PaddingValues(
                start = ZyntaSpacing.md,
                end = ZyntaSpacing.md,
                top = innerPadding.calculateTopPadding() + ZyntaSpacing.md,
                bottom = innerPadding.calculateBottomPadding() + ZyntaSpacing.md,
            ),
            verticalArrangement = Arrangement.spacedBy(ZyntaSpacing.sm),
        ) {
            item {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    rows.forEach { row ->
                        ListItem(
                            headlineContent = {
                                Text(row.label, style = MaterialTheme.typography.bodyLarge)
                            },
                            leadingContent = {
                                Icon(
                                    row.icon,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                )
                            },
                            trailingContent = {
                                // Disabled — wiring is Sprint 24 follow-up.
                                Switch(
                                    checked = row.enabled,
                                    onCheckedChange = null,
                                    enabled = false,
                                )
                            },
                            colors = ListItemDefaults.colors(
                                containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                            ),
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
            item {
                Text(
                    text = s[StringResource.COMMON_READ_ONLY],
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

private data class AuditPolicyRow(
    val label: String,
    val icon: ImageVector,
    val enabled: Boolean,
)
