package com.zyntasolutions.zyntapos.debug.tabs

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.zyntasolutions.zyntapos.debug.mvi.DebugIntent
import com.zyntasolutions.zyntapos.debug.mvi.DebugState
import com.zyntasolutions.zyntapos.designsystem.components.ZyntaButton
import com.zyntasolutions.zyntapos.designsystem.components.ZyntaButtonVariant

/**
 * UI/UX tab — in-session theme, font scale overrides (never persisted to SettingsRepository).
 *
 * All overrides are stored in [DebugState] only. They reset when the debug session ends.
 * Theme override requires the caller (App.kt) to observe [DebugState.themeOverride] and apply it.
 */
@Composable
fun UiUxTab(
    state: DebugState,
    onIntent: (DebugIntent) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {

        // ── Theme override ─────────────────────────────────────────────────────
        Text("Theme Override", style = MaterialTheme.typography.titleSmall)
        Text(
            "In-session only — not persisted to Settings. Current: ${state.themeOverride ?: "System"}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(8.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ZyntaButton(
                        text = "Light",
                        onClick = { onIntent(DebugIntent.SetThemeOverride("LIGHT")) },
                        variant = if (state.themeOverride == "LIGHT") ZyntaButtonVariant.Primary
                                  else ZyntaButtonVariant.Secondary,
                        modifier = Modifier.weight(1f),
                    )
                    ZyntaButton(
                        text = "Dark",
                        onClick = { onIntent(DebugIntent.SetThemeOverride("DARK")) },
                        variant = if (state.themeOverride == "DARK") ZyntaButtonVariant.Primary
                                  else ZyntaButtonVariant.Secondary,
                        modifier = Modifier.weight(1f),
                    )
                    ZyntaButton(
                        text = "System",
                        onClick = { onIntent(DebugIntent.SetThemeOverride(null)) },
                        variant = if (state.themeOverride == null) ZyntaButtonVariant.Primary
                                  else ZyntaButtonVariant.Secondary,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }

        // ── Font scale ────────────────────────────────────────────────────────
        Text("Font Scale Override", style = MaterialTheme.typography.titleSmall)
        Text(
            "Current scale: ${"%.2f".format(state.fontScaleOverride)}×",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Slider(
                    value = state.fontScaleOverride,
                    onValueChange = { onIntent(DebugIntent.SetFontScale(it)) },
                    valueRange = 0.75f..1.50f,
                    steps = 2, // 0.85×, 1.0×, 1.15×, 1.3×
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text("0.75×", style = MaterialTheme.typography.labelSmall)
                    Text("1.0×",  style = MaterialTheme.typography.labelSmall)
                    Text("1.50×", style = MaterialTheme.typography.labelSmall)
                }
                Spacer(Modifier.height(8.dp))
                ZyntaButton(
                    text = "Reset to 1.0×",
                    onClick = { onIntent(DebugIntent.SetFontScale(1.0f)) },
                    variant = ZyntaButtonVariant.Ghost,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        // ── Preset quick actions ───────────────────────────────────────────────
        Text("Preset Scales", style = MaterialTheme.typography.titleSmall)

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(0.85f to "Small", 1.0f to "Normal", 1.15f to "Large", 1.30f to "XL").forEach { (scale, label) ->
                ZyntaButton(
                    text = label,
                    onClick = { onIntent(DebugIntent.SetFontScale(scale)) },
                    variant = if (state.fontScaleOverride == scale) ZyntaButtonVariant.Primary
                              else ZyntaButtonVariant.Secondary,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}
