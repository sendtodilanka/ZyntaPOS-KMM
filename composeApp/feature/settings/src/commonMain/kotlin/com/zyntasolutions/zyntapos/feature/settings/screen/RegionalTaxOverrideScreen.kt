package com.zyntasolutions.zyntapos.feature.settings.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.zyntasolutions.zyntapos.core.i18n.StringResource
import com.zyntasolutions.zyntapos.designsystem.components.LocalStrings
import com.zyntasolutions.zyntapos.designsystem.components.ZyntaButton
import com.zyntasolutions.zyntapos.designsystem.components.ZyntaEmptyState
import com.zyntasolutions.zyntapos.designsystem.components.ZyntaTextField
import com.zyntasolutions.zyntapos.domain.model.RegionalTaxOverride
import com.zyntasolutions.zyntapos.core.result.onError
import com.zyntasolutions.zyntapos.core.result.onSuccess
import com.zyntasolutions.zyntapos.ui.core.mvi.BaseViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

// ─── MVI contracts ──────────────────────────────────────────────────────────

data class RegionalTaxOverrideState(
    val overrides: List<RegionalTaxOverride> = emptyList(),
    val isLoading: Boolean = false,
    val storeId: String = "",
    // Form state
    val showForm: Boolean = false,
    val editingOverrideId: String? = null,
    val formTaxGroupId: String = "",
    val formEffectiveRate: String = "",
    val formJurisdictionCode: String = "",
    val formTaxRegistrationNumber: String = "",
    val formIsActive: Boolean = true,
    // Delete confirmation
    val pendingDeleteId: String? = null,
    // Available tax groups for dropdown
    val taxGroups: List<TaxGroupOption> = emptyList(),
)

data class TaxGroupOption(
    val id: String,
    val name: String,
    val rate: Double,
)

sealed interface RegionalTaxOverrideIntent {
    data class LoadOverrides(val storeId: String) : RegionalTaxOverrideIntent
    data object OpenCreateForm : RegionalTaxOverrideIntent
    data class OpenEditForm(val override: RegionalTaxOverride) : RegionalTaxOverrideIntent
    data object DismissForm : RegionalTaxOverrideIntent
    data class UpdateFormTaxGroupId(val id: String) : RegionalTaxOverrideIntent
    data class UpdateFormEffectiveRate(val rate: String) : RegionalTaxOverrideIntent
    data class UpdateFormJurisdictionCode(val code: String) : RegionalTaxOverrideIntent
    data class UpdateFormTaxRegistrationNumber(val number: String) : RegionalTaxOverrideIntent
    data class UpdateFormIsActive(val active: Boolean) : RegionalTaxOverrideIntent
    data object SaveOverride : RegionalTaxOverrideIntent
    data class RequestDelete(val id: String) : RegionalTaxOverrideIntent
    data object ConfirmDelete : RegionalTaxOverrideIntent
    data object CancelDelete : RegionalTaxOverrideIntent
}

sealed interface RegionalTaxOverrideEffect {
    data class ShowError(val message: String) : RegionalTaxOverrideEffect
    data class ShowSuccess(val message: String) : RegionalTaxOverrideEffect
    data object NavigateBack : RegionalTaxOverrideEffect
}

// ─── ViewModel ──────────────────────────────────────────────────────────────

class RegionalTaxOverrideViewModel(
    private val taxOverrideRepository: com.zyntasolutions.zyntapos.domain.repository.RegionalTaxOverrideRepository,
    private val taxGroupRepository: com.zyntasolutions.zyntapos.domain.repository.TaxGroupRepository,
) : BaseViewModel<RegionalTaxOverrideState, RegionalTaxOverrideIntent, RegionalTaxOverrideEffect>(
    initialState = RegionalTaxOverrideState()
) {

    override suspend fun handleIntent(intent: RegionalTaxOverrideIntent) {
        when (intent) {
            is RegionalTaxOverrideIntent.LoadOverrides -> loadOverrides(intent.storeId)
            is RegionalTaxOverrideIntent.OpenCreateForm -> openCreateForm()
            is RegionalTaxOverrideIntent.OpenEditForm -> openEditForm(intent.override)
            is RegionalTaxOverrideIntent.DismissForm -> updateState { copy(showForm = false, editingOverrideId = null) }
            is RegionalTaxOverrideIntent.UpdateFormTaxGroupId -> updateState { copy(formTaxGroupId = intent.id) }
            is RegionalTaxOverrideIntent.UpdateFormEffectiveRate -> updateState { copy(formEffectiveRate = intent.rate) }
            is RegionalTaxOverrideIntent.UpdateFormJurisdictionCode -> updateState { copy(formJurisdictionCode = intent.code) }
            is RegionalTaxOverrideIntent.UpdateFormTaxRegistrationNumber -> updateState { copy(formTaxRegistrationNumber = intent.number) }
            is RegionalTaxOverrideIntent.UpdateFormIsActive -> updateState { copy(formIsActive = intent.active) }
            is RegionalTaxOverrideIntent.SaveOverride -> saveOverride()
            is RegionalTaxOverrideIntent.RequestDelete -> updateState { copy(pendingDeleteId = intent.id) }
            is RegionalTaxOverrideIntent.ConfirmDelete -> confirmDelete()
            is RegionalTaxOverrideIntent.CancelDelete -> updateState { copy(pendingDeleteId = null) }
        }
    }

    private suspend fun loadOverrides(storeId: String) {
        updateState { copy(isLoading = true, storeId = storeId) }

        // Load tax groups for the form dropdown
        viewModelScope.launch {
            taxGroupRepository.getAll().collect { groups ->
                updateState {
                    copy(taxGroups = groups.map { TaxGroupOption(it.id, it.name, it.rate) })
                }
            }
        }

        // Load overrides for this store
        viewModelScope.launch {
            taxOverrideRepository.getOverridesForStore(storeId).collect { overrides ->
                updateState { copy(overrides = overrides, isLoading = false) }
            }
        }
    }

    private fun openCreateForm() {
        updateState {
            copy(
                showForm = true,
                editingOverrideId = null,
                formTaxGroupId = taxGroups.firstOrNull()?.id ?: "",
                formEffectiveRate = "",
                formJurisdictionCode = "",
                formTaxRegistrationNumber = "",
                formIsActive = true,
            )
        }
    }

    private fun openEditForm(override: RegionalTaxOverride) {
        updateState {
            copy(
                showForm = true,
                editingOverrideId = override.id,
                formTaxGroupId = override.taxGroupId,
                formEffectiveRate = override.effectiveRate.toString(),
                formJurisdictionCode = override.jurisdictionCode,
                formTaxRegistrationNumber = override.taxRegistrationNumber,
                formIsActive = override.isActive,
            )
        }
    }

    private suspend fun saveOverride() {
        val s = state.value
        val rate = s.formEffectiveRate.toDoubleOrNull()
        if (rate == null || rate < 0.0 || rate > 100.0) {
            sendEffect(RegionalTaxOverrideEffect.ShowError("Tax rate must be between 0 and 100"))
            return
        }
        if (s.formTaxGroupId.isBlank()) {
            sendEffect(RegionalTaxOverrideEffect.ShowError("Please select a tax group"))
            return
        }

        val override = RegionalTaxOverride(
            id = s.editingOverrideId ?: com.zyntasolutions.zyntapos.core.utils.IdGenerator.newId(),
            taxGroupId = s.formTaxGroupId,
            storeId = s.storeId,
            effectiveRate = rate,
            jurisdictionCode = s.formJurisdictionCode,
            taxRegistrationNumber = s.formTaxRegistrationNumber,
            isActive = s.formIsActive,
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis(),
        )

        taxOverrideRepository.upsert(override)
            .onSuccess {
                updateState { copy(showForm = false, editingOverrideId = null) }
                sendEffect(
                    RegionalTaxOverrideEffect.ShowSuccess(
                        if (s.editingOverrideId != null) "Tax override updated" else "Tax override created"
                    )
                )
            }
            .onError { sendEffect(RegionalTaxOverrideEffect.ShowError(it.message)) }
    }

    private suspend fun confirmDelete() {
        val id = state.value.pendingDeleteId ?: return
        taxOverrideRepository.delete(id)
            .onSuccess {
                updateState { copy(pendingDeleteId = null) }
                sendEffect(RegionalTaxOverrideEffect.ShowSuccess("Tax override deleted"))
            }
            .onError { sendEffect(RegionalTaxOverrideEffect.ShowError(it.message)) }
    }

}

// ─── Screen ─────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RegionalTaxOverrideScreen(
    storeId: String,
    state: RegionalTaxOverrideState,
    effects: kotlinx.coroutines.flow.Flow<RegionalTaxOverrideEffect>,
    onIntent: (RegionalTaxOverrideIntent) -> Unit,
    onBack: () -> Unit,
) {
    val s = LocalStrings.current
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(storeId) {
        onIntent(RegionalTaxOverrideIntent.LoadOverrides(storeId))
    }

    LaunchedEffect(effects) {
        effects.collect { effect ->
            when (effect) {
                is RegionalTaxOverrideEffect.ShowError -> snackbarHostState.showSnackbar(effect.message)
                is RegionalTaxOverrideEffect.ShowSuccess -> snackbarHostState.showSnackbar(effect.message)
                is RegionalTaxOverrideEffect.NavigateBack -> onBack()
            }
        }
    }

    // Delete confirmation dialog
    if (state.pendingDeleteId != null) {
        AlertDialog(
            onDismissRequest = { onIntent(RegionalTaxOverrideIntent.CancelDelete) },
            title = { Text(s[StringResource.SETTINGS_TAX_OVERRIDE_DELETE_TITLE]) },
            text = { Text(s[StringResource.SETTINGS_TAX_OVERRIDE_DELETE_MSG]) },
            confirmButton = {
                TextButton(onClick = { onIntent(RegionalTaxOverrideIntent.ConfirmDelete) }) {
                    Text(s[StringResource.COMMON_DELETE], color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { onIntent(RegionalTaxOverrideIntent.CancelDelete) }) {
                    Text(s[StringResource.COMMON_CANCEL])
                }
            },
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(s[StringResource.SETTINGS_TAX_OVERRIDE_TITLE]) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = s[StringResource.COMMON_BACK])
                    }
                },
            )
        },
        floatingActionButton = {
            if (!state.showForm) {
                FloatingActionButton(onClick = { onIntent(RegionalTaxOverrideIntent.OpenCreateForm) }) {
                    Icon(Icons.Default.Add, contentDescription = s[StringResource.SETTINGS_TAX_OVERRIDE_ADD_CD])
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.padding(padding).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // Form
            if (state.showForm) {
                item {
                    TaxOverrideForm(
                        state = state,
                        onIntent = onIntent,
                    )
                }
            }

            // Empty state
            if (state.overrides.isEmpty() && !state.isLoading && !state.showForm) {
                item {
                    ZyntaEmptyState(
                        title = s[StringResource.SETTINGS_TAX_OVERRIDE_EMPTY_TITLE],
                        subtitle = s[StringResource.SETTINGS_TAX_OVERRIDE_EMPTY_DESC],
                    )
                }
            }

            // Override cards
            items(state.overrides, key = { it.id }) { override ->
                val taxGroupName = state.taxGroups.find { it.id == override.taxGroupId }?.name ?: override.taxGroupId
                val globalRate = state.taxGroups.find { it.id == override.taxGroupId }?.rate ?: 0.0

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = if (override.isActive)
                            MaterialTheme.colorScheme.surface
                        else
                            MaterialTheme.colorScheme.surfaceVariant,
                    ),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = taxGroupName,
                                style = MaterialTheme.typography.titleSmall,
                            )
                            Text(
                                text = s[StringResource.SETTINGS_TAX_OVERRIDE_RATE_FORMAT, override.effectiveRate, globalRate],
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.primary,
                            )
                            if (override.jurisdictionCode.isNotBlank()) {
                                Text(
                                    text = s[StringResource.SETTINGS_TAX_OVERRIDE_JURISDICTION, override.jurisdictionCode],
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            if (!override.isActive) {
                                Text(
                                    text = s[StringResource.SETTINGS_TAX_OVERRIDE_INACTIVE],
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.error,
                                )
                            }
                        }
                        IconButton(
                            onClick = { onIntent(RegionalTaxOverrideIntent.OpenEditForm(override)) },
                        ) {
                            Text(s[StringResource.COMMON_EDIT], style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                        }
                        IconButton(
                            onClick = { onIntent(RegionalTaxOverrideIntent.RequestDelete(override.id)) },
                        ) {
                            Icon(Icons.Default.Delete, contentDescription = s[StringResource.COMMON_DELETE], tint = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TaxOverrideForm(
    state: RegionalTaxOverrideState,
    onIntent: (RegionalTaxOverrideIntent) -> Unit,
) {
    val s = LocalStrings.current
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.3f)),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = if (state.editingOverrideId != null) s[StringResource.SETTINGS_TAX_OVERRIDE_EDIT_TITLE] else s[StringResource.SETTINGS_TAX_OVERRIDE_NEW_TITLE],
                style = MaterialTheme.typography.titleMedium,
            )

            // Tax group selector (simplified — text list for now)
            Text(s[StringResource.SETTINGS_TAX_OVERRIDE_TAX_GROUP], style = MaterialTheme.typography.labelMedium)
            state.taxGroups.forEach { group ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    androidx.compose.material3.RadioButton(
                        selected = state.formTaxGroupId == group.id,
                        onClick = { onIntent(RegionalTaxOverrideIntent.UpdateFormTaxGroupId(group.id)) },
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(s[StringResource.SETTINGS_TAX_OVERRIDE_TAX_RATE_FORMAT, group.name, group.rate], style = MaterialTheme.typography.bodyMedium)
                }
            }

            ZyntaTextField(
                value = state.formEffectiveRate,
                onValueChange = { onIntent(RegionalTaxOverrideIntent.UpdateFormEffectiveRate(it)) },
                label = s[StringResource.SETTINGS_TAX_OVERRIDE_RATE_LABEL],
                placeholder = s[StringResource.SETTINGS_TAX_OVERRIDE_RATE_PLACEHOLDER],
                modifier = Modifier.fillMaxWidth(),
            )

            ZyntaTextField(
                value = state.formJurisdictionCode,
                onValueChange = { onIntent(RegionalTaxOverrideIntent.UpdateFormJurisdictionCode(it)) },
                label = s[StringResource.SETTINGS_TAX_OVERRIDE_JURISDICTION_LABEL],
                placeholder = s[StringResource.SETTINGS_TAX_OVERRIDE_JURISDICTION_PLACEHOLDER],
                modifier = Modifier.fillMaxWidth(),
            )

            ZyntaTextField(
                value = state.formTaxRegistrationNumber,
                onValueChange = { onIntent(RegionalTaxOverrideIntent.UpdateFormTaxRegistrationNumber(it)) },
                label = s[StringResource.SETTINGS_TAX_OVERRIDE_REG_NUMBER],
                modifier = Modifier.fillMaxWidth(),
            )

            Row(
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(s[StringResource.SETTINGS_TAX_OVERRIDE_ACTIVE], style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.weight(1f))
                Switch(
                    checked = state.formIsActive,
                    onCheckedChange = { onIntent(RegionalTaxOverrideIntent.UpdateFormIsActive(it)) },
                )
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                ZyntaButton(
                    text = s[StringResource.COMMON_CANCEL],
                    onClick = { onIntent(RegionalTaxOverrideIntent.DismissForm) },
                    modifier = Modifier.weight(1f),
                )
                ZyntaButton(
                    text = if (state.editingOverrideId != null) s[StringResource.SETTINGS_TAX_OVERRIDE_UPDATE] else s[StringResource.SETTINGS_TAX_OVERRIDE_CREATE],
                    onClick = { onIntent(RegionalTaxOverrideIntent.SaveOverride) },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}
