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
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
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
import com.zyntasolutions.zyntapos.core.i18n.StringResource
import com.zyntasolutions.zyntapos.designsystem.components.LocalStrings
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
    val s = LocalStrings.current
    val snackbarHostState = remember { SnackbarHostState() }
    var selectedTab by remember { mutableIntStateOf(0) }
    val tabTitles = listOf(s[StringResource.SETTINGS_POS_TAB_ORDERS], s[StringResource.SETTINGS_POS_TAB_RECEIPTS], s[StringResource.SETTINGS_POS_TAB_DISCOUNTS])

    LaunchedEffect(Unit) { onIntent(SettingsIntent.LoadPos) }

    LaunchedEffect(effects) {
        effects.collectLatest { effect ->
            when (effect) {
                SettingsEffect.PosSaved -> snackbarHostState.showSnackbar(s[StringResource.SETTINGS_POS_SAVED])
                is SettingsEffect.ShowSnackbar -> snackbarHostState.showSnackbar(effect.message)
                else -> Unit
            }
        }
    }

    ZyntaPageScaffold(
        title = s[StringResource.SETTINGS_POS],
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
                                        s[StringResource.SETTINGS_POS_ORDER_TYPE_TITLE],
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.SemiBold,
                                    )
                                    Text(
                                        s[StringResource.SETTINGS_POS_ORDER_TYPE_DESC],
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                    DropdownField(
                                        label = s[StringResource.SETTINGS_POS_ORDER_TYPE_TITLE],
                                        options = OrderType.entries.map { it.name },
                                        selectedIndex = OrderType.entries.indexOf(state.defaultOrderType).coerceAtLeast(0),
                                        onSelect = { onIntent(SettingsIntent.UpdateDefaultOrderType(OrderType.entries[it])) },
                                    )
                                    Text(
                                        s[StringResource.SETTINGS_POS_ORDER_TYPE_HINT],
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
                                        s[StringResource.SETTINGS_POS_AUTO_PRINT_TITLE],
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.SemiBold,
                                    )
                                    Text(
                                        s[StringResource.SETTINGS_POS_AUTO_PRINT_DESC],
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                    ToggleRow(
                                        label = s[StringResource.SETTINGS_POS_AUTO_PRINT_LABEL],
                                        checked = state.autoPrintReceipt,
                                        onCheckedChange = { onIntent(SettingsIntent.UpdateAutoPrintReceipt(it)) },
                                    )
                                    Text(
                                        s[StringResource.SETTINGS_POS_AUTO_PRINT_HINT],
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
                                        s[StringResource.SETTINGS_POS_TAX_DISPLAY_TITLE],
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.SemiBold,
                                    )
                                    Text(
                                        s[StringResource.SETTINGS_POS_TAX_DISPLAY_DESC],
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                    DropdownField(
                                        label = s[StringResource.SETTINGS_POS_TAX_DISPLAY_MODE],
                                        options = TaxDisplayMode.entries.map {
                                            it.name.lowercase().replaceFirstChar { c -> c.uppercase() }
                                        },
                                        selectedIndex = TaxDisplayMode.entries.indexOf(state.taxDisplayMode).coerceAtLeast(0),
                                        onSelect = { onIntent(SettingsIntent.UpdateTaxDisplayMode(TaxDisplayMode.entries[it])) },
                                    )
                                    Text(
                                        s[StringResource.SETTINGS_POS_TAX_DISPLAY_HINT],
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
                                        s[StringResource.SETTINGS_POS_RECEIPT_TEMPLATE_TITLE],
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.SemiBold,
                                    )
                                    Text(
                                        s[StringResource.SETTINGS_POS_RECEIPT_TEMPLATE_DESC],
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                    DropdownField(
                                        label = s[StringResource.SETTINGS_POS_RECEIPT_TEMPLATE_TITLE],
                                        options = ReceiptTemplate.entries.map { it.name.lowercase().replaceFirstChar { c -> c.uppercase() } },
                                        selectedIndex = ReceiptTemplate.entries.indexOf(state.receiptTemplate).coerceAtLeast(0),
                                        onSelect = { onIntent(SettingsIntent.UpdateReceiptTemplate(ReceiptTemplate.entries[it])) },
                                    )
                                    Text(
                                        s[StringResource.SETTINGS_POS_RECEIPT_TEMPLATE_HINT],
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
                                        s[StringResource.SETTINGS_POS_MAX_DISCOUNT_TITLE],
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.SemiBold,
                                    )
                                    Text(
                                        s[StringResource.SETTINGS_POS_MAX_DISCOUNT_DESC],
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                    Text(
                                        text = s[StringResource.SETTINGS_POS_MAX_DISCOUNT_VALUE, state.maxDiscountPercent.toInt()],
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
                                        s[StringResource.SETTINGS_POS_MAX_DISCOUNT_HINT],
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    }
                }

                // ── Daily Sales Target ───────────────────────────────────
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        ),
                    ) {
                        Column(
                            modifier = Modifier.padding(ZyntaSpacing.md),
                            verticalArrangement = Arrangement.spacedBy(ZyntaSpacing.sm),
                        ) {
                            Text(
                                s[StringResource.SETTINGS_POS_DAILY_TARGET_TITLE],
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Text(
                                s[StringResource.SETTINGS_POS_DAILY_TARGET_DESC],
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            var targetText by remember(state.dailySalesTarget) {
                                mutableStateOf(state.dailySalesTarget.toLong().toString())
                            }
                            OutlinedTextField(
                                value = targetText,
                                onValueChange = { raw ->
                                    targetText = raw
                                    raw.toDoubleOrNull()?.let { v ->
                                        onIntent(SettingsIntent.UpdateDailySalesTarget(v))
                                    }
                                },
                                label = { Text(s[StringResource.SETTINGS_POS_DAILY_TARGET_AMOUNT]) },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                modifier = Modifier.fillMaxWidth(),
                            )
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
                        text = if (state.isSaving) s[StringResource.COMMON_SAVING] else s[StringResource.SETTINGS_POS_SAVE_ACTION],
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

// ── Preview ───────────────────────────────────────────────────────────────────

@androidx.compose.ui.tooling.preview.Preview
@androidx.compose.runtime.Composable
private fun PosSettingsScreenPreview() {
    com.zyntasolutions.zyntapos.designsystem.theme.ZyntaTheme {
        PosSettingsScreen(
            state = SettingsState.PosState(),
            effects = kotlinx.coroutines.flow.emptyFlow(),
            onIntent = {},
            onBack = {},
        )
    }
}
