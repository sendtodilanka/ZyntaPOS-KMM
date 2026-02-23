package com.zyntasolutions.zyntapos.feature.pos

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Notes
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.zyntasolutions.zyntapos.core.utils.CurrencyFormatter
import com.zyntasolutions.zyntapos.designsystem.tokens.ZyntaSpacing
import com.zyntasolutions.zyntapos.domain.model.CartItem
import com.zyntasolutions.zyntapos.domain.model.Customer
import com.zyntasolutions.zyntapos.domain.model.OrderTotals

// ─────────────────────────────────────────────────────────────────────────────
// CartContent — Internal composable shared between the permanent side panel
// (EXPANDED) and the bottom sheet (COMPACT/MEDIUM). Not part of the public API.
// ─────────────────────────────────────────────────────────────────────────────

/**
 * The full cart UI: customer chip + action row → item list → summary footer.
 *
 * Consumed by [CartPanel] to avoid duplicating the layout between the permanent
 * expanded panel and the compact bottom-sheet variant.
 *
 * @param cartItems        Current cart lines.
 * @param orderTotals      Computed financial summary.
 * @param selectedCustomer Currently attached customer; `null` for walk-in.
 * @param onIntent         Intent dispatcher.
 * @param modifier         Outer [Modifier] (typically fillMaxSize / fillMaxWidth).
 * @param formatter        Shared [CurrencyFormatter].
 */
@Composable
internal fun CartContent(
    cartItems: List<CartItem>,
    orderTotals: OrderTotals,
    selectedCustomer: Customer?,
    onIntent: (PosIntent) -> Unit,
    modifier: Modifier = Modifier,
    formatter: CurrencyFormatter = CurrencyFormatter(),
) {
    Column(modifier = modifier) {

        // ── Customer row ───────────────────────────────────────────────────
        CustomerRow(
            selectedCustomer = selectedCustomer,
            onSelectCustomer = { onIntent(PosIntent.ClearCustomer) }, // re-open dialog from parent
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = ZyntaSpacing.md, vertical = ZyntaSpacing.sm),
        )

        // ── Cart action row (Order Notes | Order Discount | Clear) ─────────
        CartActionRow(
            onNotesClicked = { /* surfaced via PosScreen dialog management */ },
            onDiscountClicked = { /* surfaced via PosScreen dialog management */ },
            onClearClicked = { onIntent(PosIntent.ClearCart) },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = ZyntaSpacing.md),
        )

        HorizontalDivider(
            thickness = 1.dp,
            modifier = Modifier.padding(top = ZyntaSpacing.xs),
        )

        // ── Cart item list ────────────────────────────────────────────────
        CartItemList(
            cartItems = cartItems,
            onIncrement = { id ->
                val item = cartItems.first { it.productId == id }
                onIntent(PosIntent.UpdateQty(id, item.quantity + 1))
            },
            onDecrement = { id ->
                val item = cartItems.first { it.productId == id }
                val newQty = item.quantity - 1
                if (newQty < 1) onIntent(PosIntent.RemoveFromCart(id))
                else onIntent(PosIntent.UpdateQty(id, newQty))
            },
            onRemove = { id -> onIntent(PosIntent.RemoveFromCart(id)) },
            formatter = formatter,
            modifier = Modifier.weight(1f),
        )

        // ── Summary footer ────────────────────────────────────────────────
        CartSummaryFooter(
            orderTotals = orderTotals,
            onPayClicked = { onIntent(PosIntent.ProcessPayment(
                method = com.zyntasolutions.zyntapos.domain.model.PaymentMethod.CASH,
            )) },
            formatter = formatter,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

// ─── Sub-composables ──────────────────────────────────────────────────────────

@Composable
private fun CustomerRow(
    selectedCustomer: Customer?,
    onSelectCustomer: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(modifier = modifier) {
        Icon(
            imageVector = Icons.Default.Person,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.width(ZyntaSpacing.xs))
        TextButton(
            onClick = onSelectCustomer,
            contentPadding = PaddingValues(horizontal = ZyntaSpacing.xs, vertical = 0.dp),
        ) {
            Text(
                text = selectedCustomer?.name ?: "Walk-in Customer ▼",
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

@Composable
private fun CartActionRow(
    onNotesClicked: () -> Unit,
    onDiscountClicked: () -> Unit,
    onClearClicked: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(ZyntaSpacing.xs),
    ) {
        IconButton(onClick = onNotesClicked) {
            Icon(Icons.AutoMirrored.Filled.Notes, contentDescription = "Order notes")
        }
        IconButton(onClick = onDiscountClicked) {
            Icon(Icons.Default.LocalOffer, contentDescription = "Order discount")
        }
        Spacer(Modifier.weight(1f))
        IconButton(onClick = onClearClicked) {
            Icon(
                Icons.Default.DeleteSweep,
                contentDescription = "Clear cart",
                tint = MaterialTheme.colorScheme.error,
            )
        }
    }
}
