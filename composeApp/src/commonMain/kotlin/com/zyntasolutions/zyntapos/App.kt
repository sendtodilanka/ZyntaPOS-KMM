package com.zyntasolutions.zyntapos

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import com.zyntasolutions.zyntapos.designsystem.components.ZyntaLoadingOverlay
import com.zyntasolutions.zyntapos.designsystem.theme.ThemeMode
import com.zyntasolutions.zyntapos.designsystem.theme.ZyntaTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import com.zyntasolutions.zyntapos.core.platform.AppInfoProvider
import com.zyntasolutions.zyntapos.debug.DebugScreen
import com.zyntasolutions.zyntapos.domain.repository.AuthRepository
import com.zyntasolutions.zyntapos.domain.repository.SettingsRepository
import com.zyntasolutions.zyntapos.feature.auth.screen.LoginScreen
import com.zyntasolutions.zyntapos.feature.auth.screen.PinLockScreen
import com.zyntasolutions.zyntapos.feature.auth.screen.SignUpScreen
import com.zyntasolutions.zyntapos.feature.dashboard.screen.DashboardScreen
import com.zyntasolutions.zyntapos.feature.onboarding.OnboardingViewModel
import com.zyntasolutions.zyntapos.feature.onboarding.screen.OnboardingScreen
import com.zyntasolutions.zyntapos.feature.inventory.CategoryListScreen
import com.zyntasolutions.zyntapos.feature.inventory.InventoryViewModel
import com.zyntasolutions.zyntapos.feature.inventory.ProductDetailScreen
import com.zyntasolutions.zyntapos.feature.inventory.ProductListScreen
import com.zyntasolutions.zyntapos.feature.inventory.SupplierListScreen
import com.zyntasolutions.zyntapos.feature.pos.OrderHistoryScreen
import com.zyntasolutions.zyntapos.feature.pos.PaymentScreen
import com.zyntasolutions.zyntapos.feature.pos.PosScreen
import com.zyntasolutions.zyntapos.feature.pos.PosViewModel
import com.zyntasolutions.zyntapos.feature.register.CloseRegisterScreen
import com.zyntasolutions.zyntapos.feature.register.OpenRegisterScreen
import com.zyntasolutions.zyntapos.feature.register.RegisterDashboardScreen
import com.zyntasolutions.zyntapos.feature.reports.SalesReportScreen
import com.zyntasolutions.zyntapos.feature.reports.StockReportScreen
import com.zyntasolutions.zyntapos.feature.settings.SettingsViewModel
import com.zyntasolutions.zyntapos.feature.settings.screen.AboutScreen
import com.zyntasolutions.zyntapos.feature.settings.screen.AppearanceSettingsScreen
import com.zyntasolutions.zyntapos.feature.settings.screen.BackupSettingsScreen
import com.zyntasolutions.zyntapos.feature.settings.screen.GeneralSettingsScreen
import com.zyntasolutions.zyntapos.feature.settings.screen.PosSettingsScreen
import com.zyntasolutions.zyntapos.feature.settings.screen.PrinterSettingsScreen
import com.zyntasolutions.zyntapos.feature.settings.screen.SettingsHomeScreen
import com.zyntasolutions.zyntapos.feature.settings.screen.SystemHealthScreen
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
 * - Android: [ZyntaApplication.onCreate] -> startKoin
 * - Desktop: `main()` first statement -> startKoin
 *
 * Wires [ZyntaNavGraph] with all feature screen composables. ViewModel
 * instances are resolved per-screen via [koinViewModel] to respect
 * NavBackStackEntry scoping.
 */
@Composable
fun App() {
    // ── Observe persisted theme mode so ZyntaTheme recomposes on change ─────
    val settingsRepository: SettingsRepository = koinInject()
    val themeModeRaw by settingsRepository.observe("appearance.theme_mode")
        .collectAsState(initial = null)
    val themeMode = when (themeModeRaw) {
        "LIGHT" -> ThemeMode.LIGHT
        "DARK" -> ThemeMode.DARK
        else -> ThemeMode.SYSTEM
    }

    val appInfoProvider: AppInfoProvider = koinInject()

    ZyntaTheme(themeMode = themeMode) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background,
        ) {
            val navController = rememberNavigationController()
            val authRepository: AuthRepository = koinInject()
            val currentUser by authRepository.getSession().collectAsState(initial = null)
            val isSessionActive = currentUser != null
            val userRole = currentUser?.role

            // ── First-run detection via SettingsRepository ────────────────────
            // "loading" sentinel = settings DB has not yet emitted the first value.
            // Once resolved: "true" means onboarding done, anything else = first run.
            val onboardingCompleted by settingsRepository
                .observe(OnboardingViewModel.ONBOARDING_COMPLETED_KEY)
                .collectAsState(initial = "loading")

            // Show a brief loading overlay while the settings DB resolves.
            if (onboardingCompleted == "loading") {
                ZyntaLoadingOverlay(isLoading = true)
                return@Surface
            }

            val isFirstRun = onboardingCompleted != "true"

            ZyntaNavGraph(
                navigationController = navController,
                isFirstRun = isFirstRun,
                isSessionActive = isSessionActive,
                userRole = userRole,
                screens = buildMainNavScreens(isDebug = appInfoProvider.isDebug),
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
                onboardingScreen = { onOnboardingComplete ->
                    OnboardingScreen(onOnboardingComplete = onOnboardingComplete)
                },
                pinLockScreen = { onUnlocked ->
                    PinLockScreen(
                        currentUser = currentUser,
                        onPinEntered = { onUnlocked() },
                        onDifferentUser = { },
                    )
                },
                debugScreen = if (appInfoProvider.isDebug) { onNavigateUp ->
                    DebugScreen(onNavigateUp = onNavigateUp)
                } else null,
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Screen wiring — one lambda per MainNavScreens slot
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun buildMainNavScreens(isDebug: Boolean) = MainNavScreens(

    // ── Dashboard ──────────────────────────────────────────────────────────
    // Provided by :composeApp:feature:dashboard (extracted from composeApp root)
    dashboard = { onNavigateToPos, onNavigateToRegister, onNavigateToReports, onNavigateToSettings ->
        DashboardScreen(
            onNavigateToPos = onNavigateToPos,
            onNavigateToRegister = onNavigateToRegister,
            onNavigateToReports = onNavigateToReports,
            onNavigateToSettings = onNavigateToSettings,
        )
    },

    // ── POS ─────────────────────────────────────────────────────────────────
    pos = { onNavigateToPayment ->
        PosScreen(onNavigateToPayment = onNavigateToPayment)
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
    settings = { onNavigateToRoute ->
        SettingsHomeScreen(
            isDebug = isDebug,
            onNavigate = { route -> onNavigateToRoute(route.name) },
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

    // ── Settings: General ───────────────────────────────────────────────────
    generalSettings = { onNavigateUp ->
        val vm: SettingsViewModel = koinViewModel()
        val state by vm.state.collectAsState()
        GeneralSettingsScreen(
            viewModel = vm,
            state = state.general,
            effects = vm.effects,
            onIntent = vm::dispatch,
            onBack = onNavigateUp,
        )
    },

    // ── Settings: Appearance ────────────────────────────────────────────────
    appearanceSettings = { onNavigateUp ->
        val vm: SettingsViewModel = koinViewModel()
        val state by vm.state.collectAsState()
        AppearanceSettingsScreen(
            state = state.appearance,
            effects = vm.effects,
            onIntent = vm::dispatch,
            onBack = onNavigateUp,
        )
    },

    // ── Settings: About ─────────────────────────────────────────────────────
    aboutSettings = { onNavigateUp ->
        AboutScreen(onBack = onNavigateUp)
    },

    // ── Settings: Backup ────────────────────────────────────────────────────
    backupSettings = { onNavigateUp ->
        val vm: SettingsViewModel = koinViewModel()
        val state by vm.state.collectAsState()
        BackupSettingsScreen(
            state = state.backup,
            effects = vm.effects,
            onIntent = vm::dispatch,
            onBack = onNavigateUp,
        )
    },

    // ── Settings: POS ───────────────────────────────────────────────────────
    posSettings = { onNavigateUp ->
        val vm: SettingsViewModel = koinViewModel()
        val state by vm.state.collectAsState()
        PosSettingsScreen(
            state = state.pos,
            effects = vm.effects,
            onIntent = vm::dispatch,
            onBack = onNavigateUp,
        )
    },

    // ── Settings: System Health ──────────────────────────────────────────────
    systemHealthSettings = { onNavigateUp ->
        SystemHealthScreen(onBack = onNavigateUp)
    },

    // ── Order History ───────────────────────────────────────────────────────
    orderHistory = { _, onNavigateUp ->
        OrderHistoryScreen(
            orders = emptyList(),
            onOrderTap = { },
            onReprintOrder = { },
        )
    },
)
