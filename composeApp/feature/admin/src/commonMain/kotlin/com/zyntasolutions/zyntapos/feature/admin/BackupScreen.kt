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
import com.zyntasolutions.zyntapos.core.i18n.StringResource
import com.zyntasolutions.zyntapos.designsystem.components.LocalStrings
import com.zyntasolutions.zyntapos.designsystem.components.ZyntaEmptyState
import com.zyntasolutions.zyntapos.designsystem.tokens.ZyntaSpacing
import com.zyntasolutions.zyntapos.domain.model.BackupInfo
import com.zyntasolutions.zyntapos.domain.model.BackupStatus

/**
 * Backup management screen (Sprint 14).
 *
 * Lists all available backups with their status, size, and creation date.
 * Managers can:
 * - Create a new backup via the FAB
 * - Restore from any completed backup (requires confirmation)
 * - Delete a backup (requires confirmation)
 *
 * @param state     Current [AdminState].
 * @param onIntent  Dispatches intents to [AdminViewModel].
 * @param modifier  Optional modifier.
 */
@Composable
fun BackupScreen(
    state: AdminState,
    onIntent: (AdminIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val s = LocalStrings.current
    Scaffold(
        modifier = modifier,
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { onIntent(AdminIntent.CreateBackup) },
                icon = {
                    if (state.isCreatingBackup) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    } else {
                        Icon(Icons.Default.Backup, contentDescription = null)
                    }
                },
                text = { Text(if (state.isCreatingBackup) s[StringResource.ADMIN_BACKUP_CREATING] else s[StringResource.ADMIN_CREATE_BACKUP]) },
            )
        },
    ) { innerPadding ->
        if (state.backups.isEmpty() && !state.isLoading) {
            ZyntaEmptyState(
                title = s[StringResource.ADMIN_NO_BACKUPS_TITLE],
                icon = Icons.Default.CloudOff,
                subtitle = s[StringResource.ADMIN_NO_BACKUPS_SUBTITLE],
                modifier = Modifier.fillMaxSize().padding(innerPadding),
            )
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(horizontal = ZyntaSpacing.md),
                verticalArrangement = Arrangement.spacedBy(ZyntaSpacing.sm),
                contentPadding = PaddingValues(vertical = ZyntaSpacing.md),
            ) {
                item {
                    Text(
                        s[StringResource.ADMIN_BACKUP_COUNT, state.backups.size],
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                items(state.backups, key = { it.id }) { backup ->
                    BackupCard(
                        backup = backup,
                        onRestore = { onIntent(AdminIntent.RestoreBackup(backup)) },
                        onDelete = { onIntent(AdminIntent.DeleteBackup(backup)) },
                    )
                }
                item { Spacer(Modifier.height(80.dp)) }
            }
        }

        // Restore confirmation dialog
        state.showRestoreConfirm?.let { backup ->
            AlertDialog(
                onDismissRequest = { onIntent(AdminIntent.CancelRestore) },
                icon = { Icon(Icons.Default.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
                title = { Text(s[StringResource.ADMIN_RESTORE_DIALOG_TITLE]) },
                text = {
                    Text(
                        s[StringResource.ADMIN_RESTORE_DIALOG_MSG, backup.fileName],
                        style = MaterialTheme.typography.bodyMedium,
                    )
                },
                confirmButton = {
                    Button(
                        onClick = { onIntent(AdminIntent.ConfirmRestore) },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    ) { Text(s[StringResource.ADMIN_RESTORE_ACTION]) }
                },
                dismissButton = {
                    TextButton(onClick = { onIntent(AdminIntent.CancelRestore) }) { Text(s[StringResource.COMMON_CANCEL]) }
                },
            )
        }

        // Delete confirmation dialog
        state.showDeleteConfirm?.let { backup ->
            AlertDialog(
                onDismissRequest = { onIntent(AdminIntent.CancelDelete) },
                title = { Text(s[StringResource.ADMIN_DELETE_BACKUP_TITLE]) },
                text = {
                    Text(
                        s[StringResource.ADMIN_DELETE_BACKUP_MSG, backup.fileName],
                        style = MaterialTheme.typography.bodyMedium,
                    )
                },
                confirmButton = {
                    Button(
                        onClick = { onIntent(AdminIntent.ConfirmDelete) },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    ) { Text(s[StringResource.ADMIN_DELETE_ACTION]) }
                },
                dismissButton = {
                    TextButton(onClick = { onIntent(AdminIntent.CancelDelete) }) { Text(s[StringResource.COMMON_CANCEL]) }
                },
            )
        }
    }
}

// ─── Private composables ──────────────────────────────────────────────────────

@Composable
private fun BackupCard(
    backup: BackupInfo,
    onRestore: () -> Unit,
    onDelete: () -> Unit,
) {
    val s = LocalStrings.current
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(ZyntaSpacing.md)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = when (backup.status) {
                            BackupStatus.SUCCESS -> Icons.Default.CheckCircle
                            BackupStatus.FAILED -> Icons.Default.Error
                            BackupStatus.CREATING, BackupStatus.RESTORING -> Icons.Default.HourglassEmpty
                        },
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                        tint = when (backup.status) {
                            BackupStatus.SUCCESS -> MaterialTheme.colorScheme.primary
                            BackupStatus.FAILED -> MaterialTheme.colorScheme.error
                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                    Spacer(Modifier.width(ZyntaSpacing.sm))
                    Column {
                        Text(
                            backup.fileName,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                        )
                        Text(
                            "${backup.status.name}  ·  v${backup.appVersion}  ·  schema ${backup.schemaVersion}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Text(
                    "%.2f MB".format(backup.sizeMb),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            backup.errorMessage?.let { err ->
                Spacer(Modifier.height(4.dp))
                Text(
                    err,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }

            if (backup.status == BackupStatus.SUCCESS) {
                Spacer(Modifier.height(ZyntaSpacing.sm))
                Row(horizontalArrangement = Arrangement.spacedBy(ZyntaSpacing.sm)) {
                    OutlinedButton(onClick = onRestore, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Default.Restore, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(s[StringResource.ADMIN_RESTORE_ACTION])
                    }
                    OutlinedButton(
                        onClick = onDelete,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.error,
                        ),
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(s[StringResource.ADMIN_DELETE_ACTION])
                    }
                }
            }
        }
    }
}
