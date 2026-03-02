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
 * **First-run (onboarding) redirect:**
 * If [isFirstRun] is `true`, the auth graph starts at [ZyntaRoute.Onboarding]
 * instead of [ZyntaRoute.Login]. Once the wizard completes it navigates to Login.
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
 * │   ├── Onboarding   ← only on first launch (isFirstRun = true)
 * │   ├── Login (default start)
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
 * @param isFirstRun Whether this is the first launch (onboarding not completed).
 * @param isSessionActive Whether a valid JWT session exists on startup.
 *   When `true`, skip Login and go directly to Dashboard.
 * @param userRole The authenticated user's [Role], used to filter navigation items.
 *   Pass `null` when unauthenticated (shows auth graph only).
 * @param screens Lambda factories for every screen composable (injected from app layer).
 * @param loginScreen Composable factory for the Login screen.
 * @param onboardingScreen Composable factory for the Onboarding wizard screen.
 * @param pinLockScreen Composable factory for the PinLock screen.
 * @param debugScreen Optional composable factory for the Debug Console. Non-null only when
 *   `AppInfoProvider.isDebug == true`. When null, [ZyntaRoute.Debug] is not registered.
 */
@Composable
fun ZyntaNavGraph(
    navigationController: NavigationController,
    isFirstRun: Boolean,
    isSessionActive: Boolean,
    userRole: Role?,
    screens: MainNavScreens,
    loginScreen: @Composable (onLoginSuccess: () -> Unit) -> Unit,
    onboardingScreen: @Composable (onOnboardingComplete: () -> Unit) -> Unit,
    pinLockScreen: @Composable (onUnlocked: () -> Unit) -> Unit,
    debugScreen: (@Composable (onNavigateUp: () -> Unit) -> Unit)? = null,
) {
    // Compute RBAC-filtered nav items once per role change
    val navItems: List<NavItem> = if (userRole != null) {
        RbacNavFilter.forRole(userRole)
    } else {
        emptyList()
    }

    // Compact items (max 5) for the COMPACT bottom NavigationBar
    val compactNavItems: List<NavItem> = if (userRole != null) {
        RbacNavFilter.compactForRole(userRole)
    } else {
        emptyList()
    }

    // Section-header groups for the EXPANDED permanent drawer
    val navGroups = RbacNavFilter.groupsForItems(navItems)

    NavHost(
        navController = navigationController.navController,
        startDestination = ZyntaRoute.AuthGraph,
    ) {
        // ── Unauthenticated graph ────────────────────────────────────────────
        authNavGraph(
            navigationController = navigationController,
            isFirstRun = isFirstRun,
            loginScreen = loginScreen,
            onboardingScreen = onboardingScreen,
            pinLockScreen = pinLockScreen,
        )

        // ── Authenticated graph ──────────────────────────────────────────────
        mainNavGraph(
            navigationController = navigationController,
            navItems = navItems,
            compactNavItems = compactNavItems,
            navGroups = navGroups,
            screens = screens,
            debugScreen = debugScreen,
        )
    }

    // ── Session redirect ─────────────────────────────────────────────────────
    // After composition settles, skip the login screen if a valid session exists.
    // LaunchedEffect key on isSessionActive so re-authentication re-evaluates.
    // The else branch handles logout and session expiry — navigates back to the
    // auth graph (safe to call even when already on AuthGraph; navigateAndClear
    // is idempotent for same-graph navigation).
    LaunchedEffect(isSessionActive) {
        if (isSessionActive) {
            navigationController.navigateAndClear(ZyntaRoute.Dashboard)
        } else {
            navigationController.navigateAndClear(ZyntaRoute.AuthGraph)
        }
    }
}
