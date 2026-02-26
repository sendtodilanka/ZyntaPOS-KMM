package com.zyntasolutions.zyntapos.feature.pos

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material.icons.Icons
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
    viewModel: PosViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val windowSize = currentWindowSize()
    var isCartSheetVisible by remember { mutableStateOf(false) }
    var isPaymentScreenVisible by remember { mutableStateOf(false) }

    // Trigger initial data load
    LaunchedEffect(Unit) {
        viewModel.dispatch(PosIntent.LoadProducts)
    }

    // Collect one-shot effects
    LaunchedEffect(Unit) {
        viewModel.effects.collectLatest { effect ->
            when (effect) {
                is PosEffect.OpenPaymentSheet  -> isPaymentScreenVisible = true
                is PosEffect.NavigateToPayment -> onNavigateToPayment(effect.orderId)
                is PosEffect.ShowReceiptScreen -> { /* handled inside PaymentScreen overlay */ }
                is PosEffect.ShowError -> snackbarHostState.showSnackbar(effect.msg)
                is PosEffect.BarcodeNotFound -> snackbarHostState.showSnackbar("Barcode not found: ${effect.barcode}")
                is PosEffect.PrintReceipt -> { /* handled by print service */ }
                is PosEffect.OpenCashDrawer -> { /* handled by HAL */ }
            }
        }
    }

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
                    )
                }
            } else {
                // ── Phone/Tablet portrait: full grid + cart bottom sheet ──────
                Box(modifier = Modifier.fillMaxSize().padding(padding)) {
                    Column(modifier = Modifier.fillMaxSize()) {
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
    }
}
