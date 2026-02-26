package com.zyntasolutions.zyntapos.feature.settings.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.zyntasolutions.zyntapos.designsystem.layouts.ZyntaPageScaffold
import com.zyntasolutions.zyntapos.designsystem.tokens.ZyntaSpacing

// ─────────────────────────────────────────────────────────────────────────────
// SecuritySettingsScreen — read-only display of active security policy.
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Security settings screen showing the active security policy.
 *
 * Displays PIN requirements, session timeout, and role-based access rules.
 * These are currently fixed policy values enforced by [PinManager] and
 * [SessionManager]. Configurable security policy is planned for Phase 2.
 *
 * @param onBack Back navigation callback.
 */
@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun SecuritySettingsScreen(onBack: () -> Unit) {
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
                                "Screen locks after 5 minutes of inactivity",
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
                        )
                    }
                }
            }
        }
    }
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
