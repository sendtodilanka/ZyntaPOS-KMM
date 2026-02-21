package com.zyntasolutions.zyntapos.feature.pos

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zyntasolutions.zyntapos.core.utils.CurrencyFormatter
import com.zyntasolutions.zyntapos.designsystem.components.NumericPadMode
import com.zyntasolutions.zyntapos.designsystem.components.ZyntaNumericPad
import com.zyntasolutions.zyntapos.designsystem.tokens.ZyntaSpacing

// ─────────────────────────────────────────────────────────────────────────────
// CashPaymentPanel — Amount Received numpad + real-time change calculation.
// Change label is shown in green (tertiary) when tendered ≥ total,
// and in error red when tendered < total.
// Stateless; caller manages [tenderedCents] via [onTenderedChanged].
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Payment entry panel for **Cash** payments.
 *
 * Displays:
 * 1. A "Amount Due" summary row showing the order total.
 * 2. Quick-amount shortcut buttons (±$50, ±$100, Exact).
 * 3. A [ZyntaNumericPad] in [NumericPadMode.PRICE] for free-form amount entry.
 * 4. A real-time change calculation row displayed in green when
 *    `change ≥ 0`, or red when tendered < total.
 *
 * ### Numeric Input Design
 * [tenderedRaw] is the internal raw integer representing cents (e.g., `10050` = $100.50).
 * The caller manages this via [onTenderedChanged]. This avoids floating-point drift
 * in the numpad display.
 *
 * @param orderTotal      Grand total of the order (in dollars).
 * @param tenderedRaw     Current tendered amount as a raw cent-integer string (e.g., "10050").
 * @param onTenderedChanged Callback with the updated cent-string after each key press.
 * @param modifier        Optional [Modifier].
 * @param formatter       [CurrencyFormatter] for display.
 */
@Composable
fun CashPaymentPanel(
    orderTotal: Double,
    tenderedRaw: String,
    onTenderedChanged: (String) -> Unit,
    modifier: Modifier = Modifier,
    formatter: CurrencyFormatter = CurrencyFormatter(),
) {
    // Convert raw cent-string to display dollars
    val tenderedAmount: Double = (tenderedRaw.toLongOrNull() ?: 0L) / 100.0
    val change: Double = tenderedAmount - orderTotal
    val isChangePositive = change >= 0.0

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(ZyntaSpacing.sm),
    ) {
        // ── Amount Due ────────────────────────────────────────────────────────
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.primaryContainer,
            shape = MaterialTheme.shapes.medium,
            tonalElevation = 0.dp,
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = ZyntaSpacing.md, vertical = ZyntaSpacing.sm),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Amount Due",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
                Text(
                    text = formatter.format(orderTotal),
                    style = MaterialTheme.typography.headlineSmall.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 22.sp,
                    ),
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
        }

        // ── Quick-Amount Shortcuts ────────────────────────────────────────────
        QuickAmountRow(
            orderTotal = orderTotal,
            onAmountSelected = { dollars ->
                onTenderedChanged((dollars * 100).toLong().toString())
            },
        )

        // ── Numpad ────────────────────────────────────────────────────────────
        ZyntaNumericPad(
            displayValue = formatter.format(tenderedAmount),
            onDigit = { digit ->
                val updated = (tenderedRaw + digit).trimStart('0').ifEmpty { "0" }
                onTenderedChanged(updated)
            },
            onDoubleZero = {
                val updated = (tenderedRaw + "00").trimStart('0').ifEmpty { "0" }
                onTenderedChanged(updated)
            },
            onDecimal = { /* cents-integer model — decimal key is a no-op */ },
            onBackspace = {
                onTenderedChanged(tenderedRaw.dropLast(1).ifEmpty { "0" })
            },
            onClear = { onTenderedChanged("0") },
            mode = NumericPadMode.PRICE,
            modifier = Modifier.fillMaxWidth(),
        )

        // ── Change Row ────────────────────────────────────────────────────────
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = if (isChangePositive)
                MaterialTheme.colorScheme.tertiaryContainer
            else
                MaterialTheme.colorScheme.errorContainer,
            shape = MaterialTheme.shapes.medium,
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = ZyntaSpacing.md, vertical = ZyntaSpacing.sm),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = if (isChangePositive) "Change" else "Remaining",
                    style = MaterialTheme.typography.titleSmall,
                    color = if (isChangePositive)
                        MaterialTheme.colorScheme.onTertiaryContainer
                    else
                        MaterialTheme.colorScheme.onErrorContainer,
                )
                Text(
                    text = formatter.format(kotlin.math.abs(change)),
                    style = MaterialTheme.typography.headlineSmall.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 22.sp,
                    ),
                    color = if (isChangePositive)
                        MaterialTheme.colorScheme.onTertiaryContainer
                    else
                        MaterialTheme.colorScheme.onErrorContainer,
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Quick-amount shortcut row
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun QuickAmountRow(
    orderTotal: Double,
    onAmountSelected: (Double) -> Unit,
) {
    // Generate shortcuts: round-up to nearest $50/$100, then Exact
    val rounded50 = (kotlin.math.ceil(orderTotal / 50.0) * 50.0)
    val rounded100 = (kotlin.math.ceil(orderTotal / 100.0) * 100.0)
    val shortcuts = buildList {
        if (rounded50 != orderTotal) add(rounded50)
        if (rounded100 != rounded50 && rounded100 != orderTotal) add(rounded100)
        add(orderTotal) // "Exact" always last
    }.distinct().take(4)

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(ZyntaSpacing.xs),
    ) {
        shortcuts.forEach { amount ->
            OutlinedButton(
                onClick = { onAmountSelected(amount) },
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 40.dp),
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp),
            ) {
                Text(
                    text = if (amount == orderTotal) "Exact" else "$${"%.0f".format(amount)}",
                    style = MaterialTheme.typography.labelSmall,
                )
            }
        }
    }
}
