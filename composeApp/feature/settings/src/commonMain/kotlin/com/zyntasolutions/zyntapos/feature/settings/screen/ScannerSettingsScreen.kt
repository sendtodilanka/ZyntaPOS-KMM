package com.zyntasolutions.zyntapos.feature.settings.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.zyntasolutions.zyntapos.designsystem.layouts.ZyntaPageScaffold
import com.zyntasolutions.zyntapos.designsystem.tokens.ZyntaSpacing
import com.zyntasolutions.zyntapos.feature.settings.SettingsEffect
import com.zyntasolutions.zyntapos.feature.settings.SettingsIntent
import com.zyntasolutions.zyntapos.feature.settings.SettingsState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectLatest
import kotlin.time.Instant

/**
 * Scanner settings screen — live scan display and configuration.
 */
@Composable
fun ScannerSettingsScreen(
    state: SettingsState.ScannerSettingsState,
    effects: Flow<SettingsEffect>,
    onIntent: (SettingsIntent) -> Unit,
    onBack: () -> Unit,
) {
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(Unit) { onIntent(SettingsIntent.LoadScannerSettings) }

    LaunchedEffect(effects) {
        effects.collectLatest { effect ->
            when (effect) {
                SettingsEffect.ScannerSettingsSaved ->
                    snackbarHostState.showSnackbar("Scanner settings saved.")
                is SettingsEffect.ShowSnackbar ->
                    snackbarHostState.showSnackbar(effect.message)
                else -> Unit
            }
        }
    }

    ZyntaPageScaffold(
        title = "Scanner Settings",
        onNavigateBack = onBack,
        snackbarHostState = snackbarHostState,
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(
                    top = innerPadding.calculateTopPadding() + ZyntaSpacing.md,
                    start = ZyntaSpacing.md,
                    end = ZyntaSpacing.md,
                    bottom = innerPadding.calculateBottomPadding() + ZyntaSpacing.md,
                ),
            verticalArrangement = Arrangement.spacedBy(ZyntaSpacing.md),
        ) {
            // ── Live scan display ─────────────────────────────────────────────
            Text("Live Scanner Test", style = MaterialTheme.typography.titleSmall)

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(
                        width = 1.dp,
                        color = MaterialTheme.colorScheme.outline,
                        shape = RoundedCornerShape(8.dp),
                    )
                    .background(
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                        shape = RoundedCornerShape(8.dp),
                    )
                    .padding(ZyntaSpacing.md),
            ) {
                if (state.lastScannedBarcode != null) {
                    Text(
                        text = state.lastScannedBarcode,
                        style = MaterialTheme.typography.headlineMedium.copy(fontFamily = FontFamily.Monospace),
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(ZyntaSpacing.md),
                    ) {
                        if (state.lastScannedFormat != null) {
                            Text(
                                text = state.lastScannedFormat,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.secondary,
                            )
                        }
                        if (state.lastScannedAt != null) {
                            Text(
                                text = Instant.fromEpochMilliseconds(state.lastScannedAt).toString(),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                } else {
                    Text(
                        text = "Scan a barcode to test the scanner…",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            HorizontalDivider()

            // ── Configuration ─────────────────────────────────────────────────
            Text("Configuration", style = MaterialTheme.typography.titleSmall)

            OutlinedTextField(
                value = state.minBarcodeLength.toString(),
                onValueChange = { v -> v.toIntOrNull()?.let { onIntent(SettingsIntent.UpdateScannerMinLength(it)) } },
                label = { Text("Minimum Barcode Length") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
            )

            OutlinedTextField(
                value = state.prefixToStrip,
                onValueChange = { onIntent(SettingsIntent.UpdateScannerPrefix(it)) },
                label = { Text("Strip Prefix (e.g. STX character)") },
                modifier = Modifier.fillMaxWidth(),
            )

            OutlinedTextField(
                value = state.suffixToStrip,
                onValueChange = { onIntent(SettingsIntent.UpdateScannerSuffix(it)) },
                label = { Text("Strip Suffix (e.g. ETX character)") },
                modifier = Modifier.fillMaxWidth(),
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text("Sound Feedback on Scan", style = MaterialTheme.typography.bodyMedium)
                Switch(
                    checked = state.soundFeedbackEnabled,
                    onCheckedChange = { onIntent(SettingsIntent.UpdateScannerSoundFeedback(it)) },
                )
            }

            Button(
                onClick = { onIntent(SettingsIntent.SaveScannerSettings) },
                enabled = !state.isSaving,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(if (state.isSaving) "Saving…" else "Save")
            }
        }
    }
}
