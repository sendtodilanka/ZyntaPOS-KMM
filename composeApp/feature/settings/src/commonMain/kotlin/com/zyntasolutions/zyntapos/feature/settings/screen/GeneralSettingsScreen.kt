package com.zyntasolutions.zyntapos.feature.settings.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Store
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.zyntasolutions.zyntapos.designsystem.components.ZyntaButton
import com.zyntasolutions.zyntapos.designsystem.components.ZyntaSnackbarHost
import com.zyntasolutions.zyntapos.designsystem.components.ZyntaTextField
import com.zyntasolutions.zyntapos.designsystem.components.ZyntaTopAppBar
import com.zyntasolutions.zyntapos.designsystem.tokens.ZyntaSpacing
import com.zyntasolutions.zyntapos.feature.settings.Currency
import com.zyntasolutions.zyntapos.feature.settings.SettingsEffect
import com.zyntasolutions.zyntapos.feature.settings.SettingsIntent
import com.zyntasolutions.zyntapos.feature.settings.SettingsState
import com.zyntasolutions.zyntapos.feature.settings.SettingsViewModel
import kotlinx.coroutines.flow.collectLatest

// ─────────────────────────────────────────────────────────────────────────────
// GeneralSettingsScreen — store name, address, phone, logo, currency, timezone,
//                         date format, language.
// Sprint 23 — Step 13.1.2
// ─────────────────────────────────────────────────────────────────────────────

private val TIMEZONES = listOf(
    "Asia/Colombo", "Asia/Kolkata", "Asia/Dubai", "Asia/Singapore",
    "Europe/London", "Europe/Paris", "America/New_York", "America/Los_Angeles",
    "Pacific/Auckland",
)

private val DATE_FORMATS = listOf("dd/MM/yyyy", "MM/dd/yyyy", "yyyy-MM-dd", "d MMM yyyy")

/**
 * General settings screen — store identity, regional preferences, logo.
 *
 * @param viewModel  Shared [SettingsViewModel].
 * @param onBack     Back navigation callback.
 */
@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun GeneralSettingsScreen(
    viewModel: SettingsViewModel,
    state: SettingsState.GeneralState,
    effects: kotlinx.coroutines.flow.Flow<SettingsEffect>,
    onIntent: (SettingsIntent) -> Unit,
    onBack: () -> Unit,
) {
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(Unit) { onIntent(SettingsIntent.LoadGeneral) }

    LaunchedEffect(effects) {
        effects.collectLatest { effect ->
            when (effect) {
                SettingsEffect.GeneralSaved -> snackbarHostState.showSnackbar("General settings saved.")
                is SettingsEffect.ShowSnackbar -> snackbarHostState.showSnackbar(effect.message)
                else -> Unit
            }
        }
    }

    Scaffold(
        topBar = {
            ZyntaTopAppBar(title = "General", onNavigateBack = onBack)
        },
        snackbarHost = { ZyntaSnackbarHost(snackbarHostState) },
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
                SectionHeader("Store Identity")
                Spacer(Modifier.height(ZyntaSpacing.sm))
                ZyntaTextField(
                    value = state.storeName,
                    onValueChange = { onIntent(SettingsIntent.UpdateStoreName(it)) },
                    label = "Store Name",
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            item {
                ZyntaTextField(
                    value = state.storeAddress,
                    onValueChange = { onIntent(SettingsIntent.UpdateStoreAddress(it)) },
                    label = "Store Address",
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = false,
                    maxLines = 3,
                )
            }
            item {
                ZyntaTextField(
                    value = state.storePhone,
                    onValueChange = { onIntent(SettingsIntent.UpdateStorePhone(it)) },
                    label = "Phone Number",
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            item {
                LogoUriRow(
                    uri = state.logoUri,
                    onUriChange = { onIntent(SettingsIntent.UpdateLogoUri(it)) },
                )
            }
            item {
                SectionHeader("Regional")
                Spacer(Modifier.height(ZyntaSpacing.sm))
                DropdownField(
                    label = "Currency",
                    options = Currency.entries.map { "${it.code} (${it.symbol})" },
                    selectedIndex = Currency.entries.indexOf(state.currency).coerceAtLeast(0),
                    onSelect = { onIntent(SettingsIntent.UpdateCurrency(Currency.entries[it])) },
                )
            }
            item {
                DropdownField(
                    label = "Timezone",
                    options = TIMEZONES,
                    selectedIndex = TIMEZONES.indexOf(state.timezone).coerceAtLeast(0),
                    onSelect = { onIntent(SettingsIntent.UpdateTimezone(TIMEZONES[it])) },
                )
            }
            item {
                DropdownField(
                    label = "Date Format",
                    options = DATE_FORMATS,
                    selectedIndex = DATE_FORMATS.indexOf(state.dateFormat).coerceAtLeast(0),
                    onSelect = { onIntent(SettingsIntent.UpdateDateFormat(DATE_FORMATS[it])) },
                )
            }
            item {
                ZyntaTextField(
                    value = state.language,
                    onValueChange = {},
                    label = "Language",
                    modifier = Modifier.fillMaxWidth(),
                    enabled = false, // English only in Phase 1
                )
                Text(
                    text = "English only (Phase 1)",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            item {
                state.saveError?.let {
                    Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
                ZyntaButton(
                    text = if (state.isSaving) "Saving…" else "Save General Settings",
                    onClick = { onIntent(SettingsIntent.SaveGeneral) },
                    enabled = !state.isSaving,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

// ─── Shared helper composables used across settings screens ───────────────────

@Composable
internal fun SectionHeader(title: String, modifier: Modifier = Modifier) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = modifier.padding(top = ZyntaSpacing.sm),
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DropdownField(
    label: String,
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = !expanded },
        modifier = modifier.fillMaxWidth(),
    ) {
        ZyntaTextField(
            value = options.getOrElse(selectedIndex) { "" },
            onValueChange = {},
            label = label,
            readOnly = true,
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier.fillMaxWidth().menuAnchor(),
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            options.forEachIndexed { index, option ->
                DropdownMenuItem(
                    text = { Text(option) },
                    onClick = {
                        onSelect(index)
                        expanded = false
                    },
                )
            }
        }
    }
}

@Composable
private fun LogoUriRow(uri: String, onUriChange: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(ZyntaSpacing.sm)) {
        Text("Store Logo", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
        if (uri.isNotBlank()) {
            Icon(
                imageVector = Icons.Filled.Store,
                contentDescription = "Logo preview",
                modifier = Modifier.size(64.dp).align(Alignment.CenterHorizontally),
                tint = MaterialTheme.colorScheme.primary,
            )
        }
        ZyntaTextField(
            value = uri,
            onValueChange = onUriChange,
            label = "Logo URI / Path",
            modifier = Modifier.fillMaxWidth(),
        )
        Text(
            text = "Paste an image URI or pick from file picker",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
