package com.zyntasolutions.zyntapos.feature.pos

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Stars
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.zyntasolutions.zyntapos.core.i18n.StringResource
import com.zyntasolutions.zyntapos.core.utils.CurrencyFormatter
import com.zyntasolutions.zyntapos.designsystem.components.LocalStrings
import com.zyntasolutions.zyntapos.designsystem.tokens.ZyntaSpacing

// ─────────────────────────────────────────────────────────────────────────────
// LoyaltyRedemptionDialog — Cashier selects how many loyalty points to redeem.
// Quick-select chips for common redemption amounts + slider for fine control.
// Points are converted to monetary discount at the rate defined by
// CalculateLoyaltyDiscountUseCase (default: 100 points = 1 currency unit).
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Dialog for selecting the number of loyalty points to redeem at checkout.
 *
 * @param availablePoints   Customer's total available loyalty point balance.
 * @param currentRedemption Number of points currently elected for redemption.
 * @param currentDiscount   Pre-computed monetary discount for [currentRedemption].
 * @param formatter         [CurrencyFormatter] for monetary display.
 * @param onConfirm         Called with the selected point count when "Apply" is tapped.
 * @param onClear           Called to clear any existing redemption (set to 0).
 * @param onDismiss         Called when the dialog is dismissed without changes.
 */
@Composable
internal fun LoyaltyRedemptionDialog(
    availablePoints: Int,
    currentRedemption: Int,
    currentDiscount: Double,
    formatter: CurrencyFormatter,
    onConfirm: (Int) -> Unit,
    onClear: () -> Unit,
    onDismiss: () -> Unit,
) {
    val s = LocalStrings.current
    var selectedPoints by remember { mutableIntStateOf(currentRedemption) }

    // Quick-select amounts: 25%, 50%, 75%, 100% of available
    val quickAmounts = remember(availablePoints) {
        listOf(
            availablePoints / 4,
            availablePoints / 2,
            (availablePoints * 3) / 4,
            availablePoints,
        ).filter { it > 0 }.distinct()
    }

    // Estimate discount for the currently selected amount (rough: 100 pts = 1 unit)
    val estimatedDiscount = selectedPoints / 100.0

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                Icons.Default.Stars,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
        },
        title = { Text(s[StringResource.POS_REDEEM_LOYALTY_TITLE]) },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(ZyntaSpacing.md),
            ) {
                // ── Available balance ────────────────────────────────────────
                Text(
                    text = s[StringResource.POS_LOYALTY_AVAILABLE_FORMAT, availablePoints],
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                // ── Quick-select chips ───────────────────────────────────────
                Row(
                    horizontalArrangement = Arrangement.spacedBy(ZyntaSpacing.sm),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    quickAmounts.forEach { amount ->
                        val pct = (amount * 100) / availablePoints
                        FilterChip(
                            selected = selectedPoints == amount,
                            onClick = { selectedPoints = amount },
                            label = { Text("$pct%") },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }

                // ── Slider for fine control ──────────────────────────────────
                if (availablePoints > 0) {
                    Slider(
                        value = selectedPoints.toFloat(),
                        onValueChange = { selectedPoints = it.toInt() },
                        valueRange = 0f..availablePoints.toFloat(),
                        steps = (availablePoints / 100).coerceIn(0, 20),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                // ── Selected points + estimated discount ─────────────────────
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = s[StringResource.POS_LOYALTY_POINTS_SELECTED_FORMAT, selectedPoints],
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                        ),
                    )
                    Text(
                        text = s[StringResource.POS_LOYALTY_ESTIMATED_DISCOUNT_FORMAT, formatter.format(estimatedDiscount)],
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.tertiary,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(selectedPoints) },
                enabled = selectedPoints > 0,
            ) {
                Text(s[StringResource.COMMON_APPLY])
            }
        },
        dismissButton = {
            Row {
                if (currentRedemption > 0) {
                    TextButton(onClick = onClear) {
                        Text(s[StringResource.COMMON_CLEAR], color = MaterialTheme.colorScheme.error)
                    }
                }
                TextButton(onClick = onDismiss) {
                    Text(s[StringResource.COMMON_CANCEL])
                }
            }
        },
    )
}
