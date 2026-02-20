package com.zyntasolutions.zyntapos.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.PointOfSale
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.Dashboard
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.PointOfSale
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.ui.graphics.vector.ImageVector
import com.zyntasolutions.zyntapos.designsystem.layouts.ZentaNavItem
import com.zyntasolutions.zyntapos.domain.model.Permission
import com.zyntasolutions.zyntapos.domain.model.Role

/**
 * Descriptor for a primary navigation destination within the authenticated area.
 *
 * @param route The [ZentaRoute] this item navigates to.
 * @param label Human-readable display label.
 * @param icon Unselected icon vector.
 * @param selectedIcon Icon shown when this destination is active (defaults to [icon]).
 * @param requiredPermission The [Permission] a user must have to see this item.
 *   `null` means visible to all authenticated users.
 */
data class NavItem(
    val route: ZentaRoute,
    val label: String,
    val icon: ImageVector,
    val selectedIcon: ImageVector = icon,
    val requiredPermission: Permission? = null,
) {
    /** Convert to the design-system [ZentaNavItem] for rendering in [ZentaScaffold]. */
    fun toZentaNavItem(): ZentaNavItem = ZentaNavItem(
        label = label,
        icon = icon,
        selectedIcon = selectedIcon,
        contentDescription = label,
    )
}

/**
 * Canonical list of all primary navigation destinations in the authenticated area.
 *
 * Ordered as they appear in the navigation bar/rail/drawer.
 * Each item carries a [NavItem.requiredPermission] that is evaluated at runtime
 * by [RbacNavFilter.forRole] to produce a user-visible subset.
 */
val AllNavItems: List<NavItem> = listOf(
    NavItem(
        route = ZentaRoute.Dashboard,
        label = "Dashboard",
        icon = Icons.Outlined.Dashboard,
        selectedIcon = Icons.Filled.Dashboard,
        requiredPermission = null, // visible to all roles
    ),
    NavItem(
        route = ZentaRoute.Pos,
        label = "POS",
        icon = Icons.Outlined.PointOfSale,
        selectedIcon = Icons.Filled.PointOfSale,
        requiredPermission = Permission.PROCESS_SALE,
    ),
    NavItem(
        route = ZentaRoute.ProductList,
        label = "Inventory",
        icon = Icons.Outlined.Inventory2,
        selectedIcon = Icons.Filled.Inventory2,
        requiredPermission = Permission.MANAGE_PRODUCTS,
    ),
    NavItem(
        route = ZentaRoute.RegisterDashboard,
        label = "Register",
        icon = Icons.Outlined.GridView,
        selectedIcon = Icons.Filled.GridView,
        requiredPermission = Permission.OPEN_REGISTER,
    ),
    NavItem(
        route = ZentaRoute.SalesReport,
        label = "Reports",
        icon = Icons.Outlined.BarChart,
        selectedIcon = Icons.Filled.BarChart,
        requiredPermission = Permission.VIEW_REPORTS,
    ),
    NavItem(
        route = ZentaRoute.Settings,
        label = "Settings",
        icon = Icons.Outlined.Settings,
        selectedIcon = Icons.Filled.Settings,
        requiredPermission = Permission.MANAGE_SETTINGS,
    ),
)

/**
 * Utility object that filters [AllNavItems] based on RBAC permissions for a given [Role].
 *
 * Uses [Permission.rolePermissions] as the single source of truth (defined in `:shared:domain`).
 * Items with `requiredPermission = null` are always included for authenticated users.
 *
 * Usage:
 * ```kotlin
 * val visible = RbacNavFilter.forRole(Role.CASHIER)
 * // → [Dashboard, POS, Register]
 * ```
 */
object RbacNavFilter {

    /**
     * Returns the subset of [AllNavItems] visible to a user with the given [role].
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
}
