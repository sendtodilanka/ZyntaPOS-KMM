package com.zyntasolutions.zyntapos.feature.pos

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.zyntasolutions.zyntapos.designsystem.components.ZyntaSnackbarHost
import com.zyntasolutions.zyntapos.designsystem.util.WindowSize
import com.zyntasolutions.zyntapos.designsystem.util.currentWindowSize
import com.zyntasolutions.zyntapos.domain.model.Customer
import kotlinx.coroutines.flow.collectLatest
import org.koin.compose.viewmodel.koinViewModel

/**
 * Main POS screen composable — composes SearchBar, CategoryFilterRow,
 * ProductGridSection, and CartPanel into an adaptive layout.
 *
 * - **EXPANDED** (desktop/tablet landscape): Side-by-side product grid + cart panel
 * - **COMPACT/MEDIUM** (phone/tablet portrait): Full product grid with cart as bottom sheet
 */
@Composable
fun PosScreen(
    onNavigateToPayment: (orderId: String) -> Unit,
    onNavigateToRefund: (orderId: String) -> Unit = {},
    viewModel: PosViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val windowSize = currentWindowSize()
    var isCartSheetVisible by remember { mutableStateOf(false) }
    var isPaymentScreenVisible by remember { mutableStateOf(false) }
    var isCustomerPickerVisible by remember { mutableStateOf(false) }
    // Play In-App Review trigger (TODO-008 ASO) — toggled by PosEffect.RequestAppReview
    var reviewTrigger by remember { mutableStateOf(false) }

    // Trigger initial data load
    LaunchedEffect(Unit) {
        viewModel.dispatch(PosIntent.LoadProducts)
    }

    // Collect one-shot effects
    LaunchedEffect(Unit) {
        viewModel.effects.collectLatest { effect ->
            when (effect) {
                is PosEffect.OpenPaymentSheet   -> isPaymentScreenVisible = true
                is PosEffect.OpenCustomerPicker -> isCustomerPickerVisible = true
                is PosEffect.NavigateToPayment  -> onNavigateToPayment(effect.orderId)
                is PosEffect.ShowReceiptScreen  -> { /* handled inside PaymentScreen overlay */ }
                is PosEffect.ShowError          -> snackbarHostState.showSnackbar(effect.msg)
                is PosEffect.BarcodeNotFound    -> snackbarHostState.showSnackbar("Barcode not found: ${effect.barcode}")
                is PosEffect.PrintReceipt       -> { /* handled by print service */ }
                is PosEffect.OpenCashDrawer     -> { /* handled by HAL */ }
                is PosEffect.ShowEmailDialog    -> { /* email dialog handled by state.emailDialogOpen */ }
                is PosEffect.ReceiptEmailSent   -> snackbarHostState.showSnackbar("Receipt emailed successfully")
                is PosEffect.A4InvoicePrinted   -> snackbarHostState.showSnackbar("A4 invoice sent to printer")
                is PosEffect.NavigateToRefund   -> onNavigateToRefund(effect.orderId)
                is PosEffect.RequestAppReview   -> reviewTrigger = true
            }
        }
    }

    // Play In-App Review composable — fires the native dialog on Android (no-op on JVM)
    PosAppReviewEffect(trigger = reviewTrigger) { reviewTrigger = false }

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            snackbarHost = { ZyntaSnackbarHost(snackbarHostState) },
            floatingActionButton = {
                // Show cart FAB on compact/medium when cart has items
                if (windowSize != WindowSize.EXPANDED && state.cartItems.isNotEmpty()) {
                    FloatingActionButton(
                        onClick = { isCartSheetVisible = true },
                        containerColor = MaterialTheme.colorScheme.primary,
                    ) {
                        BadgedBox(
                            badge = {
                                Badge { Text("${state.cartItems.size}") }
                            },
                        ) {
                            Icon(Icons.Default.ShoppingCart, contentDescription = "Cart")
                        }
                    }
                }
            },
        ) { padding ->
            if (windowSize == WindowSize.EXPANDED) {
                // ── Desktop/Tablet landscape: side-by-side layout ────────────
                Row(modifier = Modifier.fillMaxSize().padding(padding)) {
                    // Left: product catalogue
                    Column(modifier = Modifier.weight(1f)) {
                        // Printer hardware alert banner (paper-out / paper-low / cover-open)
                        PrinterStatusAlertBanner()
                        // Cashier name badge — G21 Phase 1.5
                        if (state.cashierName.isNotBlank()) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(
                                    Icons.Default.Person,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Spacer(Modifier.width(4.dp))
                                Text(
                                    text = state.cashierName,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                        PosSearchBar(
                            query = state.searchQuery,
                            scannerActive = state.scannerActive,
                            onQueryChange = { viewModel.dispatch(PosIntent.SearchQueryChanged(it)) },
                            onFocusChange = { viewModel.dispatch(PosIntent.SearchFocusChanged(it)) },
                            onScanToggle = { viewModel.dispatch(PosIntent.SetScannerActive(!state.scannerActive)) },
                        )
                        CategoryFilterRow(
                            categories = state.categories,
                            selectedCategoryId = state.selectedCategoryId,
                            onSelectCategory = { viewModel.dispatch(PosIntent.SelectCategory(it)) },
                        )
                        ProductGridSection(
                            products = state.products,
                            onAddToCart = { viewModel.dispatch(PosIntent.AddToCart(it)) },
                            modifier = Modifier.weight(1f),
                        )
                    }
                    // Right: permanent cart panel
                    CartPanel(
                        cartItems = state.cartItems,
                        orderTotals = state.orderTotals,
                        selectedCustomer = state.selectedCustomer,
                        onIntent = viewModel::dispatch,
                        isSheetVisible = true,
                        onDismissSheet = { },
                        loyaltyPointsBalance = state.loyaltyPointsBalance,
                        loyaltyPointsToRedeem = state.loyaltyPointsToRedeem,
                        loyaltyDiscount = state.loyaltyDiscount,
                    )
                }
            } else {
                // ── Phone/Tablet portrait: full grid + cart bottom sheet ──────
                Box(modifier = Modifier.fillMaxSize().padding(padding)) {
                    Column(modifier = Modifier.fillMaxSize()) {
                        // Printer hardware alert banner (paper-out / paper-low / cover-open)
                        PrinterStatusAlertBanner()
                        // Cashier name badge — G21 Phase 1.5
                        if (state.cashierName.isNotBlank()) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(
                                    Icons.Default.Person,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Spacer(Modifier.width(4.dp))
                                Text(
                                    text = state.cashierName,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                        PosSearchBar(
                            query = state.searchQuery,
                            scannerActive = state.scannerActive,
                            onQueryChange = { viewModel.dispatch(PosIntent.SearchQueryChanged(it)) },
                            onFocusChange = { viewModel.dispatch(PosIntent.SearchFocusChanged(it)) },
                            onScanToggle = { viewModel.dispatch(PosIntent.SetScannerActive(!state.scannerActive)) },
                        )
                        CategoryFilterRow(
                            categories = state.categories,
                            selectedCategoryId = state.selectedCategoryId,
                            onSelectCategory = { viewModel.dispatch(PosIntent.SelectCategory(it)) },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        ProductGridSection(
                            products = state.products,
                            onAddToCart = { viewModel.dispatch(PosIntent.AddToCart(it)) },
                            modifier = Modifier.weight(1f),
                        )
                    }

                    // Cart as bottom sheet on compact
                    CartPanel(
                        cartItems = state.cartItems,
                        orderTotals = state.orderTotals,
                        selectedCustomer = state.selectedCustomer,
                        onIntent = viewModel::dispatch,
                        isSheetVisible = isCartSheetVisible,
                        onDismissSheet = { isCartSheetVisible = false },
                        loyaltyPointsBalance = state.loyaltyPointsBalance,
                        loyaltyPointsToRedeem = state.loyaltyPointsToRedeem,
                        loyaltyDiscount = state.loyaltyDiscount,
                    )
                }
            }
        }

        // ── Payment screen overlay ────────────────────────────────────────────
        // Rendered on top of the POS scaffold when the cashier taps PAY.
        // PaymentScreen is self-contained: it collects effects for ShowReceiptScreen
        // and calls onNavigateToReceipt when the success overlay auto-dismisses.
        if (isPaymentScreenVisible) {
            PaymentScreen(
                state = state,
                effects = viewModel.effects,
                onIntent = viewModel::dispatch,
                onDismiss = { isPaymentScreenVisible = false },
                onNavigateToReceipt = { orderId ->
                    isPaymentScreenVisible = false
                    onNavigateToPayment(orderId)
                },
                modifier = Modifier.fillMaxSize(),
            )
        }

        // ── Customer picker dialog ────────────────────────────────────────────
        if (isCustomerPickerVisible) {
            CustomerPickerDialog(
                customers = state.customerPickerResults,
                searchQuery = state.customerSearchQuery,
                onSearchChange = { viewModel.dispatch(PosIntent.SearchCustomers(it)) },
                onSelect = { customer ->
                    viewModel.dispatch(PosIntent.SelectCustomer(customer))
                    isCustomerPickerVisible = false
                },
                onDismiss = { isCustomerPickerVisible = false },
            )
        }

        // ── Email receipt dialog ──────────────────────────────────────────────
        if (state.emailDialogOpen) {
            EmailReceiptDialog(
                orderId = state.emailDialogOrderId ?: "",
                isSending = state.isEmailingReceipt,
                onSend = { orderId, email ->
                    viewModel.dispatch(PosIntent.EmailReceipt(orderId, email))
                },
                onDismiss = { viewModel.dispatch(PosIntent.DismissEmailDialog) },
            )
        }
    }
}

@Composable
private fun EmailReceiptDialog(
    orderId: String,
    isSending: Boolean,
    onSend: (orderId: String, email: String) -> Unit,
    onDismiss: () -> Unit,
) {
    var emailAddress by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = { if (!isSending) onDismiss() },
        title = { Text("Email Receipt") },
        text = {
            OutlinedTextField(
                value = emailAddress,
                onValueChange = { emailAddress = it },
                label = { Text("Email address") },
                singleLine = true,
                enabled = !isSending,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            Button(
                onClick = { onSend(orderId, emailAddress) },
                enabled = !isSending && emailAddress.contains("@"),
            ) { Text(if (isSending) "Sending…" else "Send") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isSending) { Text("Cancel") }
        },
    )
}

@Composable
private fun CustomerPickerDialog(
    customers: List<Customer>,
    searchQuery: String,
    onSearchChange: (String) -> Unit,
    onSelect: (Customer) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Select Customer") },
        text = {
            Column {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = onSearchChange,
                    label = { Text("Search by name or phone") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                LazyColumn {
                    if (customers.isEmpty()) {
                        item {
                            Text(
                                text = "No customers found",
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.padding(vertical = 16.dp),
                            )
                        }
                    } else {
                        items(customers) { customer ->
                            ListItem(
                                headlineContent = { Text(customer.name) },
                                supportingContent = { Text(customer.phone) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onSelect(customer) },
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}
