package com.zyntasolutions.zyntapos.feature.settings.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.zyntasolutions.zyntapos.designsystem.layouts.ZyntaPageScaffold
import com.zyntasolutions.zyntapos.designsystem.tokens.ZyntaSpacing
import com.zyntasolutions.zyntapos.domain.model.PrinterJobType
import com.zyntasolutions.zyntapos.domain.model.PrinterProfile
import com.zyntasolutions.zyntapos.feature.settings.SettingsEffect
import com.zyntasolutions.zyntapos.feature.settings.SettingsIntent
import com.zyntasolutions.zyntapos.feature.settings.SettingsState
import com.zyntasolutions.zyntapos.core.i18n.StringResource
import com.zyntasolutions.zyntapos.designsystem.components.LocalStrings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectLatest

/**
 * Lists all [PrinterProfile] entries and provides add/edit/delete.
 */
@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun PrinterProfilesScreen(
    state: SettingsState.PrinterProfilesState,
    effects: Flow<SettingsEffect>,
    onIntent: (SettingsIntent) -> Unit,
    onBack: () -> Unit,
) {
    val s = LocalStrings.current
    val snackbarHostState = remember { SnackbarHostState() }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var profileToDelete by remember { mutableStateOf<PrinterProfile?>(null) }

    LaunchedEffect(Unit) { onIntent(SettingsIntent.LoadPrinterProfiles) }

    LaunchedEffect(effects) {
        effects.collect { effect ->
            when (effect) {
                SettingsEffect.PrinterProfileSaved   -> snackbarHostState.showSnackbar(s[StringResource.SETTINGS_PRINTER_PROFILE_SAVED])
                SettingsEffect.PrinterProfileDeleted -> snackbarHostState.showSnackbar(s[StringResource.SETTINGS_PRINTER_PROFILE_DELETED])
                is SettingsEffect.ShowSnackbar       -> snackbarHostState.showSnackbar(effect.message)
                else -> Unit
            }
        }
    }

    val showSheet = state.isCreating || state.editingProfile != null

    ZyntaPageScaffold(
        title = s[StringResource.SETTINGS_PRINTER_PROFILES],
        onNavigateBack = onBack,
        snackbarHostState = snackbarHostState,
        floatingActionButton = {
            FloatingActionButton(onClick = { onIntent(SettingsIntent.OpenCreatePrinterProfile) }) {
                Icon(Icons.Filled.Add, contentDescription = s[StringResource.SETTINGS_PRINTER_PROFILES_ADD])
            }
        },
    ) { innerPadding ->
        if (state.profiles.isEmpty() && !state.isLoading) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(ZyntaSpacing.md),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = s[StringResource.SETTINGS_PRINTER_PROFILES_EMPTY],
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = s[StringResource.SETTINGS_PRINTER_PROFILES_EMPTY_HINT],
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(
                    top = innerPadding.calculateTopPadding() + ZyntaSpacing.md,
                    bottom = innerPadding.calculateBottomPadding() + ZyntaSpacing.md,
                    start = ZyntaSpacing.md,
                    end = ZyntaSpacing.md,
                ),
                verticalArrangement = Arrangement.spacedBy(ZyntaSpacing.sm),
            ) {
                items(state.profiles, key = { it.id }) { profile ->
                    PrinterProfileCard(
                        profile = profile,
                        onEdit = { onIntent(SettingsIntent.OpenEditPrinterProfile(profile)) },
                        onDelete = { profileToDelete = profile },
                    )
                }
            }
        }

        // Edit / Create bottom sheet
        if (showSheet) {
            ModalBottomSheet(
                onDismissRequest = { onIntent(SettingsIntent.DismissPrinterProfileForm) },
                sheetState = sheetState,
            ) {
                PrinterProfileForm(
                    form = state.form,
                    isSaving = false,
                    saveError = state.saveError,
                    onIntent = onIntent,
                )
            }
        }

        // Delete confirmation dialog
        profileToDelete?.let { target ->
            AlertDialog(
                onDismissRequest = { profileToDelete = null },
                title = { Text(s[StringResource.SETTINGS_PRINTER_PROFILES_DELETE_TITLE]) },
                text = { Text(s[StringResource.SETTINGS_PRINTER_PROFILE_DELETE_BODY, target.name]) },
                confirmButton = {
                    Button(onClick = {
                        onIntent(SettingsIntent.DeletePrinterProfile(target.id))
                        profileToDelete = null
                    }) { Text(s[StringResource.COMMON_DELETE]) }
                },
                dismissButton = {
                    TextButton(onClick = { profileToDelete = null }) { Text(s[StringResource.COMMON_CANCEL]) }
                },
            )
        }
    }
}

@Composable
private fun PrinterProfileCard(
    profile: PrinterProfile,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    val s = LocalStrings.current
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = ZyntaSpacing.md, vertical = ZyntaSpacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(profile.name, style = MaterialTheme.typography.bodyLarge)
                Text(
                    text = "${profile.jobType.name} \u2022 ${profile.printerType}${if (profile.isDefault) s[StringResource.SETTINGS_PRINTER_PROFILE_DEFAULT_BADGE] else ""}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = onEdit) {
                Icon(Icons.Filled.Edit, contentDescription = s[StringResource.COMMON_EDIT])
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Filled.Delete, contentDescription = s[StringResource.COMMON_DELETE])
            }
        }
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun PrinterProfileForm(
    form: SettingsState.PrinterProfilesState.PrinterProfileForm,
    isSaving: Boolean,
    saveError: String?,
    onIntent: (SettingsIntent) -> Unit,
) {
    val s = LocalStrings.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(ZyntaSpacing.md),
        verticalArrangement = Arrangement.spacedBy(ZyntaSpacing.md),
    ) {
        Text(s[StringResource.SETTINGS_PRINTER_PROFILE], style = MaterialTheme.typography.titleMedium)

        OutlinedTextField(
            value = form.name,
            onValueChange = { onIntent(SettingsIntent.UpdateProfileName(it)) },
            label = { Text(s[StringResource.SETTINGS_PRINTER_PROFILE_NAME]) },
            modifier = Modifier.fillMaxWidth(),
        )

        // Job type dropdown
        var jobTypeExpanded by remember { mutableStateOf(false) }
        ExposedDropdownMenuBox(
            expanded = jobTypeExpanded,
            onExpandedChange = { jobTypeExpanded = it },
        ) {
            OutlinedTextField(
                value = form.jobType.name,
                onValueChange = {},
                readOnly = true,
                label = { Text(s[StringResource.SETTINGS_PRINTER_JOB_TYPE]) },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = jobTypeExpanded) },
                modifier = Modifier.fillMaxWidth().menuAnchor(),
            )
            ExposedDropdownMenu(
                expanded = jobTypeExpanded,
                onDismissRequest = { jobTypeExpanded = false },
            ) {
                PrinterJobType.entries.forEach { jt ->
                    DropdownMenuItem(
                        text = { Text(jt.name) },
                        onClick = {
                            onIntent(SettingsIntent.UpdateProfileJobType(jt))
                            jobTypeExpanded = false
                        },
                    )
                }
            }
        }

        // Printer transport type dropdown
        val transportOptions = listOf("TCP", "SERIAL", "BLUETOOTH", "USB")
        var transportExpanded by remember { mutableStateOf(false) }
        ExposedDropdownMenuBox(
            expanded = transportExpanded,
            onExpandedChange = { transportExpanded = it },
        ) {
            OutlinedTextField(
                value = form.printerType,
                onValueChange = {},
                readOnly = true,
                label = { Text(s[StringResource.SETTINGS_PRINTER_CONNECTION_TYPE]) },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = transportExpanded) },
                modifier = Modifier.fillMaxWidth().menuAnchor(),
            )
            ExposedDropdownMenu(
                expanded = transportExpanded,
                onDismissRequest = { transportExpanded = false },
            ) {
                transportOptions.forEach { t ->
                    DropdownMenuItem(
                        text = { Text(t) },
                        onClick = {
                            onIntent(SettingsIntent.UpdateProfilePrinterType(t))
                            transportExpanded = false
                        },
                    )
                }
            }
        }

        if (form.printerType == "TCP") {
            OutlinedTextField(
                value = form.tcpHost,
                onValueChange = { onIntent(SettingsIntent.UpdateProfileTcpHost(it)) },
                label = { Text(s[StringResource.SETTINGS_PRINTER_IP_ADDRESS]) },
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = form.tcpPort,
                onValueChange = { onIntent(SettingsIntent.UpdateProfileTcpPort(it)) },
                label = { Text(s[StringResource.SETTINGS_PRINTER_PORT]) },
                modifier = Modifier.fillMaxWidth(),
            )
        }

        if (form.printerType == "SERIAL") {
            OutlinedTextField(
                value = form.serialPort,
                onValueChange = { onIntent(SettingsIntent.UpdateProfileSerialPort(it)) },
                label = { Text(s[StringResource.SETTINGS_PRINTER_SERIAL_PORT]) },
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = form.baudRate,
                onValueChange = { onIntent(SettingsIntent.UpdateProfileBaudRate(it)) },
                label = { Text(s[StringResource.SETTINGS_PRINTER_BAUD_RATE]) },
                modifier = Modifier.fillMaxWidth(),
            )
        }

        if (form.printerType == "BLUETOOTH") {
            OutlinedTextField(
                value = form.btAddress,
                onValueChange = { onIntent(SettingsIntent.UpdateProfileBtAddress(it)) },
                label = { Text(s[StringResource.SETTINGS_PRINTER_BT_ADDRESS]) },
                modifier = Modifier.fillMaxWidth(),
            )
        }

        HorizontalDivider()

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(s[StringResource.SETTINGS_PRINTER_PROFILE_SET_DEFAULT, form.jobType.name], style = MaterialTheme.typography.bodyMedium)
            Switch(
                checked = form.isDefault,
                onCheckedChange = { onIntent(SettingsIntent.UpdateProfileIsDefault(it)) },
            )
        }

        if (saveError != null) {
            Text(
                text = saveError,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
            )
        }

        Button(
            onClick = { onIntent(SettingsIntent.SavePrinterProfile) },
            enabled = !isSaving,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(if (isSaving) s[StringResource.COMMON_SAVING] else s[StringResource.SETTINGS_PRINTER_PROFILE_SAVE])
        }
    }
}
