package com.zyntasolutions.zyntapos

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.zyntasolutions.zyntapos.domain.repository.AuthRepository
import com.zyntasolutions.zyntapos.feature.auth.screen.LoginScreen
import com.zyntasolutions.zyntapos.feature.auth.screen.PinLockScreen
import com.zyntasolutions.zyntapos.feature.auth.screen.SignUpScreen
import com.zyntasolutions.zyntapos.feature.inventory.CategoryListScreen
import com.zyntasolutions.zyntapos.feature.inventory.InventoryViewModel
import com.zyntasolutions.zyntapos.feature.inventory.ProductDetailScreen
import com.zyntasolutions.zyntapos.feature.inventory.ProductListScreen
import com.zyntasolutions.zyntapos.feature.inventory.SupplierListScreen
import com.zyntasolutions.zyntapos.feature.pos.PaymentScreen
import com.zyntasolutions.zyntapos.feature.pos.PosViewModel
import com.zyntasolutions.zyntapos.feature.register.CloseRegisterScreen
import com.zyntasolutions.zyntapos.feature.register.OpenRegisterScreen
import com.zyntasolutions.zyntapos.feature.register.RegisterDashboardScreen
import com.zyntasolutions.zyntapos.feature.reports.SalesReportScreen
import com.zyntasolutions.zyntapos.feature.reports.StockReportScreen
import com.zyntasolutions.zyntapos.feature.settings.SettingsViewModel
import com.zyntasolutions.zyntapos.feature.settings.screen.PrinterSettingsScreen
import com.zyntasolutions.zyntapos.feature.settings.screen.SettingsHomeScreen
import com.zyntasolutions.zyntapos.feature.settings.screen.SettingsRoute
import com.zyntasolutions.zyntapos.feature.settings.screen.TaxSettingsScreen
import com.zyntasolutions.zyntapos.feature.settings.screen.UserManagementScreen
import com.zyntasolutions.zyntapos.navigation.MainNavScreens
import com.zyntasolutions.zyntapos.navigation.ZyntaNavGraph
import com.zyntasolutions.zyntapos.navigation.rememberNavigationController
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel

/**
 * Root composable for ZyntaPOS.
 *
 * This is the top-level entry point shared across Android and Desktop targets.
 * Koin is guaranteed to be started before this composable runs:
 * - Android: [ZyntaApplication.onCreate] → startKoin
 * - Desktop: `main()` first statement → startKoin
 *
 * Wires [ZyntaNavGraph] with all feature screen composables. Screens that are
 * not yet implemented render a placeholder. ViewModel instances are resolved
 * per-screen via [koinViewModel] to respect NavBackStackEntry scoping.
 */
@Composable
fun App() {
    MaterialTheme {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background,
        ) {
            val navController = rememberNavigationController()
            val authRepository: AuthRepository = koinInject()
            val currentUser by authRepository.getSession().collectAsState(initial = null)
            val isSessionActive = currentUser != null
            val userRole = currentUser?.role

            ZyntaNavGraph(
                navigationController = navController,
                isSessionActive = isSessionActive,
                userRole = userRole,
                screens = buildMainNavScreens(),
                loginScreen = { onLoginSuccess, onNavigateToSignUp ->
                    LoginScreen(
                        onNavigateToDashboard = onLoginSuccess,
                        onNavigateToSignUp = onNavigateToSignUp,
                    )
                },
                signUpScreen = { onSignUpSuccess, onNavigateToLogin ->
                    SignUpScreen(
                        onSignUpSuccess = onSignUpSuccess,
                        onNavigateToLogin = onNavigateToLogin,
                    )
                },
                pinLockScreen = { onUnlocked ->
                    PinLockScreen(
                        currentUser = currentUser,
                        onPinEntered = { onUnlocked() },
                        onDifferentUser = { },
                    )
                },
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Screen wiring — one lambda per MainNavScreens slot
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun buildMainNavScreens() = MainNavScreens(

    // ── Dashboard (placeholder) ─────────────────────────────────────────────
    dashboard = { onNavigateToPos, onNavigateToRegister, onNavigateToReports ->
        PlaceholderScreen(
            title = "ZyntaPOS Dashboard",
            actions = listOf(
                "Open POS" to onNavigateToPos,
                "Register" to onNavigateToRegister,
                "Reports" to onNavigateToReports,
            ),
        )
    },

    // ── POS (placeholder) ───────────────────────────────────────────────────
    pos = { _ ->
        PlaceholderScreen(title = "Point of Sale — Coming Soon")
    },

    // ── Payment ─────────────────────────────────────────────────────────────
    payment = { _, onPaymentComplete, onCancel ->
        val vm: PosViewModel = koinViewModel()
        val state by vm.state.collectAsState()
        PaymentScreen(
            state = state,
            effects = vm.effects,
            onIntent = vm::dispatch,
            onDismiss = onCancel,
            onNavigateToReceipt = { onPaymentComplete() },
        )
    },

    // ── Inventory: Product List ─────────────────────────────────────────────
    productList = { onNavigateToDetail, _, _ ->
        val vm: InventoryViewModel = koinViewModel()
        val state by vm.state.collectAsState()
        ProductListScreen(
            state = state,
            onIntent = vm::dispatch,
            onNavigateToDetail = onNavigateToDetail,
        )
    },

    // ── Inventory: Product Detail ───────────────────────────────────────────
    productDetail = { _, onNavigateUp ->
        val vm: InventoryViewModel = koinViewModel()
        val state by vm.state.collectAsState()
        ProductDetailScreen(
            state = state,
            onIntent = vm::dispatch,
            onBack = onNavigateUp,
        )
    },

    // ── Inventory: Category List ────────────────────────────────────────────
    categoryList = { _ ->
        val vm: InventoryViewModel = koinViewModel()
        val state by vm.state.collectAsState()
        CategoryListScreen(
            categories = state.categories,
            isLoading = state.isLoading,
            onNavigateToDetail = { },
            onDeleteCategory = { },
        )
    },

    // ── Inventory: Supplier List ────────────────────────────────────────────
    supplierList = { _ ->
        val vm: InventoryViewModel = koinViewModel()
        val state by vm.state.collectAsState()
        SupplierListScreen(
            suppliers = state.suppliers,
            isLoading = state.isLoading,
            onNavigateToDetail = { },
        )
    },

    // ── Register: Dashboard ─────────────────────────────────────────────────
    registerDashboard = { _, _ ->
        RegisterDashboardScreen()
    },

    // ── Register: Open ──────────────────────────────────────────────────────
    openRegister = { onComplete ->
        OpenRegisterScreen(onOpened = onComplete)
    },

    // ── Register: Close ─────────────────────────────────────────────────────
    closeRegister = { onComplete ->
        CloseRegisterScreen(onClosed = { onComplete() })
    },

    // ── Reports ─────────────────────────────────────────────────────────────
    salesReport = { SalesReportScreen(onNavigateUp = { }) },
    stockReport = { StockReportScreen(onNavigateUp = { }) },

    // ── Settings: Home ──────────────────────────────────────────────────────
    settings = { onPrinter, onTax, onUsers ->
        SettingsHomeScreen(
            onNavigate = { route ->
                when (route) {
                    SettingsRoute.PRINTER -> onPrinter()
                    SettingsRoute.TAX -> onTax()
                    SettingsRoute.USERS -> onUsers()
                    else -> { }
                }
            },
            onBack = { },
        )
    },

    // ── Settings: Printer ───────────────────────────────────────────────────
    printerSettings = { onNavigateUp ->
        val vm: SettingsViewModel = koinViewModel()
        val state by vm.state.collectAsState()
        PrinterSettingsScreen(
            state = state.printer,
            effects = vm.effects,
            onIntent = vm::dispatch,
            onBack = onNavigateUp,
        )
    },

    // ── Settings: Tax ───────────────────────────────────────────────────────
    taxSettings = { onNavigateUp ->
        val vm: SettingsViewModel = koinViewModel()
        val state by vm.state.collectAsState()
        TaxSettingsScreen(
            state = state.tax,
            effects = vm.effects,
            onIntent = vm::dispatch,
            onBack = onNavigateUp,
        )
    },

    // ── Settings: User Management ───────────────────────────────────────────
    userManagement = { onNavigateUp ->
        val vm: SettingsViewModel = koinViewModel()
        val state by vm.state.collectAsState()
        UserManagementScreen(
            state = state.users,
            effects = vm.effects,
            onIntent = vm::dispatch,
            onBack = onNavigateUp,
        )
    },

    // ── Order History (placeholder) ─────────────────────────────────────────
    orderHistory = { orderId, _ ->
        PlaceholderScreen(title = "Order: $orderId")
    },
)

/** Placeholder composable for screens not yet implemented. */
@Composable
private fun PlaceholderScreen(
    title: String,
    actions: List<Pair<String, () -> Unit>> = emptyList(),
) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(title, style = MaterialTheme.typography.headlineMedium)
            if (actions.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                actions.forEach { (label, onClick) ->
                    TextButton(onClick = onClick) { Text(label) }
                }
            }
        }
    }
}
