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
import androidx.compose.material.icons.filled.Cable
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.zyntasolutions.zyntapos.designsystem.components.ZyntaButton
import com.zyntasolutions.zyntapos.designsystem.components.ZyntaButtonVariant
import com.zyntasolutions.zyntapos.designsystem.components.ZyntaTextField
import com.zyntasolutions.zyntapos.designsystem.layouts.ZyntaPageScaffold
import com.zyntasolutions.zyntapos.designsystem.tokens.ZyntaSpacing
import com.zyntasolutions.zyntapos.feature.settings.PaperWidthOption
import com.zyntasolutions.zyntapos.feature.settings.PrinterType
import com.zyntasolutions.zyntapos.feature.settings.SettingsEffect
import com.zyntasolutions.zyntapos.feature.settings.SettingsIntent
import com.zyntasolutions.zyntapos.feature.settings.SettingsState
import com.zyntasolutions.zyntapos.feature.settings.components.PrinterStatusAlertBanner
import com.zyntasolutions.zyntapos.core.i18n.StringResource
import com.zyntasolutions.zyntapos.designsystem.components.LocalStrings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectLatest

// ─────────────────────────────────────────────────────────────────────────────
// PrinterSettingsScreen — Tab-based enterprise layout
// Tabs: Connection | Receipt Layout | Options
// ─────────────────────────────────────────────────────────────────────────────

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun PrinterSettingsScreen(
    state: SettingsState.PrinterState,
    effects: Flow<SettingsEffect>,
    onIntent: (SettingsIntent) -> Unit,
    onBack: () -> Unit,
) {
    val s = LocalStrings.current
    val snackbarHostState = remember { SnackbarHostState() }
    var selectedTab by remember { mutableIntStateOf(0) }
    val tabTitles = listOf(
        s[StringResource.SETTINGS_PRINTER_TAB_CONNECTION],
        s[StringResource.SETTINGS_PRINTER_TAB_RECEIPT_LAYOUT],
        s[StringResource.SETTINGS_PRINTER_TAB_OPTIONS],
    )

    LaunchedEffect(Unit) { onIntent(SettingsIntent.LoadPrinter) }

    LaunchedEffect(effects) {
        effects.collectLatest { effect ->
            when (effect) {
                SettingsEffect.PrinterSaved -> snackbarHostState.showSnackbar(s[StringResource.SETTINGS_PRINTER_SAVED])
                SettingsEffect.PrintTestPageSent -> snackbarHostState.showSnackbar(s[StringResource.SETTINGS_PRINTER_TEST_SENT])
                is SettingsEffect.ShowSnackbar -> snackbarHostState.showSnackbar(effect.message)
                else -> Unit
            }
        }
    }

    ZyntaPageScaffold(
        title = s[StringResource.SETTINGS_PRINTER_SETTINGS_TITLE],
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
                                    0 -> Icons.Default.Cable
                                    1 -> Icons.Default.Receipt
                                    else -> Icons.Default.Settings
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
                        // ── Connection Tab ──────────────────────────────
                        // Printer hardware alert banner (paper-out / cover-open)
                        item { PrinterStatusAlertBanner(modifier = Modifier.fillMaxWidth()) }
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
                                        s[StringResource.SETTINGS_PRINTER_CONNECTION_TITLE],
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.SemiBold,
                                    )
                                    Text(
                                        s[StringResource.SETTINGS_PRINTER_CONNECTION_DESC],
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                    DropdownField(
                                        label = s[StringResource.SETTINGS_PRINTER_TYPE],
                                        options = PrinterType.entries.map { it.name },
                                        selectedIndex = PrinterType.entries.indexOf(state.printerType).coerceAtLeast(0),
                                        onSelect = { onIntent(SettingsIntent.UpdatePrinterType(PrinterType.entries[it])) },
                                    )

                                    when (state.printerType) {
                                        PrinterType.TCP -> {
                                            ZyntaTextField(
                                                value = state.tcpHost,
                                                onValueChange = { onIntent(SettingsIntent.UpdateTcpHost(it)) },
                                                label = s[StringResource.SETTINGS_PRINTER_IP_HOSTNAME],
                                                modifier = Modifier.fillMaxWidth(),
                                            )
                                            Text(
                                                s[StringResource.SETTINGS_PRINTER_IP_DESC],
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            )
                                            ZyntaTextField(
                                                value = state.tcpPort,
                                                onValueChange = { onIntent(SettingsIntent.UpdateTcpPort(it)) },
                                                label = s[StringResource.SETTINGS_PRINTER_TCP_PORT],
                                                modifier = Modifier.fillMaxWidth(),
                                            )
                                            Text(
                                                s[StringResource.SETTINGS_PRINTER_TCP_PORT_DESC],
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            )
                                        }
                                        PrinterType.SERIAL -> {
                                            ZyntaTextField(
                                                value = state.serialPort,
                                                onValueChange = { onIntent(SettingsIntent.UpdateSerialPort(it)) },
                                                label = s[StringResource.SETTINGS_PRINTER_SERIAL_PORT_LABEL],
                                                modifier = Modifier.fillMaxWidth(),
                                            )
                                            ZyntaTextField(
                                                value = state.baudRate,
                                                onValueChange = { onIntent(SettingsIntent.UpdateBaudRate(it)) },
                                                label = s[StringResource.SETTINGS_PRINTER_BAUD_RATE],
                                                modifier = Modifier.fillMaxWidth(),
                                            )
                                            Text(
                                                s[StringResource.SETTINGS_PRINTER_BAUD_RATE_HINT],
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            )
                                        }
                                        PrinterType.BLUETOOTH -> {
                                            ZyntaTextField(
                                                value = state.btAddress,
                                                onValueChange = { onIntent(SettingsIntent.UpdateBtAddress(it)) },
                                                label = s[StringResource.SETTINGS_PRINTER_BT_ADDRESS_LABEL],
                                                modifier = Modifier.fillMaxWidth(),
                                            )
                                            Text(
                                                s[StringResource.SETTINGS_PRINTER_BT_PAIR_HINT],
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            )
                                        }
                                        PrinterType.USB -> {
                                            Text(
                                                s[StringResource.SETTINGS_PRINTER_USB_HINT],
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            )
                                        }
                                    }
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
                                        s[StringResource.SETTINGS_PRINTER_PAPER_WIDTH],
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.SemiBold,
                                    )
                                    Text(
                                        s[StringResource.SETTINGS_PRINTER_PAPER_WIDTH_DESC],
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                    DropdownField(
                                        label = s[StringResource.SETTINGS_PRINTER_PAPER_WIDTH],
                                        options = PaperWidthOption.entries.map { "${it.mm}mm" },
                                        selectedIndex = PaperWidthOption.entries.indexOf(state.paperWidth).coerceAtLeast(0),
                                        onSelect = { onIntent(SettingsIntent.UpdatePaperWidth(PaperWidthOption.entries[it])) },
                                    )
                                }
                            }
                        }

                        item {
                            ZyntaButton(
                                text = if (state.isTestPrinting) s[StringResource.SETTINGS_PRINTER_PRINTING_TEST] else s[StringResource.SETTINGS_PRINTER_SEND_TEST],
                                onClick = { onIntent(SettingsIntent.TestPrint) },
                                enabled = !state.isTestPrinting,
                                variant = ZyntaButtonVariant.Secondary,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }

                    1 -> {
                        // ── Receipt Layout Tab ──────────────────────────
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
                                        s[StringResource.SETTINGS_PRINTER_RECEIPT_HEADER],
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.SemiBold,
                                    )
                                    Text(
                                        s[StringResource.SETTINGS_PRINTER_RECEIPT_HEADER_DESC],
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                    state.headerLines.forEachIndexed { index, line ->
                                        ZyntaTextField(
                                            value = line,
                                            onValueChange = { onIntent(SettingsIntent.UpdateHeaderLine(index, it)) },
                                            label = s[StringResource.SETTINGS_PRINTER_HEADER_LINE, index + 1],
                                            modifier = Modifier.fillMaxWidth(),
                                        )
                                    }
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
                                        s[StringResource.SETTINGS_PRINTER_RECEIPT_FOOTER],
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.SemiBold,
                                    )
                                    Text(
                                        s[StringResource.SETTINGS_PRINTER_RECEIPT_FOOTER_DESC],
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                    state.footerLines.forEachIndexed { index, line ->
                                        ZyntaTextField(
                                            value = line,
                                            onValueChange = { onIntent(SettingsIntent.UpdateFooterLine(index, it)) },
                                            label = s[StringResource.SETTINGS_PRINTER_FOOTER_LINE, index + 1],
                                            modifier = Modifier.fillMaxWidth(),
                                        )
                                    }
                                }
                            }
                        }
                    }

                    2 -> {
                        // ── Options Tab ─────────────────────────────────
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
                                        s[StringResource.SETTINGS_PRINTER_RECEIPT_OPTIONS],
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.SemiBold,
                                    )
                                    Text(
                                        s[StringResource.SETTINGS_PRINTER_RECEIPT_OPTIONS_DESC],
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                    ToggleRow(
                                        label = s[StringResource.SETTINGS_PRINTER_SHOW_QR],
                                        checked = state.showQrCode,
                                        onCheckedChange = { onIntent(SettingsIntent.UpdateShowQrCode(it)) },
                                    )
                                    Text(
                                        s[StringResource.SETTINGS_PRINTER_QR_DESC],
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(start = ZyntaSpacing.sm),
                                    )
                                    HorizontalDivider()
                                    ToggleRow(
                                        label = s[StringResource.SETTINGS_PRINTER_SHOW_LOGO],
                                        checked = state.showLogo,
                                        onCheckedChange = { onIntent(SettingsIntent.UpdateShowLogo(it)) },
                                    )
                                    Text(
                                        s[StringResource.SETTINGS_PRINTER_LOGO_DESC],
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(start = ZyntaSpacing.sm),
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
                        text = if (state.isSaving) s[StringResource.COMMON_SAVING] else s[StringResource.SETTINGS_PRINTER_SAVE_ACTION],
                        onClick = { onIntent(SettingsIntent.SavePrinter) },
                        enabled = !state.isSaving,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}

// ── Preview ───────────────────────────────────────────────────────────────────

@androidx.compose.ui.tooling.preview.Preview
@androidx.compose.runtime.Composable
private fun PrinterSettingsScreenPreview() {
    com.zyntasolutions.zyntapos.designsystem.theme.ZyntaTheme {
        PrinterSettingsScreen(
            state = SettingsState.PrinterState(),
            effects = kotlinx.coroutines.flow.emptyFlow(),
            onIntent = {},
            onBack = {},
        )
    }
}
