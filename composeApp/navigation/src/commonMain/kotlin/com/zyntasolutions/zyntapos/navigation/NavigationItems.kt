package com.zyntasolutions.zyntapos.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.LocalOffer
import androidx.compose.material.icons.filled.ManageAccounts
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.PointOfSale
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Store
import androidx.compose.material.icons.outlined.AccountBalance
import androidx.compose.material.icons.outlined.AdminPanelSettings
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.Dashboard
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.LocalOffer
import androidx.compose.material.icons.outlined.ManageAccounts
import androidx.compose.material.icons.outlined.NotificationsNone
import androidx.compose.material.icons.outlined.People
import androidx.compose.material.icons.outlined.PointOfSale
import androidx.compose.material.icons.outlined.Receipt
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Store
import androidx.compose.ui.graphics.vector.ImageVector
import com.zyntasolutions.zyntapos.designsystem.layouts.ZyntaNavChildItem
import com.zyntasolutions.zyntapos.designsystem.layouts.ZyntaNavGroup
import com.zyntasolutions.zyntapos.designsystem.layouts.ZyntaNavItem
import com.zyntasolutions.zyntapos.domain.model.Permission
import com.zyntasolutions.zyntapos.domain.model.Role
import com.zyntasolutions.zyntapos.domain.model.ZyntaFeature

/**
 * Logical section a [NavItem] belongs to within the EXPANDED permanent drawer.
 *
 * The drawer renders a labelled section header each time the group changes as
 * items are iterated in [AllNavItems] order.
 */
enum class NavGroupKey {
    /** Core daily-operations destinations (Dashboard, POS, Inventory, Register, Reports). */
    OPERATIONS,

    /** CRM and financial management destinations (Customers, Coupons, Expenses, Warehouses). */
    MANAGEMENT,

    /** HR, payroll, and accounting destinations (Staff, Accounting). */
    HR_FINANCE,

    /** System-administration and configuration destinations (Admin, Notifications, Settings). */
    SYSTEM,
}

/** Human-readable labels for each [NavGroupKey] used in the EXPANDED drawer. */
val navGroupLabels: Map<NavGroupKey, String> = mapOf(
    NavGroupKey.OPERATIONS to "Operations",
    NavGroupKey.MANAGEMENT to "Management",
    NavGroupKey.HR_FINANCE to "HR & Finance",
    NavGroupKey.SYSTEM to "System",
)

/**
 * Descriptor for a child navigation destination nested beneath a primary [NavItem]
 * in the EXPANDED hierarchical drawer.
 *
 * @param route The [ZyntaRoute] this child item navigates to.
 * @param label Human-readable display label.
 */
data class NavChildItem(
    val route: ZyntaRoute,
    val label: String,
)

/**
 * Descriptor for a primary navigation destination within the authenticated area.
 *
 * @param route The [ZyntaRoute] this item navigates to.
 * @param label Human-readable display label.
 * @param icon Unselected icon vector.
 * @param selectedIcon Icon shown when this destination is active (defaults to [icon]).
 * @param requiredPermission The [Permission] a user must have to see this item.
 *   `null` means visible to all authenticated users.
 * @param group The [NavGroupKey] used to render section headers in the EXPANDED drawer.
 * @param featureGate The [ZyntaFeature] that must be enabled for this item to appear.
 *   `null` means no feature gate — the item is shown regardless of enabled features
 *   (subject only to [requiredPermission]).
 * @param children Optional child destinations displayed as an expandable sub-list in the
 *   EXPANDED hierarchical drawer.  Empty for flat items (no sub-navigation).
 */
data class NavItem(
    val route: ZyntaRoute,
    val label: String,
    val icon: ImageVector,
    val selectedIcon: ImageVector = icon,
    val requiredPermission: Permission? = null,
    val group: NavGroupKey = NavGroupKey.OPERATIONS,
    val featureGate: ZyntaFeature? = null,
    val children: List<NavChildItem> = emptyList(),
) {
    /** Convert to the design-system [ZyntaNavItem] for rendering in [ZyntaScaffold]. */
    fun toZyntaNavItem(): ZyntaNavItem = ZyntaNavItem(
        label = label,
        icon = icon,
        selectedIcon = selectedIcon,
        contentDescription = label,
        children = children.map { child -> ZyntaNavChildItem(label = child.label) },
    )
}

/**
 * Canonical list of all primary navigation destinations in the authenticated area.
 *
 * ### Ordering & Grouping
 * Items are grouped by [NavGroupKey] and ordered by daily-use frequency within
 * each group. This order determines:
 * - Rendering position in MEDIUM rail and EXPANDED drawer.
 * - Which items appear first in the COMPACT bottom bar (first [COMPACT_NAV_MAX_ITEMS]
 *   RBAC-filtered items, see [RbacNavFilter.compactForRole]).
 *
 * Because Operations items come first, the COMPACT bottom bar naturally shows
 * [Dashboard, POS, Inventory, Register, Reports] for Manager/Admin roles and
 * [Dashboard, POS, Register] for Cashier roles — all within the 5-item limit.
 */
val AllNavItems: List<NavItem> = listOf(

    // ── OPERATIONS group (Phase 1) ────────────────────────────────────────────

    NavItem(
        route = ZyntaRoute.Dashboard,
        label = "Dashboard",
        icon = Icons.Outlined.Dashboard,
        selectedIcon = Icons.Filled.Dashboard,
        requiredPermission = null, // visible to all roles
        group = NavGroupKey.OPERATIONS,
        featureGate = ZyntaFeature.DASHBOARD,
    ),
    NavItem(
        route = ZyntaRoute.Pos,
        label = "POS",
        icon = Icons.Outlined.PointOfSale,
        selectedIcon = Icons.Filled.PointOfSale,
        requiredPermission = Permission.PROCESS_SALE,
        group = NavGroupKey.OPERATIONS,
        featureGate = ZyntaFeature.POS_CORE,
    ),
    NavItem(
        route = ZyntaRoute.ProductList,
        label = "Inventory",
        icon = Icons.Outlined.Inventory2,
        selectedIcon = Icons.Filled.Inventory2,
        requiredPermission = Permission.MANAGE_PRODUCTS,
        group = NavGroupKey.OPERATIONS,
        featureGate = ZyntaFeature.INVENTORY_CORE,
        children = listOf(
            NavChildItem(route = ZyntaRoute.ProductList, label = "Products"),
            NavChildItem(route = ZyntaRoute.CategoryList, label = "Categories"),
            NavChildItem(route = ZyntaRoute.SupplierList, label = "Suppliers"),
        ),
    ),
    NavItem(
        route = ZyntaRoute.RegisterDashboard,
        label = "Register",
        icon = Icons.Outlined.GridView,
        selectedIcon = Icons.Filled.GridView,
        requiredPermission = Permission.OPEN_REGISTER,
        group = NavGroupKey.OPERATIONS,
        featureGate = ZyntaFeature.REGISTER,
    ),
    NavItem(
        route = ZyntaRoute.SalesReport,
        label = "Reports",
        icon = Icons.Outlined.BarChart,
        selectedIcon = Icons.Filled.BarChart,
        requiredPermission = Permission.VIEW_REPORTS,
        group = NavGroupKey.OPERATIONS,
        featureGate = ZyntaFeature.REPORTS_STANDARD,
        children = listOf(
            NavChildItem(route = ZyntaRoute.SalesReport, label = "Sales"),
            NavChildItem(route = ZyntaRoute.StockReport, label = "Stock"),
            NavChildItem(route = ZyntaRoute.CustomerReport, label = "Customers"),
            NavChildItem(route = ZyntaRoute.ExpenseReport, label = "Expenses"),
        ),
    ),

    // ── MANAGEMENT group (Phase 2) ────────────────────────────────────────────

    NavItem(
        route = ZyntaRoute.CustomerList,
        label = "Customers",
        icon = Icons.Outlined.People,
        selectedIcon = Icons.Filled.People,
        requiredPermission = Permission.MANAGE_CUSTOMERS,
        group = NavGroupKey.MANAGEMENT,
        featureGate = ZyntaFeature.CRM_LOYALTY,
        children = listOf(
            NavChildItem(route = ZyntaRoute.CustomerList, label = "Directory"),
            NavChildItem(route = ZyntaRoute.CustomerGroupList, label = "Groups"),
        ),
    ),
    NavItem(
        route = ZyntaRoute.CouponList,
        label = "Coupons",
        icon = Icons.Outlined.LocalOffer,
        selectedIcon = Icons.Filled.LocalOffer,
        requiredPermission = Permission.MANAGE_COUPONS,
        group = NavGroupKey.MANAGEMENT,
        featureGate = ZyntaFeature.COUPONS,
    ),
    NavItem(
        route = ZyntaRoute.ExpenseList,
        label = "Expenses",
        icon = Icons.Outlined.Receipt,
        selectedIcon = Icons.Filled.Receipt,
        requiredPermission = Permission.MANAGE_EXPENSES,
        group = NavGroupKey.MANAGEMENT,
        featureGate = ZyntaFeature.EXPENSES,
        children = listOf(
            NavChildItem(route = ZyntaRoute.ExpenseList, label = "Expenses"),
            NavChildItem(route = ZyntaRoute.ExpenseCategoryList, label = "Categories"),
        ),
    ),
    NavItem(
        route = ZyntaRoute.WarehouseList,
        label = "Warehouses",
        icon = Icons.Outlined.Store,
        selectedIcon = Icons.Filled.Store,
        requiredPermission = Permission.MANAGE_WAREHOUSES,
        group = NavGroupKey.MANAGEMENT,
        featureGate = ZyntaFeature.MULTISTORE,
        children = listOf(
            NavChildItem(route = ZyntaRoute.WarehouseList, label = "Warehouses"),
            NavChildItem(route = ZyntaRoute.StockTransferList, label = "Transfers"),
        ),
    ),

    // ── HR & FINANCE group (Phase 3) ──────────────────────────────────────────

    NavItem(
        route = ZyntaRoute.EmployeeList,
        label = "Staff",
        icon = Icons.Outlined.ManageAccounts,
        selectedIcon = Icons.Filled.ManageAccounts,
        requiredPermission = Permission.MANAGE_STAFF,
        group = NavGroupKey.HR_FINANCE,
        featureGate = ZyntaFeature.STAFF_HR,
    ),
    NavItem(
        route = ZyntaRoute.AccountingLedger,
        label = "Accounting",
        icon = Icons.Outlined.AccountBalance,
        selectedIcon = Icons.Filled.AccountBalance,
        requiredPermission = Permission.MANAGE_ACCOUNTING,
        group = NavGroupKey.HR_FINANCE,
        featureGate = ZyntaFeature.ACCOUNTING,
        children = listOf(
            NavChildItem(route = ZyntaRoute.AccountingLedger, label = "Ledger"),
            NavChildItem(route = ZyntaRoute.EInvoiceList, label = "E-Invoices"),
            NavChildItem(route = ZyntaRoute.ChartOfAccounts, label = "Chart of Accounts"),
            NavChildItem(route = ZyntaRoute.JournalEntryList, label = "Journal Entries"),
        ),
    ),

    // ── SYSTEM group (Phase 3) ────────────────────────────────────────────────

    NavItem(
        route = ZyntaRoute.SystemHealthDashboard,
        label = "Admin",
        icon = Icons.Outlined.AdminPanelSettings,
        selectedIcon = Icons.Filled.AdminPanelSettings,
        requiredPermission = Permission.ADMIN_ACCESS,
        group = NavGroupKey.SYSTEM,
        featureGate = ZyntaFeature.ADMIN,
        children = listOf(
            NavChildItem(route = ZyntaRoute.SystemHealthDashboard, label = "System Health"),
            NavChildItem(route = ZyntaRoute.DatabaseMaintenance, label = "Database"),
            NavChildItem(route = ZyntaRoute.BackupManagement, label = "Backups"),
            NavChildItem(route = ZyntaRoute.AuditLogViewer, label = "Audit Log"),
        ),
    ),
    NavItem(
        route = ZyntaRoute.NotificationInbox,
        label = "Notifications",
        icon = Icons.Outlined.NotificationsNone,
        selectedIcon = Icons.Filled.Notifications,
        requiredPermission = null, // visible to all roles
        group = NavGroupKey.SYSTEM,
        featureGate = null,
    ),
    NavItem(
        route = ZyntaRoute.Settings,
        label = "Settings",
        icon = Icons.Outlined.Settings,
        selectedIcon = Icons.Filled.Settings,
        requiredPermission = Permission.MANAGE_SETTINGS,
        group = NavGroupKey.SYSTEM,
        featureGate = ZyntaFeature.SETTINGS,
    ),
)

/**
 * Maximum number of items shown in the COMPACT bottom [NavigationBar].
 *
 * Material 3 recommends no more than 5 items in a bottom navigation bar to
 * prevent clipping and maintain legibility on small screens.
 */
const val COMPACT_NAV_MAX_ITEMS = 5

/**
 * Utility object that filters [AllNavItems] based on RBAC permissions for a given [Role].
 *
 * Uses [Permission.rolePermissions] as the single source of truth (defined in `:shared:domain`).
 * Items with `requiredPermission = null` are always included for authenticated users.
 *
 * Usage:
 * ```kotlin
 * val visible = RbacNavFilter.forRole(Role.CASHIER)
 * // → [Dashboard, POS, Register, Notifications]
 *
 * val compact = RbacNavFilter.compactForRole(Role.STORE_MANAGER)
 * // → [Dashboard, POS, Inventory, Register, Reports]  (first 5)
 *
 * val groups = RbacNavFilter.groupsForItems(visible)
 * // → [ZyntaNavGroup("Operations", 0, 3), ZyntaNavGroup("System", 3, 1)]
 * ```
 */
object RbacNavFilter {

    /**
     * Returns the full ordered subset of [AllNavItems] visible to a user with the given [role].
     *
     * @param role The authenticated user's [Role].
     * @return Ordered list of [NavItem] the user is permitted to see.
     */
    fun forRole(role: Role): List<NavItem> {
        val granted: Set<Permission> = Permission.rolePermissions[role] ?: emptySet()
        return AllNavItems.filter { item ->
            item.requiredPermission == null || item.requiredPermission in granted
        }
    }

    /**
     * Returns the subset of [AllNavItems] visible to a user with the given permission set.
     * Useful when permissions are loaded from the session without re-deriving the role.
     *
     * @param permissions The set of [Permission]s granted to the current user.
     */
    fun forPermissions(permissions: Set<Permission>): List<NavItem> =
        AllNavItems.filter { item ->
            item.requiredPermission == null || item.requiredPermission in permissions
        }

    /**
     * Returns the first [COMPACT_NAV_MAX_ITEMS] items from [forRole] for display
     * in the COMPACT bottom [NavigationBar].
     *
     * Because [AllNavItems] is ordered with Operations (the most-used destinations)
     * first, the trimmed list always presents the most relevant items for each role.
     */
    fun compactForRole(role: Role): List<NavItem> =
        forRole(role).take(COMPACT_NAV_MAX_ITEMS)

    /**
     * Returns the first [COMPACT_NAV_MAX_ITEMS] items from [forPermissions].
     */
    fun compactForPermissions(permissions: Set<Permission>): List<NavItem> =
        forPermissions(permissions).take(COMPACT_NAV_MAX_ITEMS)

    /**
     * Returns the full ordered subset of [AllNavItems] visible to a user with the given [role]
     * AND where the item's [NavItem.featureGate] is contained in [enabledFeatures].
     *
     * Items whose [NavItem.featureGate] is `null` are always included (no edition gate).
     *
     * @param role The authenticated user's [Role].
     * @param enabledFeatures The set of [ZyntaFeature]s active for the current store/edition.
     * @return Ordered list of [NavItem] the user is permitted to see and the store has enabled.
     */
    fun forRoleAndFeatures(
        role: Role,
        enabledFeatures: Set<ZyntaFeature>,
    ): List<NavItem> = forRole(role).filter { item ->
        item.featureGate == null || item.featureGate in enabledFeatures
    }

    /**
     * Builds [ZyntaNavGroup] section-header descriptors for the EXPANDED permanent drawer
     * based on a pre-filtered list of [NavItem]s (e.g., from [forRole]).
     *
     * Groups are computed dynamically so that only sections with visible items are shown,
     * and indices match positions within [filteredItems] (not [AllNavItems]).
     *
     * @param filteredItems Result of [forRole] or [forPermissions] for the current user.
     * @return List of [ZyntaNavGroup] in the order they appear in [filteredItems].
     */
    fun groupsForItems(filteredItems: List<NavItem>): List<ZyntaNavGroup> {
        val result = mutableListOf<ZyntaNavGroup>()
        var currentGroup: NavGroupKey? = null
        var groupStart = 0
        var groupCount = 0

        filteredItems.forEachIndexed { index, item ->
            if (item.group != currentGroup) {
                if (currentGroup != null && groupCount > 0) {
                    result.add(
                        ZyntaNavGroup(
                            title = navGroupLabels.getValue(currentGroup!!),
                            startIndex = groupStart,
                            itemCount = groupCount,
                        ),
                    )
                }
                currentGroup = item.group
                groupStart = index
                groupCount = 1
            } else {
                groupCount++
            }
        }

        if (currentGroup != null && groupCount > 0) {
            result.add(
                ZyntaNavGroup(
                    title = navGroupLabels.getValue(currentGroup!!),
                    startIndex = groupStart,
                    itemCount = groupCount,
                ),
            )
        }

        return result
    }
}
