package com.zyntasolutions.zyntapos.feature.settings.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import com.zyntasolutions.zyntapos.designsystem.components.ZentaDialog
import com.zyntasolutions.zyntapos.designsystem.components.ZentaSnackbarHost
import com.zyntasolutions.zyntapos.designsystem.components.ZentaTable
import com.zyntasolutions.zyntapos.designsystem.components.ZentaTopAppBar
import com.zyntasolutions.zyntapos.designsystem.layouts.ZentaScaffold
import com.zyntasolutions.zyntapos.designsystem.tokens.ZentaSpacing
import com.zyntasolutions.zyntapos.domain.model.TaxGroup
import com.zyntasolutions.zyntapos.feature.settings.SettingsEffect
import com.zyntasolutions.zyntapos.feature.settings.SettingsIntent
import com.zyntasolutions.zyntapos.feature.settings.SettingsState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectLatest

// ─────────────────────────────────────────────────────────────────────────────
// TaxSettingsScreen — ZentaTable of tax groups, FAB → create, edit icon per row,
//                     delete with ZentaDialog confirm.
// Sprint 23 — Step 13.1.4
// ─────────────────────────────────────────────────────────────────────────────

private val TAX_COLUMNS = listOf("Name", "Rate (%)", "Inclusive", "")

/**
 * Tax settings screen showing all [TaxGroup] rows and CRUD controls.
 *
 * @param state     Current [SettingsState.TaxState] slice.
 * @param effects   Shared [SettingsEffect] flow.
 * @param onIntent  Dispatch callback.
 * @param onBack    Back navigation.
 */
@Composable
fun TaxSettingsScreen(
    state: SettingsState.TaxState,
    effects: Flow<SettingsEffect>,
    onIntent: (SettingsIntent) -> Unit,
    onBack: () -> Unit,
) {
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(Unit) { onIntent(SettingsIntent.LoadTaxGroups) }

    LaunchedEffect(effects) {
        effects.collectLatest { effect ->
            when (effect) {
                is SettingsEffect.ShowSnackbar -> snackbarHostState.showSnackbar(effect.message)
                else -> Unit
            }
        }
    }

    // ── Delete confirmation dialog ────────────────────────────────────────────
    state.deleteTarget?.let { target ->
        ZentaDialog(
            title = "Delete Tax Group",
            message = "Delete '${target.name}'? This cannot be undone.",
            confirmLabel = "Delete",
            dismissLabel = "Cancel",
            onConfirm = { onIntent(SettingsIntent.ConfirmDeleteTaxGroup) },
            onDismiss = { onIntent(SettingsIntent.CancelDeleteTaxGroup) },
            isDestructive = true,
        )
    }

    // ── Tax Group form bottom sheet ───────────────────────────────────────────
    val showForm = state.isCreating || state.isEditing != null
    if (showForm) {
        TaxGroupFormSheet(
            initial = state.isEditing,
            onSave = { tg, isUpdate -> onIntent(SettingsIntent.SaveTaxGroup(tg, isUpdate)) },
            onDismiss = { onIntent(SettingsIntent.DismissTaxForm) },
            saveError = state.saveError,
        )
    }

    ZentaScaffold(
        topBar = { ZentaTopAppBar(title = "Tax Groups", onNavigationClick = onBack) },
        snackbarHost = { ZentaSnackbarHost(snackbarHostState) },
        floatingActionButton = {
            FloatingActionButton(onClick = { onIntent(SettingsIntent.OpenCreateTaxGroup) }) {
                Icon(Icons.Filled.Add, contentDescription = "Add Tax Group")
            }
        },
    ) { innerPadding ->
        LazyColumn(
            contentPadding = PaddingValues(
                start = ZentaSpacing.md,
                end = ZentaSpacing.md,
                top = innerPadding.calculateTopPadding() + ZentaSpacing.md,
                bottom = innerPadding.calculateBottomPadding() + ZentaSpacing.md,
            ),
            verticalArrangement = Arrangement.spacedBy(ZentaSpacing.md),
        ) {
            item {
                if (state.isLoading) {
                    Text("Loading tax groups…", style = MaterialTheme.typography.bodyMedium,
                        modifier = androidx.compose.ui.Modifier.padding(ZentaSpacing.md))
                } else if (state.taxGroups.isEmpty()) {
                    Text("No tax groups yet. Tap + to create one.",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = androidx.compose.ui.Modifier.padding(ZentaSpacing.md))
                } else {
                    ZentaTable(
                        columns = TAX_COLUMNS,
                        rows = state.taxGroups.map { tg ->
                            listOf(tg.name, "${"%.2f".format(tg.rate)}%", if (tg.isInclusive) "Yes" else "No", "")
                        },
                        onEditRow = { index -> onIntent(SettingsIntent.OpenEditTaxGroup(state.taxGroups[index])) },
                        onDeleteRow = { index -> onIntent(SettingsIntent.RequestDeleteTaxGroup(state.taxGroups[index])) },
                        modifier = androidx.compose.ui.Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}

// ─── TaxGroupFormSheet ────────────────────────────────────────────────────────

@Composable
private fun TaxGroupFormSheet(
    initial: TaxGroup?,
    onSave: (TaxGroup, Boolean) -> Unit,
    onDismiss: () -> Unit,
    saveError: String?,
) {
    val nameState = remember(initial) { androidx.compose.runtime.mutableStateOf(initial?.name ?: "") }
    val rateState = remember(initial) { androidx.compose.runtime.mutableStateOf(initial?.rate?.toString() ?: "") }
    val inclusiveState = remember(initial) { androidx.compose.runtime.mutableStateOf(initial?.isInclusive ?: false) }

    com.zyntasolutions.zyntapos.designsystem.components.ZentaBottomSheet(
        title = if (initial != null) "Edit Tax Group" else "New Tax Group",
        onDismiss = onDismiss,
    ) {
        com.zyntasolutions.zyntapos.designsystem.components.ZentaTextField(
            value = nameState.value,
            onValueChange = { nameState.value = it },
            label = "Tax Name (e.g. VAT, GST)",
            modifier = androidx.compose.ui.Modifier.fillMaxWidth(),
        )
        com.zyntasolutions.zyntapos.designsystem.components.ZentaTextField(
            value = rateState.value,
            onValueChange = { rateState.value = it },
            label = "Rate (%)",
            modifier = androidx.compose.ui.Modifier.fillMaxWidth(),
        )
        ToggleRow(
            label = "Tax Inclusive",
            checked = inclusiveState.value,
            onCheckedChange = { inclusiveState.value = it },
        )
        saveError?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
        com.zyntasolutions.zyntapos.designsystem.components.ZentaButton(
            text = "Save Tax Group",
            onClick = {
                val rate = rateState.value.toDoubleOrNull() ?: 0.0
                val now = kotlinx.datetime.Clock.System.now()
                val tg = if (initial != null) {
                    initial.copy(name = nameState.value, rate = rate, isInclusive = inclusiveState.value, updatedAt = now)
                } else {
                    TaxGroup(
                        id = java.util.UUID.randomUUID().toString(),
                        storeId = "default",
                        name = nameState.value,
                        rate = rate,
                        isInclusive = inclusiveState.value,
                        createdAt = now,
                        updatedAt = now,
                    )
                }
                onSave(tg, initial != null)
            },
            modifier = androidx.compose.ui.Modifier.fillMaxWidth(),
        )
    }
}
