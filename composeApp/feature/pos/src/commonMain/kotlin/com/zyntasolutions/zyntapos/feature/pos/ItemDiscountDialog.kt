package com.zyntasolutions.zyntapos.feature.pos

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.zyntasolutions.zyntapos.core.utils.CurrencyFormatter
import com.zyntasolutions.zyntapos.designsystem.components.NumericPadMode
import com.zyntasolutions.zyntapos.designsystem.components.ZyntaNumericPad
import com.zyntasolutions.zyntapos.designsystem.tokens.ZyntaSpacing
import com.zyntasolutions.zyntapos.domain.model.DiscountType
import com.zyntasolutions.zyntapos.domain.model.Permission
import com.zyntasolutions.zyntapos.feature.auth.guard.RoleGuard
import com.zyntasolutions.zyntapos.domain.usecase.auth.CheckPermissionUseCase

// ─────────────────────────────────────────────────────────────────────────────
// ItemDiscountDialog — FLAT / PERCENT toggle + ZyntaNumericPad.
// Max-cap validation from settings. Wrapped in RoleGuard(APPLY_DISCOUNT).
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Line-level discount dialog for a specific cart item.
 *
 * Supports two modes toggled via a segmented button:
 * - **FLAT** — fixed monetary deduction from the line total.
 * - **PERCENT** — percentage of the line unit price.
 *
 * Max-cap validation ensures the discount cannot exceed [maxDiscountPercent]
 * percent of the line total (policy loaded from app settings). Input is provided
 * via [ZyntaNumericPad] in PRICE mode.
 *
 * The entire dialog is wrapped in a [RoleGuard] for [Permission.APPLY_DISCOUNT].
 * Users without that permission will see an access-denied state.
 *
 * @param productId             The cart item this discount applies to.
 * @param productName           Display name shown in the dialog title.
 * @param lineTotal             Current pre-discount line total (for percent cap calc).
 * @param currentDiscount       Currently applied discount value (pre-populated).
 * @param currentDiscountType   Currently selected discount type.
 * @param onApply               Invoked with `(discount, type)` when user confirms.
 * @param onDismiss             Invoked when the dialog is dismissed.
 * @param userId                Active session user ID for RBAC evaluation.
 * @param checkPermissionUseCase Koin-injected RBAC evaluator.
 * @param maxDiscountPercent    Maximum allowed discount as a percent of line total (0–100).
 * @param formatter             Currency formatter for labels.
 */
@Composable
fun ItemDiscountDialog(
    productId: String,
    productName: String,
    lineTotal: Double,
    currentDiscount: Double = 0.0,
    currentDiscountType: DiscountType = DiscountType.PERCENT,
    onApply: (productId: String, discount: Double, type: DiscountType) -> Unit,
    onDismiss: () -> Unit,
    userId: String,
    checkPermissionUseCase: CheckPermissionUseCase,
    maxDiscountPercent: Double = 100.0,
    formatter: CurrencyFormatter = CurrencyFormatter(),
) {
    RoleGuard(
        userId = userId,
        permission = Permission.APPLY_DISCOUNT,
        checkPermissionUseCase = checkPermissionUseCase,
        unauthorizedContent = {
            AlertDialog(
                onDismissRequest = onDismiss,
                title = { Text("Access Denied") },
                text = { Text("You do not have permission to apply discounts.") },
                confirmButton = { TextButton(onClick = onDismiss) { Text("OK") } },
            )
        },
    ) {
        DiscountDialogContent(
            title = "Item Discount",
            subtitle = productName,
            lineTotal = lineTotal,
            currentDiscount = currentDiscount,
            currentDiscountType = currentDiscountType,
            maxDiscountPercent = maxDiscountPercent,
            formatter = formatter,
            onApply = { discount, type -> onApply(productId, discount, type) },
            onDismiss = onDismiss,
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Shared discount dialog content (also reused by OrderDiscountDialog)
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Core discount entry UI shared between item-level and order-level discount dialogs.
 *
 * @param title              Dialog title.
 * @param subtitle           Optional subtitle (product name / "Order Total").
 * @param lineTotal          Reference amount used for PERCENT cap validation.
 * @param currentDiscount    Pre-populated discount value.
 * @param currentDiscountType Pre-selected toggle state.
 * @param maxDiscountPercent Max allowed discount as percent of [lineTotal].
 * @param formatter          Currency formatter.
 * @param onApply            Invoked on confirm with `(discount, DiscountType)`.
 * @param onDismiss          Invoked on cancel / dismiss.
 */
@Composable
internal fun DiscountDialogContent(
    title: String,
    subtitle: String,
    lineTotal: Double,
    currentDiscount: Double,
    currentDiscountType: DiscountType,
    maxDiscountPercent: Double,
    formatter: CurrencyFormatter,
    onApply: (Double, DiscountType) -> Unit,
    onDismiss: () -> Unit,
) {
    var selectedType by remember { mutableStateOf(currentDiscountType) }
    var rawInput by remember { mutableStateOf(
        if (currentDiscount == 0.0) "" else formatter.formatPlain(currentDiscount)
    ) }
    var validationError by remember { mutableStateOf<String?>(null) }

    val discountValue = rawInput.toDoubleOrNull() ?: 0.0

    // Validate on every change
    validationError = when {
        discountValue < 0.0 -> "Discount cannot be negative"
        selectedType == DiscountType.PERCENT && discountValue > maxDiscountPercent ->
            "Max discount is $maxDiscountPercent%"
        selectedType == DiscountType.FIXED && lineTotal > 0 &&
                discountValue > lineTotal * (maxDiscountPercent / 100) ->
            "Exceeds max ${maxDiscountPercent.toInt()}% cap (${formatter.format(lineTotal * maxDiscountPercent / 100)})"
        else -> null
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text(text = title, style = MaterialTheme.typography.titleLarge)
                if (subtitle.isNotBlank()) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(ZyntaSpacing.md),
            ) {
                // ── FLAT / PERCENT toggle ───────────────────────────────────
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    DiscountType.values().forEachIndexed { index, type ->
                        SegmentedButton(
                            selected = selectedType == type,
                            onClick = {
                                selectedType = type
                                rawInput = ""
                            },
                            shape = SegmentedButtonDefaults.itemShape(
                                index = index,
                                count = DiscountType.values().size,
                            ),
                        ) {
                            Text(if (type == DiscountType.FIXED) "Flat (LKR)" else "Percent (%)")
                        }
                    }
                }

                // ── Numeric pad ─────────────────────────────────────────────
                ZyntaNumericPad(
                    displayValue = buildDisplayValue(rawInput, selectedType, formatter),
                    onDigit = { digit -> rawInput = appendDigit(rawInput, digit) },
                    onDoubleZero = { rawInput = appendDigit(rawInput, "00") },
                    onDecimal = {
                        if (!rawInput.contains('.')) rawInput = "$rawInput."
                    },
                    onBackspace = { rawInput = rawInput.dropLast(1) },
                    onClear = { rawInput = "" },
                    mode = NumericPadMode.PRICE,
                    modifier = Modifier.fillMaxWidth(),
                )

                // ── Validation error ────────────────────────────────────────
                validationError?.let { error ->
                    Text(
                        text = error,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onApply(discountValue, selectedType) },
                enabled = validationError == null && discountValue >= 0.0,
            ) {
                Text("Apply", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

// ── Helpers ───────────────────────────────────────────────────────────────────

private fun buildDisplayValue(
    rawInput: String,
    type: DiscountType,
    formatter: CurrencyFormatter,
): String {
    val value = rawInput.toDoubleOrNull() ?: 0.0
    return if (type == DiscountType.PERCENT) "$rawInput %" else formatter.formatPlain(value)
}

private fun appendDigit(current: String, digit: String): String {
    if (current == "0" && digit != ".") return digit
    return current + digit
}
