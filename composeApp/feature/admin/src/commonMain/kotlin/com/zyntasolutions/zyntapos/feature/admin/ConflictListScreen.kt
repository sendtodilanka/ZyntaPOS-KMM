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
                "Unresolved Conflicts",
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
                    label = { Text("All") },
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
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.fillMaxSize(),
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Default.Warning,
                        contentDescription = null,
                        modifier = Modifier.size(48.dp),
                        tint = MaterialTheme.colorScheme.outline,
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "No unresolved conflicts",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.outline,
                    )
                }
            }
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
            onDismiss = { onIntent(AdminIntent.DismissConflictDetail) },
        )
    }
}

@Composable
private fun ConflictCard(
    conflict: SyncConflict,
    onClick: () -> Unit,
) {
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
                "Field: ${conflict.fieldName}",
                style = MaterialTheme.typography.bodyMedium,
            )
            Row(modifier = Modifier.fillMaxWidth()) {
                Text(
                    "Local: ${conflict.localValue?.take(40) ?: "null"}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.tertiary,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    "Server: ${conflict.serverValue?.take(40) ?: "null"}",
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
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Resolve Conflict") },
        text = {
            Column {
                Text("Entity: ${conflict.entityType} / ${conflict.entityId}", style = MaterialTheme.typography.labelMedium)
                Text("Field: ${conflict.fieldName}", style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(12.dp))
                Text("Local Value:", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.tertiary)
                Text(conflict.localValue ?: "null", style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(8.dp))
                Text("Server Value:", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
                Text(conflict.serverValue ?: "null", style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = onKeepLocal) { Text("Keep Local") }
                Button(onClick = onAcceptServer) { Text("Accept Server") }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}
