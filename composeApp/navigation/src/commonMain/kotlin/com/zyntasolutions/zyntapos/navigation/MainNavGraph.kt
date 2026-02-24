package com.zyntasolutions.zyntapos.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import androidx.navigation.compose.navigation
import androidx.navigation.toRoute
import com.zyntasolutions.zyntapos.designsystem.layouts.ZyntaScaffold

// ─────────────────────────────────────────────────────────────────────────────
// MAIN NAV GRAPH
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Nested navigation graph for the authenticated area of ZyntaPOS.
 *
 * Wraps all authenticated destinations inside [ZyntaScaffold], providing the
 * adaptive navigation chrome (BottomBar → Rail → Drawer) based on window size.
 *
 * Sub-graphs: POS, Inventory, Register, Reports, Settings.
 *
 * RBAC: the [navItems] list should already be filtered via [RbacNavFilter.forRole]
 * before being passed to this function. Items are mapped to routes for
 * back-stack-aware selection highlighting.
 *
 * Back-stack management:
 * - Each sub-graph uses `saveState = true` / `restoreState = true` so that
 *   switching tabs preserves scroll position and ViewModel state.
 * - Scoped ViewModels are created per nested graph entry via
 *   `NavBackStackEntry.getBackStackEntry(graphRoute)`.
 * - Desktop: no physical back button — [NavigationController.navigateUp] provides
 *   a safe fallback to [ZyntaRoute.Dashboard].
 *
 * @param navigationController Wraps the underlying [NavHostController].
 * @param navItems RBAC-filtered list of primary navigation destinations.
 * @param screens Composable factories for every authenticated screen.
 * @param debugScreen Optional composable for the Debug Console. When non-null,
 *   [ZyntaRoute.Debug] is registered and "DEBUG_CONSOLE" settings key navigates to it.
 */
fun NavGraphBuilder.mainNavGraph(
    navigationController: NavigationController,
    navItems: List<NavItem>,
    screens: MainNavScreens,
    debugScreen: (@Composable (onNavigateUp: () -> Unit) -> Unit)? = null,
) {
    navigation<ZyntaRoute.MainGraph>(startDestination = ZyntaRoute.Dashboard) {

        // ── Scaffold wrapper composable ──────────────────────────────────────
        // Each composable destination is individually registered but rendered
        // inside the scaffold by observing the current back-stack route.
        //
        // NOTE: We use a single "shell" composable pattern — the Scaffold is
        // instantiated once at the Dashboard level, and sub-destinations swap
        // the content pane. This keeps the nav chrome stable and avoids
        // recomposition of the rail/drawer on every navigation event.

        composable<ZyntaRoute.Dashboard> { entry ->
            MainScaffoldShell(
                navigationController = navigationController,
                navItems = navItems,
                currentRoute = ZyntaRoute.Dashboard,
            ) {
                screens.dashboard(
                    { navigationController.navigate(ZyntaRoute.Pos) },
                    { navigationController.navigate(ZyntaRoute.RegisterDashboard) },
                    { navigationController.navigate(ZyntaRoute.SalesReport) },
                    { navigationController.navigate(ZyntaRoute.Settings) },
                )
            }
        }

        composable<ZyntaRoute.Pos> {
            MainScaffoldShell(
                navigationController = navigationController,
                navItems = navItems,
                currentRoute = ZyntaRoute.Pos,
            ) {
                screens.pos(
                    { orderId -> navigationController.navigate(ZyntaRoute.Payment(orderId)) },
                )
            }
        }

        composable<ZyntaRoute.Payment> { entry ->
            val route = entry.toRoute<ZyntaRoute.Payment>()
            screens.payment(
                route.orderId,
                {
                    // Clear payment from back stack, return to POS
                    navigationController.navController.popBackStack(
                        route = ZyntaRoute.Pos,
                        inclusive = false,
                    )
                },
                { navigationController.popBackStack() },
            )
        }

        // ── Inventory sub-graph ──────────────────────────────────────────────
        navigation<ZyntaRoute.InventoryGraph>(startDestination = ZyntaRoute.ProductList) {
            composable<ZyntaRoute.ProductList> {
                MainScaffoldShell(
                    navigationController = navigationController,
                    navItems = navItems,
                    currentRoute = ZyntaRoute.ProductList,
                ) {
                    screens.productList(
                        { productId -> navigationController.openProductDetail(productId) },
                        { navigationController.navigate(ZyntaRoute.CategoryList) },
                        { navigationController.navigate(ZyntaRoute.SupplierList) },
                    )
                }
            }

            composable<ZyntaRoute.ProductDetail> { entry ->
                val route = entry.toRoute<ZyntaRoute.ProductDetail>()
                screens.productDetail(
                    route.productId,
                    { navigationController.navigateUp(ZyntaRoute.ProductList) },
                )
            }

            composable<ZyntaRoute.CategoryList> {
                screens.categoryList(
                    { navigationController.navigateUp(ZyntaRoute.ProductList) },
                )
            }

            composable<ZyntaRoute.SupplierList> {
                screens.supplierList(
                    { navigationController.navigateUp(ZyntaRoute.ProductList) },
                )
            }
        }

        // ── Register sub-graph ───────────────────────────────────────────────
        navigation<ZyntaRoute.RegisterGraph>(startDestination = ZyntaRoute.RegisterDashboard) {
            composable<ZyntaRoute.RegisterDashboard> {
                MainScaffoldShell(
                    navigationController = navigationController,
                    navItems = navItems,
                    currentRoute = ZyntaRoute.RegisterDashboard,
                ) {
                    screens.registerDashboard(
                        { navigationController.navigate(ZyntaRoute.OpenRegister) },
                        { navigationController.navigate(ZyntaRoute.CloseRegister) },
                    )
                }
            }

            composable<ZyntaRoute.OpenRegister> {
                screens.openRegister(
                    {
                        navigationController.navController.popBackStack(
                            route = ZyntaRoute.RegisterDashboard,
                            inclusive = false,
                        )
                    },
                )
            }

            composable<ZyntaRoute.CloseRegister> {
                screens.closeRegister(
                    {
                        navigationController.navController.popBackStack(
                            route = ZyntaRoute.RegisterDashboard,
                            inclusive = false,
                        )
                    },
                )
            }
        }

        // ── Reports sub-graph ────────────────────────────────────────────────
        navigation<ZyntaRoute.ReportsGraph>(startDestination = ZyntaRoute.SalesReport) {
            composable<ZyntaRoute.SalesReport> {
                MainScaffoldShell(
                    navigationController = navigationController,
                    navItems = navItems,
                    currentRoute = ZyntaRoute.SalesReport,
                ) {
                    screens.salesReport()
                }
            }

            composable<ZyntaRoute.StockReport> {
                MainScaffoldShell(
                    navigationController = navigationController,
                    navItems = navItems,
                    currentRoute = ZyntaRoute.StockReport,
                ) {
                    screens.stockReport()
                }
            }
        }

        // ── Settings sub-graph ───────────────────────────────────────────────
        navigation<ZyntaRoute.SettingsGraph>(startDestination = ZyntaRoute.Settings) {
            composable<ZyntaRoute.Settings> {
                MainScaffoldShell(
                    navigationController = navigationController,
                    navItems = navItems,
                    currentRoute = ZyntaRoute.Settings,
                ) {
                    screens.settings { routeKey ->
                        when (routeKey) {
                            "PRINTER" -> navigationController.navigate(ZyntaRoute.PrinterSettings)
                            "TAX" -> navigationController.navigate(ZyntaRoute.TaxSettings)
                            "USERS" -> navigationController.navigate(ZyntaRoute.UserManagement)
                            "GENERAL" -> navigationController.navigate(ZyntaRoute.GeneralSettings)
                            "APPEARANCE" -> navigationController.navigate(ZyntaRoute.AppearanceSettings)
                            "ABOUT" -> navigationController.navigate(ZyntaRoute.AboutSettings)
                            "BACKUP" -> navigationController.navigate(ZyntaRoute.BackupSettings)
                            "POS" -> navigationController.navigate(ZyntaRoute.PosSettings)
                            "SYSTEM_HEALTH" -> navigationController.navigate(ZyntaRoute.SystemHealthSettings)
                            "DEBUG_CONSOLE" -> if (debugScreen != null) {
                                navigationController.navigate(ZyntaRoute.Debug)
                            }
                        }
                    }
                }
            }

            composable<ZyntaRoute.PrinterSettings> {
                screens.printerSettings(
                    { navigationController.navigateUp(ZyntaRoute.Settings) },
                )
            }

            composable<ZyntaRoute.TaxSettings> {
                screens.taxSettings(
                    { navigationController.navigateUp(ZyntaRoute.Settings) },
                )
            }

            composable<ZyntaRoute.UserManagement> {
                screens.userManagement(
                    { navigationController.navigateUp(ZyntaRoute.Settings) },
                )
            }

            composable<ZyntaRoute.GeneralSettings> {
                screens.generalSettings(
                    { navigationController.navigateUp(ZyntaRoute.Settings) },
                )
            }

            composable<ZyntaRoute.AppearanceSettings> {
                screens.appearanceSettings(
                    { navigationController.navigateUp(ZyntaRoute.Settings) },
                )
            }

            composable<ZyntaRoute.AboutSettings> {
                screens.aboutSettings(
                    { navigationController.navigateUp(ZyntaRoute.Settings) },
                )
            }

            composable<ZyntaRoute.BackupSettings> {
                screens.backupSettings(
                    { navigationController.navigateUp(ZyntaRoute.Settings) },
                )
            }

            composable<ZyntaRoute.PosSettings> {
                screens.posSettings(
                    { navigationController.navigateUp(ZyntaRoute.Settings) },
                )
            }

            composable<ZyntaRoute.SystemHealthSettings> {
                screens.systemHealthSettings(
                    { navigationController.navigateUp(ZyntaRoute.Settings) },
                )
            }

            // ── Debug Console — only registered in debug builds ──────────────
            if (debugScreen != null) {
                composable<ZyntaRoute.Debug> {
                    debugScreen { navigationController.navigateUp(ZyntaRoute.Settings) }
                }
            }
        }

        // ── Deep-link target: OrderHistory ───────────────────────────────────
        composable<ZyntaRoute.OrderHistory> { entry ->
            val route = entry.toRoute<ZyntaRoute.OrderHistory>()
            screens.orderHistory(
                route.orderId,
                { navigationController.navigateUp(ZyntaRoute.Dashboard) },
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Scaffold shell — wraps content with adaptive nav chrome
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Internal composable that wraps any authenticated destination inside [ZyntaScaffold].
 *
 * Resolves the selected index by matching the [currentRoute]'s target route
 * against [navItems], providing back-stack-aware highlighting.
 */
@Composable
private fun MainScaffoldShell(
    navigationController: NavigationController,
    navItems: List<NavItem>,
    currentRoute: ZyntaRoute,
    content: @Composable () -> Unit,
) {
    // Find the selected index based on the current route
    val selectedIndex = navItems.indexOfFirst { item ->
        when (currentRoute) {
            // Inventory sub-graph: highlight Inventory item for all sub-routes
            is ZyntaRoute.ProductList,
            is ZyntaRoute.ProductDetail,
            is ZyntaRoute.CategoryList,
            is ZyntaRoute.SupplierList -> item.route is ZyntaRoute.ProductList

            // Register sub-graph
            is ZyntaRoute.RegisterDashboard,
            is ZyntaRoute.OpenRegister,
            is ZyntaRoute.CloseRegister -> item.route is ZyntaRoute.RegisterDashboard

            // Reports sub-graph
            is ZyntaRoute.SalesReport,
            is ZyntaRoute.StockReport -> item.route is ZyntaRoute.SalesReport

            // Settings sub-graph
            is ZyntaRoute.Settings,
            is ZyntaRoute.PrinterSettings,
            is ZyntaRoute.TaxSettings,
            is ZyntaRoute.UserManagement,
            is ZyntaRoute.GeneralSettings,
            is ZyntaRoute.AppearanceSettings,
            is ZyntaRoute.AboutSettings,
            is ZyntaRoute.BackupSettings,
            is ZyntaRoute.PosSettings,
            is ZyntaRoute.SystemHealthSettings -> item.route is ZyntaRoute.Settings

            else -> item.route::class == currentRoute::class
        }
    }.coerceAtLeast(0)

    ZyntaScaffold(
        items = navItems.map { it.toZyntaNavItem() },
        selectedIndex = selectedIndex,
        onItemSelected = { index ->
            navItems.getOrNull(index)?.let { item ->
                navigationController.navigate(item.route) {
                    // Save state so each tab restores its own back-stack
                    popUpTo(navigationController.navController.graph.startDestinationId) {
                        saveState = true
                    }
                    launchSingleTop = true
                    restoreState = true
                }
            }
        },
        content = { _ -> content() },
    )
}
