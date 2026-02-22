package com.zyntasolutions.zyntapos.feature.inventory

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.zyntasolutions.zyntapos.designsystem.components.NumericPadMode
import com.zyntasolutions.zyntapos.designsystem.components.ZyntaNumericPad
import com.zyntasolutions.zyntapos.designsystem.tokens.ZyntaSpacing
import com.zyntasolutions.zyntapos.domain.model.Product
import com.zyntasolutions.zyntapos.domain.model.StockAdjustment

// ─────────────────────────────────────────────────────────────────────────────
// StockAdjustmentDialog — Sprint 18, Task 10.1.6
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Dialog for performing manual stock adjustments (Sprint 18, task 10.1.6).
 *
 * ### Features
 * 1. **Product Info Header:** Shows target product name, SKU, and current stock.
 * 2. **Adjustment Type Selector:** Increase / Decrease / Transfer via `FilterChip` row.
 * 3. **Quantity Input:** Uses [ZyntaNumericPad] in QUANTITY mode for precise entry.
 * 4. **Reason Field:** Required free-text explanation for audit trail compliance.
 * 5. **Confirm Action:** Dispatches [InventoryIntent.SubmitStockAdjustment] which
 *    triggers [AdjustStockUseCase] → persists [StockAdjustment] → logs to audit trail
 *    via [SecurityAuditLogger.logStockAdjustment].
 *
 * ### Architecture
 * Stateless composable — the product target is passed via [InventoryState.stockAdjustmentTarget].
 * Dismissal dispatches [InventoryIntent.DismissStockAdjustment]. All form state is
 * local (remember) since adjustments are atomic single-shot operations.
 *
 * @param product   The product being adjusted (from [InventoryState.stockAdjustmentTarget]).
 * @param onIntent  Dispatches [InventoryIntent] to the ViewModel.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StockAdjustmentDialog(
    product: Product,
    onIntent: (InventoryIntent) -> Unit,
) {
    var adjustmentType by remember { mutableStateOf(StockAdjustment.Type.INCREASE) }
    var quantityText by remember { mutableStateOf("0") }
    var reason by remember { mutableStateOf("") }
    var showValidation by remember { mutableStateOf(false) }

    val quantity = quantityText.toDoubleOrNull() ?: 0.0
    val isQuantityValid = quantity > 0.0
    val isReasonValid = reason.isNotBlank()
    val canSubmit = isQuantityValid && isReasonValid

    AlertDialog(
        onDismissRequest = { onIntent(InventoryIntent.DismissStockAdjustment) },
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(ZyntaSpacing.sm),
            ) {
                Icon(Icons.Default.Inventory, contentDescription = null)
                Text("Adjust Stock", style = MaterialTheme.typography.titleMedium)
            }
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(ZyntaSpacing.md),
                modifier = Modifier.widthIn(min = 320.dp, max = 480.dp),
            ) {
                // ── Product info header ──────────────────────────────────
                ProductInfoCard(product)

                HorizontalDivider()

                // ── Adjustment type selector ─────────────────────────────
                Text("Adjustment Type", style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(ZyntaSpacing.sm)) {
                    StockAdjustment.Type.entries.forEach { type ->
                        FilterChip(
                            selected = adjustmentType == type,
                            onClick = { adjustmentType = type },
                            label = { Text(type.displayLabel()) },
                            leadingIcon = {
                                Icon(
                                    imageVector = type.icon(),
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp),
                                )
                            },
                        )
                    }
                }

                // ── Quantity input ───────────────────────────────────────
                Text("Quantity", style = MaterialTheme.typography.labelLarge)
                ZyntaNumericPad(
                    displayValue = quantityText,
                    onDigit = { digit ->
                        quantityText = if (quantityText == "0") digit else quantityText + digit
                    },
                    onDoubleZero = {
                        quantityText = if (quantityText == "0") "0" else quantityText + "00"
                    },
                    onDecimal = {
                        if (!quantityText.contains(".")) quantityText += "."
                    },
                    onBackspace = {
                        quantityText = if (quantityText.length <= 1) "0" else quantityText.dropLast(1)
                    },
                    onClear = { quantityText = "0" },
                    mode = NumericPadMode.QUANTITY,
                    buttonSize = 48.dp,
                    modifier = Modifier.fillMaxWidth(),
                )

                // ── Quantity validation hint ─────────────────────────────
                if (showValidation && !isQuantityValid) {
                    Text(
                        text = "Quantity must be greater than zero.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }

                // ── Reason text field (required for audit) ───────────────
                OutlinedTextField(
                    value = reason,
                    onValueChange = { reason = it },
                    label = { Text("Reason *") },
                    placeholder = { Text("e.g., Received delivery, Damaged goods, Shrinkage") },
                    isError = showValidation && !isReasonValid,
                    supportingText = if (showValidation && !isReasonValid) {
                        { Text("Reason is required for the audit trail.") }
                    } else null,
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2,
                    maxLines = 4,
                )

                // ── Result preview ───────────────────────────────────────
                if (isQuantityValid) {
                    val newStock = when (adjustmentType) {
                        StockAdjustment.Type.INCREASE -> product.stockQty + quantity
                        StockAdjustment.Type.DECREASE -> (product.stockQty - quantity).coerceAtLeast(0.0)
                        StockAdjustment.Type.TRANSFER -> product.stockQty - quantity
                    }
                    val color = when {
                        newStock <= 0.0 -> MaterialTheme.colorScheme.error
                        newStock <= (product.minStockQty ?: 0.0) -> MaterialTheme.colorScheme.tertiary
                        else -> MaterialTheme.colorScheme.primary
                    }
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = color.copy(alpha = 0.1f),
                        ),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(ZyntaSpacing.sm),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text("Current: ${product.stockQty}", style = MaterialTheme.typography.bodySmall)
                            Text(
                                "${adjustmentType.symbol()} $quantity",
                                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                            )
                            Text(
                                "New: $newStock",
                                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                                color = color,
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            FilledTonalButton(
                onClick = {
                    showValidation = true
                    if (canSubmit) {
                        onIntent(
                            InventoryIntent.SubmitStockAdjustment(
                                type = adjustmentType,
                                quantity = quantity,
                                reason = reason.trim(),
                            )
                        )
                    }
                },
            ) {
                Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(ZyntaSpacing.xs))
                Text("Confirm Adjustment")
            }
        },
        dismissButton = {
            TextButton(onClick = { onIntent(InventoryIntent.DismissStockAdjustment) }) {
                Text("Cancel")
            }
        },
    )
}

// ─────────────────────────────────────────────────────────────────────────────
// Product Info Card
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Compact card showing the target product's key information.
 */
@Composable
private fun ProductInfoCard(product: Product) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(ZyntaSpacing.md),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(ZyntaSpacing.md),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = product.name,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                )
                if (!product.sku.isNullOrBlank()) {
                    Text(
                        text = "SKU: ${product.sku}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (!product.barcode.isNullOrBlank()) {
                    Text(
                        text = "Barcode: ${product.barcode}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            // Current stock badge
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "In Stock",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = "${product.stockQty}",
                    style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                    color = when {
                        product.stockQty <= 0.0 -> MaterialTheme.colorScheme.error
                        product.stockQty <= (product.minStockQty ?: 0.0) -> MaterialTheme.colorScheme.tertiary
                        else -> MaterialTheme.colorScheme.primary
                    },
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Extension Helpers for StockAdjustment.Type
// ─────────────────────────────────────────────────────────────────────────────

/** User-friendly label for the adjustment type chip. */
private fun StockAdjustment.Type.displayLabel(): String = when (this) {
    StockAdjustment.Type.INCREASE -> "Increase"
    StockAdjustment.Type.DECREASE -> "Decrease"
    StockAdjustment.Type.TRANSFER -> "Transfer"
}

/** Icon for the adjustment type chip. */
private fun StockAdjustment.Type.icon() = when (this) {
    StockAdjustment.Type.INCREASE -> Icons.Default.Add
    StockAdjustment.Type.DECREASE -> Icons.Default.Remove
    StockAdjustment.Type.TRANSFER -> Icons.Default.SwapHoriz
}

/** Arithmetic symbol for the result preview card. */
private fun StockAdjustment.Type.symbol(): String = when (this) {
    StockAdjustment.Type.INCREASE -> "+"
    StockAdjustment.Type.DECREASE -> "−"
    StockAdjustment.Type.TRANSFER -> "→"
}
