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
import com.zyntasolutions.zyntapos.domain.model.AuditPolicy
import com.zyntasolutions.zyntapos.feature.settings.AuditPolicyIntent
import com.zyntasolutions.zyntapos.feature.settings.AuditPolicyState

/**
 * Audit Policy settings screen (Sprint 23 task 23.9 — persistence slice).
 *
 * Renders one [Switch] per [AuditPolicy.Category]. Toggling fires
 * [AuditPolicyIntent.Toggle], which the view-model persists optimistically
 * via `SetAuditPolicyEnabledUseCase`. The `ROLE_CHANGES` switch is rendered
 * disabled — its corresponding setter rejects `false` writes anyway, so the
 * UI just locks the affordance to keep the contract visible.
 *
 * @param state    Current loaded policy + transient error from
 *                 [com.zyntasolutions.zyntapos.feature.settings.AuditPolicyViewModel].
 * @param onIntent Pipe back to `viewModel.dispatch`.
 * @param onBack   Back navigation handler.
 */
@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun AuditPolicySettingsScreen(
    state: AuditPolicyState,
    onIntent: (AuditPolicyIntent) -> Unit,
    onBack: () -> Unit,
) {
    val s = LocalStrings.current
    val rows: List<AuditPolicyRow> = listOf(
        AuditPolicyRow(s[StringResource.SETTINGS_AUDIT_CAT_LOGIN], Icons.Default.Login, AuditPolicy.Category.LOGIN),
        AuditPolicyRow(s[StringResource.SETTINGS_AUDIT_CAT_PRODUCT], Icons.Default.Inventory, AuditPolicy.Category.PRODUCT),
        AuditPolicyRow(s[StringResource.SETTINGS_AUDIT_CAT_ORDER], Icons.Default.Receipt, AuditPolicy.Category.ORDER),
        AuditPolicyRow(s[StringResource.SETTINGS_AUDIT_CAT_CUSTOMER], Icons.Default.People, AuditPolicy.Category.CUSTOMER),
        AuditPolicyRow(s[StringResource.SETTINGS_AUDIT_CAT_SETTINGS], Icons.Default.Tune, AuditPolicy.Category.SETTINGS),
        AuditPolicyRow(s[StringResource.SETTINGS_AUDIT_CAT_PAYROLL], Icons.Default.Paid, AuditPolicy.Category.PAYROLL),
        AuditPolicyRow(s[StringResource.SETTINGS_AUDIT_CAT_BACKUP], Icons.Default.Restore, AuditPolicy.Category.BACKUP),
        AuditPolicyRow(s[StringResource.SETTINGS_AUDIT_CAT_ROLE], Icons.Default.Security, AuditPolicy.Category.ROLE_CHANGES),
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
                        val locked = row.category == AuditPolicy.Category.ROLE_CHANGES
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
                                Switch(
                                    checked = state.policy.isEnabled(row.category),
                                    onCheckedChange = if (locked) null else { _ ->
                                        onIntent(AuditPolicyIntent.Toggle(row.category))
                                    },
                                    enabled = !locked && !state.isLoading,
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
}

private data class AuditPolicyRow(
    val label: String,
    val icon: ImageVector,
    val category: AuditPolicy.Category,
)
