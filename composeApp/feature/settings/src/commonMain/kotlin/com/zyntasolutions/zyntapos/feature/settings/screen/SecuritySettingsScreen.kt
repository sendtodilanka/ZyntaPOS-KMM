package com.zyntasolutions.zyntapos.feature.settings.screen

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
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
) {
    LaunchedEffect(Unit) { onIntent(SettingsIntent.LoadSecuritySettings) }

    ZyntaPageScaffold(
        title = "Security",
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
                SectionLabel("Session")
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    ListItem(
                        headlineContent = {
                            Text("Auto-Lock Timeout", style = MaterialTheme.typography.bodyLarge)
                        },
                        supportingContent = {
                            Text(
                                "Screen locks after inactivity. Tap to change.",
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
                SectionLabel("PIN Policy")
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    ListItem(
                        headlineContent = {
                            Text("PIN Requirements", style = MaterialTheme.typography.bodyLarge)
                        },
                        supportingContent = {
                            Text(
                                "Numeric PIN, hashed with SHA-256 + 16-byte salt. " +
                                    "Minimum 4 digits. Set via User Management.",
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
                                "Fixed",
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
                SectionLabel("Role-Based Access")
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    listOf(
                        "Admin" to "Full system access — all settings, users, and data",
                        "Manager" to "POS, inventory, reports, staff management",
                        "Cashier" to "POS checkout and basic order operations",
                        "Reporter" to "Read-only access to reports and analytics",
                    ).forEachIndexed { index, (role, description) ->
                        ListItem(
                            headlineContent = {
                                Text(role, style = MaterialTheme.typography.bodyLarge)
                            },
                            supportingContent = {
                                Text(description, style = MaterialTheme.typography.bodySmall)
                            },
                            leadingContent = {
                                Icon(
                                    Icons.Default.Security,
                                    contentDescription = null,
                                    tint = if (index == 0) MaterialTheme.colorScheme.error
                                    else MaterialTheme.colorScheme.primary,
                                )
                            },
                            trailingContent = {
                                Text(
                                    "View only",
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

private val AUTO_LOCK_OPTIONS: List<Pair<Int, String>> = listOf(
    0 to "Never",
    1 to "1 minute",
    2 to "2 minutes",
    5 to "5 minutes",
    10 to "10 minutes",
    15 to "15 minutes",
    30 to "30 minutes",
)

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
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Auto-Lock Timeout") },
        text = {
            Column {
                AUTO_LOCK_OPTIONS.forEach { (minutes, label) ->
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
            TextButton(onClick = onDismiss) { Text("Cancel") }
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
        )
    }
}
