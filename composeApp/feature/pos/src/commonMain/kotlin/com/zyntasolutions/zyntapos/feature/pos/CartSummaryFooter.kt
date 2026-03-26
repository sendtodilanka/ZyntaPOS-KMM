package com.zyntasolutions.zyntapos.feature.pos

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zyntasolutions.zyntapos.core.i18n.StringResource
import com.zyntasolutions.zyntapos.core.utils.CurrencyFormatter
import com.zyntasolutions.zyntapos.designsystem.components.LocalStrings
import com.zyntasolutions.zyntapos.designsystem.components.ZyntaButton
import com.zyntasolutions.zyntapos.designsystem.components.ZyntaButtonSize
import com.zyntasolutions.zyntapos.designsystem.tokens.ZyntaSpacing
import com.zyntasolutions.zyntapos.domain.model.OrderTotals

// ─────────────────────────────────────────────────────────────────────────────
// CartSummaryFooter — Subtotal, Tax, Discount (if > 0), Total, PAY button.
// All monetary amounts formatted via CurrencyFormatter. Stateless.
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Financial summary footer displayed at the bottom of the cart panel.
 *
 * Shows subtotal, tax, discount (only when > 0), and the grand total in bold
 * large text. A primary [ZyntaButton] labelled "PAY" triggers [onPayClicked].
 *
 * All amounts are rendered through [CurrencyFormatter] to ensure consistent
 * locale-aware formatting across the entire application.
 *
 * @param orderTotals   Computed totals from `CalculateOrderTotalsUseCase`.
 * @param onPayClicked  Invoked when the PAY button is tapped.
 * @param modifier      Optional [Modifier].
 * @param formatter     [CurrencyFormatter] for amount display.
 * @param isLoading     When `true`, the PAY button enters loading state.
 */
@Composable
fun CartSummaryFooter(
    orderTotals: OrderTotals,
    onPayClicked: () -> Unit,
    modifier: Modifier = Modifier,
    formatter: CurrencyFormatter = CurrencyFormatter(),
    isLoading: Boolean = false,
    loyaltyDiscount: Double = 0.0,
) {
    val s = LocalStrings.current
    Surface(
        modifier = modifier.fillMaxWidth(),
        tonalElevation = 4.dp,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(ZyntaSpacing.md),
            verticalArrangement = Arrangement.spacedBy(ZyntaSpacing.xs),
        ) {
            // ── Subtotal ────────────────────────────────────────────────────
            SummaryRow(
                label = s[StringResource.COMMON_SUBTOTAL],
                amount = formatter.format(orderTotals.subtotal),
            )

            // ── Tax ─────────────────────────────────────────────────────────
            SummaryRow(
                label = s[StringResource.COMMON_TAX],
                amount = formatter.format(orderTotals.taxAmount),
            )

            // ── Discount (shown only when > 0) ───────────────────────────────
            if (orderTotals.discountAmount > 0.0) {
                SummaryRow(
                    label = s[StringResource.COMMON_DISCOUNT],
                    amount = "- ${formatter.format(orderTotals.discountAmount)}",
                    amountColor = MaterialTheme.colorScheme.error,
                )
            }

            // ── Loyalty discount (shown only when > 0) ──────────────────────
            if (loyaltyDiscount > 0.0) {
                SummaryRow(
                    label = s[StringResource.POS_LOYALTY_POINTS_LABEL],
                    amount = "- ${formatter.format(loyaltyDiscount)}",
                    amountColor = MaterialTheme.colorScheme.tertiary,
                )
            }

            HorizontalDivider(
                modifier = Modifier.padding(vertical = ZyntaSpacing.xs),
                thickness = 1.dp,
            )

            // ── Grand Total ─────────────────────────────────────────────────
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = s[StringResource.POS_CART_TOTAL_LABEL],
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                    ),
                )
                Text(
                    text = formatter.format(orderTotals.total),
                    style = MaterialTheme.typography.headlineSmall.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 24.sp,
                    ),
                    color = MaterialTheme.colorScheme.primary,
                )
            }

            Spacer(Modifier.height(ZyntaSpacing.sm))

            // ── PAY Button ──────────────────────────────────────────────────
            ZyntaButton(
                text = s[StringResource.POS_PAY_AMOUNT_FORMAT, formatter.format(orderTotals.total)],
                onClick = onPayClicked,
                size = ZyntaButtonSize.Large,
                isLoading = isLoading,
                enabled = orderTotals.itemCount > 0,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun SummaryRow(
    label: String,
    amount: String,
    amountColor: androidx.compose.ui.graphics.Color =
        MaterialTheme.colorScheme.onSurface,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = amount,
            style = MaterialTheme.typography.bodyMedium,
            color = amountColor,
        )
    }
}
