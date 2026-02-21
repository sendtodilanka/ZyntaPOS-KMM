package com.zyntasolutions.zyntapos

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.zyntasolutions.zyntapos.navigation.ZyntaNavGraph
import com.zyntasolutions.zyntapos.navigation.rememberNavigationController

/**
 * Root composable for ZyntaPOS.
 *
 * This is the top-level entry point shared across Android and Desktop targets.
 * Koin is guaranteed to be started before this composable runs:
 * - Android: [ZyntaApplication.onCreate] → startKoin
 * - Desktop: `main()` first statement → startKoin
 *
 * ## Current state — MERGED-A1
 * [rememberNavigationController] is created here and will be forwarded to
 * [ZyntaNavGraph] once the full wiring below is completed.
 *
 * ## TODO — Wire ZyntaNavGraph (see MERGED-A1)
 *
 * [ZyntaNavGraph] has the following signature and requires the items listed
 * below before it can replace the current [Box] placeholder:
 *
 * ```kotlin
 * ZyntaNavGraph(
 *     navigationController = navController,   // ✅ available via rememberNavigationController()
 *     isSessionActive      = isSessionActive,  // ⏳ collect from AuthRepository.getSession()
 *     userRole             = userRole,         // ⏳ collect from AuthRepository.getSession()
 *     screens              = MainNavScreens(   // ⏳ requires all feature screens to be implemented:
 *         dashboard        = { ... },          //    Sprint 11 — DashboardScreen
 *         pos              = { ... },          //    Sprint 14 — PosScreen
 *         payment          = { ... },          //    Sprint 15 — PaymentScreen
 *         productList      = { ... },          //    Sprint 18 — ProductListScreen
 *         productDetail    = { ... },          //    Sprint 18 — ProductDetailScreen
 *         categoryList     = { ... },          //    Sprint 19 — CategoryListScreen
 *         supplierList     = { ... },          //    Sprint 19 — SupplierListScreen
 *         registerDashboard = { ... },         //    Sprint 20 — RegisterDashboardScreen
 *         openRegister     = { ... },          //    Sprint 20 — OpenRegisterScreen
 *         closeRegister    = { ... },          //    Sprint 21 — CloseRegisterScreen
 *         salesReport      = { ... },          //    Sprint 22 — SalesReportScreen
 *         stockReport      = { ... },          //    Sprint 22 — StockReportScreen
 *         settings         = { ... },          //    Sprint 23 — SettingsScreen
 *         printerSettings  = { ... },          //    Sprint 23 — PrinterSettingsScreen
 *         taxSettings      = { ... },          //    Sprint 23 — TaxSettingsScreen
 *         userManagement   = { ... },          //    Sprint 23 — UserManagementScreen
 *         orderHistory     = { ... },          //    Sprint 24 — OrderHistoryScreen
 *     ),
 *     loginScreen          = { ... },          // ⏳ Sprint 11 — LoginScreen from :feature:auth
 *     pinLockScreen        = { ... },          // ⏳ Sprint 11 — PinLockScreen from :feature:auth
 * )
 * ```
 *
 * Replace the [Box] below with [ZyntaNavGraph] once all screen lambdas are implemented.
 */
@Composable
fun App() {
    MaterialTheme {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background,
        ) {
            // NavigationController is ready — Koin is guaranteed started by the platform entry point.
            val navController = rememberNavigationController()

            Box(
                modifier = Modifier.fillMaxSize(),
            ) {
                // TODO MERGED-A1: Replace this Box with ZyntaNavGraph once all
                // feature screen composables are implemented (see KDoc above for
                // the full parameter checklist). Koin is active; navController is
                // ready. Remove this comment and the Box wrapper when wiring is complete.
                //
                // ZyntaNavGraph(
                //     navigationController = navController,
                //     isSessionActive      = /* collect from AuthRepository */,
                //     userRole             = /* collect from AuthRepository */,
                //     screens              = /* MainNavScreens(...) */,
                //     loginScreen          = { onLoginSuccess -> /* LoginScreen */ },
                //     pinLockScreen        = { onUnlocked    -> /* PinLockScreen */ },
                // )
            }
        }
    }
}
