package com.zyntasolutions.zyntapos.feature.pos

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Print
import androidx.compose.material3.*
import com.zyntasolutions.zyntapos.designsystem.layouts.ZyntaPageScaffold
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.zyntasolutions.zyntapos.core.utils.CurrencyFormatter
import com.zyntasolutions.zyntapos.designsystem.components.SortDirection
import com.zyntasolutions.zyntapos.designsystem.components.ZyntaEmptyState
import com.zyntasolutions.zyntapos.designsystem.components.ZyntaTable
import com.zyntasolutions.zyntapos.designsystem.components.ZyntaTableColumn
import com.zyntasolutions.zyntapos.designsystem.tokens.ZyntaSpacing
import com.zyntasolutions.zyntapos.domain.model.Order
import com.zyntasolutions.zyntapos.domain.model.OrderStatus
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.koin.compose.koinInject

// ─────────────────────────────────────────────────────────────────────────────
// OrderHistoryScreen — Sprint 17, task 9.1.25
// Today's orders ZyntaTable (order #, time, items, total, status);
// filter by status chips; tap → order detail; reprint button per row
// ─────────────────────────────────────────────────────────────────────────────

/** Available filter options for the order history status chips. */
private val STATUS_FILTERS = listOf(
    null,                          // "All"
    OrderStatus.COMPLETED,
    OrderStatus.HELD,
    OrderStatus.VOIDED,
)

/**
 * Today's order history screen with status chip filtering and per-row reprint.
 *
 * ### Layout
 * ```
 * ┌───────────────────────────────────────────────────────┐
 * │  TopAppBar — "Order History"                          │
 * ├───────────────────────────────────────────────────────┤
 * │  [ All ] [ Completed ] [ Held ] [ Voided ]  ← chips  │
 * ├───────────────────────────────────────────────────────┤
 * │  ORDER # │ TIME  │ ITEMS │  TOTAL   │ STATUS │       │
 * │──────────┼───────┼───────┼──────────┼────────┤       │
 * │  ORD-001 │ 09:15 │   3   │  $45.50  │ ✅     │ [🖨]  │
 * │  ORD-002 │ 10:02 │   1   │  $12.00  │ ✅     │ [🖨]  │
 * └───────────────────────────────────────────────────────┘
 * ```
 *
 * ### Sorting
 * Tapping any column header cycles through [SortDirection]: None → Descending → Ascending.
 * Sorting is handled entirely in this composable via [remember] — no extra state in ViewModel.
 *
 * @param orders         Full list of today's [Order] objects (all statuses).
 * @param isLoading      When `true`, shows a loading skeleton inside [ZyntaTable].
 * @param onOrderTap     Invoked when a table row is tapped (navigate to order detail).
 * @param onReprintOrder Invoked when the reprint icon button is tapped.
 * @param modifier       Optional [Modifier].
 */
@Composable
fun OrderHistoryScreen(
    orders: List<Order>,
    isLoading: Boolean = false,
    onOrderTap: (orderId: String) -> Unit,
    onReprintOrder: (orderId: String) -> Unit,
    modifier: Modifier = Modifier,
    formatter: CurrencyFormatter = koinInject(),
) {
    // ── Local filter / sort state ─────────────────────────────────────────────
    var selectedStatus by remember { mutableStateOf<OrderStatus?>(null) }
    var sortColumn by remember { mutableStateOf<String?>(null) }
    var sortDirection by remember { mutableStateOf(SortDirection.None) }

    // ── Filtered + sorted list ────────────────────────────────────────────────
    val displayOrders = remember(orders, selectedStatus, sortColumn, sortDirection) {
        var list = if (selectedStatus == null) orders else orders.filter { it.status == selectedStatus }
        list = when (sortColumn) {
            COL_ORDER  -> if (sortDirection == SortDirection.Ascending) list.sortedBy { it.orderNumber } else list.sortedByDescending { it.orderNumber }
            COL_TIME   -> if (sortDirection == SortDirection.Ascending) list.sortedBy { it.createdAt } else list.sortedByDescending { it.createdAt }
            COL_ITEMS  -> if (sortDirection == SortDirection.Ascending) list.sortedBy { it.items.size } else list.sortedByDescending { it.items.size }
            COL_TOTAL  -> if (sortDirection == SortDirection.Ascending) list.sortedBy { it.total } else list.sortedByDescending { it.total }
            COL_STATUS -> if (sortDirection == SortDirection.Ascending) list.sortedBy { it.status.name } else list.sortedByDescending { it.status.name }
            else       -> list
        }
        list
    }

    ZyntaPageScaffold(
        title = "Order History",
        modifier = modifier,
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            // ── Status filter chips ────────────────────────────────────────────
            LazyRow(
                contentPadding = PaddingValues(horizontal = ZyntaSpacing.md, vertical = ZyntaSpacing.sm),
                horizontalArrangement = Arrangement.spacedBy(ZyntaSpacing.xs),
            ) {
                items(STATUS_FILTERS) { status ->
                    val selected = selectedStatus == status
                    FilterChip(
                        selected = selected,
                        onClick = { selectedStatus = status },
                        label = { Text(status?.label() ?: "All") },
                    )
                }
            }

            HorizontalDivider()

            // ── Orders table ──────────────────────────────────────────────────
            ZyntaTable(
                columns = TABLE_COLUMNS,
                items = displayOrders,
                sortColumnKey = sortColumn,
                sortDirection = sortDirection,
                onSort = { key ->
                    if (sortColumn == key) {
                        sortDirection = when (sortDirection) {
                            SortDirection.None       -> SortDirection.Descending
                            SortDirection.Descending -> SortDirection.Ascending
                            SortDirection.Ascending  -> SortDirection.None
                        }
                        if (sortDirection == SortDirection.None) sortColumn = null
                    } else {
                        sortColumn = key
                        sortDirection = SortDirection.Descending
                    }
                },
                isLoading = isLoading,
                isEmpty = displayOrders.isEmpty(),
                rowKey = { it.id },
                modifier = Modifier.fillMaxSize(),
                emptyContent = {
                    ZyntaEmptyState(
                        icon = Icons.Default.History,
                        title = "No Orders",
                        subtitle = if (selectedStatus != null)
                            "No ${selectedStatus!!.label()} orders today."
                        else
                            "No orders have been processed today.",
                        modifier = Modifier.fillMaxWidth().padding(ZyntaSpacing.xl),
                    )
                },
                rowContent = { order: Order ->
                    // ORDER # column
                    Column(modifier = Modifier.weight(TABLE_COLUMNS[0].weight)) {
                        Text(
                            text = order.orderNumber,
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Medium,
                        )
                    }

                    // TIME column
                    Text(
                        text = order.createdAt.toLocalDateTime(TimeZone.currentSystemDefault())
                            .let { "${it.hour.toString().padStart(2,'0')}:${it.minute.toString().padStart(2,'0')}" },
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.weight(TABLE_COLUMNS[1].weight),
                    )

                    // ITEMS column
                    Text(
                        text = order.items.size.toString(),
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.weight(TABLE_COLUMNS[2].weight),
                    )

                    // TOTAL column
                    Text(
                        text = formatter.format(order.total),
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.weight(TABLE_COLUMNS[3].weight),
                    )

                    // STATUS column
                    Box(modifier = Modifier.weight(TABLE_COLUMNS[4].weight)) {
                        StatusBadge(status = order.status)
                    }

                    // REPRINT icon button (no column weight — fixed width)
                    IconButton(
                        onClick = { onReprintOrder(order.id) },
                        modifier = Modifier.size(36.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Default.Print,
                            contentDescription = "Reprint order ${order.orderNumber}",
                            tint = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                },
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// StatusBadge — compact coloured chip for OrderStatus
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun StatusBadge(status: OrderStatus) {
    val (container, content) = when (status) {
        OrderStatus.COMPLETED -> MaterialTheme.colorScheme.tertiaryContainer to MaterialTheme.colorScheme.onTertiaryContainer
        OrderStatus.HELD      -> MaterialTheme.colorScheme.secondaryContainer to MaterialTheme.colorScheme.onSecondaryContainer
        OrderStatus.VOIDED    -> MaterialTheme.colorScheme.errorContainer to MaterialTheme.colorScheme.onErrorContainer
        else                  -> MaterialTheme.colorScheme.surfaceVariant to MaterialTheme.colorScheme.onSurfaceVariant
    }
    Surface(
        shape = MaterialTheme.shapes.small,
        color = container,
    ) {
        Text(
            text = status.label(),
            style = MaterialTheme.typography.labelSmall,
            color = content,
            modifier = Modifier.padding(horizontal = ZyntaSpacing.xs, vertical = 2.dp),
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Constants
// ─────────────────────────────────────────────────────────────────────────────

private const val COL_ORDER  = "order_number"
private const val COL_TIME   = "time"
private const val COL_ITEMS  = "items"
private const val COL_TOTAL  = "total"
private const val COL_STATUS = "status"

private val TABLE_COLUMNS = listOf(
    ZyntaTableColumn(key = COL_ORDER,  header = "ORDER #",  weight = 1.8f),
    ZyntaTableColumn(key = COL_TIME,   header = "TIME",     weight = 0.8f),
    ZyntaTableColumn(key = COL_ITEMS,  header = "ITEMS",    weight = 0.7f, sortable = true),
    ZyntaTableColumn(key = COL_TOTAL,  header = "TOTAL",    weight = 1.0f),
    ZyntaTableColumn(key = COL_STATUS, header = "STATUS",   weight = 1.2f),
)

/** Human-readable label for [OrderStatus] filter chips. */
private fun OrderStatus.label(): String = when (this) {
    OrderStatus.COMPLETED -> "Completed"
    OrderStatus.HELD      -> "Held"
    OrderStatus.VOIDED    -> "Voided"
    else                  -> name.lowercase().replaceFirstChar { it.uppercaseChar() }
}
