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
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Store
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.zyntasolutions.zyntapos.designsystem.components.ZyntaButton
import com.zyntasolutions.zyntapos.designsystem.components.ZyntaTextField
import com.zyntasolutions.zyntapos.designsystem.layouts.ZyntaPageScaffold
import com.zyntasolutions.zyntapos.designsystem.tokens.ZyntaSpacing
import com.zyntasolutions.zyntapos.designsystem.util.FilePickerMode
import com.zyntasolutions.zyntapos.designsystem.util.PlatformFilePicker
import com.zyntasolutions.zyntapos.feature.settings.Currency
import com.zyntasolutions.zyntapos.feature.settings.SettingsEffect
import com.zyntasolutions.zyntapos.feature.settings.SettingsIntent
import com.zyntasolutions.zyntapos.feature.settings.SettingsState
import com.zyntasolutions.zyntapos.feature.settings.SettingsViewModel
import kotlinx.coroutines.flow.collectLatest

// ─────────────────────────────────────────────────────────────────────────────
// GeneralSettingsScreen — Tab-based enterprise layout
// Tabs: Store Identity | Regional
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
    var selectedTab by remember { mutableIntStateOf(0) }
    val tabTitles = listOf("Store Identity", "Regional")

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

    ZyntaPageScaffold(
        title = "General",
        onNavigateBack = onBack,
        snackbarHostState = snackbarHostState,
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            // ── Tab Row ─────────────────────────────────────────────────
            PrimaryScrollableTabRow(
                selectedTabIndex = selectedTab,
                edgePadding = ZyntaSpacing.md,
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.primary,
                divider = { HorizontalDivider() },
            ) {
                tabTitles.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        text = { Text(title) },
                        icon = {
                            Icon(
                                imageVector = when (index) {
                                    0 -> Icons.Default.Store
                                    else -> Icons.Default.Language
                                },
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                            )
                        },
                    )
                }
            }

            // ── Tab Content ─────────────────────────────────────────────
            LazyColumn(
                contentPadding = PaddingValues(ZyntaSpacing.md),
                verticalArrangement = Arrangement.spacedBy(ZyntaSpacing.md),
                modifier = Modifier.fillMaxSize(),
            ) {
                when (selectedTab) {
                    0 -> {
                        // ── Store Identity Tab ──────────────────────────
                        item {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                                ),
                            ) {
                                Column(
                                    modifier = Modifier.padding(ZyntaSpacing.md),
                                    verticalArrangement = Arrangement.spacedBy(ZyntaSpacing.md),
                                ) {
                                    Text(
                                        "Store Details",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.SemiBold,
                                    )
                                    Text(
                                        "Enter the core information that identifies your store. This appears on receipts, invoices, and reports.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                    ZyntaTextField(
                                        value = state.storeName,
                                        onValueChange = { onIntent(SettingsIntent.UpdateStoreName(it)) },
                                        label = "Store Name",
                                        modifier = Modifier.fillMaxWidth(),
                                    )
                                    Text(
                                        "The business name displayed on receipts and customer-facing materials.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                    ZyntaTextField(
                                        value = state.storeAddress,
                                        onValueChange = { onIntent(SettingsIntent.UpdateStoreAddress(it)) },
                                        label = "Store Address",
                                        modifier = Modifier.fillMaxWidth(),
                                        singleLine = false,
                                        maxLines = 3,
                                    )
                                    Text(
                                        "Full street address including city, state/province, and postal code.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                    ZyntaTextField(
                                        value = state.storePhone,
                                        onValueChange = { onIntent(SettingsIntent.UpdateStorePhone(it)) },
                                        label = "Phone Number",
                                        modifier = Modifier.fillMaxWidth(),
                                    )
                                    Text(
                                        "Contact phone number printed on receipts for customer enquiries.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }

                        item {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                                ),
                            ) {
                                Column(
                                    modifier = Modifier.padding(ZyntaSpacing.md),
                                    verticalArrangement = Arrangement.spacedBy(ZyntaSpacing.md),
                                ) {
                                    Text(
                                        "Store Logo",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.SemiBold,
                                    )
                                    Text(
                                        "Upload your store logo to display on receipts and the POS dashboard. Recommended size: 200x200px.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                    LogoUriRow(
                                        uri = state.logoUri,
                                        onUriChange = { onIntent(SettingsIntent.UpdateLogoUri(it)) },
                                    )
                                }
                            }
                        }
                    }

                    1 -> {
                        // ── Regional Tab ─────────────────────────────────
                        item {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                                ),
                            ) {
                                Column(
                                    modifier = Modifier.padding(ZyntaSpacing.md),
                                    verticalArrangement = Arrangement.spacedBy(ZyntaSpacing.md),
                                ) {
                                    Text(
                                        "Currency",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.SemiBold,
                                    )
                                    Text(
                                        "Select the currency used for pricing, transactions, and reports across your store.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                    DropdownField(
                                        label = "Currency",
                                        options = Currency.entries.map { "${it.code} (${it.symbol})" },
                                        selectedIndex = Currency.entries.indexOf(state.currency).coerceAtLeast(0),
                                        onSelect = { onIntent(SettingsIntent.UpdateCurrency(Currency.entries[it])) },
                                    )
                                    Text(
                                        "This affects how monetary values are formatted and displayed throughout the application.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }

                        item {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                                ),
                            ) {
                                Column(
                                    modifier = Modifier.padding(ZyntaSpacing.md),
                                    verticalArrangement = Arrangement.spacedBy(ZyntaSpacing.md),
                                ) {
                                    Text(
                                        "Timezone & Date Format",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.SemiBold,
                                    )
                                    Text(
                                        "Configure how dates and times are displayed across reports, receipts, and order history.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                    DropdownField(
                                        label = "Timezone",
                                        options = TIMEZONES,
                                        selectedIndex = TIMEZONES.indexOf(state.timezone).coerceAtLeast(0),
                                        onSelect = { onIntent(SettingsIntent.UpdateTimezone(TIMEZONES[it])) },
                                    )
                                    Text(
                                        "All timestamps on receipts, reports, and audit logs use this timezone.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                    DropdownField(
                                        label = "Date Format",
                                        options = DATE_FORMATS,
                                        selectedIndex = DATE_FORMATS.indexOf(state.dateFormat).coerceAtLeast(0),
                                        onSelect = { onIntent(SettingsIntent.UpdateDateFormat(DATE_FORMATS[it])) },
                                    )
                                    Text(
                                        "Controls the order of day, month, and year in all displayed dates.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }

                        item {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                                ),
                            ) {
                                Column(
                                    modifier = Modifier.padding(ZyntaSpacing.md),
                                    verticalArrangement = Arrangement.spacedBy(ZyntaSpacing.md),
                                ) {
                                    Text(
                                        "Language",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.SemiBold,
                                    )
                                    Text(
                                        "Set the display language for the POS interface and printed receipts.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                    ZyntaTextField(
                                        value = state.language,
                                        onValueChange = {},
                                        label = "Language",
                                        modifier = Modifier.fillMaxWidth(),
                                        enabled = false, // English only in Phase 1
                                    )
                                    Text(
                                        "English only (Phase 1). Additional languages will be available in a future update.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    }
                }

                // ── Save Button (always visible) ────────────────────────
                item {
                    Spacer(Modifier.height(ZyntaSpacing.sm))
                    state.saveError?.let {
                        Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    }
                    ZyntaButton(
                        text = if (state.isSaving) "Saving..." else "Save General Settings",
                        onClick = { onIntent(SettingsIntent.SaveGeneral) },
                        enabled = !state.isSaving,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
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
            modifier = Modifier.fillMaxWidth().menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
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
    var showImagePicker by remember { mutableStateOf(false) }

    PlatformFilePicker(
        show = showImagePicker,
        mode = FilePickerMode.IMAGE,
        onResult = { pickedFile ->
            showImagePicker = false
            if (pickedFile != null) {
                onUriChange(pickedFile.path)
            }
        },
    )

    Column(verticalArrangement = Arrangement.spacedBy(ZyntaSpacing.sm)) {
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
        ZyntaButton(
            text = "Browse Image",
            onClick = { showImagePicker = true },
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
