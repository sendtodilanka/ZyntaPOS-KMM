package com.zyntasolutions.zyntapos.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.navigation.NavDeepLink
import androidx.navigation.compose.NavHost
import androidx.navigation.navDeepLink
import com.zyntasolutions.zyntapos.domain.model.Permission
import com.zyntasolutions.zyntapos.domain.model.Role

// ─────────────────────────────────────────────────────────────────────────────
// Deep link URI scheme
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Base URI scheme for ZyntaPOS deep links.
 *
 * Registered in AndroidManifest.xml (intent filter) and Desktop launch args.
 * Examples:
 * - `zyntapos://product/4901234567890` → [ZyntaRoute.ProductDetail]
 * - `zyntapos://order/ORD-2025-0042`  → [ZyntaRoute.OrderHistory]
 */
const val ZENTA_DEEP_LINK_SCHEME = "zyntapos"

/** Deep link: `zyntapos://product/{productId}` — scans barcode → ProductDetail. */
val deepLinkProduct: NavDeepLink = navDeepLink<ZyntaRoute.ProductDetail>(
    basePath = "$ZENTA_DEEP_LINK_SCHEME://product",
)

/** Deep link: `zyntapos://order/{orderId}` — notification tap → OrderHistory. */
val deepLinkOrder: NavDeepLink = navDeepLink<ZyntaRoute.OrderHistory>(
    basePath = "$ZENTA_DEEP_LINK_SCHEME://order",
)

// ─────────────────────────────────────────────────────────────────────────────
// Root NavHost
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Root navigation host for the ZyntaPOS application.
 *
 * **Authentication redirect:**
 * If [isSessionActive] is `true` on launch, the NavHost immediately navigates
 * to [ZyntaRoute.Dashboard], bypassing [ZyntaRoute.Login]. The redirect is
 * driven by [LaunchedEffect] so it happens after the initial composition settles.
 *
 * **Back-stack architecture:**
 * ```
 * NavHost (startDestination = ZyntaRoute.AuthGraph)
 * │
 * ├── authNavGraph
 * │   ├── Login (start)
 * │   └── PinLock
 * │
 * └── mainNavGraph
 *     ├── Dashboard (start)
 *     ├── Pos
 *     ├── Payment
 *     ├── [Inventory sub-graph]
 *     │   ├── ProductList (start)
 *     │   ├── ProductDetail  ← deep link: zyntapos://product/{productId}
 *     │   ├── CategoryList
 *     │   └── SupplierList
 *     ├── [Register sub-graph]
 *     │   ├── RegisterDashboard (start)
 *     │   ├── OpenRegister
 *     │   └── CloseRegister
 *     ├── [Reports sub-graph]
 *     │   ├── SalesReport (start)
 *     │   └── StockReport
 *     ├── [Settings sub-graph]
 *     │   ├── Settings (start)
 *     │   ├── PrinterSettings
 *     │   ├── TaxSettings
 *     │   └── UserManagement
 *     └── OrderHistory  ← deep link: zyntapos://order/{orderId}
 * ```
 *
 * **RBAC:**
 * The [navItems] list is filtered via [RbacNavFilter.forRole] before being
 * passed here. Route-level RBAC guards inside individual screens are handled
 * by each screen's ViewModel via [CheckPermissionUseCase].
 *
 * @param navigationController Wrapper around [NavHostController].
 * @param isSessionActive Whether a valid JWT session exists on startup.
 *   When `true`, skip Login and go directly to Dashboard.
 * @param userRole The authenticated user's [Role], used to filter navigation items.
 *   Pass `null` when unauthenticated (shows auth graph only).
 * @param screens Lambda factories for every screen composable (injected from app layer).
 * @param loginScreen Composable factory for the Login screen.
 * @param pinLockScreen Composable factory for the PinLock screen.
 */
@Composable
fun ZyntaNavGraph(
    navigationController: NavigationController,
    isSessionActive: Boolean,
    userRole: Role?,
    screens: MainNavScreens,
    loginScreen: @Composable (onLoginSuccess: () -> Unit) -> Unit,
    pinLockScreen: @Composable (onUnlocked: () -> Unit) -> Unit,
) {
    // Compute RBAC-filtered nav items once per role change
    val navItems: List<NavItem> = if (userRole != null) {
        RbacNavFilter.forRole(userRole)
    } else {
        emptyList()
    }

    NavHost(
        navController = navigationController.navController,
        startDestination = ZyntaRoute.AuthGraph,
    ) {
        // ── Unauthenticated graph ────────────────────────────────────────────
        authNavGraph(
            navigationController = navigationController,
            loginScreen = loginScreen,
            pinLockScreen = pinLockScreen,
        )

        // ── Authenticated graph ──────────────────────────────────────────────
        mainNavGraph(
            navigationController = navigationController,
            navItems = navItems,
            screens = screens,
        )
    }

    // ── Session redirect ─────────────────────────────────────────────────────
    // After composition settles, skip the login screen if a valid session exists.
    // LaunchedEffect key on isSessionActive so re-authentication re-evaluates.
    LaunchedEffect(isSessionActive) {
        if (isSessionActive) {
            navigationController.navigateAndClear(ZyntaRoute.Dashboard)
        }
    }
}
