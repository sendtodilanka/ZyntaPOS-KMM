package com.zyntasolutions.zyntapos.feature.settings.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.material3.ExperimentalMaterial3Api
import com.zyntasolutions.zyntapos.designsystem.layouts.ZyntaPageScaffold
import com.zyntasolutions.zyntapos.designsystem.tokens.ZyntaSpacing
import com.zyntasolutions.zyntapos.feature.settings.SettingsEffect
import com.zyntasolutions.zyntapos.feature.settings.SettingsIntent
import com.zyntasolutions.zyntapos.feature.settings.SettingsState
import com.zyntasolutions.zyntapos.feature.settings.ThemeMode
import com.zyntasolutions.zyntapos.core.i18n.StringResource
import com.zyntasolutions.zyntapos.designsystem.components.LocalStrings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectLatest

// ─────────────────────────────────────────────────────────────────────────────
// AppearanceSettingsScreen — Light / Dark / System RadioButton group.
//                            Selection writes to SettingsRepository and triggers
//                            ZyntaTheme recomposition via ThemeModeChanged effect.
// Sprint 23 — Step 13.1.9
// ─────────────────────────────────────────────────────────────────────────────

private data class ThemeModeOption(
    val mode: ThemeMode,
    val label: String,
    val description: String,
)

@Composable
private fun themeOptions() = listOf(
    ThemeModeOption(ThemeMode.LIGHT,  LocalStrings.current[StringResource.SETTINGS_APPEARANCE_LIGHT],  LocalStrings.current[StringResource.SETTINGS_APPEARANCE_LIGHT_DESC]),
    ThemeModeOption(ThemeMode.DARK,   LocalStrings.current[StringResource.SETTINGS_APPEARANCE_DARK],   LocalStrings.current[StringResource.SETTINGS_APPEARANCE_DARK_DESC]),
    ThemeModeOption(ThemeMode.SYSTEM, LocalStrings.current[StringResource.SETTINGS_APPEARANCE_SYSTEM], LocalStrings.current[StringResource.SETTINGS_APPEARANCE_SYSTEM_DESC]),
)

/**
 * Appearance settings screen — selects the application theme mode.
 *
 * The selected [ThemeMode] is persisted via [SettingsRepository] and broadcast
 * via [SettingsEffect.ThemeModeChanged] so the root [ZyntaTheme] can recompose.
 *
 * @param state     Current [SettingsState.AppearanceState] slice.
 * @param effects   Shared [SettingsEffect] flow.
 * @param onIntent  Dispatch callback.
 * @param onBack    Back navigation.
 */
@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun AppearanceSettingsScreen(
    state: SettingsState.AppearanceState,
    effects: Flow<SettingsEffect>,
    onIntent: (SettingsIntent) -> Unit,
    onBack: () -> Unit,
) {
    val s = LocalStrings.current
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(Unit) { onIntent(SettingsIntent.LoadAppearance) }

    LaunchedEffect(effects) {
        effects.collect { effect ->
            when (effect) {
                is SettingsEffect.ThemeModeChanged ->
                    snackbarHostState.showSnackbar(s[StringResource.SETTINGS_APPEARANCE_THEME_SET, effect.mode.name.lowercase()])
                is SettingsEffect.ShowSnackbar -> snackbarHostState.showSnackbar(effect.message)
                else -> Unit
            }
        }
    }

    ZyntaPageScaffold(
        title = s[StringResource.SETTINGS_APPEARANCE],
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
                SectionHeader(s[StringResource.SETTINGS_APPEARANCE_THEME])
                Spacer(Modifier.height(ZyntaSpacing.sm))
                Text(
                    text = s[StringResource.SETTINGS_APPEARANCE_THEME_DESC],
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = ZyntaSpacing.sm),
                )
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column {
                        themeOptions().forEach { option ->
                            ThemeModeRow(
                                option = option,
                                selected = state.themeMode == option.mode,
                                onClick = { onIntent(SettingsIntent.UpdateThemeMode(option.mode)) },
                            )
                        }
                    }
                }
            }
        }
    }
}

// ─── Sub-composable ───────────────────────────────────────────────────────────

@Composable
private fun ThemeModeRow(
    option: ThemeModeOption,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = ZyntaSpacing.md, vertical = ZyntaSpacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(ZyntaSpacing.sm),
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Column(modifier = Modifier.weight(1f)) {
            Text(option.label, style = MaterialTheme.typography.bodyLarge)
            Text(
                option.description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

// ── Preview ───────────────────────────────────────────────────────────────────

@androidx.compose.ui.tooling.preview.Preview
@androidx.compose.runtime.Composable
private fun AppearanceSettingsScreenPreview() {
    com.zyntasolutions.zyntapos.designsystem.theme.ZyntaTheme {
        AppearanceSettingsScreen(
            state = SettingsState.AppearanceState(),
            effects = kotlinx.coroutines.flow.emptyFlow(),
            onIntent = {},
            onBack = {},
        )
    }
}
