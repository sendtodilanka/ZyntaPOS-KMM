package com.zyntasolutions.zyntapos.feature.pos

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zyntasolutions.zyntapos.core.i18n.StringResource
import com.zyntasolutions.zyntapos.core.utils.CurrencyFormatter
import com.zyntasolutions.zyntapos.designsystem.components.LocalStrings
import com.zyntasolutions.zyntapos.designsystem.components.NumericPadMode
import com.zyntasolutions.zyntapos.designsystem.components.ZyntaButton
import com.zyntasolutions.zyntapos.designsystem.components.ZyntaButtonSize
import com.zyntasolutions.zyntapos.designsystem.components.ZyntaNumericPad
import com.zyntasolutions.zyntapos.designsystem.tokens.ZyntaSpacing
import com.zyntasolutions.zyntapos.domain.model.PaymentMethod
import com.zyntasolutions.zyntapos.domain.model.PaymentSplit

// ─────────────────────────────────────────────────────────────────────────────
// SplitPaymentPanel — Multiple payment method rows, per-method amount entry,
// remaining amount tracker, validates sum = total before enabling PAY.
// Stateless; split list is managed externally via [splits] / [onSplitsChanged].
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Payment entry panel for **Split** payments.
 *
 * Allows the cashier to allocate portions of the order total across multiple
 * payment methods. Shows:
 *
 * 1. An "Amount Due" header.
 * 2. A list of [PaymentSplitRow] entries, each showing a method selector
 *    and amount entry field.
 * 3. An "Add Payment Method" button to append new rows.
 * 4. A "Remaining" balance tracker (highlighted in error-red when > 0).
 * 5. A "PAY" button enabled **only** when the sum of all split amounts
 *    equals [orderTotal] (within ±0.01 floating-point tolerance).
 *
 * ### Validation Rule
 * `splits.sumOf { it.amount } == orderTotal` ± 0.01 tolerance.
 *
 * @param orderTotal      Grand total to be paid.
 * @param splits          Current list of [PaymentSplit] legs. May be empty.
 * @param onSplitsChanged Callback with the updated split list after any mutation.
 * @param onPayClicked    Invoked when PAY is clicked (sum = total validated).
 * @param modifier        Optional [Modifier].
 * @param formatter       [CurrencyFormatter] for amounts.
 */
@Composable
fun SplitPaymentPanel(
    orderTotal: Double,
    splits: List<PaymentSplit>,
    onSplitsChanged: (List<PaymentSplit>) -> Unit,
    onPayClicked: () -> Unit,
    modifier: Modifier = Modifier,
    formatter: CurrencyFormatter = CurrencyFormatter(),
) {
    val s = LocalStrings.current
    val totalPaid = splits.sumOf { it.amount }
    val remaining = orderTotal - totalPaid
    val isBalanced = kotlin.math.abs(remaining) < 0.01

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(ZyntaSpacing.sm),
    ) {
        // ── Amount Due header ─────────────────────────────────────────────────
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.primaryContainer,
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
                    s[StringResource.POS_AMOUNT_DUE],
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
                Text(
                    formatter.format(orderTotal),
                    style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold, fontSize = 22.sp),
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
        }

        // ── Split rows ────────────────────────────────────────────────────────
        splits.forEachIndexed { index, split ->
            PaymentSplitRow(
                split = split,
                onAmountChanged = { newAmount ->
                    val updated = splits.toMutableList()
                    updated[index] = split.copy(amount = newAmount)
                    onSplitsChanged(updated)
                },
                onMethodChanged = { newMethod ->
                    val updated = splits.toMutableList()
                    // Validate: no duplicate methods unless the list has exactly 2 entries
                    if (splits.none { it.method == newMethod } || splits[index].method == newMethod) {
                        updated[index] = split.copy(method = newMethod)
                        onSplitsChanged(updated)
                    }
                },
                onRemove = if (splits.size > 1) {
                    {
                        val updated = splits.toMutableList()
                        updated.removeAt(index)
                        onSplitsChanged(updated)
                    }
                } else null,
                formatter = formatter,
            )
        }

        // ── Add Payment Method button ─────────────────────────────────────────
        val usedMethods = splits.map { it.method }.toSet()
        val availableToAdd = PaymentMethod.entries
            .filter { it != PaymentMethod.SPLIT && it !in usedMethods }
        if (availableToAdd.isNotEmpty()) {
            OutlinedButton(
                onClick = {
                    val newMethod = availableToAdd.first()
                    // Auto-fill remaining amount on the new row
                    val autoFill = if (remaining > 0.0) remaining else 0.0
                    onSplitsChanged(splits + PaymentSplit(method = newMethod, amount = autoFill.coerceAtLeast(0.01)))
                },
                modifier = Modifier.fillMaxWidth().heightIn(min = 44.dp),
            ) {
                Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(s[StringResource.POS_ADD_PAYMENT_METHOD])
            }
        }

        // ── Remaining tracker ─────────────────────────────────────────────────
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = if (isBalanced)
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
                    text = if (isBalanced) s[StringResource.POS_BALANCED] else s[StringResource.POS_REMAINING],
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                    color = if (isBalanced)
                        MaterialTheme.colorScheme.onTertiaryContainer
                    else
                        MaterialTheme.colorScheme.onErrorContainer,
                )
                Text(
                    text = formatter.format(kotlin.math.abs(remaining)),
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = if (isBalanced)
                        MaterialTheme.colorScheme.onTertiaryContainer
                    else
                        MaterialTheme.colorScheme.onErrorContainer,
                )
            }
        }

        // ── PAY button ────────────────────────────────────────────────────────
        ZyntaButton(
            text = s[StringResource.POS_PAY_AMOUNT_FORMAT, formatter.format(orderTotal)],
            onClick = onPayClicked,
            size = ZyntaButtonSize.Large,
            enabled = isBalanced && splits.isNotEmpty(),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Internal — PaymentSplitRow
// ─────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PaymentSplitRow(
    split: PaymentSplit,
    onAmountChanged: (Double) -> Unit,
    onMethodChanged: (PaymentMethod) -> Unit,
    onRemove: (() -> Unit)?,
    formatter: CurrencyFormatter,
) {
    val s = LocalStrings.current
    var showMethodMenu by remember { mutableStateOf(false) }
    var _amountInput by remember(split) {
        mutableStateOf("%.2f".format(split.amount))
    }
    val showNumpad = remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(modifier = Modifier.padding(ZyntaSpacing.sm)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(ZyntaSpacing.sm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Method selector chip
                ExposedDropdownMenuBox(
                    expanded = showMethodMenu,
                    onExpandedChange = { showMethodMenu = it },
                    modifier = Modifier.weight(1f),
                ) {
                    OutlinedButton(
                        onClick = { showMethodMenu = true },
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                            .heightIn(min = 44.dp),
                    ) {
                        Icon(split.method.icon, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(split.method.label, style = MaterialTheme.typography.labelMedium)
                    }
                    ExposedDropdownMenu(
                        expanded = showMethodMenu,
                        onDismissRequest = { showMethodMenu = false },
                    ) {
                        PaymentMethod.entries
                            .filter { it != PaymentMethod.SPLIT }
                            .forEach { method ->
                                DropdownMenuItem(
                                    text = { Text(method.label) },
                                    leadingIcon = { Icon(method.icon, contentDescription = null) },
                                    onClick = {
                                        onMethodChanged(method)
                                        showMethodMenu = false
                                    },
                                )
                            }
                    }
                }

                // Amount display → taps to show inline numpad
                Surface(
                    onClick = { showNumpad.value = !showNumpad.value },
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 44.dp),
                    shape = MaterialTheme.shapes.small,
                    color = MaterialTheme.colorScheme.surface,
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp,
                        MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                    ),
                ) {
                    Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                        Text(
                            text = formatter.format(split.amount),
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        )
                    }
                }

                // Remove row button
                if (onRemove != null) {
                    IconButton(onClick = onRemove, modifier = Modifier.size(36.dp)) {
                        Icon(
                            Icons.Filled.Delete,
                            contentDescription = s[StringResource.POS_REMOVE_PAYMENT_CD],
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
            }

            // Inline numpad for this row
            if (showNumpad.value) {
                Spacer(Modifier.height(ZyntaSpacing.xs))
                var rawCents by remember(split.amount) {
                    mutableStateOf((split.amount * 100).toLong().toString())
                }
                ZyntaNumericPad(
                    displayValue = formatter.format((rawCents.toLongOrNull() ?: 0L) / 100.0),
                    onDigit = { digit ->
                        rawCents = (rawCents + digit).trimStart('0').ifEmpty { "0" }
                        onAmountChanged((rawCents.toLongOrNull() ?: 0L) / 100.0)
                    },
                    onDoubleZero = {
                        rawCents = (rawCents + "00").trimStart('0').ifEmpty { "0" }
                        onAmountChanged((rawCents.toLongOrNull() ?: 0L) / 100.0)
                    },
                    onDecimal = { /* cents model — no-op */ },
                    onBackspace = {
                        rawCents = rawCents.dropLast(1).ifEmpty { "0" }
                        onAmountChanged((rawCents.toLongOrNull() ?: 0L) / 100.0)
                    },
                    onClear = {
                        rawCents = "0"
                        onAmountChanged(0.0)
                    },
                    mode = NumericPadMode.PRICE,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}
