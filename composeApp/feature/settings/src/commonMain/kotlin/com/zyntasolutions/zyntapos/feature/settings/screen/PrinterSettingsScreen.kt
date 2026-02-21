package com.zyntasolutions.zyntapos.feature.settings.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import com.zyntasolutions.zyntapos.designsystem.components.ZentaButton
import com.zyntasolutions.zyntapos.designsystem.components.ZentaSnackbarHost
import com.zyntasolutions.zyntapos.designsystem.components.ZentaTextField
import com.zyntasolutions.zyntapos.designsystem.components.ZentaTopAppBar
import com.zyntasolutions.zyntapos.designsystem.layouts.ZentaScaffold
import com.zyntasolutions.zyntapos.designsystem.tokens.ZentaSpacing
import com.zyntasolutions.zyntapos.feature.settings.PaperWidthOption
import com.zyntasolutions.zyntapos.feature.settings.PrinterType
import com.zyntasolutions.zyntapos.feature.settings.SettingsEffect
import com.zyntasolutions.zyntapos.feature.settings.SettingsIntent
import com.zyntasolutions.zyntapos.feature.settings.SettingsState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectLatest

// ─────────────────────────────────────────────────────────────────────────────
// PrinterSettingsScreen — printer type, connection params, paper width,
//                         test print, receipt header/footer customisation.
// Sprint 23 — Step 13.1.5
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Printer configuration screen.
 *
 * Connection parameter fields are shown conditionally based on [PrinterType]:
 * - USB  → no extra params
 * - TCP  → host + port
 * - SERIAL → COM port + baud rate
 * - BLUETOOTH → BT address selector
 *
 * @param state     Current [SettingsState.PrinterState] slice.
 * @param effects   Shared [SettingsEffect] flow.
 * @param onIntent  Dispatch callback.
 * @param onBack    Back navigation.
 */
@Composable
fun PrinterSettingsScreen(
    state: SettingsState.PrinterState,
    effects: Flow<SettingsEffect>,
    onIntent: (SettingsIntent) -> Unit,
    onBack: () -> Unit,
) {
    val snackbarHostState = remember { SnackbarHostState() }

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

    ZentaScaffold(
        topBar = { ZentaTopAppBar(title = "Printer Settings", onNavigationClick = onBack) },
        snackbarHost = { ZentaSnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        LazyColumn(
            contentPadding = PaddingValues(
                start = ZentaSpacing.md,
                end = ZentaSpacing.md,
                top = innerPadding.calculateTopPadding() + ZentaSpacing.md,
                bottom = innerPadding.calculateBottomPadding() + ZentaSpacing.md,
            ),
            verticalArrangement = Arrangement.spacedBy(ZentaSpacing.md),
        ) {
            item {
                SectionHeader("Connection")
                Spacer(Modifier.height(ZentaSpacing.sm))
                DropdownField(
                    label = "Printer Type",
                    options = PrinterType.entries.map { it.name },
                    selectedIndex = PrinterType.entries.indexOf(state.printerType).coerceAtLeast(0),
                    onSelect = { onIntent(SettingsIntent.UpdatePrinterType(PrinterType.entries[it])) },
                )
            }

            // Conditional connection params
            when (state.printerType) {
                PrinterType.TCP -> item {
                    Column(verticalArrangement = Arrangement.spacedBy(ZentaSpacing.sm)) {
                        ZentaTextField(
                            value = state.tcpHost,
                            onValueChange = { onIntent(SettingsIntent.UpdateTcpHost(it)) },
                            label = "IP Address / Hostname",
                            modifier = Modifier.fillMaxWidth(),
                        )
                        ZentaTextField(
                            value = state.tcpPort,
                            onValueChange = { onIntent(SettingsIntent.UpdateTcpPort(it)) },
                            label = "TCP Port",
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
                PrinterType.SERIAL -> item {
                    Column(verticalArrangement = Arrangement.spacedBy(ZentaSpacing.sm)) {
                        ZentaTextField(
                            value = state.serialPort,
                            onValueChange = { onIntent(SettingsIntent.UpdateSerialPort(it)) },
                            label = "COM Port (e.g. COM3, /dev/ttyUSB0)",
                            modifier = Modifier.fillMaxWidth(),
                        )
                        ZentaTextField(
                            value = state.baudRate,
                            onValueChange = { onIntent(SettingsIntent.UpdateBaudRate(it)) },
                            label = "Baud Rate",
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
                PrinterType.BLUETOOTH -> item {
                    ZentaTextField(
                        value = state.btAddress,
                        onValueChange = { onIntent(SettingsIntent.UpdateBtAddress(it)) },
                        label = "Bluetooth Device Address (XX:XX:XX:XX:XX:XX)",
                        modifier = Modifier.fillMaxWidth(),
                        supportingText = "Pair the device in system settings first",
                    )
                }
                PrinterType.USB -> Unit // no extra params
            }

            item {
                SectionHeader("Paper")
                Spacer(Modifier.height(ZentaSpacing.sm))
                DropdownField(
                    label = "Paper Width",
                    options = PaperWidthOption.entries.map { "${it.mm}mm" },
                    selectedIndex = PaperWidthOption.entries.indexOf(state.paperWidth).coerceAtLeast(0),
                    onSelect = { onIntent(SettingsIntent.UpdatePaperWidth(PaperWidthOption.entries[it])) },
                )
            }

            item {
                ZentaButton(
                    text = if (state.isTestPrinting) "Printing test page…" else "Test Print",
                    onClick = { onIntent(SettingsIntent.TestPrint) },
                    enabled = !state.isTestPrinting,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            item {
                SectionHeader("Receipt Customisation")
                Spacer(Modifier.height(ZentaSpacing.sm))
                Text("Header Lines", style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Column(verticalArrangement = Arrangement.spacedBy(ZentaSpacing.sm)) {
                    state.headerLines.forEachIndexed { index, line ->
                        ZentaTextField(
                            value = line,
                            onValueChange = { onIntent(SettingsIntent.UpdateHeaderLine(index, it)) },
                            label = "Header Line ${index + 1}",
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }

            item {
                Text("Footer Lines", style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Column(verticalArrangement = Arrangement.spacedBy(ZentaSpacing.sm)) {
                    state.footerLines.forEachIndexed { index, line ->
                        ZentaTextField(
                            value = line,
                            onValueChange = { onIntent(SettingsIntent.UpdateFooterLine(index, it)) },
                            label = "Footer Line ${index + 1}",
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }

            item {
                SectionHeader("Show / Hide Fields")
                Spacer(Modifier.height(ZentaSpacing.sm))
                ToggleRow(
                    label = "Show QR code on receipt",
                    checked = state.showQrCode,
                    onCheckedChange = { onIntent(SettingsIntent.UpdateShowQrCode(it)) },
                )
                ToggleRow(
                    label = "Show logo on receipt",
                    checked = state.showLogo,
                    onCheckedChange = { onIntent(SettingsIntent.UpdateShowLogo(it)) },
                )
            }

            item {
                state.saveError?.let {
                    Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
                ZentaButton(
                    text = if (state.isSaving) "Saving…" else "Save Printer Settings",
                    onClick = { onIntent(SettingsIntent.SavePrinter) },
                    enabled = !state.isSaving,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}
