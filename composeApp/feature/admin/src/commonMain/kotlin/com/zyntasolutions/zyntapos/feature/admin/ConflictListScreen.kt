package com.zyntasolutions.zyntapos.feature.admin

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.zyntasolutions.zyntapos.core.i18n.StringResource
import com.zyntasolutions.zyntapos.designsystem.components.LocalStrings
import com.zyntasolutions.zyntapos.designsystem.components.ZyntaEmptyState
import com.zyntasolutions.zyntapos.domain.model.SyncConflict

/**
 * Conflict list screen — 4th tab in the Admin feature.
 *
 * Displays all unresolved sync conflicts with entity type filter and detail dialog.
 * Follows the AuditLogScreen pattern: list → tap → detail dialog → resolve action.
 */
@Composable
fun ConflictListScreen(
    state: AdminState,
    onIntent: (AdminIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val s = LocalStrings.current
    val filtered = remember(state.conflicts, state.conflictEntityTypeFilter) {
        if (state.conflictEntityTypeFilter == null) state.conflicts
        else state.conflicts.filter { it.entityType == state.conflictEntityTypeFilter }
    }

    Column(modifier = modifier.padding(16.dp)) {
        // ── Header with count ──────────────────────────────────────────
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                s[StringResource.ADMIN_CONFLICTS_TITLE],
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f),
            )
            if (state.unresolvedConflictCount > 0) {
                Badge { Text("${state.unresolvedConflictCount}") }
            }
        }
        Spacer(Modifier.height(8.dp))

        // ── Entity type filter chips ───────────────────────────────────
        val entityTypes = remember(state.conflicts) {
            state.conflicts.map { it.entityType }.distinct().sorted()
        }
        if (entityTypes.isNotEmpty()) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                FilterChip(
                    selected = state.conflictEntityTypeFilter == null,
                    onClick = { onIntent(AdminIntent.FilterConflictsByEntityType(null)) },
                    label = { Text(s[StringResource.COMMON_ALL]) },
                )
                entityTypes.forEach { type ->
                    FilterChip(
                        selected = state.conflictEntityTypeFilter == type,
                        onClick = { onIntent(AdminIntent.FilterConflictsByEntityType(type)) },
                        label = { Text(type) },
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
        }

        // ── Conflict list ──────────────────────────────────────────────
        if (filtered.isEmpty()) {
            ZyntaEmptyState(
                title = s[StringResource.ADMIN_NO_CONFLICTS],
                icon = Icons.Default.Warning,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                items(filtered, key = { it.id }) { conflict ->
                    ConflictCard(
                        conflict = conflict,
                        onClick = { onIntent(AdminIntent.SelectConflict(conflict)) },
                    )
                }
            }
        }
    }

    // ── Detail dialog ──────────────────────────────────────────────────
    state.selectedConflict?.let { conflict ->
        ConflictDetailDialog(
            conflict = conflict,
            onKeepLocal = { onIntent(AdminIntent.ResolveConflictKeepLocal(conflict.id)) },
            onAcceptServer = { onIntent(AdminIntent.ResolveConflictAcceptServer(conflict.id)) },
            onResolveManual = { value -> onIntent(AdminIntent.ResolveConflictManual(conflict.id, value)) },
            onDismiss = { onIntent(AdminIntent.DismissConflictDetail) },
        )
    }
}

@Composable
private fun ConflictCard(
    conflict: SyncConflict,
    onClick: () -> Unit,
) {
    val s = LocalStrings.current
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    conflict.entityType.uppercase(),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    conflict.entityId,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline,
                    modifier = Modifier.weight(1f),
                )
            }
            Spacer(Modifier.height(4.dp))
            Text(
                s[StringResource.ADMIN_FIELD_FORMAT, conflict.fieldName],
                style = MaterialTheme.typography.bodyMedium,
            )
            Row(modifier = Modifier.fillMaxWidth()) {
                Text(
                    s[StringResource.ADMIN_LOCAL_VALUE_FORMAT, conflict.localValue?.take(40) ?: s[StringResource.COMMON_NO_DATA]],
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.tertiary,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    s[StringResource.ADMIN_SERVER_VALUE_FORMAT, conflict.serverValue?.take(40) ?: s[StringResource.COMMON_NO_DATA]],
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun ConflictDetailDialog(
    conflict: SyncConflict,
    onKeepLocal: () -> Unit,
    onAcceptServer: () -> Unit,
    onResolveManual: (value: String) -> Unit,
    onDismiss: () -> Unit,
) {
    val s = LocalStrings.current
    var customValue by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(s[StringResource.ADMIN_RESOLVE_TITLE]) },
        text = {
            Column {
                Text(s[StringResource.ADMIN_ENTITY_FORMAT, conflict.entityType, conflict.entityId], style = MaterialTheme.typography.labelMedium)
                Text(s[StringResource.ADMIN_FIELD_FORMAT, conflict.fieldName], style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(12.dp))
                Text(s[StringResource.ADMIN_LOCAL_VALUE_LABEL], style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.tertiary)
                Text(conflict.localValue ?: s[StringResource.COMMON_NO_DATA], style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(8.dp))
                Text(s[StringResource.ADMIN_SERVER_VALUE_LABEL], style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
                Text(conflict.serverValue ?: s[StringResource.COMMON_NO_DATA], style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = customValue,
                    onValueChange = { customValue = it },
                    label = { Text(s[StringResource.ADMIN_CUSTOM_MERGE_LABEL]) },
                    placeholder = { Text(s[StringResource.ADMIN_CUSTOM_MERGE_PLACEHOLDER]) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = onKeepLocal) { Text(s[StringResource.ADMIN_KEEP_LOCAL]) }
                    Button(onClick = onAcceptServer) { Text(s[StringResource.ADMIN_ACCEPT_SERVER]) }
                }
                if (customValue.isNotBlank()) {
                    Button(
                        onClick = { onResolveManual(customValue) },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(s[StringResource.ADMIN_USE_CUSTOM])
                    }
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(s[StringResource.COMMON_CANCEL]) }
        },
    )
}
