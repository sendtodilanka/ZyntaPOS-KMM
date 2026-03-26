package com.zyntasolutions.zyntapos.feature.settings.screen

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.AlertDialog
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import com.zyntasolutions.zyntapos.core.i18n.StringResource
import com.zyntasolutions.zyntapos.designsystem.components.LocalStrings
import com.zyntasolutions.zyntapos.designsystem.layouts.ZyntaPageScaffold
import com.zyntasolutions.zyntapos.designsystem.tokens.ZyntaSpacing
import com.zyntasolutions.zyntapos.feature.settings.SettingsIntent
import com.zyntasolutions.zyntapos.feature.settings.SettingsState
import androidx.compose.ui.tooling.preview.Preview

// ─────────────────────────────────────────────────────────────────────────────
// SecuritySettingsScreen — configurable security policy.
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Security settings screen.
 *
 * Allows the cashier to configure the auto-lock timeout. PIN policy and
 * role-based access rows are informational (read-only) because they are
 * enforced by [PinManager] and [RbacEngine] respectively.
 *
 * @param state    Security state slice from [SettingsViewModel].
 * @param onIntent Intent dispatcher to [SettingsViewModel].
 * @param onBack   Back navigation callback.
 */
@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun SecuritySettingsScreen(
    state: SettingsState.SecurityState,
    onIntent: (SettingsIntent) -> Unit,
    onBack: () -> Unit,
    onNavigateToRbacManagement: () -> Unit = {},
) {
    val s = LocalStrings.current
    LaunchedEffect(Unit) { onIntent(SettingsIntent.LoadSecuritySettings) }

    ZyntaPageScaffold(
        title = s[StringResource.SETTINGS_SECURITY],
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
                SectionLabel(s[StringResource.SETTINGS_SECURITY_SESSION])
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    ListItem(
                        headlineContent = {
                            Text(s[StringResource.SETTINGS_SECURITY_AUTO_LOCK_TIMEOUT], style = MaterialTheme.typography.bodyLarge)
                        },
                        supportingContent = {
                            Text(
                                s[StringResource.SETTINGS_SECURITY_AUTO_LOCK_DESC],
                                style = MaterialTheme.typography.bodySmall,
                            )
                        },
                        leadingContent = {
                            Icon(
                                Icons.Default.Timer,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                            )
                        },
                        trailingContent = {
                            Text(
                                text = autoLockLabel(state.autoLockMinutes),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        },
                        colors = ListItemDefaults.colors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onIntent(SettingsIntent.OpenAutoLockDialog) },
                    )
                }
            }

            item {
                SectionLabel(s[StringResource.SETTINGS_SECURITY_PIN_POLICY])
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    ListItem(
                        headlineContent = {
                            Text(s[StringResource.SETTINGS_SECURITY_PIN_REQUIREMENTS], style = MaterialTheme.typography.bodyLarge)
                        },
                        supportingContent = {
                            Text(
                                s[StringResource.SETTINGS_SECURITY_PIN_REQUIREMENTS_DESC],
                                style = MaterialTheme.typography.bodySmall,
                            )
                        },
                        leadingContent = {
                            Icon(
                                Icons.Default.Lock,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                            )
                        },
                        trailingContent = {
                            Text(
                                s[StringResource.SETTINGS_SECURITY_FIXED],
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        },
                        colors = ListItemDefaults.colors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                        ),
                    )
                }
            }

            item {
                SectionLabel(s[StringResource.SETTINGS_SECURITY_RBAC])
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    ListItem(
                        headlineContent = {
                            Text(s[StringResource.SETTINGS_SECURITY_ROLES_PERMISSIONS], style = MaterialTheme.typography.bodyLarge)
                        },
                        supportingContent = {
                            Text(
                                s[StringResource.SETTINGS_SECURITY_ROLES_PERMISSIONS_DESC],
                                style = MaterialTheme.typography.bodySmall,
                            )
                        },
                        leadingContent = {
                            Icon(
                                Icons.Default.Security,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                            )
                        },
                        trailingContent = {
                            Icon(
                                Icons.Default.KeyboardArrowRight,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        },
                        colors = ListItemDefaults.colors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onNavigateToRbacManagement() },
                    )
                }
            }
        }
    }

    if (state.isAutoLockDialogVisible) {
        AutoLockTimeoutDialog(
            currentMinutes = state.autoLockMinutes,
            onSelect = { onIntent(SettingsIntent.SetAutoLockTimeout(it)) },
            onDismiss = { onIntent(SettingsIntent.DismissAutoLockDialog) },
        )
    }
}

// ── AutoLockTimeoutDialog ─────────────────────────────────────────────────────

private val AUTO_LOCK_MINUTES = listOf(0, 1, 2, 5, 10, 15, 30)

/**
 * Dialog for selecting the auto-lock timeout duration.
 *
 * Tapping an option immediately dispatches [SettingsIntent.SetAutoLockTimeout]
 * and dismisses the dialog via state (the VM sets [SettingsState.SecurityState.isAutoLockDialogVisible]
 * to `false` after persisting the selection).
 */
@Composable
private fun AutoLockTimeoutDialog(
    currentMinutes: Int,
    onSelect: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val s = LocalStrings.current
    val autoLockLabels = mapOf(
        0 to s[StringResource.SETTINGS_SECURITY_NEVER],
        1 to s[StringResource.SETTINGS_SECURITY_1_MINUTE],
        2 to s[StringResource.SETTINGS_SECURITY_2_MINUTES],
        5 to s[StringResource.SETTINGS_SECURITY_5_MINUTES],
        10 to s[StringResource.SETTINGS_SECURITY_10_MINUTES],
        15 to s[StringResource.SETTINGS_SECURITY_15_MINUTES],
        30 to s[StringResource.SETTINGS_SECURITY_30_MINUTES],
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(s[StringResource.SETTINGS_SECURITY_AUTO_LOCK_TIMEOUT]) },
        text = {
            Column {
                AUTO_LOCK_MINUTES.forEach { minutes ->
                    val label = autoLockLabels[minutes] ?: "$minutes min"
                    ListItem(
                        headlineContent = {
                            Text(
                                label,
                                style = MaterialTheme.typography.bodyLarge,
                                color = if (minutes == currentMinutes)
                                    MaterialTheme.colorScheme.primary
                                else
                                    MaterialTheme.colorScheme.onSurface,
                            )
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(minutes) },
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

// ── Helpers ───────────────────────────────────────────────────────────────────

private fun autoLockLabel(minutes: Int): String = when (minutes) {
    0    -> "Never"
    1    -> "1 min"
    else -> "$minutes min"
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = ZyntaSpacing.xs, bottom = ZyntaSpacing.xs),
    )
}

// ── Preview ───────────────────────────────────────────────────────────────────

@Preview
@Composable
private fun SecuritySettingsScreenPreview() {
    com.zyntasolutions.zyntapos.designsystem.theme.ZyntaTheme {
        SecuritySettingsScreen(
            state = SettingsState.SecurityState(autoLockMinutes = 5),
            onIntent = {},
            onBack = {},
            onNavigateToRbacManagement = {},
        )
    }
}
