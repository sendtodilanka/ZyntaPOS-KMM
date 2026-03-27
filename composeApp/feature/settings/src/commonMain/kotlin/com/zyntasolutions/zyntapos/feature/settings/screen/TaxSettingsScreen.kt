package com.zyntasolutions.zyntapos.feature.settings.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.OutlinedCard
import androidx.compose.ui.Alignment
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import com.zyntasolutions.zyntapos.designsystem.components.SortDirection
import com.zyntasolutions.zyntapos.designsystem.components.ZyntaBottomSheet
import com.zyntasolutions.zyntapos.designsystem.components.ZyntaButton
import com.zyntasolutions.zyntapos.designsystem.components.ZyntaDialogContent
import com.zyntasolutions.zyntapos.designsystem.components.ZyntaDialogVariant
import com.zyntasolutions.zyntapos.designsystem.components.ZyntaTable
import com.zyntasolutions.zyntapos.designsystem.components.ZyntaTableColumn
import com.zyntasolutions.zyntapos.designsystem.components.ZyntaTextField
import com.zyntasolutions.zyntapos.designsystem.layouts.ZyntaPageScaffold
import com.zyntasolutions.zyntapos.designsystem.tokens.ZyntaSpacing
import com.zyntasolutions.zyntapos.domain.model.TaxGroup
import com.zyntasolutions.zyntapos.feature.settings.SettingsEffect
import com.zyntasolutions.zyntapos.feature.settings.SettingsIntent
import com.zyntasolutions.zyntapos.feature.settings.SettingsState
import com.zyntasolutions.zyntapos.core.i18n.StringResource
import com.zyntasolutions.zyntapos.designsystem.components.LocalStrings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectLatest

// ─────────────────────────────────────────────────────────────────────────────
// TaxSettingsScreen — ZyntaTable of tax groups, FAB → create, edit per row,
//                     delete with ZyntaDialogContent confirm.
// Sprint 23 — Step 13.1.4
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun taxColumns() = listOf(
    ZyntaTableColumn(key = "name",      header = LocalStrings.current[StringResource.COMMON_NAME],       weight = 2f),
    ZyntaTableColumn(key = "rate",      header = LocalStrings.current[StringResource.SETTINGS_TAX_RATE_PERCENT],   weight = 1f),
    ZyntaTableColumn(key = "inclusive", header = LocalStrings.current[StringResource.SETTINGS_TAX_INCLUSIVE],  weight = 1f),
    ZyntaTableColumn(key = "actions",   header = "",           weight = 1f, sortable = false),
)

/**
 * Tax settings screen showing all [TaxGroup] rows and CRUD controls.
 *
 * @param state     Current [SettingsState.TaxState] slice.
 * @param effects   Shared [SettingsEffect] flow.
 * @param onIntent  Dispatch callback.
 * @param onBack    Back navigation.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TaxSettingsScreen(
    state: SettingsState.TaxState,
    effects: Flow<SettingsEffect>,
    onIntent: (SettingsIntent) -> Unit,
    onBack: () -> Unit,
    onNavigateToRegionalOverrides: (storeId: String) -> Unit = {},
) {
    val s = LocalStrings.current
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
        ZyntaDialogContent(
            variant = ZyntaDialogVariant.Confirm(
                title = s[StringResource.SETTINGS_TAX_DELETE_TITLE],
                message = s[StringResource.SETTINGS_TAX_DELETE_BODY, target.name],
                confirmLabel = s[StringResource.COMMON_DELETE],
                cancelLabel = s[StringResource.COMMON_CANCEL],
                onConfirm = { onIntent(SettingsIntent.ConfirmDeleteTaxGroup) },
                onCancel = { onIntent(SettingsIntent.CancelDeleteTaxGroup) },
                isDangerous = true,
            ),
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

    ZyntaPageScaffold(
        title = s[StringResource.SETTINGS_TAX_GROUPS],
        onNavigateBack = onBack,
        snackbarHostState = snackbarHostState,
        floatingActionButton = {
            FloatingActionButton(onClick = { onIntent(SettingsIntent.OpenCreateTaxGroup) }) {
                Icon(Icons.Filled.Add, contentDescription = s[StringResource.SETTINGS_TAX_ADD_GROUP])
            }
        },
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
                when {
                    state.isLoading -> Text(
                        s[StringResource.SETTINGS_TAX_LOADING],
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(ZyntaSpacing.md),
                    )
                    state.taxGroups.isEmpty() -> Text(
                        s[StringResource.SETTINGS_TAX_EMPTY],
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(ZyntaSpacing.md),
                    )
                    else -> ZyntaTable(
                        columns = taxColumns(),
                        items = state.taxGroups,
                        rowKey = { it.id },
                        modifier = Modifier.fillMaxWidth(),
                        rowContent = { tg ->
                            // Name
                            Text(tg.name, modifier = Modifier.weight(2f),
                                style = MaterialTheme.typography.bodyMedium)
                            // Rate
                            Text(s[StringResource.COMMON_PERCENT_FORMAT, "%.2f".format(tg.rate)], modifier = Modifier.weight(1f),
                                style = MaterialTheme.typography.bodyMedium)
                            // Inclusive
                            Text(if (tg.isInclusive) s[StringResource.COMMON_YES] else s[StringResource.COMMON_NO], modifier = Modifier.weight(1f),
                                style = MaterialTheme.typography.bodyMedium)
                            // Actions
                            Column(modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(ZyntaSpacing.xs)) {
                                ZyntaButton(
                                    text = s[StringResource.COMMON_EDIT],
                                    onClick = { onIntent(SettingsIntent.OpenEditTaxGroup(tg)) },
                                )
                                ZyntaButton(
                                    text = s[StringResource.COMMON_DELETE],
                                    onClick = { onIntent(SettingsIntent.RequestDeleteTaxGroup(tg)) },
                                )
                            }
                        },
                    )
                }
            }

            // ── Regional Tax Overrides action ─────────────────────────────────
            item {
                OutlinedCard(
                    onClick = { onNavigateToRegionalOverrides("default") },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(ZyntaSpacing.md),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                s[StringResource.SETTINGS_TAX_REGIONAL_OVERRIDES],
                                style = MaterialTheme.typography.titleSmall,
                            )
                            Text(
                                s[StringResource.SETTINGS_TAX_REGIONAL_OVERRIDES_DESC],
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowForward,
                            contentDescription = s[StringResource.SETTINGS_TAX_REGIONAL_OVERRIDES],
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }
        }
    }
}

// ─── TaxGroupFormSheet ────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TaxGroupFormSheet(
    initial: TaxGroup?,
    onSave: (TaxGroup, Boolean) -> Unit,
    onDismiss: () -> Unit,
    saveError: String?,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val nameState = remember(initial) { mutableStateOf(initial?.name ?: "") }
    val rateState = remember(initial) { mutableStateOf(initial?.rate?.toString() ?: "") }
    val inclusiveState = remember(initial) { mutableStateOf(initial?.isInclusive ?: false) }

    val s = LocalStrings.current
    ZyntaBottomSheet(
        sheetState = sheetState,
        onDismiss = onDismiss,
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(ZyntaSpacing.sm),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = ZyntaSpacing.md)
                .padding(bottom = ZyntaSpacing.lg),
        ) {
            Text(
                text = if (initial != null) s[StringResource.SETTINGS_TAX_EDIT_GROUP] else s[StringResource.SETTINGS_TAX_NEW_GROUP],
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(bottom = ZyntaSpacing.sm),
            )
            ZyntaTextField(
                value = nameState.value,
                onValueChange = { nameState.value = it },
                label = s[StringResource.SETTINGS_TAX_NAME_LABEL],
                modifier = Modifier.fillMaxWidth(),
            )
            ZyntaTextField(
                value = rateState.value,
                onValueChange = { rateState.value = it },
                label = s[StringResource.SETTINGS_TAX_RATE_PERCENT],
                modifier = Modifier.fillMaxWidth(),
            )
            ToggleRow(
                label = s[StringResource.SETTINGS_TAX_INCLUSIVE],
                checked = inclusiveState.value,
                onCheckedChange = { inclusiveState.value = it },
            )
            saveError?.let {
                Text(it, color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall)
            }
            ZyntaButton(
                text = s[StringResource.SETTINGS_TAX_SAVE_GROUP],
                onClick = {
                    val rate = rateState.value.toDoubleOrNull() ?: 0.0
                    val tg = if (initial != null) {
                        initial.copy(
                            name = nameState.value,
                            rate = rate,
                            isInclusive = inclusiveState.value,
                        )
                    } else {
                        TaxGroup(
                            id = com.zyntasolutions.zyntapos.core.utils.IdGenerator.newId(),
                            name = nameState.value,
                            rate = rate,
                            isInclusive = inclusiveState.value,
                        )
                    }
                    onSave(tg, initial != null)
                },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

// ── Preview ───────────────────────────────────────────────────────────────────

@androidx.compose.ui.tooling.preview.Preview
@androidx.compose.runtime.Composable
private fun TaxSettingsScreenPreview() {
    com.zyntasolutions.zyntapos.designsystem.theme.ZyntaTheme {
        TaxSettingsScreen(
            state = SettingsState.TaxState(),
            effects = kotlinx.coroutines.flow.emptyFlow(),
            onIntent = {},
            onBack = {},
        )
    }
}
