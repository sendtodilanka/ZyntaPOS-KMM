package com.zyntasolutions.zyntapos.feature.pos

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.zyntasolutions.zyntapos.core.i18n.StringResource
import com.zyntasolutions.zyntapos.designsystem.components.LocalStrings
import com.zyntasolutions.zyntapos.designsystem.components.ZyntaBottomSheet
import com.zyntasolutions.zyntapos.designsystem.components.ZyntaEmptyState
import com.zyntasolutions.zyntapos.designsystem.tokens.ZyntaSpacing
import com.zyntasolutions.zyntapos.domain.model.Order
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

// ─────────────────────────────────────────────────────────────────────────────
// HeldOrdersBottomSheet — Sprint 17, task 9.1.24
// LazyColumn of held orders (hold time, item count, total)
// tap → RetrieveHeldOrderUseCase → restore cart state; F9 shortcut opens
// ─────────────────────────────────────────────────────────────────────────────

/**
 * A [ZyntaBottomSheet] listing all orders with [OrderStatus.HELD] status.
 *
 * ### Keyboard Shortcut
 * The sheet is opened via **F9** from [PosScreen]'s [KeyboardShortcutHandler].
 * The `Modifier.onPreviewKeyEvent` within the sheet itself intercepts F9 again
 * to allow the same key to close it (toggle behaviour).
 *
 * ### Row Layout
 * ```
 * ┌──────────────────────────────────────────────┐
 * │  🛒 Hold #3          12:34  •  3 items  $45.50│
 * │     Held 5 min ago              [Retrieve]    │
 * └──────────────────────────────────────────────┘
 * ```
 *
 * @param heldOrders   List of [Order] objects with [OrderStatus.HELD] status.
 * @param sheetState   Hoisted [SheetState] owned by the parent composable.
 * @param onDismiss    Invoked when the sheet is dismissed.
 * @param onRetrieve   Invoked with the hold [Order.id] when the cashier taps "Retrieve".
 *                     The caller dispatches [PosIntent.RetrieveHeld].
 * @param modifier     Optional [Modifier].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HeldOrdersBottomSheet(
    heldOrders: List<Order>,
    sheetState: SheetState,
    onDismiss: () -> Unit,
    onRetrieve: (holdId: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    ZyntaBottomSheet(
        sheetState = sheetState,
        onDismiss = onDismiss,
        modifier = modifier
            .onPreviewKeyEvent { event ->
                // F9 closes the sheet (same key toggles)
                if (event.key == Key.F9 && event.type == KeyEventType.KeyDown) {
                    onDismiss()
                    true
                } else false
            },
    ) {
        val s = LocalStrings.current
        Column(modifier = Modifier.fillMaxWidth()) {

            // ── Header ───────────────────────────────────────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = ZyntaSpacing.md, vertical = ZyntaSpacing.sm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = s[StringResource.POS_HELD_ORDERS_TITLE],
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                )
                AssistChip(
                    onClick = {},
                    label = { Text(s[StringResource.POS_HELD_ORDERS_SHORTCUT]) },
                    leadingIcon = null,
                    modifier = Modifier.height(28.dp),
                )
            }

            HorizontalDivider()

            // ── Body ─────────────────────────────────────────────────────────
            if (heldOrders.isEmpty()) {
                ZyntaEmptyState(
                    icon = Icons.Default.ShoppingCart,
                    title = s[StringResource.POS_NO_HELD_ORDERS],
                    subtitle = s[StringResource.POS_NO_HELD_ORDERS_HINT],
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(ZyntaSpacing.xl),
                )
            } else {
                LazyColumn(
                    modifier = Modifier.heightIn(max = 400.dp),
                    contentPadding = PaddingValues(vertical = ZyntaSpacing.xs),
                ) {
                    items(
                        items = heldOrders,
                        key = { it.id },
                    ) { order ->
                        HeldOrderRow(
                            order = order,
                            onRetrieve = { onRetrieve(order.id) },
                        )
                        HorizontalDivider(thickness = 0.5.dp)
                    }
                }
            }

            // Bottom padding for nav bar / gesture inset
            Spacer(Modifier.navigationBarsPadding())
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// HeldOrderRow
// ─────────────────────────────────────────────────────────────────────────────

/**
 * A single row in the [HeldOrdersBottomSheet] list.
 *
 * Displays: order reference, hold time, item count, total, and a "Retrieve" button.
 *
 * @param order      The held [Order].
 * @param onRetrieve Invoked when the "Retrieve" button is tapped.
 */
@Composable
private fun HeldOrderRow(
    order: Order,
    onRetrieve: () -> Unit,
) {
    val s = LocalStrings.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = ZyntaSpacing.md, vertical = ZyntaSpacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(ZyntaSpacing.sm),
    ) {
        // Cart icon badge
        Icon(
            imageVector = Icons.Default.ShoppingCart,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.secondary,
            modifier = Modifier.size(24.dp),
        )

        // Order info
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = s[StringResource.POS_ORDER_NUMBER_FORMAT, order.orderNumber],
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(ZyntaSpacing.xs)) {
                Text(
                    text = order.createdAt.formatHoldTime(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = "•",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = s[StringResource.POS_ITEMS_COUNT_FORMAT, order.items.size, if (order.items.size != 1) "s" else ""],
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = "•",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = "$%.2f".format(order.total),
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }

        // Retrieve button
        FilledTonalButton(
            onClick = onRetrieve,
            contentPadding = PaddingValues(horizontal = ZyntaSpacing.md, vertical = ZyntaSpacing.xs),
        ) {
            Text(s[StringResource.POS_RETRIEVE], style = MaterialTheme.typography.labelMedium)
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Formatting helpers
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Formats an [Instant] as a short "HH:MM" time string in the system default time zone.
 * Used to show the time the order was placed (held).
 */
private fun Instant.formatHoldTime(): String {
    val local = toLocalDateTime(TimeZone.currentSystemDefault())
    return "${local.hour.toString().padStart(2, '0')}:${local.minute.toString().padStart(2, '0')}"
}
