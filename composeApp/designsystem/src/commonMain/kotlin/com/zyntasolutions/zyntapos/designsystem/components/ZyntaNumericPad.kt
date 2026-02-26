package com.zyntasolutions.zyntapos.designsystem.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zyntasolutions.zyntapos.designsystem.tokens.ZyntaSpacing

// ─────────────────────────────────────────────────────────────────────────────
// ZyntaNumericPad — Stateless; modes: PRICE, QUANTITY, PIN (masked, max 6 digits)
// ─────────────────────────────────────────────────────────────────────────────

/** Operating mode that controls input formatting and display. */
enum class NumericPadMode {
    /** Decimal value with 2 decimal places. Input appends digits right-to-left. */
    PRICE,
    /** Integer or decimal quantity. Decimal key enabled. */
    QUANTITY,
    /** Masked PIN entry. Decimal and 00 keys are hidden. Maximum 6 digits. */
    PIN,
}

/**
 * Full numeric keypad for price entry, quantity input, or secure PIN input.
 *
 * @param displayValue Current formatted display string managed by the caller.
 * @param onDigit Invoked with the digit character ("0"–"9").
 * @param onDoubleZero Invoked when "00" is tapped (disabled in PIN mode).
 * @param onDecimal Invoked when "." is tapped (disabled in PIN mode).
 * @param onBackspace Invoked when the backspace key is tapped.
 * @param onClear Invoked when the clear (C) key is tapped.
 * @param modifier Optional [Modifier].
 * @param mode Controls which keys are visible and masked display.
 * @param buttonSize Height of each numpad button.
 */
@Composable
fun ZyntaNumericPad(
    displayValue: String,
    onDigit: (String) -> Unit,
    onDoubleZero: () -> Unit,
    onDecimal: () -> Unit,
    onBackspace: () -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier,
    mode: NumericPadMode = NumericPadMode.PRICE,
    buttonSize: Dp = 64.dp,
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(ZyntaSpacing.xs),
    ) {
        // Display area
        DisplayArea(displayValue = displayValue, mode = mode)

        Spacer(Modifier.height(ZyntaSpacing.xs))

        // Grid rows: 1 2 3 / 4 5 6 / 7 8 9 / bottom row
        val rows = listOf(
            listOf("1", "2", "3"),
            listOf("4", "5", "6"),
            listOf("7", "8", "9"),
        )
        rows.forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(ZyntaSpacing.xs)) {
                row.forEach { digit ->
                    NumPadButton(
                        label = digit,
                        onClick = { onDigit(digit) },
                        size = buttonSize,
                    )
                }
            }
        }

        // Bottom row — 00 / 0 / . or just 0 in PIN mode
        Row(horizontalArrangement = Arrangement.spacedBy(ZyntaSpacing.xs)) {
            if (mode != NumericPadMode.PIN) {
                NumPadButton("00", onClick = onDoubleZero, size = buttonSize)
            } else {
                Spacer(Modifier.size(buttonSize))
            }
            NumPadButton("0", onClick = { onDigit("0") }, size = buttonSize)
            if (mode != NumericPadMode.PIN) {
                NumPadButton(".", onClick = onDecimal, size = buttonSize)
            } else {
                Spacer(Modifier.size(buttonSize))
            }
        }

        // Backspace + Clear row
        Row(horizontalArrangement = Arrangement.spacedBy(ZyntaSpacing.xs)) {
            FilledTonalIconButton(
                onClick = onBackspace,
                modifier = Modifier.size(buttonSize),
                shape = RoundedCornerShape(12.dp),
            ) {
                Icon(Icons.AutoMirrored.Filled.Backspace, contentDescription = "Backspace")
            }
            Spacer(Modifier.size(buttonSize))
            FilledTonalButton(
                onClick = onClear,
                modifier = Modifier.size(buttonSize),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.filledTonalButtonColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.onErrorContainer,
                ),
                contentPadding = PaddingValues(0.dp),
            ) {
                Text("C", fontWeight = FontWeight.Bold)
            }
        }
    }
}

// ── Preview ───────────────────────────────────────────────────────────────────

@androidx.compose.ui.tooling.preview.Preview
@androidx.compose.runtime.Composable
private fun ZyntaNumericPadPreview() {
    com.zyntasolutions.zyntapos.designsystem.theme.ZyntaTheme {
        ZyntaNumericPad(
            displayValue = "0.00",
            onDigit = {},
            onDoubleZero = {},
            onDecimal = {},
            onBackspace = {},
            onClear = {},
        )
    }
}

@Composable
private fun DisplayArea(displayValue: String, mode: NumericPadMode) {
    val shown = if (mode == NumericPadMode.PIN) "●".repeat(displayValue.length) else displayValue
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = ZyntaSpacing.sm),
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = MaterialTheme.shapes.medium,
    ) {
        Text(
            text = shown.ifEmpty { "0" },
            style = MaterialTheme.typography.headlineLarge.copy(
                fontWeight = FontWeight.Bold,
                fontSize = 32.sp,
            ),
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = ZyntaSpacing.md, vertical = ZyntaSpacing.sm),
        )
    }
}

@Composable
private fun NumPadButton(label: String, onClick: () -> Unit, size: Dp) {
    FilledTonalButton(
        onClick = onClick,
        modifier = Modifier.size(size),
        shape = RoundedCornerShape(12.dp),
        contentPadding = PaddingValues(0.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold),
        )
    }
}
