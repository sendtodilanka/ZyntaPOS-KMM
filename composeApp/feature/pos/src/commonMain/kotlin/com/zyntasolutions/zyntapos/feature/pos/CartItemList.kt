package com.zyntasolutions.zyntapos.feature.pos

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.zyntasolutions.zyntapos.core.utils.CurrencyFormatter
import com.zyntasolutions.zyntapos.designsystem.components.ZentaCartItemRow
import com.zyntasolutions.zyntapos.domain.model.CartItem

// ─────────────────────────────────────────────────────────────────────────────
// CartItemList — Stateless LazyColumn of ZentaCartItemRow.
// key = { it.productId } for stable recomposition on quantity / discount changes.
// SwipeToDismissBox is handled inside ZentaCartItemRow (EndToStart → remove).
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Scrollable list of cart line items.
 *
 * Uses `key = { it.productId }` so Compose can perform stable item animations
 * when a new product is added, an existing one is removed, or quantities change
 * — preventing full-list recomposition on every cart mutation.
 *
 * Swipe-to-remove is delegated to [ZentaCartItemRow]'s `SwipeToDismissBox`;
 * the [onRemove] lambda is forwarded from the cart intent dispatcher.
 *
 * @param cartItems    Ordered list of current cart lines. Empty list shows an
 *                     empty-state placeholder.
 * @param onIncrement  Invoked with [CartItem.productId] when the [+] stepper is tapped.
 * @param onDecrement  Invoked with [CartItem.productId] when the [−] stepper is tapped.
 * @param onRemove     Invoked with [CartItem.productId] when the row is swiped away.
 * @param modifier     Optional [Modifier].
 * @param formatter    [CurrencyFormatter] instance for unit price and line total formatting.
 */
@Composable
fun CartItemList(
    cartItems: List<CartItem>,
    onIncrement: (productId: String) -> Unit,
    onDecrement: (productId: String) -> Unit,
    onRemove: (productId: String) -> Unit,
    modifier: Modifier = Modifier,
    formatter: CurrencyFormatter = CurrencyFormatter(),
) {
    if (cartItems.isEmpty()) {
        // Empty state
        Box(
            modifier = modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "Cart is empty\nAdd products from the grid",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
        return
    }

    LazyColumn(modifier = modifier) {
        items(
            items = cartItems,
            key = { item -> item.productId },
        ) { item ->
            ZentaCartItemRow(
                name = item.productName,
                unitPrice = formatter.format(item.unitPrice),
                quantity = item.quantity.toInt(),
                lineTotal = formatter.format(item.lineTotal),
                onIncrement = { onIncrement(item.productId) },
                onDecrement = { onDecrement(item.productId) },
                onRemove = { onRemove(item.productId) },

                modifier = Modifier.fillMaxWidth(),
            )
            HorizontalDivider(
                modifier = Modifier.padding(horizontal = 16.dp),
                thickness = 0.5.dp,
                color = MaterialTheme.colorScheme.outlineVariant,
            )
        }
    }
}
