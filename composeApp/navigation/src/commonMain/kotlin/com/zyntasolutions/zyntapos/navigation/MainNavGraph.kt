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

            composable<ZyntaRoute.CustomerReport> {
                MainScaffoldShell(
                    navigationController = navigationController,
                    navItems = navItems,
                    currentRoute = ZyntaRoute.CustomerReport,
                ) {
                    screens.customerReport()
                }
            }

            composable<ZyntaRoute.ExpenseReport> {
                MainScaffoldShell(
                    navigationController = navigationController,
                    navItems = navItems,
                    currentRoute = ZyntaRoute.ExpenseReport,
                ) {
                    screens.expenseReport()
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

        // ── CRM sub-graph ────────────────────────────────────────────────────
        navigation<ZyntaRoute.CrmGraph>(startDestination = ZyntaRoute.CustomerList) {
            composable<ZyntaRoute.CustomerList> {
                MainScaffoldShell(
                    navigationController = navigationController,
                    navItems = navItems,
                    currentRoute = ZyntaRoute.CustomerList,
                ) {
                    screens.customerList(
                        { customerId -> navigationController.navigate(ZyntaRoute.CustomerDetail(customerId)) },
                        { navigationController.navigate(ZyntaRoute.CustomerGroupList) },
                    )
                }
            }

            composable<ZyntaRoute.CustomerDetail> { entry ->
                val route = entry.toRoute<ZyntaRoute.CustomerDetail>()
                screens.customerDetail(
                    route.customerId,
                    { navigationController.navigateUp(ZyntaRoute.CustomerList) },
                    { cid -> navigationController.navigate(ZyntaRoute.CustomerWallet(cid)) },
                )
            }

            composable<ZyntaRoute.CustomerGroupList> {
                screens.customerGroupList(
                    { navigationController.navigateUp(ZyntaRoute.CustomerList) },
                )
            }

            composable<ZyntaRoute.CustomerWallet> { entry ->
                val route = entry.toRoute<ZyntaRoute.CustomerWallet>()
                screens.customerWallet(
                    route.customerId,
                    { navigationController.navigateUp(ZyntaRoute.CustomerList) },
                )
            }
        }

        // ── Coupons sub-graph ────────────────────────────────────────────────
        navigation<ZyntaRoute.CouponsGraph>(startDestination = ZyntaRoute.CouponList) {
            composable<ZyntaRoute.CouponList> {
                MainScaffoldShell(
                    navigationController = navigationController,
                    navItems = navItems,
                    currentRoute = ZyntaRoute.CouponList,
                ) {
                    screens.couponList(
                        { couponId -> navigationController.navigate(ZyntaRoute.CouponDetail(couponId)) },
                    )
                }
            }

            composable<ZyntaRoute.CouponDetail> { entry ->
                val route = entry.toRoute<ZyntaRoute.CouponDetail>()
                screens.couponDetail(
                    route.couponId,
                    { navigationController.navigateUp(ZyntaRoute.CouponList) },
                )
            }
        }

        // ── Expenses sub-graph ───────────────────────────────────────────────
        navigation<ZyntaRoute.ExpensesGraph>(startDestination = ZyntaRoute.ExpenseList) {
            composable<ZyntaRoute.ExpenseList> {
                MainScaffoldShell(
                    navigationController = navigationController,
                    navItems = navItems,
                    currentRoute = ZyntaRoute.ExpenseList,
                ) {
                    screens.expenseList(
                        { expenseId -> navigationController.navigate(ZyntaRoute.ExpenseDetail(expenseId)) },
                        { navigationController.navigate(ZyntaRoute.ExpenseCategoryList) },
                    )
                }
            }

            composable<ZyntaRoute.ExpenseDetail> { entry ->
                val route = entry.toRoute<ZyntaRoute.ExpenseDetail>()
                screens.expenseDetail(
                    route.expenseId,
                    { navigationController.navigateUp(ZyntaRoute.ExpenseList) },
                )
            }

            composable<ZyntaRoute.ExpenseCategoryList> {
                screens.expenseCategoryList(
                    { navigationController.navigateUp(ZyntaRoute.ExpenseList) },
                )
            }
        }

        // ── Multi-store sub-graph ────────────────────────────────────────────
        navigation<ZyntaRoute.MultiStoreGraph>(startDestination = ZyntaRoute.WarehouseList) {
            composable<ZyntaRoute.WarehouseList> {
                MainScaffoldShell(
                    navigationController = navigationController,
                    navItems = navItems,
                    currentRoute = ZyntaRoute.WarehouseList,
                ) {
                    screens.warehouseList(
                        { warehouseId -> navigationController.navigate(ZyntaRoute.WarehouseDetail(warehouseId)) },
                        { navigationController.navigate(ZyntaRoute.StockTransferList) },
                    )
                }
            }

            composable<ZyntaRoute.WarehouseDetail> { entry ->
                val route = entry.toRoute<ZyntaRoute.WarehouseDetail>()
                screens.warehouseDetail(
                    route.warehouseId,
                    { navigationController.navigateUp(ZyntaRoute.WarehouseList) },
                )
            }

            composable<ZyntaRoute.StockTransferList> {
                screens.stockTransferList(
                    { srcId -> navigationController.navigate(ZyntaRoute.NewStockTransfer(srcId)) },
                    { navigationController.navigateUp(ZyntaRoute.WarehouseList) },
                )
            }

            composable<ZyntaRoute.NewStockTransfer> { entry ->
                val route = entry.toRoute<ZyntaRoute.NewStockTransfer>()
                screens.newStockTransfer(
                    route.sourceWarehouseId,
                    {
                        navigationController.navController.popBackStack(
                            route = ZyntaRoute.StockTransferList,
                            inclusive = false,
                        )
                    },
                    { navigationController.popBackStack() },
                )
            }
        }

        // ── Warehouse Rack screens  (Sprint 18) ─────────────────────────────────
        composable<ZyntaRoute.WarehouseRackList> { entry ->
            val route = entry.toRoute<ZyntaRoute.WarehouseRackList>()
            screens.warehouseRackList(
                route.warehouseId,
                { rackId, whId -> navigationController.navigate(ZyntaRoute.WarehouseRackDetail(rackId, whId)) },
                { navigationController.popBackStack() },
            )
        }

        composable<ZyntaRoute.WarehouseRackDetail> { entry ->
            val route = entry.toRoute<ZyntaRoute.WarehouseRackDetail>()
            screens.warehouseRackDetail(
                route.rackId,
                route.warehouseId,
                { navigationController.navigateUp(ZyntaRoute.WarehouseRackList(route.warehouseId)) },
            )
        }

        // ── Accounting / E-Invoice sub-graph  (Sprint 18-24) ─────────────────
        navigation<ZyntaRoute.AccountingGraph>(startDestination = ZyntaRoute.AccountingLedger) {
            composable<ZyntaRoute.AccountingLedger> {
                screens.accountingLedger(
                    { accountCode, fiscalPeriod ->
                        navigationController.navigate(ZyntaRoute.AccountDetail(accountCode, fiscalPeriod))
                    },
                    { navigationController.navigateUp(ZyntaRoute.Dashboard) },
                )
            }
            composable<ZyntaRoute.AccountDetail> { entry ->
                val route = entry.toRoute<ZyntaRoute.AccountDetail>()
                screens.accountDetail(
                    route.accountCode,
                    route.fiscalPeriod,
                    { navigationController.navigateUp(ZyntaRoute.AccountingLedger) },
                )
            }
            composable<ZyntaRoute.EInvoiceList> {
                screens.eInvoiceList(
                    { invoiceId -> navigationController.navigate(ZyntaRoute.EInvoiceDetail(invoiceId)) },
                )
            }
            composable<ZyntaRoute.EInvoiceDetail> { entry ->
                val route = entry.toRoute<ZyntaRoute.EInvoiceDetail>()
                screens.eInvoiceDetail(
                    route.invoiceId,
                    { navigationController.navigateUp(ZyntaRoute.EInvoiceList) },
                )
            }
        }

        // ── Admin sub-graph  (Sprint 13-15) ─────────────────────────────────────
        navigation<ZyntaRoute.AdminGraph>(startDestination = ZyntaRoute.SystemHealthDashboard) {
            composable<ZyntaRoute.SystemHealthDashboard> {
                MainScaffoldShell(
                    navigationController = navigationController,
                    navItems = navItems,
                    currentRoute = ZyntaRoute.SystemHealthDashboard,
                ) {
                    screens.adminScreen { navigationController.popBackStack() }
                }
            }
            // These routes all land on the same tabbed AdminScreen; the caller is
            // responsible for dispatching SwitchTab to the correct initial tab.
            composable<ZyntaRoute.DatabaseMaintenance> {
                screens.adminScreen { navigationController.popBackStack() }
            }
            composable<ZyntaRoute.BackupManagement> {
                screens.adminScreen { navigationController.popBackStack() }
            }
            composable<ZyntaRoute.AuditLogViewer> {
                screens.adminScreen { navigationController.popBackStack() }
            }
        }

        // ── Staff sub-graph  (Sprint 8-12) ───────────────────────────────────────
        navigation<ZyntaRoute.StaffGraph>(startDestination = ZyntaRoute.EmployeeList) {
            composable<ZyntaRoute.EmployeeList> {
                MainScaffoldShell(
                    navigationController = navigationController,
                    navItems = navItems,
                    currentRoute = ZyntaRoute.EmployeeList,
                ) {
                    screens.staffScreen { navigationController.popBackStack() }
                }
            }
            // All staff sub-routes land on the unified StaffScreen; internal
            // tab/detail state is managed entirely by StaffViewModel.
            composable<ZyntaRoute.EmployeeDetail> {
                screens.staffScreen { navigationController.popBackStack() }
            }
            composable<ZyntaRoute.AttendanceDashboard> {
                screens.staffScreen { navigationController.popBackStack() }
            }
            composable<ZyntaRoute.AttendanceHistory> {
                screens.staffScreen { navigationController.popBackStack() }
            }
            composable<ZyntaRoute.LeaveManagement> {
                screens.staffScreen { navigationController.popBackStack() }
            }
            composable<ZyntaRoute.SubmitLeave> {
                screens.staffScreen { navigationController.popBackStack() }
            }
            composable<ZyntaRoute.ShiftScheduler> {
                screens.staffScreen { navigationController.popBackStack() }
            }
            composable<ZyntaRoute.PayrollDashboard> {
                screens.staffScreen { navigationController.popBackStack() }
            }
            composable<ZyntaRoute.PayrollDetail> {
                screens.staffScreen { navigationController.popBackStack() }
            }
        }

        // ── Notification inbox ───────────────────────────────────────────────
        composable<ZyntaRoute.NotificationInbox> {
            screens.notificationInbox(
                { navigationController.popBackStack() },
            )
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
            is ZyntaRoute.StockReport,
            is ZyntaRoute.CustomerReport,
            is ZyntaRoute.ExpenseReport -> item.route is ZyntaRoute.SalesReport

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

            // CRM sub-graph
            is ZyntaRoute.CustomerList,
            is ZyntaRoute.CustomerDetail,
            is ZyntaRoute.CustomerGroupList,
            is ZyntaRoute.CustomerWallet -> item.route is ZyntaRoute.CustomerList

            // Coupons sub-graph
            is ZyntaRoute.CouponList,
            is ZyntaRoute.CouponDetail -> item.route is ZyntaRoute.CouponList

            // Expenses sub-graph
            is ZyntaRoute.ExpenseList,
            is ZyntaRoute.ExpenseDetail,
            is ZyntaRoute.ExpenseCategoryList -> item.route is ZyntaRoute.ExpenseList

            // Multi-store sub-graph
            is ZyntaRoute.WarehouseList,
            is ZyntaRoute.WarehouseDetail,
            is ZyntaRoute.StockTransferList,
            is ZyntaRoute.NewStockTransfer,
            is ZyntaRoute.WarehouseRackList,
            is ZyntaRoute.WarehouseRackDetail -> item.route is ZyntaRoute.WarehouseList

            // Accounting / E-Invoice sub-graph
            is ZyntaRoute.AccountingLedger,
            is ZyntaRoute.AccountDetail,
            is ZyntaRoute.EInvoiceList,
            is ZyntaRoute.EInvoiceDetail -> item.route is ZyntaRoute.AccountingLedger

            // Admin sub-graph
            is ZyntaRoute.SystemHealthDashboard,
            is ZyntaRoute.DatabaseMaintenance,
            is ZyntaRoute.BackupManagement,
            is ZyntaRoute.AuditLogViewer -> item.route is ZyntaRoute.SystemHealthDashboard

            // Staff sub-graph
            is ZyntaRoute.EmployeeList,
            is ZyntaRoute.EmployeeDetail,
            is ZyntaRoute.AttendanceDashboard,
            is ZyntaRoute.AttendanceHistory,
            is ZyntaRoute.LeaveManagement,
            is ZyntaRoute.SubmitLeave,
            is ZyntaRoute.ShiftScheduler,
            is ZyntaRoute.PayrollDashboard,
            is ZyntaRoute.PayrollDetail -> item.route is ZyntaRoute.EmployeeList

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
