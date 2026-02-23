package com.zyntasolutions.zyntapos.feature.settings.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Percent
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.zyntasolutions.zyntapos.designsystem.components.ZyntaButton
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
// PosSettingsScreen — Tab-based enterprise layout
// Tabs: Orders | Receipts | Discounts
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
    var selectedTab by remember { mutableIntStateOf(0) }
    val tabTitles = listOf("Orders", "Receipts", "Discounts")

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
                                    0 -> Icons.Default.ShoppingCart
                                    1 -> Icons.Default.Receipt
                                    else -> Icons.Default.Percent
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
                        // ── Orders Tab ──────────────────────────────────
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
                                        "Default Order Type",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.SemiBold,
                                    )
                                    Text(
                                        "Choose the order type that is pre-selected when creating a new sale. Staff can still change it per transaction.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                    DropdownField(
                                        label = "Default Order Type",
                                        options = OrderType.entries.map { it.name },
                                        selectedIndex = OrderType.entries.indexOf(state.defaultOrderType).coerceAtLeast(0),
                                        onSelect = { onIntent(SettingsIntent.UpdateDefaultOrderType(OrderType.entries[it])) },
                                    )
                                    Text(
                                        "Options include Dine-in, Takeaway, and Delivery depending on your store configuration.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    }

                    1 -> {
                        // ── Receipts Tab ─────────────────────────────────
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
                                        "Auto-Print",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.SemiBold,
                                    )
                                    Text(
                                        "Automatically send the receipt to the printer when a sale is completed.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                    ToggleRow(
                                        label = "Auto-print receipt after sale",
                                        checked = state.autoPrintReceipt,
                                        onCheckedChange = { onIntent(SettingsIntent.UpdateAutoPrintReceipt(it)) },
                                    )
                                    Text(
                                        "When enabled, a receipt is printed immediately after each completed transaction without staff interaction.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(start = ZyntaSpacing.sm),
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
                                        "Tax Display",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.SemiBold,
                                    )
                                    Text(
                                        "Control how tax amounts are shown on receipts and the POS interface.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                    DropdownField(
                                        label = "Tax Display Mode",
                                        options = TaxDisplayMode.entries.map { it.name.lowercase().replaceFirstChar { c -> c.uppercase() } },
                                        selectedIndex = TaxDisplayMode.entries.indexOf(state.taxDisplayMode).coerceAtLeast(0),
                                        onSelect = { onIntent(SettingsIntent.UpdateTaxDisplayMode(TaxDisplayMode.entries[it])) },
                                    )
                                    Text(
                                        "Inclusive shows prices with tax included; Exclusive shows tax as a separate line item.",
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
                                        "Receipt Template",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.SemiBold,
                                    )
                                    Text(
                                        "Select the visual template used for printed and digital receipts.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                    DropdownField(
                                        label = "Receipt Template",
                                        options = ReceiptTemplate.entries.map { it.name.lowercase().replaceFirstChar { c -> c.uppercase() } },
                                        selectedIndex = ReceiptTemplate.entries.indexOf(state.receiptTemplate).coerceAtLeast(0),
                                        onSelect = { onIntent(SettingsIntent.UpdateReceiptTemplate(ReceiptTemplate.entries[it])) },
                                    )
                                    Text(
                                        "Choose a layout that best fits your brand and paper size. Templates can be previewed in Printer Settings.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    }

                    2 -> {
                        // ── Discounts Tab ────────────────────────────────
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
                                        "Maximum Discount",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.SemiBold,
                                    )
                                    Text(
                                        "Set the highest discount percentage that staff members can apply to an order without manager approval.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                    Text(
                                        text = "Max Discount: ${state.maxDiscountPercent.toInt()}%",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.SemiBold,
                                    )
                                    Slider(
                                        value = state.maxDiscountPercent.toFloat(),
                                        onValueChange = { onIntent(SettingsIntent.UpdateMaxDiscount(it.toDouble())) },
                                        valueRange = 0f..100f,
                                        steps = 19, // 5% increments
                                        modifier = Modifier.fillMaxWidth(),
                                    )
                                    Text(
                                        "Drag the slider to adjust in 5% increments. Discounts above this limit will require manager override.",
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
                        text = if (state.isSaving) "Saving..." else "Save POS Settings",
                        onClick = { onIntent(SettingsIntent.SavePos) },
                        enabled = !state.isSaving,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
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
