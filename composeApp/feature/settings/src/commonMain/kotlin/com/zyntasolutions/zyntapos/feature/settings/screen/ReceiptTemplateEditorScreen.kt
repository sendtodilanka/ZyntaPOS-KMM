package com.zyntasolutions.zyntapos.feature.settings.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Preview
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zyntasolutions.zyntapos.designsystem.tokens.ZyntaSpacing
import com.zyntasolutions.zyntapos.designsystem.util.WindowSize
import com.zyntasolutions.zyntapos.designsystem.util.currentWindowSize
import com.zyntasolutions.zyntapos.domain.model.PaperWidth
import com.zyntasolutions.zyntapos.domain.model.ReceiptSection
import com.zyntasolutions.zyntapos.domain.model.ReceiptTemplateConfig

/**
 * Visual receipt template editor (Phase 3 nice-to-have).
 *
 * Provides a side-by-side layout on tablets/desktop:
 * - Left: toggle switches for each receipt section
 * - Right: live monospace preview of the receipt
 *
 * On compact screens, shows a tabbed layout with editor and preview tabs.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReceiptTemplateEditorScreen(
    onNavigateUp: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var config by remember { mutableStateOf(ReceiptTemplateConfig()) }
    var showPreview by remember { mutableStateOf(false) }
    val windowSize = currentWindowSize()

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text("Receipt Template Editor") },
                navigationIcon = {
                    IconButton(onClick = onNavigateUp) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (windowSize == WindowSize.COMPACT) {
                        IconButton(onClick = { showPreview = !showPreview }) {
                            Icon(Icons.Default.Preview, contentDescription = "Toggle Preview")
                        }
                    }
                    IconButton(onClick = { /* Save config via SettingsIntent */ }) {
                        Icon(Icons.Default.Save, contentDescription = "Save")
                    }
                },
            )
        },
    ) { padding ->
        when {
            windowSize == WindowSize.COMPACT && showPreview -> {
                ReceiptPreviewPane(
                    config = config,
                    modifier = Modifier.fillMaxSize().padding(padding),
                )
            }
            windowSize == WindowSize.COMPACT -> {
                ReceiptEditorPane(
                    config = config,
                    onConfigChange = { config = it },
                    modifier = Modifier.fillMaxSize().padding(padding),
                )
            }
            else -> {
                // Side-by-side: editor (55%) + preview (45%)
                Row(
                    modifier = Modifier.fillMaxSize().padding(padding),
                ) {
                    ReceiptEditorPane(
                        config = config,
                        onConfigChange = { config = it },
                        modifier = Modifier.weight(0.55f).fillMaxHeight(),
                    )
                    VerticalDivider()
                    ReceiptPreviewPane(
                        config = config,
                        modifier = Modifier.weight(0.45f).fillMaxHeight(),
                    )
                }
            }
        }
    }
}

@Composable
private fun ReceiptEditorPane(
    config: ReceiptTemplateConfig,
    onConfigChange: (ReceiptTemplateConfig) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(ZyntaSpacing.md),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        // Store Info section
        item { SectionHeader("Store Information") }
        item { ToggleRow("Store Name", config.showStoreName) { onConfigChange(config.copy(showStoreName = it)) } }
        item { ToggleRow("Store Address", config.showStoreAddress) { onConfigChange(config.copy(showStoreAddress = it)) } }
        item { ToggleRow("Store Phone", config.showStorePhone) { onConfigChange(config.copy(showStorePhone = it)) } }

        // Order Details
        item { SectionHeader("Order Details") }
        item { ToggleRow("Order Number", config.showOrderNumber) { onConfigChange(config.copy(showOrderNumber = it)) } }
        item { ToggleRow("Date & Time", config.showDateTime) { onConfigChange(config.copy(showDateTime = it)) } }
        item { ToggleRow("Cashier Name", config.showCashierName) { onConfigChange(config.copy(showCashierName = it)) } }

        // Items
        item { SectionHeader("Item List") }
        item { ToggleRow("Itemized List", config.showItemizedList) { onConfigChange(config.copy(showItemizedList = it)) } }
        item { ToggleRow("Show SKU", config.showItemSku) { onConfigChange(config.copy(showItemSku = it)) } }

        // Totals
        item { SectionHeader("Totals & Tax") }
        item { ToggleRow("Subtotal", config.showSubtotal) { onConfigChange(config.copy(showSubtotal = it)) } }
        item { ToggleRow("Tax Breakdown", config.showTaxBreakdown) { onConfigChange(config.copy(showTaxBreakdown = it)) } }
        item { ToggleRow("Discounts", config.showDiscounts) { onConfigChange(config.copy(showDiscounts = it)) } }

        // Payment
        item { SectionHeader("Payment") }
        item { ToggleRow("Payment Method", config.showPaymentMethod) { onConfigChange(config.copy(showPaymentMethod = it)) } }
        item { ToggleRow("Change Given", config.showChangeGiven) { onConfigChange(config.copy(showChangeGiven = it)) } }

        // Extras
        item { SectionHeader("Extras") }
        item { ToggleRow("QR Code", config.showQrCode) { onConfigChange(config.copy(showQrCode = it)) } }
        item { ToggleRow("Barcode", config.showBarcode) { onConfigChange(config.copy(showBarcode = it)) } }
        item { ToggleRow("Loyalty Points", config.showLoyaltyPoints) { onConfigChange(config.copy(showLoyaltyPoints = it)) } }
        item { ToggleRow("Thank You Message", config.showThankYouMessage) { onConfigChange(config.copy(showThankYouMessage = it)) } }

        // Custom text
        item { SectionHeader("Custom Footer") }
        item {
            OutlinedTextField(
                value = config.footerLines.joinToString("\n"),
                onValueChange = { text ->
                    onConfigChange(config.copy(footerLines = text.lines().filter { it.isNotBlank() }))
                },
                label = { Text("Footer Lines") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 2,
                maxLines = 4,
            )
        }

        // Paper width
        item { SectionHeader("Paper Size") }
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                PaperWidth.entries.forEach { width ->
                    Card(
                        onClick = { onConfigChange(config.copy(paperWidth = width)) },
                        colors = CardDefaults.cardColors(
                            containerColor = if (config.paperWidth == width)
                                MaterialTheme.colorScheme.primaryContainer
                            else MaterialTheme.colorScheme.surfaceVariant,
                        ),
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(
                            text = "${width.mm}mm (${width.charsPerLine} chars)",
                            modifier = Modifier.padding(ZyntaSpacing.md),
                            textAlign = TextAlign.Center,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            }
        }

        item { Spacer(Modifier.height(80.dp)) }
    }
}

@Composable
private fun ReceiptPreviewPane(
    config: ReceiptTemplateConfig,
    modifier: Modifier = Modifier,
) {
    val previewText = remember(config) { generatePreviewText(config) }

    Column(
        modifier = modifier.padding(ZyntaSpacing.md),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            "Live Preview",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.height(8.dp))

        // Simulated thermal receipt paper
        Box(
            modifier = Modifier
                .width(if (config.paperWidth == PaperWidth.MM_80) 320.dp else 240.dp)
                .background(
                    color = MaterialTheme.colorScheme.surfaceContainerHighest,
                    shape = RoundedCornerShape(4.dp),
                )
                .border(
                    width = 1.dp,
                    color = MaterialTheme.colorScheme.outlineVariant,
                    shape = RoundedCornerShape(4.dp),
                )
                .padding(12.dp),
        ) {
            Text(
                text = previewText,
                fontFamily = FontFamily.Monospace,
                fontSize = when (config.fontSize) {
                    com.zyntasolutions.zyntapos.domain.model.ReceiptFontSize.SMALL -> 10.sp
                    com.zyntasolutions.zyntapos.domain.model.ReceiptFontSize.NORMAL -> 12.sp
                    com.zyntasolutions.zyntapos.domain.model.ReceiptFontSize.LARGE -> 14.sp
                },
                lineHeight = 16.sp,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

private fun generatePreviewText(config: ReceiptTemplateConfig): String {
    val w = config.paperWidth.charsPerLine
    val sep = "-".repeat(w)

    return buildString {
        if (config.showStoreName) {
            appendLine(center("ZYNTA POS STORE", w))
        }
        if (config.showStoreAddress) {
            appendLine(center("123 Main Street, Colombo", w))
        }
        if (config.showStorePhone) {
            appendLine(center("Tel: +94 11 234 5678", w))
        }
        if (config.showStoreName || config.showStoreAddress || config.showStorePhone) {
            appendLine(sep)
        }
        config.headerLines.forEach { appendLine(center(it, w)) }
        if (config.headerLines.isNotEmpty()) appendLine(sep)

        if (config.showOrderNumber) appendLine("Order: #ORD-2026-0042")
        if (config.showDateTime) appendLine("Date:  2026-03-26 14:30")
        if (config.showCashierName) appendLine("Staff: Jane Doe")
        if (config.showOrderNumber || config.showDateTime || config.showCashierName) {
            appendLine(sep)
        }

        if (config.showItemizedList) {
            appendLine(leftRight("Rice & Curry x2", "1,200.00", w))
            if (config.showItemSku) appendLine("  SKU: RC-001")
            appendLine(leftRight("Fresh Juice x1", "350.00", w))
            if (config.showItemSku) appendLine("  SKU: FJ-015")
            appendLine(leftRight("Dessert x1", "450.00", w))
            if (config.showItemSku) appendLine("  SKU: DS-008")
            appendLine(sep)
        }

        if (config.showSubtotal) appendLine(leftRight("Subtotal:", "2,000.00", w))
        if (config.showTaxBreakdown) appendLine(leftRight("VAT (15%):", "300.00", w))
        if (config.showDiscounts) appendLine(leftRight("Discount:", "-100.00", w))
        if (config.showSubtotal || config.showTaxBreakdown || config.showDiscounts) {
            appendLine(sep)
            appendLine(leftRight("TOTAL:", "LKR 2,200.00", w))
            appendLine(sep)
        }

        if (config.showPaymentMethod) appendLine(leftRight("Payment:", "Cash", w))
        if (config.showChangeGiven) appendLine(leftRight("Change:", "300.00", w))

        if (config.showLoyaltyPoints) {
            appendLine(sep)
            appendLine(center("Points Earned: +22", w))
            appendLine(center("Balance: 156 pts", w))
        }

        if (config.showQrCode) {
            appendLine(sep)
            appendLine(center("[QR CODE]", w))
        }
        if (config.showBarcode) {
            appendLine(center("||||| ORD-2026-0042 |||||", w))
        }

        if (config.showThankYouMessage || config.footerLines.isNotEmpty()) {
            appendLine(sep)
            config.footerLines.forEach { appendLine(center(it, w)) }
        }
    }.trimEnd()
}

private fun center(text: String, width: Int): String {
    if (text.length >= width) return text.take(width)
    val pad = (width - text.length) / 2
    return " ".repeat(pad) + text
}

private fun leftRight(left: String, right: String, width: Int): String {
    val gap = width - left.length - right.length
    return if (gap > 0) left + " ".repeat(gap) + right
    else "$left $right"
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 12.dp, bottom = 4.dp),
    )
}

@Composable
private fun ToggleRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
