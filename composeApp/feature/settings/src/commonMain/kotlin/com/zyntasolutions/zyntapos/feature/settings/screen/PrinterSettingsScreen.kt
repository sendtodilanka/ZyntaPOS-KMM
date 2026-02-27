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
    val snackbarHostState = remember { SnackbarHostState() }
    var selectedTab by remember { mutableIntStateOf(0) }
    val tabTitles = listOf("Connection", "Receipt Layout", "Options")

    LaunchedEffect(Unit) { onIntent(SettingsIntent.LoadPrinter) }

    LaunchedEffect(effects) {
        effects.collectLatest { effect ->
            when (effect) {
                SettingsEffect.PrinterSaved -> snackbarHostState.showSnackbar("Printer settings saved.")
                SettingsEffect.PrintTestPageSent -> snackbarHostState.showSnackbar("Test page sent to printer.")
                is SettingsEffect.ShowSnackbar -> snackbarHostState.showSnackbar(effect.message)
                else -> Unit
            }
        }
    }

    ZyntaPageScaffold(
        title = "Printer Settings",
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
                                        "Printer Connection",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.SemiBold,
                                    )
                                    Text(
                                        "Select how your receipt printer is connected to this device.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                    DropdownField(
                                        label = "Printer Type",
                                        options = PrinterType.entries.map { it.name },
                                        selectedIndex = PrinterType.entries.indexOf(state.printerType).coerceAtLeast(0),
                                        onSelect = { onIntent(SettingsIntent.UpdatePrinterType(PrinterType.entries[it])) },
                                    )

                                    when (state.printerType) {
                                        PrinterType.TCP -> {
                                            ZyntaTextField(
                                                value = state.tcpHost,
                                                onValueChange = { onIntent(SettingsIntent.UpdateTcpHost(it)) },
                                                label = "IP Address / Hostname",
                                                modifier = Modifier.fillMaxWidth(),
                                            )
                                            Text(
                                                "Enter the network address of your receipt printer (e.g. 192.168.1.100).",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            )
                                            ZyntaTextField(
                                                value = state.tcpPort,
                                                onValueChange = { onIntent(SettingsIntent.UpdateTcpPort(it)) },
                                                label = "TCP Port",
                                                modifier = Modifier.fillMaxWidth(),
                                            )
                                            Text(
                                                "Default ESC/POS port is 9100.",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            )
                                        }
                                        PrinterType.SERIAL -> {
                                            ZyntaTextField(
                                                value = state.serialPort,
                                                onValueChange = { onIntent(SettingsIntent.UpdateSerialPort(it)) },
                                                label = "COM Port (e.g. COM3, /dev/ttyUSB0)",
                                                modifier = Modifier.fillMaxWidth(),
                                            )
                                            ZyntaTextField(
                                                value = state.baudRate,
                                                onValueChange = { onIntent(SettingsIntent.UpdateBaudRate(it)) },
                                                label = "Baud Rate",
                                                modifier = Modifier.fillMaxWidth(),
                                            )
                                            Text(
                                                "Common baud rates: 9600, 19200, 38400, 115200.",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            )
                                        }
                                        PrinterType.BLUETOOTH -> {
                                            ZyntaTextField(
                                                value = state.btAddress,
                                                onValueChange = { onIntent(SettingsIntent.UpdateBtAddress(it)) },
                                                label = "Bluetooth Device Address (XX:XX:XX:XX:XX:XX)",
                                                modifier = Modifier.fillMaxWidth(),
                                            )
                                            Text(
                                                "Pair the Bluetooth printer in your system settings before configuring here.",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            )
                                        }
                                        PrinterType.USB -> {
                                            Text(
                                                "USB printer will be auto-detected when connected. No additional configuration needed.",
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
                                        "Paper Width",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.SemiBold,
                                    )
                                    Text(
                                        "Select the paper roll width installed in your printer.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                    DropdownField(
                                        label = "Paper Width",
                                        options = PaperWidthOption.entries.map { "${it.mm}mm" },
                                        selectedIndex = PaperWidthOption.entries.indexOf(state.paperWidth).coerceAtLeast(0),
                                        onSelect = { onIntent(SettingsIntent.UpdatePaperWidth(PaperWidthOption.entries[it])) },
                                    )
                                }
                            }
                        }

                        item {
                            ZyntaButton(
                                text = if (state.isTestPrinting) "Printing test page..." else "Send Test Page",
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
                                        "Receipt Header",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.SemiBold,
                                    )
                                    Text(
                                        "Customise the text that appears at the top of each receipt (e.g. store name, address, phone).",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                    state.headerLines.forEachIndexed { index, line ->
                                        ZyntaTextField(
                                            value = line,
                                            onValueChange = { onIntent(SettingsIntent.UpdateHeaderLine(index, it)) },
                                            label = "Header Line ${index + 1}",
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
                                        "Receipt Footer",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.SemiBold,
                                    )
                                    Text(
                                        "Customise the text at the bottom of each receipt (e.g. thank-you message, return policy).",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                    state.footerLines.forEachIndexed { index, line ->
                                        ZyntaTextField(
                                            value = line,
                                            onValueChange = { onIntent(SettingsIntent.UpdateFooterLine(index, it)) },
                                            label = "Footer Line ${index + 1}",
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
                                        "Receipt Options",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.SemiBold,
                                    )
                                    Text(
                                        "Control what additional elements appear on each printed receipt.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                    ToggleRow(
                                        label = "Show QR code on receipt",
                                        checked = state.showQrCode,
                                        onCheckedChange = { onIntent(SettingsIntent.UpdateShowQrCode(it)) },
                                    )
                                    Text(
                                        "Prints a QR code linking to the digital receipt for the customer.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(start = ZyntaSpacing.sm),
                                    )
                                    HorizontalDivider()
                                    ToggleRow(
                                        label = "Show logo on receipt",
                                        checked = state.showLogo,
                                        onCheckedChange = { onIntent(SettingsIntent.UpdateShowLogo(it)) },
                                    )
                                    Text(
                                        "Prints your store logo at the top of each receipt (requires logo upload in General settings).",
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
                        text = if (state.isSaving) "Saving..." else "Save Printer Settings",
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
