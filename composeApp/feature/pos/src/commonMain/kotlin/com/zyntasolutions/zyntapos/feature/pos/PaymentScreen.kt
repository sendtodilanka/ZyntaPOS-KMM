package com.zyntasolutions.zyntapos.feature.pos

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zyntasolutions.zyntapos.core.utils.CurrencyFormatter
import com.zyntasolutions.zyntapos.designsystem.components.ZyntaButton
import com.zyntasolutions.zyntapos.designsystem.components.ZyntaButtonSize
import com.zyntasolutions.zyntapos.designsystem.layouts.ZyntaPageScaffold
import com.zyntasolutions.zyntapos.designsystem.tokens.ZyntaSpacing
import com.zyntasolutions.zyntapos.designsystem.util.WindowSize
import com.zyntasolutions.zyntapos.designsystem.util.currentWindowSize
import com.zyntasolutions.zyntapos.domain.model.CartItem
import com.zyntasolutions.zyntapos.domain.model.OrderTotals
import com.zyntasolutions.zyntapos.domain.model.PaymentMethod
import com.zyntasolutions.zyntapos.domain.model.PaymentSplit
import kotlinx.coroutines.flow.Flow

// ─────────────────────────────────────────────────────────────────────────────
// PaymentScreen — Full-screen payment flow (Sprint 16, task 9.1.15)
//
// Layout (Expanded / Desktop):
//   Left 40 %  — read-only Order Summary (item list + totals breakdown)
//   Right 60 % — Payment method grid + per-method input panel + PAY button
//
// Layout (Compact / Phone):
//   Vertically stacked: collapsible order summary → method selector → input
//
// MVI wiring:
//   Dispatches `PosIntent.ProcessPayment` via [onIntent].
//   Collects `PosEffect.ShowReceiptScreen` from [effects] to trigger
//   `PaymentSuccessOverlay` → [onNavigateToReceipt].
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Full-screen payment screen composed as a modal route.
 *
 * ### Responsibilities
 * - Renders a two-pane layout (Expanded) or single-pane (Compact) adapting to
 *   the current [WindowSizeClassHelper.isExpandedWidth].
 * - Manages local UI state: selected payment method, tendered amount (cash),
 *   and split payment legs.
 * - Dispatches [PosIntent.ProcessPayment] on PAY confirmation.
 * - Observes [effects] for [PosEffect.ShowReceiptScreen] to display
 *   [PaymentSuccessOverlay] before navigating to the receipt screen.
 *
 * @param state               Latest [PosState] from `PosViewModel`.
 * @param effects             Hot [Flow] of [PosEffect] from `PosViewModel`.
 * @param onIntent            Dispatch function into `PosViewModel`.
 * @param onDismiss           Called when the user presses Back.
 * @param onNavigateToReceipt Called after [PaymentSuccessOverlay] auto-dismisses.
 * @param modifier            Optional [Modifier].
 * @param formatter           [CurrencyFormatter] for all monetary display.
 */
@Composable
fun PaymentScreen(
    state: PosState,
    effects: Flow<PosEffect>,
    onIntent: (PosIntent) -> Unit,
    onDismiss: () -> Unit,
    onNavigateToReceipt: (orderId: String) -> Unit,
    modifier: Modifier = Modifier,
    formatter: CurrencyFormatter = CurrencyFormatter(),
) {
    // ── Local UI state ─────────────────────────────────────────────────────────
    var selectedMethod by remember { mutableStateOf<PaymentMethod?>(PaymentMethod.CASH) }
    var tenderedRaw by remember { mutableStateOf("0") }
    var splits by remember {
        mutableStateOf(listOf(PaymentSplit(PaymentMethod.CASH, 0.01)))
    }
    var showSummary by remember { mutableStateOf(true) }
    var showSuccessOverlay by remember { mutableStateOf(false) }
    var pendingReceiptOrderId by remember { mutableStateOf<String?>(null) }

    // ── Effect collection ──────────────────────────────────────────────────────
    LaunchedEffect(Unit) {
        effects.collect { effect ->
            when (effect) {
                is PosEffect.ShowReceiptScreen -> {
                    pendingReceiptOrderId = effect.orderId
                    showSuccessOverlay = true
                }
                else -> Unit
            }
        }
    }

    val orderTotal = state.orderTotals.total
    val isExpandedLayout = currentWindowSize() == WindowSize.EXPANDED

    val tenderedAmount: Double = (tenderedRaw.toLongOrNull() ?: 0L) / 100.0
    val isCashTenderValid = tenderedAmount >= orderTotal
    val isSplitValid = kotlin.math.abs(splits.sumOf { it.amount } - orderTotal) < 0.01
    val isPayEnabled: Boolean = when (selectedMethod) {
        PaymentMethod.CASH -> isCashTenderValid
        PaymentMethod.SPLIT -> isSplitValid
        null -> false
        else -> true
    }

    fun onPayClicked() {
        val method = selectedMethod ?: return
        if (method == PaymentMethod.SPLIT) {
            onIntent(PosIntent.ProcessPayment(method = method, splits = splits, tendered = orderTotal))
        } else {
            val tendered = if (method == PaymentMethod.CASH) tenderedAmount else orderTotal
            onIntent(PosIntent.ProcessPayment(method = method, tendered = tendered))
        }
    }

    // ── Scaffold ───────────────────────────────────────────────────────────────
    ZyntaPageScaffold(
        title = "Payment",
        modifier = modifier,
        onNavigateBack = onDismiss,
    ) { innerPadding ->
        if (isExpandedLayout) {
            // ── EXPANDED two-pane ──────────────────────────────────────────────
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(ZyntaSpacing.md),
                horizontalArrangement = Arrangement.spacedBy(ZyntaSpacing.md),
            ) {
                OrderSummaryPane(
                    cartItems = state.cartItems,
                    orderTotals = state.orderTotals,
                    selectedCustomer = state.selectedCustomer?.name,
                    formatter = formatter,
                    modifier = Modifier.weight(0.4f).fillMaxHeight(),
                )
                PaymentInputPane(
                    orderTotal = orderTotal,
                    selectedMethod = selectedMethod,
                    tenderedRaw = tenderedRaw,
                    splits = splits,
                    isPayEnabled = isPayEnabled,
                    isLoading = state.isLoading,
                    formatter = formatter,
                    onMethodSelected = { selectedMethod = it },
                    onTenderedChanged = { tenderedRaw = it },
                    onSplitsChanged = { splits = it },
                    onPayClicked = ::onPayClicked,
                    modifier = Modifier.weight(0.6f).fillMaxHeight(),
                )
            }
        } else {
            // ── COMPACT single-pane ────────────────────────────────────────────
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(horizontal = ZyntaSpacing.md),
                verticalArrangement = Arrangement.spacedBy(ZyntaSpacing.sm),
                contentPadding = PaddingValues(bottom = 96.dp),
            ) {
                item {
                    CollapsibleOrderSummary(
                        cartItems = state.cartItems,
                        orderTotals = state.orderTotals,
                        isExpanded = showSummary,
                        onToggle = { showSummary = !showSummary },
                        formatter = formatter,
                    )
                }
                item {
                    PaymentInputPane(
                        orderTotal = orderTotal,
                        selectedMethod = selectedMethod,
                        tenderedRaw = tenderedRaw,
                        splits = splits,
                        isPayEnabled = isPayEnabled,
                        isLoading = state.isLoading,
                        formatter = formatter,
                        onMethodSelected = { selectedMethod = it },
                        onTenderedChanged = { tenderedRaw = it },
                        onSplitsChanged = { splits = it },
                        onPayClicked = ::onPayClicked,
                    )
                }
            }
        }
    }

    // ── Success overlay ────────────────────────────────────────────────────────
    if (showSuccessOverlay) {
        PaymentSuccessOverlay(
            onDismissed = {
                showSuccessOverlay = false
                pendingReceiptOrderId?.let { onNavigateToReceipt(it) }
            },
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// PaymentInputPane — right pane (Expanded) or embedded item (Compact)
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun PaymentInputPane(
    orderTotal: Double,
    selectedMethod: PaymentMethod?,
    tenderedRaw: String,
    splits: List<PaymentSplit>,
    isPayEnabled: Boolean,
    isLoading: Boolean,
    formatter: CurrencyFormatter,
    onMethodSelected: (PaymentMethod) -> Unit,
    onTenderedChanged: (String) -> Unit,
    onSplitsChanged: (List<PaymentSplit>) -> Unit,
    onPayClicked: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(ZyntaSpacing.sm),
        verticalArrangement = Arrangement.spacedBy(ZyntaSpacing.md),
    ) {
        Text(
            "Select Payment Method",
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
        )

        PaymentMethodGrid(
            selectedMethod = selectedMethod,
            onMethodSelected = onMethodSelected,
        )

        when (selectedMethod) {
            PaymentMethod.CASH -> {
                CashPaymentPanel(
                    orderTotal = orderTotal,
                    tenderedRaw = tenderedRaw,
                    onTenderedChanged = onTenderedChanged,
                    formatter = formatter,
                )
                ZyntaButton(
                    text = if (isLoading) "Processing…" else "PAY  ${formatter.format(orderTotal)}",
                    onClick = onPayClicked,
                    size = ZyntaButtonSize.Large,
                    enabled = isPayEnabled && !isLoading,
                    isLoading = isLoading,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            PaymentMethod.SPLIT -> {
                SplitPaymentPanel(
                    orderTotal = orderTotal,
                    splits = splits,
                    onSplitsChanged = onSplitsChanged,
                    onPayClicked = onPayClicked,
                    formatter = formatter,
                )
                // SplitPaymentPanel contains its own PAY button — no duplicate here
            }

            PaymentMethod.CARD, PaymentMethod.MOBILE, PaymentMethod.BANK_TRANSFER -> {
                NonCashSummary(orderTotal = orderTotal, method = selectedMethod, formatter = formatter)
                ZyntaButton(
                    text = if (isLoading) "Processing…" else "PAY  ${formatter.format(orderTotal)}",
                    onClick = onPayClicked,
                    size = ZyntaButtonSize.Large,
                    enabled = !isLoading,
                    isLoading = isLoading,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            null -> {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = MaterialTheme.shapes.medium,
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.padding(ZyntaSpacing.lg),
                    ) {
                        Text(
                            "Please select a payment method above",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// NonCashSummary — Card / Mobile / Bank — shows charge amount, no numpad
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun NonCashSummary(
    orderTotal: Double,
    method: PaymentMethod?,
    formatter: CurrencyFormatter,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.secondaryContainer,
        shape = MaterialTheme.shapes.medium,
    ) {
        Column(
            modifier = Modifier.padding(ZyntaSpacing.md),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(ZyntaSpacing.xs),
        ) {
            if (method != null) {
                Icon(
                    imageVector = method.icon,
                    contentDescription = null,
                    modifier = Modifier.size(40.dp),
                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                )
            }
            Text(
                text = "Charge ${formatter.format(orderTotal)}",
                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSecondaryContainer,
            )
            Text(
                text = "via ${method?.label ?: ""}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f),
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// OrderSummaryPane — Left pane (Expanded): read-only order details
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun OrderSummaryPane(
    cartItems: List<CartItem>,
    orderTotals: OrderTotals,
    selectedCustomer: String?,
    formatter: CurrencyFormatter,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        tonalElevation = 1.dp,
        shape = MaterialTheme.shapes.medium,
    ) {
        Column(modifier = Modifier.padding(ZyntaSpacing.md)) {
            Text("Order Summary", style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold))

            if (selectedCustomer != null) {
                Spacer(Modifier.height(ZyntaSpacing.xs))
                Text(
                    "Customer: $selectedCustomer",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Spacer(Modifier.height(ZyntaSpacing.sm))
            Divider()
            Spacer(Modifier.height(ZyntaSpacing.sm))

            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(ZyntaSpacing.xs),
            ) {
                items(cartItems, key = { it.productId }) { item ->
                    OrderSummaryItem(item = item, formatter = formatter)
                }
            }

            Divider(modifier = Modifier.padding(vertical = ZyntaSpacing.sm))
            OrderSummaryTotals(orderTotals = orderTotals, formatter = formatter)
        }
    }
}

@Composable
private fun OrderSummaryItem(item: CartItem, formatter: CurrencyFormatter) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(item.productName, style = MaterialTheme.typography.bodyMedium)
            Text(
                "×${"%.0f".format(item.quantity)} @ ${formatter.format(item.unitPrice)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            formatter.format(item.unitPrice * item.quantity),
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
        )
    }
}

@Composable
private fun OrderSummaryTotals(orderTotals: OrderTotals, formatter: CurrencyFormatter) {
    Column(verticalArrangement = Arrangement.spacedBy(ZyntaSpacing.xs)) {
        SummaryLine("Subtotal", formatter.format(orderTotals.subtotal))
        SummaryLine("Tax", formatter.format(orderTotals.taxAmount))
        if (orderTotals.discountAmount > 0) {
            SummaryLine("Discount", "− ${formatter.format(orderTotals.discountAmount)}", MaterialTheme.colorScheme.error)
        }
        Spacer(Modifier.height(ZyntaSpacing.xs))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("TOTAL", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
            Text(
                formatter.format(orderTotals.total),
                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold, fontSize = 22.sp),
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

@Composable
private fun SummaryLine(
    label: String,
    value: String,
    valueColor: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSurface,
) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium, color = valueColor)
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// CollapsibleOrderSummary — Compact layout
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun CollapsibleOrderSummary(
    cartItems: List<CartItem>,
    orderTotals: OrderTotals,
    isExpanded: Boolean,
    onToggle: () -> Unit,
    formatter: CurrencyFormatter,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(ZyntaSpacing.sm)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "Order (${cartItems.size} items)",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        formatter.format(orderTotals.total),
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(Modifier.width(ZyntaSpacing.xs))
                    IconButton(onClick = onToggle) {
                        Icon(
                            imageVector = if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                            contentDescription = if (isExpanded) "Collapse" else "Expand",
                        )
                    }
                }
            }
            if (isExpanded) {
                Spacer(Modifier.height(ZyntaSpacing.xs))
                cartItems.forEach { item -> OrderSummaryItem(item = item, formatter = formatter) }
                Divider(modifier = Modifier.padding(vertical = ZyntaSpacing.xs))
                OrderSummaryTotals(orderTotals = orderTotals, formatter = formatter)
            }
        }
    }
}
