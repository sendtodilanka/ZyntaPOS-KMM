package com.zyntasolutions.zyntapos.feature.settings.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.zyntasolutions.zyntapos.designsystem.components.ZyntaButton
import com.zyntasolutions.zyntapos.designsystem.components.ZyntaDialogContent
import com.zyntasolutions.zyntapos.designsystem.components.ZyntaDialogVariant
import androidx.compose.material3.ExperimentalMaterial3Api
import com.zyntasolutions.zyntapos.designsystem.layouts.ZyntaPageScaffold
import com.zyntasolutions.zyntapos.designsystem.tokens.ZyntaSpacing
import com.zyntasolutions.zyntapos.designsystem.util.FilePickerMode
import com.zyntasolutions.zyntapos.designsystem.util.PlatformFilePicker
import com.zyntasolutions.zyntapos.feature.settings.SettingsEffect
import com.zyntasolutions.zyntapos.feature.settings.SettingsIntent
import com.zyntasolutions.zyntapos.feature.settings.SettingsState
import kotlinx.coroutines.flow.Flow
import com.zyntasolutions.zyntapos.core.i18n.StringResource
import com.zyntasolutions.zyntapos.designsystem.components.LocalStrings
import kotlinx.coroutines.flow.collectLatest
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

// ─────────────────────────────────────────────────────────────────────────────
// BackupSettingsScreen — manual backup trigger, last backup timestamp,
//                        restore from backup (file picker + confirmation).
// Sprint 23 — Step 13.1.7
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Backup & restore settings screen.
 *
 * "Backup Now" creates an encrypted copy of the database via [BackupService].
 * "Restore" opens a platform-native file picker via [PlatformFilePicker];
 * once the user selects a file, a [ZyntaDialogContent] requires explicit
 * confirmation before the restore proceeds.
 *
 * @param state     Current [SettingsState.BackupState] slice.
 * @param effects   Shared [SettingsEffect] flow.
 * @param onIntent  Dispatch callback.
 * @param onBack    Back navigation.
 */
@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun BackupSettingsScreen(
    state: SettingsState.BackupState,
    effects: Flow<SettingsEffect>,
    onIntent: (SettingsIntent) -> Unit,
    onBack: () -> Unit,
) {
    val s = LocalStrings.current
    val snackbarHostState = remember { SnackbarHostState() }
    var showFilePicker by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { onIntent(SettingsIntent.LoadBackupInfo) }

    LaunchedEffect(effects) {
        effects.collectLatest { effect ->
            when (effect) {
                is SettingsEffect.BackupComplete ->
                    snackbarHostState.showSnackbar(s[StringResource.SETTINGS_BACKUP_COMPLETE, effect.filePath])
                SettingsEffect.RestoreComplete ->
                    snackbarHostState.showSnackbar(s[StringResource.SETTINGS_BACKUP_RESTORE_COMPLETE])
                is SettingsEffect.ShowSnackbar -> snackbarHostState.showSnackbar(effect.message)
                else -> Unit
            }
        }
    }

    // ── Platform file picker for restore ─────────────────────────────────────
    PlatformFilePicker(
        show = showFilePicker,
        mode = FilePickerMode.DATABASE,
        onResult = { pickedFile ->
            showFilePicker = false
            if (pickedFile != null) {
                onIntent(SettingsIntent.RestoreSelected(pickedFile.path))
            }
        },
    )

    // ── Restore confirmation dialog ───────────────────────────────────────────
    if (state.confirmRestore) {
        ZyntaDialogContent(
            variant = ZyntaDialogVariant.Confirm(
                title = s[StringResource.SETTINGS_BACKUP_CONFIRM_RESTORE],
                message = s[StringResource.SETTINGS_BACKUP_RESTORE_CONFIRM_MSG, state.restoreFilePath ?: ""],
                confirmLabel = s[StringResource.SETTINGS_BACKUP_RESTORE_ACTION],
                cancelLabel = s[StringResource.COMMON_CANCEL],
                onConfirm = { onIntent(SettingsIntent.ConfirmRestore) },
                onCancel = { onIntent(SettingsIntent.CancelRestore) },
                isDangerous = true,
            ),
        )
    }

    ZyntaPageScaffold(
        title = s[StringResource.SETTINGS_BACKUP_RESTORE],
        onNavigateBack = onBack,
        snackbarHostState = snackbarHostState,
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
                SectionHeader(s[StringResource.SETTINGS_BACKUP_SECTION])
                Spacer(Modifier.height(ZyntaSpacing.sm))
                val lastBackupText = state.lastBackupAt?.let { instant ->
                    val local = instant.toLocalDateTime(TimeZone.currentSystemDefault())
                    val timeStr = "${local.hour.toString().padStart(2, '0')}:${local.minute.toString().padStart(2, '0')}"
                    s[StringResource.SETTINGS_BACKUP_LAST_BACKUP, local.date.toString(), timeStr]
                } ?: s[StringResource.SETTINGS_BACKUP_NO_BACKUP]
                Text(
                    text = lastBackupText,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = ZyntaSpacing.sm),
                )
                state.backupError?.let {
                    Text(
                        text = it,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(bottom = ZyntaSpacing.sm),
                    )
                }
                ZyntaButton(
                    text = if (state.isBackingUp) s[StringResource.SETTINGS_BACKUP_BACKING_UP] else s[StringResource.SETTINGS_BACKUP_NOW],
                    onClick = { onIntent(SettingsIntent.TriggerBackup) },
                    enabled = !state.isBackingUp && !state.isRestoring,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            item {
                SectionHeader(s[StringResource.SETTINGS_BACKUP_RESTORE_SECTION])
                Spacer(Modifier.height(ZyntaSpacing.sm))
                Text(
                    text = s[StringResource.SETTINGS_BACKUP_SELECT_FILE],
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = ZyntaSpacing.sm),
                )
                ZyntaButton(
                    text = if (state.isRestoring) s[StringResource.SETTINGS_BACKUP_RESTORING] else s[StringResource.SETTINGS_BACKUP_SELECT_BACKUP],
                    onClick = { showFilePicker = true },
                    enabled = !state.isBackingUp && !state.isRestoring,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

// ── Preview ───────────────────────────────────────────────────────────────────

@androidx.compose.ui.tooling.preview.Preview
@androidx.compose.runtime.Composable
private fun BackupSettingsScreenPreview() {
    com.zyntasolutions.zyntapos.designsystem.theme.ZyntaTheme {
        BackupSettingsScreen(
            state = SettingsState.BackupState(),
            effects = kotlinx.coroutines.flow.emptyFlow(),
            onIntent = {},
            onBack = {},
        )
    }
}
