package com.zyntasolutions.zyntapos.domain.usecase.rbac

import com.zyntasolutions.zyntapos.domain.model.Permission
import com.zyntasolutions.zyntapos.domain.model.PermissionGroup
import com.zyntasolutions.zyntapos.domain.model.PermissionItem

/**
 * Default implementation of [GetPermissionsTreeUseCase].
 *
 * Returns the canonical, app-wide grouping of every [Permission] organised
 * by the module it belongs to. Used by the RBAC role editor (Sprint 23 task
 * 23.5) to render the permission tree with module-level [PermissionGroup]
 * headers and per-permission [PermissionItem] rows.
 *
 * ### Invariants enforced by [GetPermissionsTreeUseCaseImplTest]
 * * Every value in `Permission.entries` appears in **exactly one** group —
 *   a missing or duplicated permission fails the test, which catches future
 *   additions to [Permission] that aren't slotted into a module.
 * * Group ordering is deterministic and matches the order roles are
 *   typically taught (POS → Register → Inventory → … → Multi-Store).
 *
 * ### Why a no-arg constructor
 * The catalogue is a pure mapping from the [Permission] enum — it has no
 * runtime state, no IO, and no platform dependencies. Keeping it
 * dependency-free means it can be `factoryOf(::GetPermissionsTreeUseCaseImpl)`
 * in Koin without further wiring.
 */
class GetPermissionsTreeUseCaseImpl : GetPermissionsTreeUseCase {

    override suspend fun invoke(): List<PermissionGroup> = TREE

    private companion object {
        private val TREE: List<PermissionGroup> = listOf(
            PermissionGroup(
                module = "POS",
                displayName = "POS Operations",
                permissions = listOf(
                    PermissionItem(Permission.PROCESS_SALE, "Process Sale", "Ring up new sale transactions."),
                    PermissionItem(Permission.VOID_ORDER, "Void Order", "Cancel a completed order."),
                    PermissionItem(Permission.APPLY_DISCOUNT, "Apply Discount", "Apply item or order-level discounts."),
                    PermissionItem(Permission.HOLD_ORDER, "Hold Order", "Park an order for later retrieval."),
                    PermissionItem(Permission.PROCESS_REFUND, "Process Refund", "Issue refunds and returns."),
                ),
            ),
            PermissionGroup(
                module = "REGISTER",
                displayName = "Cash Register",
                permissions = listOf(
                    PermissionItem(Permission.OPEN_REGISTER, "Open Register", "Open a new cash-register session."),
                    PermissionItem(Permission.CLOSE_REGISTER, "Close Register", "Close the current session and generate the Z-Report."),
                    PermissionItem(Permission.RECORD_CASH_MOVEMENT, "Record Cash Movement", "Log petty-cash IN / OUT during a session."),
                ),
            ),
            PermissionGroup(
                module = "INVENTORY",
                displayName = "Inventory",
                permissions = listOf(
                    PermissionItem(Permission.MANAGE_PRODUCTS, "Manage Products", "Create, edit, and delete products."),
                    PermissionItem(Permission.MANAGE_CATEGORIES, "Manage Categories", "Create, edit, and delete product categories."),
                    PermissionItem(Permission.ADJUST_STOCK, "Adjust Stock", "Increase, decrease, or transfer stock manually."),
                    PermissionItem(Permission.MANAGE_SUPPLIERS, "Manage Suppliers", "Maintain suppliers and purchase orders."),
                    PermissionItem(Permission.MANAGE_STOCKTAKE, "Manage Stocktake", "Run physical counts and apply variances."),
                ),
            ),
            PermissionGroup(
                module = "STAFF",
                displayName = "Staff & HR",
                permissions = listOf(
                    PermissionItem(Permission.MANAGE_STAFF, "Manage Staff", "Employees, attendance, shifts, leave requests, and payroll."),
                ),
            ),
            PermissionGroup(
                module = "REPORTS",
                displayName = "Reports",
                permissions = listOf(
                    PermissionItem(Permission.VIEW_REPORTS, "View Reports", "Open sales, stock, and financial reports."),
                    PermissionItem(Permission.EXPORT_REPORTS, "Export Reports", "Export reports to CSV or PDF."),
                    PermissionItem(Permission.PRINT_INVOICE, "Print Invoice", "Print or download an A4 tax invoice."),
                ),
            ),
            PermissionGroup(
                module = "CUSTOMERS",
                displayName = "Customers & Loyalty",
                permissions = listOf(
                    PermissionItem(Permission.MANAGE_CUSTOMERS, "Manage Customers", "View, create, and edit customer profiles."),
                    PermissionItem(Permission.MANAGE_CUSTOMER_GROUPS, "Manage Customer Groups", "Pricing tiers and group discounts."),
                    PermissionItem(Permission.MANAGE_WALLETS, "Manage Wallets", "Credit, debit, and view customer wallet balances."),
                    PermissionItem(Permission.MANAGE_LOYALTY, "Manage Loyalty", "Loyalty tiers and reward points configuration."),
                ),
            ),
            PermissionGroup(
                module = "COUPONS",
                displayName = "Coupons & Promotions",
                permissions = listOf(
                    PermissionItem(Permission.MANAGE_COUPONS, "Manage Coupons", "Create, edit, activate, and deactivate coupons."),
                ),
            ),
            PermissionGroup(
                module = "EXPENSES",
                displayName = "Expenses",
                permissions = listOf(
                    PermissionItem(Permission.MANAGE_EXPENSES, "Manage Expenses", "Submit and edit expense records."),
                    PermissionItem(Permission.APPROVE_EXPENSES, "Approve Expenses", "Approve or reject expense submissions."),
                ),
            ),
            PermissionGroup(
                module = "SETTINGS",
                displayName = "Settings",
                permissions = listOf(
                    PermissionItem(Permission.MANAGE_USERS, "Manage Users", "Create, edit, deactivate user accounts and assign roles."),
                    PermissionItem(Permission.MANAGE_SETTINGS, "Manage Settings", "Modify application-wide settings."),
                    PermissionItem(Permission.MANAGE_TAX, "Manage Tax", "Maintain tax groups and tax rates."),
                    PermissionItem(Permission.MANAGE_HARDWARE, "Manage Hardware", "Configure printer and hardware settings."),
                ),
            ),
            PermissionGroup(
                module = "ADMIN",
                displayName = "Admin Console",
                permissions = listOf(
                    PermissionItem(Permission.ADMIN_ACCESS, "Admin Access", "Open the admin panel (system health, database, audit log)."),
                    PermissionItem(Permission.MANAGE_BACKUP, "Manage Backup", "Trigger manual backups and restores."),
                    PermissionItem(Permission.VIEW_AUDIT_LOG, "View Audit Log", "Inspect the security audit log."),
                ),
            ),
            PermissionGroup(
                module = "ACCOUNTING",
                displayName = "Accounting",
                permissions = listOf(
                    PermissionItem(Permission.MANAGE_ACCOUNTING, "Manage Accounting", "Double-entry GL ledger and journal entries."),
                ),
            ),
            PermissionGroup(
                module = "MULTI_STORE",
                displayName = "Multi-Store",
                permissions = listOf(
                    PermissionItem(Permission.MANAGE_WAREHOUSES, "Manage Warehouses", "Create, edit, and manage warehouses."),
                    PermissionItem(Permission.MANAGE_STOCK_TRANSFERS, "Manage Stock Transfers", "Initiate and commit inter-warehouse transfers."),
                ),
            ),
        )
    }
}
