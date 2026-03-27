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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AssignmentReturn
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.Store
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.zyntasolutions.zyntapos.core.i18n.StringResource
import com.zyntasolutions.zyntapos.designsystem.components.LocalStrings
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
    val s = LocalStrings.current
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
    LaunchedEffect(viewModel.effects) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is PosEffect.OpenPaymentSheet   -> isPaymentScreenVisible = true
                is PosEffect.OpenCustomerPicker -> isCustomerPickerVisible = true
                is PosEffect.NavigateToPayment  -> onNavigateToPayment(effect.orderId)
                is PosEffect.ShowReceiptScreen  -> { /* handled inside PaymentScreen overlay */ }
                is PosEffect.ShowError          -> snackbarHostState.showSnackbar(effect.msg)
                is PosEffect.BarcodeNotFound    -> snackbarHostState.showSnackbar(s[StringResource.POS_BARCODE_NOT_FOUND_FORMAT, effect.barcode])
                is PosEffect.PrintReceipt       -> { /* handled by print service */ }
                is PosEffect.OpenCashDrawer     -> { /* handled by HAL */ }
                is PosEffect.ShowEmailDialog    -> { /* email dialog handled by state.emailDialogOpen */ }
                is PosEffect.ReceiptEmailSent   -> snackbarHostState.showSnackbar(s[StringResource.POS_RECEIPT_EMAILED])
                is PosEffect.A4InvoicePrinted   -> snackbarHostState.showSnackbar(s[StringResource.POS_A4_INVOICE_SENT])
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
                            Icon(Icons.Default.ShoppingCart, contentDescription = s[StringResource.POS_CART])
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
                        // Store + Cashier context bar — G3 Phase 2 store switcher + G21 Phase 1.5
                        if (state.storeName.isNotBlank() || state.cashierName.isNotBlank()) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                if (state.storeName.isNotBlank()) {
                                    Icon(
                                        Icons.Default.Store,
                                        contentDescription = s[StringResource.POS_ACTIVE_STORE],
                                        modifier = Modifier.size(16.dp),
                                        tint = MaterialTheme.colorScheme.primary,
                                    )
                                    Spacer(Modifier.width(4.dp))
                                    Text(
                                        text = state.storeName,
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.primary,
                                    )
                                }
                                if (state.storeName.isNotBlank() && state.cashierName.isNotBlank()) {
                                    Text(
                                        text = s[StringResource.COMMON_SEPARATOR_MIDDLE_DOT],
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                if (state.cashierName.isNotBlank()) {
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
                                Spacer(Modifier.weight(1f))
                                IconButton(onClick = { viewModel.dispatch(PosIntent.ShowReturnLookupDialog) }) {
                                    Icon(
                                        Icons.Default.AssignmentReturn,
                                        contentDescription = s[StringResource.POS_PROCESS_RETURN],
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
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
                        // Store + Cashier context bar — G3 Phase 2 store switcher + G21 Phase 1.5
                        if (state.storeName.isNotBlank() || state.cashierName.isNotBlank()) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                if (state.storeName.isNotBlank()) {
                                    Icon(
                                        Icons.Default.Store,
                                        contentDescription = s[StringResource.POS_ACTIVE_STORE],
                                        modifier = Modifier.size(16.dp),
                                        tint = MaterialTheme.colorScheme.primary,
                                    )
                                    Spacer(Modifier.width(4.dp))
                                    Text(
                                        text = state.storeName,
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.primary,
                                    )
                                }
                                if (state.storeName.isNotBlank() && state.cashierName.isNotBlank()) {
                                    Text(
                                        text = s[StringResource.COMMON_SEPARATOR_MIDDLE_DOT],
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                if (state.cashierName.isNotBlank()) {
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
                                Spacer(Modifier.weight(1f))
                                IconButton(onClick = { viewModel.dispatch(PosIntent.ShowReturnLookupDialog) }) {
                                    Icon(
                                        Icons.Default.AssignmentReturn,
                                        contentDescription = s[StringResource.POS_PROCESS_RETURN],
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
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

        // ── Return lookup dialog ──────────────────────────────────────────────
        if (state.showReturnLookupDialog) {
            ReturnLookupDialog(
                query = state.returnLookupQuery,
                isLoading = state.isReturnLookupLoading,
                error = state.returnLookupError,
                onQueryChange = { viewModel.dispatch(PosIntent.SetReturnLookupQuery(it)) },
                onLookup = { viewModel.dispatch(PosIntent.LookupOrderForReturn) },
                onDismiss = { viewModel.dispatch(PosIntent.DismissReturnLookupDialog) },
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
    val s = LocalStrings.current
    var emailAddress by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = { if (!isSending) onDismiss() },
        title = { Text(s[StringResource.POS_EMAIL_RECEIPT_TITLE]) },
        text = {
            OutlinedTextField(
                value = emailAddress,
                onValueChange = { emailAddress = it },
                label = { Text(s[StringResource.POS_EMAIL_ADDRESS_LABEL]) },
                singleLine = true,
                enabled = !isSending,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            Button(
                onClick = { onSend(orderId, emailAddress) },
                enabled = !isSending && emailAddress.contains("@"),
            ) { Text(if (isSending) s[StringResource.POS_SENDING] else s[StringResource.POS_SEND]) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isSending) { Text(s[StringResource.COMMON_CANCEL]) }
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
    val s = LocalStrings.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(s[StringResource.POS_SELECT_CUSTOMER]) },
        text = {
            Column {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = onSearchChange,
                    label = { Text(s[StringResource.POS_SEARCH_CUSTOMER]) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                LazyColumn {
                    if (customers.isEmpty()) {
                        item {
                            Text(
                                text = s[StringResource.POS_NO_CUSTOMERS_FOUND],
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.padding(vertical = 16.dp),
                            )
                        }
                    } else {
                        items(customers, key = { it.id }) { customer ->
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
            TextButton(onClick = onDismiss) { Text(s[StringResource.COMMON_CANCEL]) }
        },
    )
}

@Composable
private fun ReturnLookupDialog(
    query: String,
    isLoading: Boolean,
    error: String?,
    onQueryChange: (String) -> Unit,
    onLookup: () -> Unit,
    onDismiss: () -> Unit,
) {
    val s = LocalStrings.current
    AlertDialog(
        onDismissRequest = { if (!isLoading) onDismiss() },
        title = { Text(s[StringResource.POS_PROCESS_RETURN]) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    s[StringResource.POS_RETURN_LOOKUP_DESCRIPTION],
                    style = MaterialTheme.typography.bodyMedium,
                )
                OutlinedTextField(
                    value = query,
                    onValueChange = onQueryChange,
                    label = { Text(s[StringResource.POS_ORDER_ID_LABEL]) },
                    singleLine = true,
                    enabled = !isLoading,
                    isError = error != null,
                    supportingText = error?.let { { Text(it, color = MaterialTheme.colorScheme.error) } },
                    modifier = Modifier.fillMaxWidth(),
                )
                if (isLoading) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp))
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onLookup,
                enabled = !isLoading && query.isNotBlank(),
            ) { Text(s[StringResource.POS_LOOK_UP]) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isLoading) { Text(s[StringResource.COMMON_CANCEL]) }
        },
    )
}
