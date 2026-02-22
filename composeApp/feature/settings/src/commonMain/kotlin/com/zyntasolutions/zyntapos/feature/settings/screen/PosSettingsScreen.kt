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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.zyntasolutions.zyntapos.designsystem.components.ZyntaButton
import androidx.compose.material3.ExperimentalMaterial3Api
import com.zyntasolutions.zyntapos.designsystem.layouts.ZyntaPageScaffold
import com.zyntasolutions.zyntapos.designsystem.tokens.ZyntaSpacing
import com.zyntasolutions.zyntapos.domain.model.OrderType
import com.zyntasolutions.zyntapos.feature.settings.ReceiptTemplate
import com.zyntasolutions.zyntapos.feature.settings.SettingsEffect
import com.zyntasolutions.zyntapos.feature.settings.SettingsIntent
import com.zyntasolutions.zyntapos.feature.settings.SettingsState
import com.zyntasolutions.zyntapos.feature.settings.TaxDisplayMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectLatest

// ─────────────────────────────────────────────────────────────────────────────
// PosSettingsScreen — default order type, auto-print, tax display, receipt template,
//                     max discount %.
// Sprint 23 — Step 13.1.3
// ─────────────────────────────────────────────────────────────────────────────

/**
 * POS settings screen.
 *
 * @param state     Current [SettingsState.PosState] slice.
 * @param effects   Shared [SettingsEffect] flow.
 * @param onIntent  Dispatch callback.
 * @param onBack    Back navigation.
 */
@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun PosSettingsScreen(
    state: SettingsState.PosState,
    effects: Flow<SettingsEffect>,
    onIntent: (SettingsIntent) -> Unit,
    onBack: () -> Unit,
) {
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(Unit) { onIntent(SettingsIntent.LoadPos) }

    LaunchedEffect(effects) {
        effects.collectLatest { effect ->
            when (effect) {
                SettingsEffect.PosSaved -> snackbarHostState.showSnackbar("POS settings saved.")
                is SettingsEffect.ShowSnackbar -> snackbarHostState.showSnackbar(effect.message)
                else -> Unit
            }
        }
    }

    ZyntaPageScaffold(
        title = "POS Settings",
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
                SectionHeader("Order")
                Spacer(Modifier.height(ZyntaSpacing.sm))
                DropdownField(
                    label = "Default Order Type",
                    options = OrderType.entries.map { it.name },
                    selectedIndex = OrderType.entries.indexOf(state.defaultOrderType).coerceAtLeast(0),
                    onSelect = { onIntent(SettingsIntent.UpdateDefaultOrderType(OrderType.entries[it])) },
                )
            }
            item {
                SectionHeader("Receipts")
                Spacer(Modifier.height(ZyntaSpacing.sm))
                ToggleRow(
                    label = "Auto-print receipt after sale",
                    checked = state.autoPrintReceipt,
                    onCheckedChange = { onIntent(SettingsIntent.UpdateAutoPrintReceipt(it)) },
                )
            }
            item {
                DropdownField(
                    label = "Tax Display Mode",
                    options = TaxDisplayMode.entries.map { it.name.lowercase().replaceFirstChar { c -> c.uppercase() } },
                    selectedIndex = TaxDisplayMode.entries.indexOf(state.taxDisplayMode).coerceAtLeast(0),
                    onSelect = { onIntent(SettingsIntent.UpdateTaxDisplayMode(TaxDisplayMode.entries[it])) },
                )
            }
            item {
                DropdownField(
                    label = "Receipt Template",
                    options = ReceiptTemplate.entries.map { it.name.lowercase().replaceFirstChar { c -> c.uppercase() } },
                    selectedIndex = ReceiptTemplate.entries.indexOf(state.receiptTemplate).coerceAtLeast(0),
                    onSelect = { onIntent(SettingsIntent.UpdateReceiptTemplate(ReceiptTemplate.entries[it])) },
                )
            }
            item {
                SectionHeader("Discounts")
                Spacer(Modifier.height(ZyntaSpacing.sm))
                Text(
                    text = "Max Discount: ${state.maxDiscountPercent.toInt()}%",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Slider(
                    value = state.maxDiscountPercent.toFloat(),
                    onValueChange = { onIntent(SettingsIntent.UpdateMaxDiscount(it.toDouble())) },
                    valueRange = 0f..100f,
                    steps = 19, // 5% increments
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            item {
                state.saveError?.let {
                    Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
                ZyntaButton(
                    text = if (state.isSaving) "Saving…" else "Save POS Settings",
                    onClick = { onIntent(SettingsIntent.SavePos) },
                    enabled = !state.isSaving,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

// ─── Shared toggle row (reused across settings screens) ───────────────────────

@Composable
internal fun ToggleRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth().padding(vertical = ZyntaSpacing.xs),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
