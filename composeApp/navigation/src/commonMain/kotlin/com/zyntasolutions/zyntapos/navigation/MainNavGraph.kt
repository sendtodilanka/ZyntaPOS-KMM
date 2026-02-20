package com.zyntasolutions.zyntapos.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import androidx.navigation.compose.navigation
import androidx.navigation.toRoute
import com.zyntasolutions.zyntapos.designsystem.layouts.ZentaScaffold

// ─────────────────────────────────────────────────────────────────────────────
// MAIN NAV GRAPH
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Nested navigation graph for the authenticated area of ZyntaPOS.
 *
 * Wraps all authenticated destinations inside [ZentaScaffold], providing the
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
 *   a safe fallback to [ZentaRoute.Dashboard].
 *
 * @param navigationController Wraps the underlying [NavHostController].
 * @param navItems RBAC-filtered list of primary navigation destinations.
 * @param screens Composable factories for every authenticated screen.
 */
fun NavGraphBuilder.mainNavGraph(
    navigationController: NavigationController,
    navItems: List<NavItem>,
    screens: MainNavScreens,
) {
    navigation<ZentaRoute.Dashboard>(startDestination = ZentaRoute.Dashboard) {

        // ── Scaffold wrapper composable ──────────────────────────────────────
        // Each composable destination is individually registered but rendered
        // inside the scaffold by observing the current back-stack route.
        //
        // NOTE: We use a single "shell" composable pattern — the Scaffold is
        // instantiated once at the Dashboard level, and sub-destinations swap
        // the content pane. This keeps the nav chrome stable and avoids
        // recomposition of the rail/drawer on every navigation event.

        composable<ZentaRoute.Dashboard> { entry ->
            MainScaffoldShell(
                navigationController = navigationController,
                navItems = navItems,
                currentRoute = ZentaRoute.Dashboard,
            ) {
                screens.dashboard(
                    onNavigateToPos = { navigationController.navigate(ZentaRoute.Pos) },
                    onNavigateToRegister = { navigationController.navigate(ZentaRoute.RegisterDashboard) },
                    onNavigateToReports = { navigationController.navigate(ZentaRoute.SalesReport) },
                )
            }
        }

        composable<ZentaRoute.Pos> {
            MainScaffoldShell(
                navigationController = navigationController,
                navItems = navItems,
                currentRoute = ZentaRoute.Pos,
            ) {
                screens.pos(
                    onNavigateToPayment = { orderId ->
                        navigationController.navigate(ZentaRoute.Payment(orderId))
                    },
                )
            }
        }

        composable<ZentaRoute.Payment> { entry ->
            val route = entry.toRoute<ZentaRoute.Payment>()
            screens.payment(
                orderId = route.orderId,
                onPaymentComplete = {
                    // Clear payment from back stack, return to POS
                    navigationController.navController.popBackStack(
                        route = ZentaRoute.Pos,
                        inclusive = false,
                    )
                },
                onCancel = { navigationController.popBackStack() },
            )
        }

        // ── Inventory sub-graph ──────────────────────────────────────────────
        navigation<ZentaRoute.ProductList>(startDestination = ZentaRoute.ProductList) {
            composable<ZentaRoute.ProductList> {
                MainScaffoldShell(
                    navigationController = navigationController,
                    navItems = navItems,
                    currentRoute = ZentaRoute.ProductList,
                ) {
                    screens.productList(
                        onNavigateToDetail = { productId ->
                            navigationController.openProductDetail(productId)
                        },
                        onNavigateToCategories = {
                            navigationController.navigate(ZentaRoute.CategoryList)
                        },
                        onNavigateToSuppliers = {
                            navigationController.navigate(ZentaRoute.SupplierList)
                        },
                    )
                }
            }

            composable<ZentaRoute.ProductDetail> { entry ->
                val route = entry.toRoute<ZentaRoute.ProductDetail>()
                screens.productDetail(
                    productId = route.productId,
                    onNavigateUp = { navigationController.navigateUp(ZentaRoute.ProductList) },
                )
            }

            composable<ZentaRoute.CategoryList> {
                screens.categoryList(
                    onNavigateUp = { navigationController.navigateUp(ZentaRoute.ProductList) },
                )
            }

            composable<ZentaRoute.SupplierList> {
                screens.supplierList(
                    onNavigateUp = { navigationController.navigateUp(ZentaRoute.ProductList) },
                )
            }
        }

        // ── Register sub-graph ───────────────────────────────────────────────
        navigation<ZentaRoute.RegisterDashboard>(startDestination = ZentaRoute.RegisterDashboard) {
            composable<ZentaRoute.RegisterDashboard> {
                MainScaffoldShell(
                    navigationController = navigationController,
                    navItems = navItems,
                    currentRoute = ZentaRoute.RegisterDashboard,
                ) {
                    screens.registerDashboard(
                        onOpenRegister = { navigationController.navigate(ZentaRoute.OpenRegister) },
                        onCloseRegister = { navigationController.navigate(ZentaRoute.CloseRegister) },
                    )
                }
            }

            composable<ZentaRoute.OpenRegister> {
                screens.openRegister(
                    onComplete = {
                        navigationController.navController.popBackStack(
                            route = ZentaRoute.RegisterDashboard,
                            inclusive = false,
                        )
                    },
                )
            }

            composable<ZentaRoute.CloseRegister> {
                screens.closeRegister(
                    onComplete = {
                        navigationController.navController.popBackStack(
                            route = ZentaRoute.RegisterDashboard,
                            inclusive = false,
                        )
                    },
                )
            }
        }

        // ── Reports sub-graph ────────────────────────────────────────────────
        navigation<ZentaRoute.SalesReport>(startDestination = ZentaRoute.SalesReport) {
            composable<ZentaRoute.SalesReport> {
                MainScaffoldShell(
                    navigationController = navigationController,
                    navItems = navItems,
                    currentRoute = ZentaRoute.SalesReport,
                ) {
                    screens.salesReport()
                }
            }

            composable<ZentaRoute.StockReport> {
                MainScaffoldShell(
                    navigationController = navigationController,
                    navItems = navItems,
                    currentRoute = ZentaRoute.StockReport,
                ) {
                    screens.stockReport()
                }
            }
        }

        // ── Settings sub-graph ───────────────────────────────────────────────
        navigation<ZentaRoute.Settings>(startDestination = ZentaRoute.Settings) {
            composable<ZentaRoute.Settings> {
                MainScaffoldShell(
                    navigationController = navigationController,
                    navItems = navItems,
                    currentRoute = ZentaRoute.Settings,
                ) {
                    screens.settings(
                        onNavigateToPrinterSettings = {
                            navigationController.navigate(ZentaRoute.PrinterSettings)
                        },
                        onNavigateToTaxSettings = {
                            navigationController.navigate(ZentaRoute.TaxSettings)
                        },
                        onNavigateToUserManagement = {
                            navigationController.navigate(ZentaRoute.UserManagement)
                        },
                    )
                }
            }

            composable<ZentaRoute.PrinterSettings> {
                screens.printerSettings(
                    onNavigateUp = { navigationController.navigateUp(ZentaRoute.Settings) },
                )
            }

            composable<ZentaRoute.TaxSettings> {
                screens.taxSettings(
                    onNavigateUp = { navigationController.navigateUp(ZentaRoute.Settings) },
                )
            }

            composable<ZentaRoute.UserManagement> {
                screens.userManagement(
                    onNavigateUp = { navigationController.navigateUp(ZentaRoute.Settings) },
                )
            }
        }

        // ── Deep-link target: OrderHistory ───────────────────────────────────
        composable<ZentaRoute.OrderHistory> { entry ->
            val route = entry.toRoute<ZentaRoute.OrderHistory>()
            screens.orderHistory(
                orderId = route.orderId,
                onNavigateUp = { navigationController.navigateUp(ZentaRoute.Dashboard) },
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Scaffold shell — wraps content with adaptive nav chrome
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Internal composable that wraps any authenticated destination inside [ZentaScaffold].
 *
 * Resolves the selected index by matching the [currentRoute]'s target route
 * against [navItems], providing back-stack-aware highlighting.
 */
@Composable
private fun MainScaffoldShell(
    navigationController: NavigationController,
    navItems: List<NavItem>,
    currentRoute: ZentaRoute,
    content: @Composable () -> Unit,
) {
    // Find the selected index based on the current route
    val selectedIndex = navItems.indexOfFirst { item ->
        when (currentRoute) {
            // Inventory sub-graph: highlight Inventory item for all sub-routes
            is ZentaRoute.ProductList,
            is ZentaRoute.ProductDetail,
            is ZentaRoute.CategoryList,
            is ZentaRoute.SupplierList -> item.route is ZentaRoute.ProductList

            // Register sub-graph
            is ZentaRoute.RegisterDashboard,
            is ZentaRoute.OpenRegister,
            is ZentaRoute.CloseRegister -> item.route is ZentaRoute.RegisterDashboard

            // Reports sub-graph
            is ZentaRoute.SalesReport,
            is ZentaRoute.StockReport -> item.route is ZentaRoute.SalesReport

            // Settings sub-graph
            is ZentaRoute.Settings,
            is ZentaRoute.PrinterSettings,
            is ZentaRoute.TaxSettings,
            is ZentaRoute.UserManagement -> item.route is ZentaRoute.Settings

            else -> item.route::class == currentRoute::class
        }
    }.coerceAtLeast(0)

    ZentaScaffold(
        items = navItems.map { it.toZentaNavItem() },
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
