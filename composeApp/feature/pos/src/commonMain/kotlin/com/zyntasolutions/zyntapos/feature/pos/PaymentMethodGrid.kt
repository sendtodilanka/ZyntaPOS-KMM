package com.zyntasolutions.zyntapos.feature.pos

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.zyntasolutions.zyntapos.designsystem.tokens.ZentaSpacing
import com.zyntasolutions.zyntapos.domain.model.PaymentMethod

// ─────────────────────────────────────────────────────────────────────────────
// PaymentMethodGrid — 2×3 grid of payment method tiles (min 56dp height).
// Selected tile is highlighted using primary container colour.
// Stateless; state hoisted via [selectedMethod] + [onMethodSelected].
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Grid of selectable payment method tiles.
 *
 * Displays up to 6 tiles arranged in a 2-column grid. Each tile shows an icon
 * and a label. The currently [selectedMethod] tile is highlighted with the
 * Material 3 primary container colour and a primary border stroke.
 *
 * ### Min Touch Target
 * Each tile has a **minimum height of 56dp** per §8 of the UI/UX plan,
 * satisfying WCAG 2.1 touch-target guidelines.
 *
 * @param selectedMethod Currently selected [PaymentMethod]. May be `null` when
 *   no method has been chosen yet.
 * @param onMethodSelected Callback invoked with the chosen [PaymentMethod] when
 *   a tile is tapped. Dispatches [PosIntent.SelectPaymentMethod] in the caller.
 * @param modifier Optional [Modifier].
 * @param availableMethods Ordered list of methods to display. Defaults to all
 *   standard methods. Pass a filtered list to restrict choices (e.g., disable
 *   SPLIT when only one item in cart).
 */
@Composable
fun PaymentMethodGrid(
    selectedMethod: PaymentMethod?,
    onMethodSelected: (PaymentMethod) -> Unit,
    modifier: Modifier = Modifier,
    availableMethods: List<PaymentMethod> = PaymentMethod.entries,
) {
    // 2-column grid layout
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(ZentaSpacing.sm),
    ) {
        availableMethods.chunked(2).forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(ZentaSpacing.sm),
            ) {
                row.forEach { method ->
                    PaymentMethodTile(
                        method = method,
                        isSelected = method == selectedMethod,
                        onClick = { onMethodSelected(method) },
                        modifier = Modifier.weight(1f),
                    )
                }
                // Filler spacer when row has only 1 item (odd total)
                if (row.size == 1) {
                    Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Internal tile
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun PaymentMethodTile(
    method: PaymentMethod,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val containerColor = if (isSelected) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.surfaceVariant
    }
    val contentColor = if (isSelected) {
        MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    val borderColor = if (isSelected) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)
    }

    Surface(
        onClick = onClick,
        modifier = modifier.heightIn(min = 56.dp),
        shape = RoundedCornerShape(12.dp),
        color = containerColor,
        contentColor = contentColor,
        border = BorderStroke(
            width = if (isSelected) 2.dp else 1.dp,
            color = borderColor,
        ),
        tonalElevation = if (isSelected) 4.dp else 0.dp,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 10.dp, horizontal = ZentaSpacing.sm),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Icon(
                imageVector = method.icon,
                contentDescription = method.label,
                modifier = Modifier.size(24.dp),
                tint = contentColor,
            )
            Text(
                text = method.label,
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                color = contentColor,
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Extension helpers — label + icon for each PaymentMethod
// ─────────────────────────────────────────────────────────────────────────────

/** Human-readable label for display in the payment method tile. */
val PaymentMethod.label: String
    get() = when (this) {
        PaymentMethod.CASH -> "Cash"
        PaymentMethod.CARD -> "Card"
        PaymentMethod.MOBILE -> "Mobile"
        PaymentMethod.BANK_TRANSFER -> "Bank Transfer"
        PaymentMethod.SPLIT -> "Split"
    }

/** Material icon associated with each payment method. */
val PaymentMethod.icon: ImageVector
    get() = when (this) {
        PaymentMethod.CASH -> Icons.Filled.Money
        PaymentMethod.CARD -> Icons.Filled.CreditCard
        PaymentMethod.MOBILE -> Icons.Filled.PhoneAndroid
        PaymentMethod.BANK_TRANSFER -> Icons.Filled.AccountBalance
        PaymentMethod.SPLIT -> Icons.Filled.CallSplit
    }
