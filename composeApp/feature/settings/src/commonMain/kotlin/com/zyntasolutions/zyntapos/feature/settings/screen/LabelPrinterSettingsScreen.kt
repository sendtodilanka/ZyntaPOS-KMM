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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.Slider
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
import androidx.compose.ui.unit.dp
import com.zyntasolutions.zyntapos.designsystem.layouts.ZyntaPageScaffold
import com.zyntasolutions.zyntapos.designsystem.tokens.ZyntaSpacing
import com.zyntasolutions.zyntapos.feature.settings.LabelPrinterTypeOption
import com.zyntasolutions.zyntapos.feature.settings.SettingsEffect
import com.zyntasolutions.zyntapos.feature.settings.SettingsIntent
import com.zyntasolutions.zyntapos.feature.settings.SettingsState
import com.zyntasolutions.zyntapos.feature.settings.components.PrinterStatusBadge
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectLatest

/**
 * Label printer settings screen — 3 tabs: Connection, Options, Status.
 */
@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun LabelPrinterSettingsScreen(
    state: SettingsState.LabelPrinterState,
    effects: Flow<SettingsEffect>,
    onIntent: (SettingsIntent) -> Unit,
    onBack: () -> Unit,
) {
    val snackbarHostState = remember { SnackbarHostState() }
    var selectedTab by remember { mutableIntStateOf(0) }
    val tabTitles = listOf("Connection", "Options", "Status")

    LaunchedEffect(Unit) { onIntent(SettingsIntent.LoadLabelPrinter) }

    LaunchedEffect(effects) {
        effects.collectLatest { effect ->
            when (effect) {
                SettingsEffect.LabelPrinterSaved ->
                    snackbarHostState.showSnackbar("Label printer settings saved.")
                is SettingsEffect.ShowSnackbar ->
                    snackbarHostState.showSnackbar(effect.message)
                else -> Unit
            }
        }
    }

    ZyntaPageScaffold(
        title = "Label Printer",
        onNavigateBack = onBack,
        snackbarHostState = snackbarHostState,
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = innerPadding.calculateTopPadding()),
        ) {
            PrimaryScrollableTabRow(selectedTabIndex = selectedTab) {
                tabTitles.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        text = { Text(title) },
                    )
                }
            }

            when (selectedTab) {
                0 -> LabelPrinterConnectionTab(state, onIntent)
                1 -> LabelPrinterOptionsTab(state, onIntent)
                2 -> LabelPrinterStatusTab(state)
            }
        }
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun LabelPrinterConnectionTab(
    state: SettingsState.LabelPrinterState,
    onIntent: (SettingsIntent) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(ZyntaSpacing.md),
        verticalArrangement = Arrangement.spacedBy(ZyntaSpacing.md),
    ) {
        // Printer type selector
        var typeExpanded by remember { mutableStateOf(false) }
        ExposedDropdownMenuBox(
            expanded = typeExpanded,
            onExpandedChange = { typeExpanded = it },
        ) {
            OutlinedTextField(
                value = state.printerType.displayName,
                onValueChange = {},
                readOnly = true,
                label = { Text("Printer Type") },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = typeExpanded) },
                modifier = Modifier.fillMaxWidth().menuAnchor(),
            )
            ExposedDropdownMenu(
                expanded = typeExpanded,
                onDismissRequest = { typeExpanded = false },
            ) {
                LabelPrinterTypeOption.entries.forEach { option ->
                    DropdownMenuItem(
                        text = { Text(option.displayName) },
                        onClick = {
                            onIntent(SettingsIntent.UpdateLabelPrinterType(option))
                            typeExpanded = false
                        },
                    )
                }
            }
        }

        // TCP fields
        val showTcp = state.printerType.domainKey.endsWith("TCP")
        if (showTcp) {
            OutlinedTextField(
                value = state.tcpHost,
                onValueChange = { onIntent(SettingsIntent.UpdateLabelPrinterTcpHost(it)) },
                label = { Text("IP Address / Hostname") },
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = state.tcpPort,
                onValueChange = { onIntent(SettingsIntent.UpdateLabelPrinterTcpPort(it)) },
                label = { Text("TCP Port") },
                modifier = Modifier.fillMaxWidth(),
            )
        }

        // Serial fields
        val showSerial = state.printerType.domainKey.endsWith("USB")
        if (showSerial) {
            OutlinedTextField(
                value = state.serialPort,
                onValueChange = { onIntent(SettingsIntent.UpdateLabelPrinterSerialPort(it)) },
                label = { Text("Serial Port (e.g. /dev/ttyUSB0)") },
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = state.baudRate,
                onValueChange = { onIntent(SettingsIntent.UpdateLabelPrinterBaudRate(it)) },
                label = { Text("Baud Rate") },
                modifier = Modifier.fillMaxWidth(),
            )
        }

        // Bluetooth field
        val showBt = state.printerType.domainKey.endsWith("BT")
        if (showBt) {
            OutlinedTextField(
                value = state.btAddress,
                onValueChange = { onIntent(SettingsIntent.UpdateLabelPrinterBtAddress(it)) },
                label = { Text("Bluetooth MAC Address") },
                modifier = Modifier.fillMaxWidth(),
            )
        }

        if (state.saveError != null) {
            Text(
                text = state.saveError,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
            )
        }

        Button(
            onClick = { onIntent(SettingsIntent.SaveLabelPrinter) },
            enabled = !state.isSaving,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(if (state.isSaving) "Saving…" else "Save")
        }
    }
}

@Composable
private fun LabelPrinterOptionsTab(
    state: SettingsState.LabelPrinterState,
    onIntent: (SettingsIntent) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(ZyntaSpacing.md),
        verticalArrangement = Arrangement.spacedBy(ZyntaSpacing.md),
    ) {
        Text("Print Darkness (${state.darknessLevel})", style = MaterialTheme.typography.bodyMedium)
        Slider(
            value = state.darknessLevel.toFloat(),
            onValueChange = { onIntent(SettingsIntent.UpdateLabelPrinterDarkness(it.toInt())) },
            valueRange = 0f..15f,
            steps = 14,
        )

        Text("Print Speed (${state.speedLevel})", style = MaterialTheme.typography.bodyMedium)
        Slider(
            value = state.speedLevel.toFloat(),
            onValueChange = { onIntent(SettingsIntent.UpdateLabelPrinterSpeed(it.toInt())) },
            valueRange = 0f..14f,
            steps = 13,
        )

        Button(
            onClick = { onIntent(SettingsIntent.SaveLabelPrinter) },
            enabled = !state.isSaving,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(if (state.isSaving) "Saving…" else "Save Options")
        }
    }
}

@Composable
private fun LabelPrinterStatusTab(state: SettingsState.LabelPrinterState) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(ZyntaSpacing.md),
        verticalArrangement = Arrangement.spacedBy(ZyntaSpacing.md),
    ) {
        Text("Connection Status", style = MaterialTheme.typography.titleSmall)
        PrinterStatusBadge(isConnected = state.isConnected)
        if (!state.isConnected && state.printerType != LabelPrinterTypeOption.NONE) {
            Text(
                text = "Configure connection settings and save to connect.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
