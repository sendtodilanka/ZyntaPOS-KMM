package com.zyntasolutions.zyntapos.debug.tabs

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Badge
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.zyntasolutions.zyntapos.debug.mvi.DebugIntent
import com.zyntasolutions.zyntapos.debug.mvi.DebugState
import com.zyntasolutions.zyntapos.designsystem.components.ZyntaButton
import com.zyntasolutions.zyntapos.designsystem.components.ZyntaButtonVariant

/**
 * Auth tab — session info, user list, session clear.
 *
 * No password or token value is ever displayed or stored.
 */
@Composable
fun AuthTab(
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

        // ── Current session ────────────────────────────────────────────────────
        Text("Current Session", style = MaterialTheme.typography.titleSmall)

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                if (state.currentUserEmail != null) {
                    SessionRow("Email", state.currentUserEmail)
                    SessionRow("Role", state.currentUserRole ?: "-")
                    SessionRow("PIN configured", if (state.hasPinConfigured) "Yes" else "No")
                } else {
                    Text(
                        "No active session",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        // ── Session actions ────────────────────────────────────────────────────
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ZyntaButton(
                text = "Reload Session",
                onClick = { onIntent(DebugIntent.LoadInitialData) },
                variant = ZyntaButtonVariant.Secondary,
                modifier = Modifier.weight(1f),
            )
            ZyntaButton(
                text = "Clear Session",
                onClick = { onIntent(DebugIntent.RequestClearSession) },
                variant = ZyntaButtonVariant.Danger,
                modifier = Modifier.weight(1f),
            )
        }

        // ── All users ──────────────────────────────────────────────────────────
        Text("All Users", style = MaterialTheme.typography.titleSmall)

        ZyntaButton(
            text = "Reload Users",
            onClick = { onIntent(DebugIntent.LoadUsers) },
            variant = ZyntaButtonVariant.Ghost,
            modifier = Modifier.fillMaxWidth(),
        )

        if (state.allUsers.isEmpty()) {
            Text(
                "No users found. Use Seeds → Setup Admin Account to create one.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(vertical = 8.dp)) {
                    state.allUsers.forEachIndexed { index, user ->
                        if (index > 0) HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(user.name, style = MaterialTheme.typography.bodyMedium)
                                Text(user.email, style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Spacer(Modifier.height(2.dp))
                                Badge(containerColor = MaterialTheme.colorScheme.secondaryContainer) {
                                    Text(user.role, style = MaterialTheme.typography.labelSmall)
                                }
                            }
                            if (user.isActive && user.id != state.currentUserId) {
                                ZyntaButton(
                                    text = "Deactivate",
                                    onClick = { onIntent(DebugIntent.DeactivateUser(user.id)) },
                                    variant = ZyntaButtonVariant.Ghost,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SessionRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodySmall)
    }
}
