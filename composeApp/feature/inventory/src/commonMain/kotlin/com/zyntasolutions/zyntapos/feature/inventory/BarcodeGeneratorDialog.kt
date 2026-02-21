package com.zyntasolutions.zyntapos.feature.inventory

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zyntasolutions.zyntapos.designsystem.tokens.ZyntaSpacing
import com.zyntasolutions.zyntapos.domain.model.Product
import kotlin.random.Random
// ─────────────────────────────────────────────────────────────────────────────
// Barcode Type Enum
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Supported barcode symbologies for product label generation.
 *
 * @property displayName User-facing label shown in the type selector.
 */
enum class BarcodeType(val displayName: String) {
    /** EAN-13: 13-digit numeric barcode (GS1 standard, retail). */
    EAN_13("EAN-13"),

    /** Code 128: Variable-length alphanumeric barcode (logistics, internal use). */
    CODE_128("Code 128"),
}

// ─────────────────────────────────────────────────────────────────────────────
// Dialog Composable
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Dialog for generating EAN-13 or Code128 barcodes for products (Sprint 18, task 10.1.4).
 *
 * ### Features
 * - Barcode type selector: EAN-13 (13-digit numeric) or Code128 (alphanumeric).
 * - Auto-generate button that produces a valid barcode with check digit.
 * - Manual entry field for user-supplied barcode values.
 * - Canvas-drawn barcode preview using simplified rendering.
 * - "Apply to Product" assigns the barcode to the product form field.
 * - "Print Label" triggers barcode label printing via [InventoryEffect.PrintBarcode].
 *
 * ### EAN-13 Generation
 * Uses a "200" prefix (in-store use range) followed by 9 random digits + 1 check digit.
 * The check digit is calculated per the GS1 standard (alternating ×1/×3 weighted sum mod 10).
 *
 * @param product        Target product (null = new product with no ID yet).
 * @param onApply        Callback with the generated barcode string to assign to the product.
 * @param onPrint        Callback to trigger barcode label printing.
 * @param onDismiss      Dismissal callback.
 */@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BarcodeGeneratorDialog(
    product: Product?,
    onApply: (String) -> Unit,
    onPrint: (barcode: String, productName: String) -> Unit,
    onDismiss: () -> Unit,
) {
    var barcodeType by remember { mutableStateOf(BarcodeType.EAN_13) }
    var barcodeValue by remember { mutableStateOf(product?.barcode ?: "") }
    var validationError by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = if (product != null) "Generate Barcode — ${product.name}" else "Generate Barcode",
                style = MaterialTheme.typography.titleMedium,
            )
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(ZyntaSpacing.md),
                modifier = Modifier.widthIn(min = 320.dp),
            ) {
                // ── Barcode type selector ────────────────────────────────
                Text("Barcode Type", style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(ZyntaSpacing.sm)) {
                    BarcodeType.entries.forEach { type ->
                        FilterChip(
                            selected = barcodeType == type,
                            onClick = {
                                barcodeType = type
                                barcodeValue = ""
                                validationError = null
                            },
                            label = { Text(type.displayName) },
                        )
                    }
                }
                // ── Manual entry + auto-generate ─────────────────────────
                OutlinedTextField(
                    value = barcodeValue,
                    onValueChange = {
                        barcodeValue = it
                        validationError = validateBarcodeInput(it, barcodeType)
                    },
                    label = { Text("Barcode Value") },
                    isError = validationError != null,
                    supportingText = validationError?.let { { Text(it) } },
                    trailingIcon = {
                        IconButton(onClick = {
                            barcodeValue = generateBarcode(barcodeType)
                            validationError = null
                        }) {
                            Icon(Icons.Default.Autorenew, contentDescription = "Auto-generate")
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )

                // ── Barcode preview (Canvas-drawn) ───────────────────────
                if (barcodeValue.isNotBlank() && validationError == null) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
                        ),
                    ) {
                        Column(
                            modifier = Modifier.padding(ZyntaSpacing.md),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Text(
                                "Preview",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Spacer(Modifier.height(ZyntaSpacing.sm))
                            BarcodeCanvas(
                                value = barcodeValue,
                                type = barcodeType,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(100.dp),
                            )
                        }
                    }
                }

                // ── Print button ─────────────────────────────────────────
                if (barcodeValue.isNotBlank() && validationError == null) {
                    OutlinedButton(
                        onClick = { onPrint(barcodeValue, product?.name ?: "New Product") },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(Icons.Default.Print, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(ZyntaSpacing.xs))
                        Text("Print Label")
                    }
                }
            }
        },
        confirmButton = {
            FilledTonalButton(
                onClick = { onApply(barcodeValue) },
                enabled = barcodeValue.isNotBlank() && validationError == null,
            ) {
                Text("Apply to Product")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}
// ─────────────────────────────────────────────────────────────────────────────
// Canvas-Drawn Barcode Preview
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Draws a barcode preview on a Compose [Canvas].
 *
 * For EAN-13: renders standard UPC-style vertical bars with digit text below.
 * For Code128: renders a simplified vertical bar representation.
 *
 * This is a **visual representation only** — the actual barcode encoding for
 * printing is handled by the ESC/POS printer driver in the HAL layer.
 */
@Composable
private fun BarcodeCanvas(
    value: String,
    type: BarcodeType,
    modifier: Modifier = Modifier,
) {
    val barColor = MaterialTheme.colorScheme.onSurface
    val textMeasurer = rememberTextMeasurer()
    val textStyle = TextStyle(fontSize = 10.sp, textAlign = TextAlign.Center)

    Canvas(modifier = modifier) {
        val barAreaHeight = size.height * 0.75f
        val textY = barAreaHeight + 4.dp.toPx()

        when (type) {
            BarcodeType.EAN_13 -> drawEan13Bars(value, barColor, barAreaHeight)
            BarcodeType.CODE_128 -> drawCode128Bars(value, barColor, barAreaHeight)
        }
        // Draw the barcode digits below the bars
        val measuredText = textMeasurer.measure(
            text = value,
            style = textStyle.copy(fontSize = 12.sp),
        )
        drawText(
            textLayoutResult = measuredText,
            topLeft = Offset(
                x = (size.width - measuredText.size.width) / 2f,
                y = textY,
            ),
        )
    }
}

/** Draws EAN-13 style vertical bars on the Canvas (simplified visual preview). */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawEan13Bars(
    value: String,
    barColor: Color,
    barHeight: Float,
) {
    val totalBars = value.length * 7 + 11
    val barWidth = size.width / totalBars.toFloat()
    var x = 0f

    // Start guard: 101
    drawBar(x, barWidth, barHeight, barColor); x += barWidth
    x += barWidth
    drawBar(x, barWidth, barHeight, barColor); x += barWidth
    // Data bars — each digit rendered as 7 modules with pattern
    value.forEachIndexed { idx, ch ->
        val digit = ch.digitToIntOrNull() ?: 0
        for (module in 0 until 7) {
            val isBar = ean13ModulePattern(digit, module, idx < 7)
            if (isBar) drawBar(x, barWidth, barHeight, barColor)
            x += barWidth
        }
        // Center guard after 6th digit
        if (idx == 5) {
            x += barWidth
            drawBar(x, barWidth, barHeight, barColor); x += barWidth
            x += barWidth
            drawBar(x, barWidth, barHeight, barColor); x += barWidth
            x += barWidth
        }
    }

    // End guard: 101
    drawBar(x, barWidth, barHeight, barColor); x += barWidth
    x += barWidth
    drawBar(x, barWidth, barHeight, barColor)
}

/** Draws Code128 style bars using a hash-based pattern for visual preview. */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawCode128Bars(
    value: String,
    barColor: Color,
    barHeight: Float,
) {    val totalModules = (value.length + 4) * 11
    val barWidth = size.width / totalModules.toFloat()
    var x = 0f

    // Start pattern
    repeat(2) { drawBar(x, barWidth * 2, barHeight, barColor); x += barWidth * 3 }

    // Data modules — character code → pseudo-random bar pattern
    value.forEach { ch ->
        val code = ch.code % 128
        for (bit in 0 until 11) {
            val isBar = (code shr (10 - bit)) and 1 == 1
            if (isBar) drawBar(x, barWidth, barHeight, barColor)
            x += barWidth
        }
    }

    // Stop pattern
    repeat(2) { drawBar(x, barWidth * 2, barHeight, barColor); x += barWidth * 3 }
}

/** Draws a single vertical bar on the Canvas. */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawBar(
    x: Float,
    width: Float,
    height: Float,
    color: Color,
) {
    drawRect(color = color, topLeft = Offset(x, 0f), size = Size(width, height))
}
// ─────────────────────────────────────────────────────────────────────────────
// Barcode Generation & Validation Helpers
// ─────────────────────────────────────────────────────────────────────────────

/**
 * EAN-13 module pattern lookup for visual rendering.
 *
 * Returns true if the given module position for a digit should be a black bar.
 * Uses the standard L-code encoding table for the left half and R-code for the right.
 *
 * @param digit  The digit value (0–9).
 * @param module The module position within the 7-module digit (0–6).
 * @param isLeft Whether this digit is in the left half of the barcode.
 * @return True if the module is a black bar, false for a white space.
 */
internal fun ean13ModulePattern(digit: Int, module: Int, isLeft: Boolean): Boolean {
    // Standard EAN-13 L-code encoding patterns (7 modules per digit)
    // 1 = black bar, 0 = white space
    val lCodes = intArrayOf(
        0b0001101, // 0
        0b0011001, // 1
        0b0010011, // 2
        0b0111101, // 3
        0b0100011, // 4
        0b0110001, // 5
        0b0101111, // 6
        0b0111011, // 7
        0b0110111, // 8
        0b0001011, // 9
    )    // R-code patterns (complement of L-codes)
    val rCodes = intArrayOf(
        0b1110010, // 0
        0b1100110, // 1
        0b1101100, // 2
        0b1000010, // 3
        0b1011100, // 4
        0b1001110, // 5
        0b1010000, // 6
        0b1000100, // 7
        0b1001000, // 8
        0b1110100, // 9
    )

    val pattern = if (isLeft) lCodes[digit.coerceIn(0, 9)] else rCodes[digit.coerceIn(0, 9)]
    val bitPosition = 6 - module // MSB first
    return (pattern shr bitPosition) and 1 == 1
}

/**
 * Generates a valid barcode string for the given [type].
 *
 * - **EAN-13:** "200" prefix (in-store use) + 9 random digits + 1 GS1 check digit.
 * - **Code128:** "ZP-" prefix + 8 random alphanumeric characters.
 *
 * @param type The barcode symbology.
 * @return A valid barcode string.
 */
internal fun generateBarcode(type: BarcodeType): String = when (type) {
    BarcodeType.EAN_13 -> generateEan13()
    BarcodeType.CODE_128 -> generateCode128()
}
/**
 * Generates a valid EAN-13 barcode with "200" prefix (in-store use range).
 *
 * The check digit is calculated per the GS1 standard:
 * sum of (digit × weight) where weight alternates 1, 3, 1, 3, …
 * Check digit = (10 - (sum mod 10)) mod 10
 */
private fun generateEan13(): String {
    val prefix = "200"
    val randomDigits = buildString {
        repeat(9) { append(Random.nextInt(0, 10)) }
    }
    val partial = prefix + randomDigits // 12 digits
    val checkDigit = calculateEan13CheckDigit(partial)
    return partial + checkDigit
}

/**
 * Calculates the EAN-13 check digit (position 13) per the GS1 standard.
 *
 * @param first12 The first 12 digits of the barcode.
 * @return The check digit character ('0'–'9').
 */
internal fun calculateEan13CheckDigit(first12: String): Char {
    require(first12.length == 12) { "EAN-13 requires exactly 12 digits before check digit" }
    var sum = 0
    first12.forEachIndexed { idx, ch ->
        val digit = ch.digitToInt()
        sum += if (idx % 2 == 0) digit else digit * 3
    }
    val check = (10 - (sum % 10)) % 10
    return check.digitToChar()
}
/** Generates a Code128 barcode with "ZP-" prefix and 8 random alphanumeric chars. */
private fun generateCode128(): String {
    val chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789"
    val random = buildString {
        repeat(8) { append(chars[Random.nextInt(chars.length)]) }
    }
    return "ZP-$random"
}

/**
 * Validates a manually entered barcode value against the expected format.
 *
 * @param value        The user-entered barcode string.
 * @param barcodeType  The selected barcode symbology.
 * @return An error message if validation fails, or null if valid.
 */
internal fun validateBarcodeInput(value: String, barcodeType: BarcodeType): String? {
    if (value.isBlank()) return null // Empty is allowed — means "no barcode yet"

    return when (barcodeType) {
        BarcodeType.EAN_13 -> {
            if (!value.all { it.isDigit() }) {
                "EAN-13 must contain only digits."
            } else if (value.length != 13) {
                "EAN-13 must be exactly 13 digits (currently ${value.length})."
            } else {
                // Verify check digit
                val expectedCheck = calculateEan13CheckDigit(value.substring(0, 12))
                if (value.last() != expectedCheck) {
                    "Invalid check digit. Expected '$expectedCheck', got '${value.last()}'."
                } else null
            }
        }
        BarcodeType.CODE_128 -> {
            if (value.length < 4) {
                "Code 128 barcode must be at least 4 characters."
            } else if (value.length > 48) {
                "Code 128 barcode should not exceed 48 characters."
            } else if (!value.all { it.code in 32..126 }) {
                "Code 128 supports only printable ASCII characters."
            } else null
        }
    }
}