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
import com.zyntasolutions.zyntapos.core.i18n.StringResource
import com.zyntasolutions.zyntapos.designsystem.components.LocalStrings
import com.zyntasolutions.zyntapos.feature.settings.Currency
import com.zyntasolutions.zyntapos.feature.settings.SettingsEffect
import com.zyntasolutions.zyntapos.feature.settings.SettingsIntent
import com.zyntasolutions.zyntapos.feature.settings.SettingsState
import kotlinx.coroutines.flow.collectLatest
import kotlinx.datetime.toLocalDateTime

// ─────────────────────────────────────────────────────────────────────────────
// GeneralSettingsScreen — Tab-based enterprise layout
// Tabs: Store Identity | Regional
// Sprint 23 — Step 13.1.2
// ─────────────────────────────────────────────────────────────────────────────

private val TIMEZONE_IDS = listOf(
    "Asia/Colombo", "Asia/Kolkata", "Asia/Dubai", "Asia/Singapore",
    "Europe/London", "Europe/Paris", "America/New_York", "America/Los_Angeles",
    "Pacific/Auckland",
)

/**
 * Formats timezone IDs with UTC offset for display.
 * E.g. "Asia/Colombo (UTC+05:30)"
 *
 * Computes offset by comparing local time to UTC at the current instant.
 */
private fun formatTimezoneWithOffset(tzId: String): String {
    return try {
        val tz = kotlinx.datetime.TimeZone.of(tzId)
        val now = kotlin.time.Clock.System.now()
        val localDt = now.toLocalDateTime(tz)
        val utcDt = now.toLocalDateTime(kotlinx.datetime.TimeZone.UTC)
        // Compute offset in minutes (local - UTC)
        val localMinutes = localDt.hour * 60 + localDt.minute
        val utcMinutes = utcDt.hour * 60 + utcDt.minute
        // Handle day boundary (e.g. UTC 23:00 → local next day 04:30)
        var diffMinutes = localMinutes - utcMinutes
        if (localDt.date > utcDt.date) diffMinutes += 24 * 60
        else if (localDt.date < utcDt.date) diffMinutes -= 24 * 60
        val sign = if (diffMinutes >= 0) "+" else "-"
        val absMins = kotlin.math.abs(diffMinutes)
        val hours = absMins / 60
        val minutes = absMins % 60
        val offsetStr = "$sign${hours.toString().padStart(2, '0')}:${minutes.toString().padStart(2, '0')}"
        "$tzId (UTC$offsetStr)"
    } catch (_: Exception) {
        tzId
    }
}

private val TIMEZONES = TIMEZONE_IDS.map { formatTimezoneWithOffset(it) }

private val DATE_FORMATS = listOf("dd/MM/yyyy", "MM/dd/yyyy", "yyyy-MM-dd", "d MMM yyyy")

/**
 * General settings screen — store identity, regional preferences, logo.
 *
 * @param state    Current [SettingsState.GeneralState] slice.
 * @param effects  Shared [SettingsEffect] flow.
 * @param onIntent Intent dispatcher to [SettingsViewModel].
 * @param onBack   Back navigation callback.
 */
@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun GeneralSettingsScreen(
    state: SettingsState.GeneralState,
    effects: kotlinx.coroutines.flow.Flow<SettingsEffect>,
    onIntent: (SettingsIntent) -> Unit,
    onBack: () -> Unit,
) {
    val s = LocalStrings.current
    val snackbarHostState = remember { SnackbarHostState() }
    var selectedTab by remember { mutableIntStateOf(0) }
    val tabTitles = listOf(s[StringResource.SETTINGS_GENERAL_TAB_STORE_IDENTITY], s[StringResource.SETTINGS_GENERAL_TAB_REGIONAL])

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
        title = s[StringResource.SETTINGS_GENERAL],
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
                                        s[StringResource.SETTINGS_GENERAL_STORE_DETAILS],
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.SemiBold,
                                    )
                                    Text(
                                        s[StringResource.SETTINGS_GENERAL_STORE_DETAILS_DESC],
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                    ZyntaTextField(
                                        value = state.storeName,
                                        onValueChange = { onIntent(SettingsIntent.UpdateStoreName(it)) },
                                        label = s[StringResource.SETTINGS_GENERAL_STORE_NAME],
                                        modifier = Modifier.fillMaxWidth(),
                                    )
                                    Text(
                                        s[StringResource.SETTINGS_GENERAL_STORE_NAME_DESC],
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                    ZyntaTextField(
                                        value = state.storeAddress,
                                        onValueChange = { onIntent(SettingsIntent.UpdateStoreAddress(it)) },
                                        label = s[StringResource.SETTINGS_GENERAL_STORE_ADDRESS],
                                        modifier = Modifier.fillMaxWidth(),
                                        singleLine = false,
                                        maxLines = 3,
                                    )
                                    Text(
                                        s[StringResource.SETTINGS_GENERAL_STORE_ADDRESS_DESC],
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                    ZyntaTextField(
                                        value = state.storePhone,
                                        onValueChange = { onIntent(SettingsIntent.UpdateStorePhone(it)) },
                                        label = s[StringResource.SETTINGS_GENERAL_PHONE_NUMBER],
                                        modifier = Modifier.fillMaxWidth(),
                                    )
                                    Text(
                                        s[StringResource.SETTINGS_GENERAL_PHONE_NUMBER_DESC],
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
                                        s[StringResource.SETTINGS_GENERAL_STORE_LOGO],
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.SemiBold,
                                    )
                                    Text(
                                        s[StringResource.SETTINGS_GENERAL_STORE_LOGO_DESC],
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
                                        s[StringResource.SETTINGS_GENERAL_CURRENCY],
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.SemiBold,
                                    )
                                    Text(
                                        s[StringResource.SETTINGS_GENERAL_CURRENCY_DESC],
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                    DropdownField(
                                        label = s[StringResource.SETTINGS_GENERAL_CURRENCY],
                                        options = Currency.entries.map { "${it.code} (${it.symbol})" },
                                        selectedIndex = Currency.entries.indexOf(state.currency).coerceAtLeast(0),
                                        onSelect = { onIntent(SettingsIntent.UpdateCurrency(Currency.entries[it])) },
                                    )
                                    Text(
                                        s[StringResource.SETTINGS_GENERAL_CURRENCY_EFFECT_DESC],
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
                                        s[StringResource.SETTINGS_GENERAL_TIMEZONE_DATE_FORMAT],
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.SemiBold,
                                    )
                                    Text(
                                        s[StringResource.SETTINGS_GENERAL_TIMEZONE_DATE_FORMAT_DESC],
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                    DropdownField(
                                        label = s[StringResource.SETTINGS_GENERAL_TIMEZONE],
                                        options = TIMEZONES,
                                        selectedIndex = TIMEZONE_IDS.indexOf(state.timezone).coerceAtLeast(0),
                                        onSelect = { onIntent(SettingsIntent.UpdateTimezone(TIMEZONE_IDS[it])) },
                                    )
                                    Text(
                                        s[StringResource.SETTINGS_GENERAL_TIMEZONE_DESC],
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                    DropdownField(
                                        label = s[StringResource.SETTINGS_GENERAL_DATE_FORMAT],
                                        options = DATE_FORMATS,
                                        selectedIndex = DATE_FORMATS.indexOf(state.dateFormat).coerceAtLeast(0),
                                        onSelect = { onIntent(SettingsIntent.UpdateDateFormat(DATE_FORMATS[it])) },
                                    )
                                    Text(
                                        s[StringResource.SETTINGS_GENERAL_DATE_FORMAT_DESC],
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
                                        s[StringResource.SETTINGS_GENERAL_LANGUAGE],
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.SemiBold,
                                    )
                                    Text(
                                        s[StringResource.SETTINGS_GENERAL_LANGUAGE_DESC],
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                    ZyntaTextField(
                                        value = state.language,
                                        onValueChange = {},
                                        label = s[StringResource.SETTINGS_GENERAL_LANGUAGE],
                                        modifier = Modifier.fillMaxWidth(),
                                        enabled = false, // English only in Phase 1
                                    )
                                    Text(
                                        s[StringResource.SETTINGS_GENERAL_LANGUAGE_PHASE1_NOTICE],
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
                        text = if (state.isSaving) s[StringResource.COMMON_SAVING] else s[StringResource.SETTINGS_GENERAL_SAVE],
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
    val s = LocalStrings.current
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
                contentDescription = s[StringResource.SETTINGS_GENERAL_LOGO_PREVIEW],
                modifier = Modifier.size(64.dp).align(Alignment.CenterHorizontally),
                tint = MaterialTheme.colorScheme.primary,
            )
        }
        ZyntaTextField(
            value = uri,
            onValueChange = onUriChange,
            label = s[StringResource.SETTINGS_GENERAL_LOGO_URI],
            modifier = Modifier.fillMaxWidth(),
        )
        ZyntaButton(
            text = s[StringResource.SETTINGS_GENERAL_BROWSE_IMAGE],
            onClick = { showImagePicker = true },
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

// ── Preview ───────────────────────────────────────────────────────────────────

@androidx.compose.ui.tooling.preview.Preview
@androidx.compose.runtime.Composable
private fun GeneralSettingsScreenPreview() {
    com.zyntasolutions.zyntapos.designsystem.theme.ZyntaTheme {
        GeneralSettingsScreen(
            state = SettingsState.GeneralState(),
            effects = kotlinx.coroutines.flow.emptyFlow(),
            onIntent = {},
            onBack = {},
        )
    }
}
