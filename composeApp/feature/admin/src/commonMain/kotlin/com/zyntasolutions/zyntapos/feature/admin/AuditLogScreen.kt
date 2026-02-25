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
import com.zyntasolutions.zyntapos.designsystem.tokens.ZyntaSpacing
import com.zyntasolutions.zyntapos.domain.model.AuditEntry
import com.zyntasolutions.zyntapos.domain.model.AuditEventType
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/**
 * Security audit log viewer screen (Sprint 15).
 *
 * Displays all audit entries in reverse chronological order.
 * Supports filtering by user ID. Each entry shows:
 * - Event type with an icon
 * - Actor user ID and device ID
 * - Success / failure badge
 * - ISO timestamp
 * - Optional JSON payload (expandable)
 *
 * @param state     Current [AdminState].
 * @param onIntent  Dispatches intents to [AdminViewModel].
 * @param modifier  Optional modifier.
 */
@Composable
fun AuditLogScreen(
    state: AdminState,
    onIntent: (AdminIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    // Apply user filter locally (reactive: observeAll() already pushed to state)
    val filtered = remember(state.auditEntries, state.auditUserFilter) {
        if (state.auditUserFilter.isBlank()) state.auditEntries
        else state.auditEntries.filter {
            it.userId.contains(state.auditUserFilter, ignoreCase = true)
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = ZyntaSpacing.md),
    ) {
        // Filter bar
        OutlinedTextField(
            value = state.auditUserFilter,
            onValueChange = { onIntent(AdminIntent.FilterAuditByUser(it)) },
            label = { Text("Filter by user ID") },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            trailingIcon = {
                if (state.auditUserFilter.isNotBlank()) {
                    IconButton(onClick = { onIntent(AdminIntent.FilterAuditByUser("")) }) {
                        Icon(Icons.Default.Clear, contentDescription = "Clear filter")
                    }
                }
            },
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = ZyntaSpacing.sm),
        )

        Text(
            "${filtered.size} event(s)",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = ZyntaSpacing.sm),
        )

        if (filtered.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Default.EventNote,
                        contentDescription = null,
                        modifier = Modifier.size(48.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        if (state.auditUserFilter.isBlank()) "No audit events recorded."
                        else "No events for \"${state.auditUserFilter}\".",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(ZyntaSpacing.sm)) {
                items(filtered, key = { it.id }) { entry ->
                    AuditEntryCard(entry)
                }
                item { Spacer(Modifier.height(24.dp)) }
            }
        }
    }
}

// ─── Private composables ──────────────────────────────────────────────────────

@Composable
private fun AuditEntryCard(entry: AuditEntry) {
    var expanded by remember { mutableStateOf(false) }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(ZyntaSpacing.sm)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = iconForEventType(entry.eventType),
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = if (entry.success) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.error,
                )
                Spacer(Modifier.width(ZyntaSpacing.sm))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        entry.eventType.name.replace('_', ' '),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                    )
                    Text(
                        "User: ${entry.userId}  ·  Device: ${entry.deviceId}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    SuccessBadge(entry.success)
                    Text(
                        formatInstant(entry),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            if (entry.payload.isNotBlank() && entry.payload != "{}") {
                TextButton(
                    onClick = { expanded = !expanded },
                    contentPadding = PaddingValues(0.dp),
                ) {
                    Text(
                        if (expanded) "Hide payload" else "Show payload",
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
                if (expanded) {
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = MaterialTheme.shapes.small,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            entry.payload,
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.padding(ZyntaSpacing.sm),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SuccessBadge(success: Boolean) {
    Surface(
        color = if (success) MaterialTheme.colorScheme.secondaryContainer
        else MaterialTheme.colorScheme.errorContainer,
        shape = MaterialTheme.shapes.extraSmall,
    ) {
        Text(
            text = if (success) "OK" else "FAIL",
            style = MaterialTheme.typography.labelSmall,
            color = if (success) MaterialTheme.colorScheme.onSecondaryContainer
            else MaterialTheme.colorScheme.onErrorContainer,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
        )
    }
}

private fun formatInstant(entry: AuditEntry): String {
    return try {
        val ldt = entry.createdAt.toLocalDateTime(TimeZone.currentSystemDefault())
        "%02d:%02d:%02d".format(ldt.hour, ldt.minute, ldt.second)
    } catch (_: Exception) {
        "?"
    }
}

private fun iconForEventType(type: AuditEventType) = when (type) {
    AuditEventType.LOGIN_ATTEMPT -> Icons.Default.Login
    AuditEventType.LOGOUT -> Icons.Default.Logout
    AuditEventType.PERMISSION_DENIED -> Icons.Default.Block
    AuditEventType.ORDER_VOID -> Icons.Default.Cancel
    AuditEventType.STOCK_ADJUSTMENT -> Icons.Default.Inventory
    AuditEventType.USER_CREATED -> Icons.Default.PersonAdd
    AuditEventType.USER_DEACTIVATED -> Icons.Default.PersonOff
    AuditEventType.SETTINGS_CHANGED -> Icons.Default.Settings
    AuditEventType.REGISTER_OPENED -> Icons.Default.LockOpen
    AuditEventType.REGISTER_CLOSED -> Icons.Default.Lock
    AuditEventType.DATA_EXPORT -> Icons.Default.FileDownload
}
