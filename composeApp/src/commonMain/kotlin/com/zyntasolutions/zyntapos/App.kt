package com.zyntasolutions.zyntapos

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import com.zyntasolutions.zyntapos.core.utils.AppTimezone
import com.zyntasolutions.zyntapos.core.utils.CurrencyFormatter
import com.zyntasolutions.zyntapos.debug.DebugViewModel
import com.zyntasolutions.zyntapos.core.i18n.LocalizationManager
import com.zyntasolutions.zyntapos.core.i18n.StringResource
import com.zyntasolutions.zyntapos.designsystem.components.LocalStrings
import com.zyntasolutions.zyntapos.designsystem.components.LocalSyncDisplayStatus
import com.zyntasolutions.zyntapos.designsystem.components.LocalSyncPendingCount
import com.zyntasolutions.zyntapos.designsystem.components.StringResolver
import com.zyntasolutions.zyntapos.designsystem.components.SyncDisplayStatus
import com.zyntasolutions.zyntapos.domain.port.SyncStatusPort
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
import com.zyntasolutions.zyntapos.feature.auth.AuthViewModel
import com.zyntasolutions.zyntapos.feature.auth.mvi.AuthEffect
import com.zyntasolutions.zyntapos.feature.auth.mvi.AuthIntent
import com.zyntasolutions.zyntapos.feature.auth.screen.PinLockScreen
import com.zyntasolutions.zyntapos.feature.dashboard.screen.DashboardScreen
import com.zyntasolutions.zyntapos.feature.onboarding.OnboardingViewModel
import com.zyntasolutions.zyntapos.feature.onboarding.screen.OnboardingScreen
import com.zyntasolutions.zyntapos.feature.inventory.CategoryDetailScreen
import com.zyntasolutions.zyntapos.feature.inventory.CategoryListScreen
import com.zyntasolutions.zyntapos.feature.inventory.InventoryIntent
import com.zyntasolutions.zyntapos.feature.inventory.InventoryViewModel
import com.zyntasolutions.zyntapos.feature.inventory.ProductDetailScreen
import com.zyntasolutions.zyntapos.feature.inventory.ProductListScreen
import com.zyntasolutions.zyntapos.feature.inventory.SupplierDetailScreen
import com.zyntasolutions.zyntapos.feature.inventory.SupplierListScreen
import com.zyntasolutions.zyntapos.feature.inventory.label.BarcodeLabelPrintScreen
import com.zyntasolutions.zyntapos.feature.inventory.label.BarcodeLabelPrintViewModel
import com.zyntasolutions.zyntapos.feature.inventory.label.BarcodeLabelPrintIntent
import com.zyntasolutions.zyntapos.feature.pos.OrderHistoryScreen
import com.zyntasolutions.zyntapos.feature.pos.PaymentScreen
import com.zyntasolutions.zyntapos.feature.pos.PosScreen
import com.zyntasolutions.zyntapos.feature.pos.PosViewModel
import com.zyntasolutions.zyntapos.feature.pos.fulfillment.FulfillmentQueueScreen
import com.zyntasolutions.zyntapos.feature.admin.AdminScreen
import com.zyntasolutions.zyntapos.feature.admin.AdminViewModel
import com.zyntasolutions.zyntapos.feature.admin.notification.NotificationInboxScreen
import com.zyntasolutions.zyntapos.feature.staff.EmployeeRoamingIntent
import com.zyntasolutions.zyntapos.feature.staff.EmployeeRoamingViewModel
import com.zyntasolutions.zyntapos.feature.staff.EmployeeStoreAssignmentScreen
import com.zyntasolutions.zyntapos.feature.staff.StaffEffect
import com.zyntasolutions.zyntapos.feature.staff.StaffScreen
import com.zyntasolutions.zyntapos.feature.staff.StaffViewModel
import com.zyntasolutions.zyntapos.feature.coupons.CouponDetailScreen
import com.zyntasolutions.zyntapos.feature.coupons.CouponListScreen
import com.zyntasolutions.zyntapos.feature.customers.CustomerDetailScreen
import com.zyntasolutions.zyntapos.feature.customers.CustomerGroupScreen
import com.zyntasolutions.zyntapos.feature.customers.CustomerListScreen
import com.zyntasolutions.zyntapos.feature.customers.CustomerViewModel
import com.zyntasolutions.zyntapos.feature.customers.CustomerWalletScreen
import com.zyntasolutions.zyntapos.feature.expenses.ExpenseCategoryListScreen
import com.zyntasolutions.zyntapos.feature.expenses.ExpenseDetailScreen
import com.zyntasolutions.zyntapos.feature.expenses.ExpenseListScreen
import com.zyntasolutions.zyntapos.feature.multistore.NewStockTransferScreen
import com.zyntasolutions.zyntapos.feature.multistore.StockTransferListScreen
import com.zyntasolutions.zyntapos.feature.multistore.PickListScreen
import com.zyntasolutions.zyntapos.feature.multistore.StoreTransferDashboardScreen
import com.zyntasolutions.zyntapos.feature.multistore.WarehouseDetailScreen
import com.zyntasolutions.zyntapos.feature.multistore.WarehouseListScreen
import com.zyntasolutions.zyntapos.feature.multistore.WarehouseRackListScreen
import com.zyntasolutions.zyntapos.feature.multistore.WarehouseRackDetailScreen
import com.zyntasolutions.zyntapos.feature.multistore.WarehouseViewModel
import com.zyntasolutions.zyntapos.feature.multistore.dashboard.MultiStoreDashboardScreen
import com.zyntasolutions.zyntapos.feature.multistore.dashboard.MultiStoreDashboardViewModel
import com.zyntasolutions.zyntapos.feature.accounting.EInvoiceListScreen
import com.zyntasolutions.zyntapos.feature.accounting.EInvoiceDetailScreen
import com.zyntasolutions.zyntapos.feature.accounting.EInvoiceViewModel
import com.zyntasolutions.zyntapos.feature.accounting.AccountingLedgerScreen
import com.zyntasolutions.zyntapos.feature.accounting.AccountDetailScreen
import com.zyntasolutions.zyntapos.feature.accounting.AccountingViewModel
import com.zyntasolutions.zyntapos.feature.accounting.ChartOfAccountsScreen
import com.zyntasolutions.zyntapos.feature.accounting.JournalEntryListScreen
import com.zyntasolutions.zyntapos.feature.accounting.JournalEntryDetailScreen
import com.zyntasolutions.zyntapos.feature.accounting.JournalEntryDetailViewModel
import com.zyntasolutions.zyntapos.feature.accounting.FinancialStatementsScreen
import com.zyntasolutions.zyntapos.feature.accounting.FinancialStatementTab
import com.zyntasolutions.zyntapos.feature.accounting.GeneralLedgerScreen
import com.zyntasolutions.zyntapos.feature.register.CloseRegisterScreen
import com.zyntasolutions.zyntapos.feature.register.OpenRegisterScreen
import com.zyntasolutions.zyntapos.feature.register.RegisterDashboardScreen
import com.zyntasolutions.zyntapos.feature.reports.CustomerReportScreen
import com.zyntasolutions.zyntapos.feature.reports.ExpenseReportScreen
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
import com.zyntasolutions.zyntapos.feature.settings.edition.EditionManagementScreen
import com.zyntasolutions.zyntapos.feature.settings.screen.RbacManagementScreen
import com.zyntasolutions.zyntapos.feature.settings.screen.SecuritySettingsScreen
import com.zyntasolutions.zyntapos.feature.settings.screen.SystemHealthScreen
import com.zyntasolutions.zyntapos.feature.settings.screen.TaxSettingsScreen
import com.zyntasolutions.zyntapos.feature.settings.screen.RegionalTaxOverrideScreen
import com.zyntasolutions.zyntapos.feature.settings.screen.RegionalTaxOverrideViewModel
import com.zyntasolutions.zyntapos.feature.settings.screen.UserManagementScreen
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.dp
import com.zyntasolutions.zyntapos.feature.customers.CustomerEffect
import com.zyntasolutions.zyntapos.feature.reports.ReportExporter
import kotlinx.coroutines.launch
import com.zyntasolutions.zyntapos.feature.diagnostic.DiagnosticConsentScreen
import com.zyntasolutions.zyntapos.feature.diagnostic.DiagnosticEffect
import com.zyntasolutions.zyntapos.feature.diagnostic.DiagnosticIntent
import com.zyntasolutions.zyntapos.feature.diagnostic.DiagnosticViewModel
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
    val userThemeMode = when (themeModeRaw) {
        "LIGHT" -> ThemeMode.LIGHT
        "DARK" -> ThemeMode.DARK
        else -> ThemeMode.SYSTEM
    }

    // ── C6.3: Load store timezone on startup so all display calls use it ─────
    val persistedTimezone by settingsRepository.observe("general.timezone")
        .collectAsState(initial = null)
    LaunchedEffect(persistedTimezone) {
        val tz = persistedTimezone
        if (!tz.isNullOrBlank()) {
            AppTimezone.set(tz)
        }
    }

    // ── C2.2: Load store currency so all currency display uses it ────────────
    val currencyFormatter: CurrencyFormatter = koinInject()
    val persistedCurrency by settingsRepository.observe("general.currency")
        .collectAsState(initial = null)
    LaunchedEffect(persistedCurrency) {
        val currency = persistedCurrency
        if (!currency.isNullOrBlank()) {
            currencyFormatter.defaultCurrency = currency
        }
    }

    val appInfoProvider: AppInfoProvider = koinInject()

    // ── C6.2: Observe sync engine state for the offline/sync indicator ────────
    val syncStatus: SyncStatusPort = koinInject()
    val isNetworkConnected by syncStatus.isNetworkConnected.collectAsState()
    val isSyncing by syncStatus.isSyncing.collectAsState()
    val lastSyncFailed by syncStatus.lastSyncFailed.collectAsState()

    val syncPendingCount by syncStatus.pendingCount.collectAsState()

    val syncDisplayStatus = when {
        !isNetworkConnected -> SyncDisplayStatus.OFFLINE
        isSyncing -> SyncDisplayStatus.SYNCING
        lastSyncFailed -> SyncDisplayStatus.ERROR
        else -> SyncDisplayStatus.SYNCED
    }

    // ── Debug overrides (active only when debug console is loaded) ────────────
    // These keys are only ever written by DebugViewModel; they emit null in
    // production builds where debugModule is never loaded, so the effective
    // theme and font scale fall through to the user-configured values.
    val debugThemeRaw by settingsRepository.observe(DebugViewModel.KEY_DEBUG_THEME)
        .collectAsState(initial = null)
    val debugFontScaleRaw by settingsRepository.observe(DebugViewModel.KEY_DEBUG_FONT_SCALE)
        .collectAsState(initial = null)

    val effectiveThemeMode = when (debugThemeRaw) {
        "LIGHT"  -> ThemeMode.LIGHT
        "DARK"   -> ThemeMode.DARK
        "SYSTEM" -> ThemeMode.SYSTEM
        else     -> userThemeMode   // null (no override) → honour user preference
    }
    val debugFontScale = debugFontScaleRaw?.toFloatOrNull()?.coerceIn(0.75f, 1.50f) ?: 1.0f

    // ── Localization ──────────────────────────────────────────────────────────
    val localizationManager: LocalizationManager = koinInject()
    val stringResolver = remember(localizationManager) { StringResolver(localizationManager) }

    ZyntaTheme(themeMode = effectiveThemeMode) {
        val baseDensity = LocalDensity.current
        CompositionLocalProvider(
            LocalDensity provides Density(
                density   = baseDensity.density,
                fontScale = baseDensity.fontScale * debugFontScale,
            ),
            LocalSyncDisplayStatus provides syncDisplayStatus,
            LocalSyncPendingCount provides syncPendingCount,
            LocalStrings provides stringResolver,
        ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background,
        ) {
            val s = LocalStrings.current
            val navController = rememberNavigationController()
            val authRepository: AuthRepository = koinInject()
            val currentUser by authRepository.getSession().collectAsState(initial = null)
            val isSessionActive = currentUser != null
            val userRole = currentUser?.role

            // ── Drawer footer user info ───────────────────────────────────────
            val drawerUserName = currentUser?.name
            val drawerUserInitials = currentUser?.name?.split(" ")
                ?.filter { it.isNotEmpty() }
                ?.take(2)
                ?.joinToString("") { it.first().uppercaseChar().toString() }
                ?.ifEmpty { currentUser?.name?.take(1)?.uppercase() }
            val drawerUserRole = currentUser?.role?.name
                ?.lowercase()
                ?.split("_")
                ?.joinToString(" ") { word -> word.replaceFirstChar { it.uppercase() } }

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
                currentUserName = drawerUserName,
                currentUserInitials = drawerUserInitials,
                currentUserRole = drawerUserRole,
                screens = buildMainNavScreens(isDebug = appInfoProvider.isDebug),
                loginScreen = { onLoginSuccess ->
                    LoginScreen(
                        onNavigateToDashboard = onLoginSuccess,
                    )
                },
                onboardingScreen = { onOnboardingComplete ->
                    OnboardingScreen(onOnboardingComplete = onOnboardingComplete)
                },
                pinLockScreen = { onUnlocked ->
                    val authVm = koinViewModel<AuthViewModel>()
                    val authState by authVm.state.collectAsState()

                    // Collect one-shot effects for PIN unlock / quick-switch
                    LaunchedEffect(Unit) {
                        authVm.effects.collect { effect ->
                            when (effect) {
                                is AuthEffect.PinUnlocked,
                                is AuthEffect.QuickSwitchCompleted -> onUnlocked()
                                is AuthEffect.NavigateToLogin -> {
                                    // Full login required — navigate away from PIN lock
                                    navController.navigateAndClear(com.zyntasolutions.zyntapos.navigation.ZyntaRoute.Login)
                                }
                                else -> Unit
                            }
                        }
                    }

                    if (authState.isQuickSwitchMode) {
                        // Quick-switch: show user picker or PIN entry for selected target
                        val targetId = authState.quickSwitchTargetId
                        if (targetId == null) {
                            // Show candidate picker dialog
                            AlertDialog(
                                onDismissRequest = { authVm.dispatch(AuthIntent.CancelQuickSwitch) },
                                title = { Text(s[StringResource.AUTH_SWITCH_USER]) },
                                text = {
                                    Column {
                                        authState.quickSwitchCandidates.forEach { candidate ->
                                            TextButton(
                                                onClick = { authVm.dispatch(AuthIntent.QuickSwitchSelected(candidate.id)) },
                                                modifier = Modifier.fillMaxWidth(),
                                            ) {
                                                Text("${candidate.name} (${candidate.role.name})")
                                            }
                                        }
                                    }
                                },
                                confirmButton = {},
                                dismissButton = {
                                    TextButton(onClick = { authVm.dispatch(AuthIntent.CancelQuickSwitch) }) {
                                        Text(s[StringResource.COMMON_CANCEL])
                                    }
                                },
                            )
                        } else {
                            // Show PIN entry for the selected quick-switch target
                            val targetCandidate = authState.quickSwitchCandidates.find { it.id == targetId }
                            PinLockScreen(
                                currentUser = targetCandidate?.let {
                                    com.zyntasolutions.zyntapos.domain.model.User(
                                        id = it.id, name = it.name, email = "", role = it.role, storeId = "",
                                        createdAt = kotlinx.datetime.Instant.DISTANT_PAST,
                                        updatedAt = kotlinx.datetime.Instant.DISTANT_PAST,
                                    )
                                },
                                onPinEntered = { pin -> authVm.dispatch(AuthIntent.QuickSwitchPinEntered(pin)) },
                                onDifferentUser = { authVm.dispatch(AuthIntent.CancelQuickSwitch) },
                                isLoading = authState.isPinValidating,
                                errorMessage = authState.pinError,
                            )
                        }
                    } else {
                        PinLockScreen(
                            currentUser = currentUser,
                            onPinEntered = { pin -> authVm.dispatch(AuthIntent.PinEntered(pin)) },
                            onDifferentUser = { authVm.dispatch(AuthIntent.OpenQuickSwitch) },
                            isLoading = authState.isPinValidating,
                            errorMessage = authState.pinError,
                        )
                    }
                },
                debugScreen = if (appInfoProvider.isDebug) { onNavigateUp ->
                    DebugScreen(onNavigateUp = onNavigateUp)
                } else null,
                diagnosticConsentScreen = { token, onDismiss ->
                    val vm = koinViewModel<DiagnosticViewModel>()
                    LaunchedEffect(token) { vm.dispatch(DiagnosticIntent.LoadToken(token)) }
                    val state by vm.state.collectAsState()
                    // Dismiss the screen when consent is accepted or denied
                    LaunchedEffect(Unit) {
                        vm.effects.collect { effect ->
                            when (effect) {
                                is DiagnosticEffect.ConsentAccepted,
                                is DiagnosticEffect.ConsentDenied ->
                                    onDismiss()
                                is DiagnosticEffect.ShowError ->
                                    Unit // Error message shown inline via state.errorMessage
                            }
                        }
                    }
                    DiagnosticConsentScreen(state = state, onIntent = vm::dispatch)
                },
            )
        }
        } // end CompositionLocalProvider
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
    productList = { onNavigateToDetail, _, _, onNavigateToPrintLabels, onNavigateToStocktake ->
        val vm: InventoryViewModel = koinViewModel()
        val state by vm.state.collectAsState()
        ProductListScreen(
            state = state,
            onIntent = vm::dispatch,
            onNavigateToDetail = onNavigateToDetail,
            onNavigateToPrintLabels = onNavigateToPrintLabels,
            onNavigateToStocktake = onNavigateToStocktake,
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
    categoryList = { _, onNavigateToDetail ->
        val vm: InventoryViewModel = koinViewModel()
        val state by vm.state.collectAsState()
        CategoryListScreen(
            categories = state.categories,
            isLoading = state.isLoading,
            onNavigateToDetail = onNavigateToDetail,
            onDeleteCategory = { vm.dispatch(InventoryIntent.DeleteCategory(it)) },
        )
    },

    // ── Inventory: Category Detail ───────────────────────────────────────────
    categoryDetail = { categoryId, onNavigateUp ->
        val vm: InventoryViewModel = koinViewModel()
        val state by vm.state.collectAsState()
        androidx.compose.runtime.LaunchedEffect(categoryId) {
            if (categoryId != null) vm.dispatch(InventoryIntent.OpenCategoryDetail(categoryId))
        }
        CategoryDetailScreen(
            existingCategory = state.selectedCategory,
            allCategories = state.categories,
            isLoading = state.isLoading,
            errorMessage = state.error,
            onConfirm = { category ->
                vm.dispatch(InventoryIntent.SaveCategory(category))
                onNavigateUp()
            },
            onNavigateBack = onNavigateUp,
        )
    },

    // ── Inventory: Supplier List ────────────────────────────────────────────
    supplierList = { _, onNavigateToDetail ->
        val vm: InventoryViewModel = koinViewModel()
        val state by vm.state.collectAsState()
        SupplierListScreen(
            suppliers = state.suppliers,
            isLoading = state.isLoading,
            onNavigateToDetail = onNavigateToDetail,
        )
    },

    // ── Inventory: Supplier Detail ───────────────────────────────────────────
    supplierDetail = { supplierId, onNavigateUp ->
        val vm: InventoryViewModel = koinViewModel()
        val state by vm.state.collectAsState()
        androidx.compose.runtime.LaunchedEffect(supplierId) {
            if (supplierId != null) vm.dispatch(InventoryIntent.OpenSupplierDetail(supplierId))
        }
        SupplierDetailScreen(
            existingSupplier = state.selectedSupplier,
            purchaseHistory = state.supplierPurchaseHistory,
            isLoading = state.isLoading,
            errorMessage = state.error,
            onConfirm = { supplier ->
                vm.dispatch(InventoryIntent.SaveSupplier(supplier))
                onNavigateUp()
            },
            onNavigateBack = onNavigateUp,
        )
    },

    // ── Inventory: Barcode Label Print ──────────────────────────────────────
    barcodeLabelPrint = { initialProductId, onNavigateBack ->
        val vm: BarcodeLabelPrintViewModel = koinViewModel()
        val state by vm.state.collectAsState()
        androidx.compose.runtime.LaunchedEffect(initialProductId) {
            vm.dispatch(BarcodeLabelPrintIntent.Initialize(initialProductId))
        }
        BarcodeLabelPrintScreen(
            state = state,
            onIntent = vm::dispatch,
            onNavigateBack = onNavigateBack,
        )
    },

    // ── Inventory: Stocktake ────────────────────────────────────────────────
    stocktake = { onNavigateBack ->
        com.zyntasolutions.zyntapos.feature.inventory.stocktake.StocktakeScreen(
            onNavigateBack = onNavigateBack,
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
    customerReport = { CustomerReportScreen(onNavigateUp = { }) },
    expenseReport = { ExpenseReportScreen(onNavigateUp = { }) },

    // ── Store Comparison Report (C5.2) ──────────────────────────────────
    storeComparisonReport = { onNavigateUp ->
        val vm: com.zyntasolutions.zyntapos.feature.reports.ReportsViewModel = koinViewModel()
        val state by vm.state.collectAsState()
        com.zyntasolutions.zyntapos.feature.reports.StoreComparisonReportScreen(
            state = state.storeComparison,
            onIntent = vm::dispatch,
            onNavigateUp = onNavigateUp,
        )
    },

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
    taxSettings = { onNavigateUp, onNavigateToRegionalOverrides ->
        val vm: SettingsViewModel = koinViewModel()
        val state by vm.state.collectAsState()
        TaxSettingsScreen(
            state = state.tax,
            effects = vm.effects,
            onIntent = vm::dispatch,
            onBack = onNavigateUp,
            onNavigateToRegionalOverrides = onNavigateToRegionalOverrides,
        )
    },

    // ── Settings: Regional Tax Override ──────────────────────────────────
    regionalTaxOverride = { storeId, onNavigateUp ->
        val vm: RegionalTaxOverrideViewModel = koinViewModel()
        val state by vm.state.collectAsState()
        RegionalTaxOverrideScreen(
            storeId = storeId,
            state = state,
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

    // ── Settings: Security ───────────────────────────────────────────────────
    securitySettings = { onNavigateUp, onNavigateToRbacManagement ->
        val vm: SettingsViewModel = koinViewModel()
        val state by vm.state.collectAsState()
        SecuritySettingsScreen(
            state = state.security,
            onIntent = vm::dispatch,
            onBack = onNavigateUp,
            onNavigateToRbacManagement = onNavigateToRbacManagement,
        )
    },

    // ── Settings: RBAC Management ────────────────────────────────────────────
    rbacManagement = { onNavigateUp ->
        val vm: SettingsViewModel = koinViewModel()
        val state by vm.state.collectAsState()
        RbacManagementScreen(
            state = state.rbac,
            effects = vm.effects,
            onIntent = vm::dispatch,
            onBack = onNavigateUp,
        )
    },

    // ── Settings: Edition Management ─────────────────────────────────────────
    editionManagement = { onNavigateUp ->
        EditionManagementScreen(
            onNavigateBack = onNavigateUp,
        )
    },

    // ── Click & Collect: Fulfillment Queue (C4.4) ──────────────────────────
    fulfillmentQueue = { onNavigateUp ->
        FulfillmentQueueScreen(onNavigateUp = onNavigateUp)
    },

    // ── Order History ───────────────────────────────────────────────────────
    orderHistory = { _, onNavigateUp ->
        OrderHistoryScreen(
            orders = emptyList(),
            onOrderTap = { },
            onReprintOrder = { },
        )
    },

    // ── CRM: Customers ──────────────────────────────────────────────────────
    customerList = { onNavigateToDetail, onNavigateToGroups ->
        val vm: CustomerViewModel = koinViewModel()
        val state by vm.state.collectAsState()
        CustomerListScreen(
            state = state,
            onIntent = vm::dispatch,
            onNavigateToDetail = onNavigateToDetail,
            onNavigateToGroups = onNavigateToGroups,
        )
    },

    customerDetail = { customerId, onNavigateUp, onNavigateToWallet ->
        val s = LocalStrings.current
        val vm: CustomerViewModel = koinViewModel()
        val state by vm.state.collectAsState()
        val snackbarHostState = remember { SnackbarHostState() }
        var exportedJson by remember { mutableStateOf<String?>(null) }
        val reportExporter: ReportExporter = koinInject()
        val scope = rememberCoroutineScope()
        var isGdprExporting by remember { mutableStateOf(false) }

        // Collect one-shot effects (snackbar, GDPR export, navigation)
        LaunchedEffect(Unit) {
            vm.effects.collect { effect ->
                when (effect) {
                    is CustomerEffect.ShowSuccess -> snackbarHostState.showSnackbar(effect.message)
                    is CustomerEffect.ShowError -> snackbarHostState.showSnackbar(effect.message)
                    is CustomerEffect.CustomerDataExported -> exportedJson = effect.json
                    is CustomerEffect.NavigateToList -> onNavigateUp()
                    is CustomerEffect.MergeCompleted -> snackbarHostState.showSnackbar(effect.message)
                    else -> Unit
                }
            }
        }

        // GDPR export result dialog (G10)
        val currentExportedJson = exportedJson
        if (currentExportedJson != null) {
            AlertDialog(
                onDismissRequest = { exportedJson = null },
                title = { Text(s[StringResource.CUSTOMERS_GDPR_EXPORT_TITLE]) },
                text = {
                    Column {
                        Text(
                            text = s[StringResource.CUSTOMERS_GDPR_EXPORT_DESC],
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Spacer(Modifier.height(8.dp))
                        Surface(
                            tonalElevation = 2.dp,
                            modifier = Modifier.fillMaxWidth().heightIn(max = 300.dp),
                        ) {
                            SelectionContainer {
                                Text(
                                    text = currentExportedJson,
                                    style = MaterialTheme.typography.bodySmall,
                                    modifier = Modifier.padding(8.dp)
                                        .verticalScroll(rememberScrollState()),
                                )
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            val json = currentExportedJson
                            val cid = customerId
                            if (json != null && cid != null && !isGdprExporting) {
                                isGdprExporting = true
                                scope.launch {
                                    try {
                                        reportExporter.exportGdprJson(cid, json)
                                    } catch (_: Exception) {
                                        // Cancelled or failed — no-op (user can copy text instead)
                                    } finally {
                                        isGdprExporting = false
                                    }
                                }
                            }
                        },
                        enabled = !isGdprExporting,
                    ) {
                        Text(if (isGdprExporting) s[StringResource.CUSTOMERS_GDPR_SAVING] else s[StringResource.CUSTOMERS_GDPR_SAVE_SHARE])
                    }
                },
                dismissButton = {
                    TextButton(onClick = { exportedJson = null }) { Text(s[StringResource.COMMON_CLOSE]) }
                },
            )
        }

        CustomerDetailScreen(
            customerId = customerId,
            state = state,
            onIntent = vm::dispatch,
            onNavigateUp = onNavigateUp,
            onNavigateToWallet = onNavigateToWallet,
            snackbarHostState = snackbarHostState,
        )
    },

    customerGroupList = { onNavigateUp ->
        val vm: CustomerViewModel = koinViewModel()
        val state by vm.state.collectAsState()
        CustomerGroupScreen(
            state = state,
            onIntent = vm::dispatch,
            onNavigateUp = onNavigateUp,
        )
    },

    customerWallet = { customerId, onNavigateUp ->
        val vm: CustomerViewModel = koinViewModel()
        val state by vm.state.collectAsState()
        CustomerWalletScreen(
            customerId = customerId,
            state = state,
            onIntent = vm::dispatch,
            onNavigateUp = onNavigateUp,
        )
    },

    // ── Coupons ─────────────────────────────────────────────────────────────
    couponList = { onNavigateToDetail ->
        CouponListScreen(onNavigateToDetail = onNavigateToDetail)
    },

    couponDetail = { couponId, onNavigateUp ->
        CouponDetailScreen(couponId = couponId, onNavigateUp = onNavigateUp)
    },

    // ── Expenses ────────────────────────────────────────────────────────────
    expenseList = { onNavigateToDetail, onNavigateToCategories ->
        ExpenseListScreen(
            onNavigateToDetail = onNavigateToDetail,
            onNavigateToCategories = onNavigateToCategories,
        )
    },

    expenseDetail = { expenseId, onNavigateUp ->
        ExpenseDetailScreen(expenseId = expenseId, onNavigateUp = onNavigateUp)
    },

    expenseCategoryList = { onNavigateUp ->
        ExpenseCategoryListScreen(onNavigateUp = onNavigateUp)
    },

    // ── Multi-store / Warehouses ─────────────────────────────────────────────
    warehouseList = { onNavigateToDetail, onNavigateToTransfers ->
        WarehouseListScreen(
            onNavigateToDetail = onNavigateToDetail,
            onNavigateToTransfers = onNavigateToTransfers,
        )
    },

    warehouseDetail = { warehouseId, onNavigateUp ->
        WarehouseDetailScreen(warehouseId = warehouseId, onNavigateUp = onNavigateUp)
    },

    storeTransferDashboard = { onNavigateToNewTransfer, onNavigateUp, onNavigateToPickList ->
        StoreTransferDashboardScreen(
            onNavigateToNewTransfer = onNavigateToNewTransfer,
            onNavigateUp = onNavigateUp,
            onNavigateToPickList = onNavigateToPickList,
        )
    },

    stockTransferList = { onNavigateToNewTransfer, onNavigateUp ->
        StockTransferListScreen(
            onNavigateToNewTransfer = onNavigateToNewTransfer,
            onNavigateUp = onNavigateUp,
        )
    },

    newStockTransfer = { sourceWarehouseId, onComplete, onCancel ->
        NewStockTransferScreen(
            sourceWarehouseId = sourceWarehouseId,
            onComplete = onComplete,
            onCancel = onCancel,
        )
    },

    pickListView = { transferId, onNavigateUp ->
        PickListScreen(transferId = transferId, onNavigateUp = onNavigateUp)
    },

    // ── Admin  (Sprint 13-15) ────────────────────────────────────────────────
    adminScreen = { _ ->
        val vm: AdminViewModel = koinViewModel()
        val state by vm.state.collectAsState()
        AdminScreen(
            state = state,
            onIntent = vm::dispatch,
        )
    },

    // ── Staff  (Sprint 8-12) ─────────────────────────────────────────────────
    staffScreen = { _, onNavigateToEmployeeStores ->
        val authRepository: AuthRepository = koinInject()
        val session by authRepository.getSession().collectAsState(initial = null)
        val vm: StaffViewModel = koinViewModel()
        val state by vm.state.collectAsState()
        // Collect one-shot effects for navigation
        LaunchedEffect(Unit) {
            vm.effects.collect { effect ->
                when (effect) {
                    is StaffEffect.NavigateToEmployeeStores ->
                        onNavigateToEmployeeStores(effect.employeeId)
                    else -> Unit
                }
            }
        }
        StaffScreen(
            state = state,
            onIntent = vm::dispatch,
            storeId = session?.storeId ?: "",
        )
    },

    // ── Employee Store Assignments  (C3.4) ───────────────────────────────────
    employeeStoreAssignments = { employeeId, onNavigateUp ->
        val vm: EmployeeRoamingViewModel = koinViewModel()
        val state by vm.state.collectAsState()
        LaunchedEffect(employeeId) {
            vm.dispatch(EmployeeRoamingIntent.LoadAssignments(employeeId, employeeId))
        }
        EmployeeStoreAssignmentScreen(
            state = state,
            onIntent = vm::dispatch,
            onNavigateUp = onNavigateUp,
        )
    },

    // ── Notifications ────────────────────────────────────────────────────────
    notificationInbox = { onNavigateUp ->
        NotificationInboxScreen(onNavigateUp = onNavigateUp)
    },

    // ── Warehouse Racks  (Sprint 18) ─────────────────────────────────────────
    warehouseRackList = { warehouseId, onNavigateToDetail, _ ->
        val vm: WarehouseViewModel = koinViewModel()
        val state by vm.state.collectAsState()
        WarehouseRackListScreen(
            state = state,
            onIntent = vm::dispatch,
            warehouseId = warehouseId,
        )
    },

    warehouseRackDetail = { _, warehouseId, onNavigateUp ->
        val vm: WarehouseViewModel = koinViewModel()
        val state by vm.state.collectAsState()
        WarehouseRackDetailScreen(
            state = state,
            onIntent = vm::dispatch,
            onBack = onNavigateUp,
        )
    },

    // ── Multi-Store Dashboard (C3.3) ────────────────────────────────────────
    multiStoreDashboard = { onNavigateUp ->
        val vm: MultiStoreDashboardViewModel = koinViewModel()
        MultiStoreDashboardScreen(viewModel = vm, onNavigateBack = onNavigateUp)
    },

    // ── Accounting / E-Invoice  (Sprint 18-24) ────────────────────────────────
    accountingLedger = { onNavigateToDetail, onNavigateUp ->
        val vm: AccountingViewModel = koinViewModel()
        val state by vm.state.collectAsState()
        AccountingLedgerScreen(
            state = state,
            onIntent = vm::dispatch,
            onNavigateToDetail = onNavigateToDetail,
            onNavigateUp = onNavigateUp,
        )
    },

    // Legacy ledger detail (Sprint 18): passes accountCode as accountId for backwards compat.
    // Wave 4B introduces AccountManagementDetail which uses the updated AccountDetailScreen API.
    accountDetail = { accountCode, _, onNavigateUp ->
        AccountDetailScreen(
            accountId = accountCode,
            storeId = "default-store",
            onNavigateBack = onNavigateUp,
        )
    },

    eInvoiceList = { onNavigateToDetail ->
        val vm: EInvoiceViewModel = koinViewModel()
        val state by vm.state.collectAsState()
        EInvoiceListScreen(
            state = state,
            onIntent = vm::dispatch,
            onNavigateToDetail = onNavigateToDetail,
        )
    },

    eInvoiceDetail = { invoiceId, _ ->
        val vm: EInvoiceViewModel = koinViewModel()
        val state by vm.state.collectAsState()
        if (invoiceId != null) {
            vm.dispatch(com.zyntasolutions.zyntapos.feature.accounting.EInvoiceIntent.LoadInvoice(invoiceId))
        }
        EInvoiceDetailScreen(
            state = state,
            onIntent = vm::dispatch,
        )
    },

    // ── Wave 4B: Chart of Accounts, Journal Entries, Financial Statements ──
    chartOfAccounts = { onNavigateToAccountDetail, onNavigateBack ->
        ChartOfAccountsScreen(
            onNavigateToAccountDetail = onNavigateToAccountDetail,
            onNavigateBack = onNavigateBack,
        )
    },

    accountManagementDetail = { accountId, storeId, onNavigateBack ->
        AccountDetailScreen(
            accountId = accountId,
            storeId = storeId,
            onNavigateBack = onNavigateBack,
        )
    },

    journalEntryList = { storeId, onNavigateToEntry, onNavigateBack ->
        JournalEntryListScreen(
            storeId = storeId,
            onNavigateToEntry = onNavigateToEntry,
            onNavigateBack = onNavigateBack,
        )
    },

    journalEntryDetail = { entryId, storeId, createdBy, onNavigateBack, onNavigateToEntry ->
        val vm: JournalEntryDetailViewModel = koinViewModel()
        JournalEntryDetailScreen(
            entryId = entryId,
            storeId = storeId,
            createdBy = createdBy,
            viewModel = vm,
            onNavigateBack = onNavigateBack,
            onNavigateToEntry = onNavigateToEntry,
        )
    },

    financialStatements = { storeId, onNavigateBack ->
        val s = LocalStrings.current
        var exportedCsv by remember { mutableStateOf<Pair<String, String>?>(null) }
        val currentExport = exportedCsv
        if (currentExport != null) {
            AlertDialog(
                onDismissRequest = { exportedCsv = null },
                title = { Text(currentExport.second) },
                text = {
                    Column {
                        Text(
                            text = "CSV export is ready. Select the text below to copy it.",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Spacer(Modifier.height(8.dp))
                        Surface(
                            tonalElevation = 2.dp,
                            modifier = Modifier.fillMaxWidth().heightIn(max = 300.dp),
                        ) {
                            SelectionContainer {
                                Text(
                                    text = currentExport.first,
                                    style = MaterialTheme.typography.bodySmall,
                                    modifier = Modifier.padding(8.dp)
                                        .verticalScroll(rememberScrollState()),
                                )
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { exportedCsv = null }) { Text(s[StringResource.COMMON_CLOSE]) }
                },
            )
        }
        FinancialStatementsScreen(
            storeId = storeId,
            onNavigateBack = onNavigateBack,
            onShareExport = { content, fileName -> exportedCsv = content to fileName },
        )
    },

    generalLedger = { storeId, initialAccountId, onNavigateBack ->
        GeneralLedgerScreen(
            storeId = storeId,
            initialAccountId = initialAccountId,
            onNavigateBack = onNavigateBack,
        )
    },
)
