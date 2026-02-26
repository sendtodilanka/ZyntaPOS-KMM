package com.zyntasolutions.zyntapos.navigation

import kotlinx.serialization.Serializable

/**
 * Type-safe navigation route hierarchy for ZyntaPOS.
 *
 * Every destination in the application is represented as a `@Serializable`
 * sealed class or object so that the Compose Navigation type-safe API can
 * serialize/deserialize routes without relying on string paths.
 *
 * Grouping convention:
 * - Auth group  — unauthenticated / lock-screen flows
 * - Main group  — top-level authenticated destinations (dashboard, POS)
 * - Inventory   — product / category / supplier management
 * - Register    — shift open/close lifecycle
 * - Reports     — sales & stock reporting
 * - Settings    — configuration screens
 *
 * Deep-link URI scheme: `zyntapos://`
 * Examples:
 *   `zyntapos://product/{barcode}`  → [ZyntaRoute.ProductDetail]
 *   `zyntapos://order/{orderId}`    → [ZyntaRoute.OrderHistory]
 *
 * @see ZyntaNavGraph
 * @see NavigationController
 */
sealed class ZyntaRoute {

    // ─────────────────────────────────────────────────────────────────
    // GRAPH ROUTES (used as nested-graph identifiers, NOT destinations)
    // ─────────────────────────────────────────────────────────────────

    /** Graph route for the unauthenticated auth flow (Login, PinLock). */
    @Serializable data object AuthGraph : ZyntaRoute()

    /** Graph route for the authenticated main area. */
    @Serializable data object MainGraph : ZyntaRoute()

    /** Graph route for the inventory sub-graph. */
    @Serializable data object InventoryGraph : ZyntaRoute()

    /** Graph route for the register sub-graph. */
    @Serializable data object RegisterGraph : ZyntaRoute()

    /** Graph route for the reports sub-graph. */
    @Serializable data object ReportsGraph : ZyntaRoute()

    /** Graph route for the settings sub-graph. */
    @Serializable data object SettingsGraph : ZyntaRoute()

    /** Graph route for the CRM / customers sub-graph. */
    @Serializable data object CrmGraph : ZyntaRoute()

    /** Graph route for the coupons & promotions sub-graph. */
    @Serializable data object CouponsGraph : ZyntaRoute()

    /** Graph route for the expenses sub-graph. */
    @Serializable data object ExpensesGraph : ZyntaRoute()

    /** Graph route for the multi-store / warehouses sub-graph. */
    @Serializable data object MultiStoreGraph : ZyntaRoute()

    // ─────────────────────────────────────────────────────────────────
    // AUTH GROUP
    // ─────────────────────────────────────────────────────────────────

    /**
     * First-run onboarding wizard.
     * Shown exactly once before the login screen if `onboarding.completed` is
     * not set in [SettingsRepository]. Collects business name + admin credentials.
     */
    @Serializable
    data object Onboarding : ZyntaRoute()

    /**
     * Email / password login screen.
     * Start destination before a valid session is established.
     */
    @Serializable
    data object Login : ZyntaRoute()

    /**
     * Account registration screen — creates a new local user.
     * Navigated to from the Login screen via "Sign Up" link.
     */
    @Serializable
    data object SignUp : ZyntaRoute()

    /**
     * PIN lock screen displayed after an idle timeout while authenticated.
     * Navigated to automatically by [NavigationController.lockScreen].
     */
    @Serializable
    data object PinLock : ZyntaRoute()

    // ─────────────────────────────────────────────────────────────────
    // MAIN GROUP
    // ─────────────────────────────────────────────────────────────────

    /** Home dashboard — KPI cards, recent activity, quick actions. */
    @Serializable
    data object Dashboard : ZyntaRoute()

    /** POS checkout interface — product grid, cart, barcode scan. */
    @Serializable
    data object Pos : ZyntaRoute()

    /**
     * Payment flow modal — payment method selection, numpad, change calc.
     *
     * @param orderId Identifier of the active order being paid.
     */
    @Serializable
    data class Payment(val orderId: String) : ZyntaRoute()

    // ─────────────────────────────────────────────────────────────────
    // INVENTORY GROUP
    // ─────────────────────────────────────────────────────────────────

    /** Product list/grid with search and filter. */
    @Serializable
    data object ProductList : ZyntaRoute()

    /**
     * Product detail / edit screen.
     *
     * @param productId Existing product ID for edit mode; `null` for create mode.
     *                  Deep-link: `zyntapos://product/{barcode}` passes barcode as productId.
     */
    @Serializable
    data class ProductDetail(val productId: String? = null) : ZyntaRoute()

    /** Category management list. */
    @Serializable
    data object CategoryList : ZyntaRoute()

    /** Supplier management list. */
    @Serializable
    data object SupplierList : ZyntaRoute()

    // ─────────────────────────────────────────────────────────────────
    // REGISTER GROUP
    // ─────────────────────────────────────────────────────────────────

    /** Register dashboard — current session status, quick actions. */
    @Serializable
    data object RegisterDashboard : ZyntaRoute()

    /** Open-register wizard — starting float entry, confirmation. */
    @Serializable
    data object OpenRegister : ZyntaRoute()

    /** Close-register wizard — count, Z-report preview, finalization. */
    @Serializable
    data object CloseRegister : ZyntaRoute()

    // ─────────────────────────────────────────────────────────────────
    // REPORTS GROUP
    // ─────────────────────────────────────────────────────────────────

    /** Sales report — period selector, revenue, transaction count, export. */
    @Serializable
    data object SalesReport : ZyntaRoute()

    /** Stock report — inventory value, low-stock alerts, movement history. */
    @Serializable
    data object StockReport : ZyntaRoute()

    /** Customer report — acquisition, loyalty, order frequency analytics. */
    @Serializable
    data object CustomerReport : ZyntaRoute()

    /** Expense report — breakdown by category and period with P&L view. */
    @Serializable
    data object ExpenseReport : ZyntaRoute()

    // ─────────────────────────────────────────────────────────────────
    // SETTINGS GROUP
    // ─────────────────────────────────────────────────────────────────

    /** Top-level settings menu. */
    @Serializable
    data object Settings : ZyntaRoute()

    /** Thermal printer and hardware configuration. */
    @Serializable
    data object PrinterSettings : ZyntaRoute()

    /** Tax group and rate configuration. */
    @Serializable
    data object TaxSettings : ZyntaRoute()

    /** User account management (admin only). */
    @Serializable
    data object UserManagement : ZyntaRoute()

    /** General store configuration (name, address, currency, logo). */
    @Serializable
    data object GeneralSettings : ZyntaRoute()

    /** Appearance configuration (theme mode, dynamic color). */
    @Serializable
    data object AppearanceSettings : ZyntaRoute()

    /** About screen (version, licenses, legal). */
    @Serializable
    data object AboutSettings : ZyntaRoute()

    /** Backup & restore settings. */
    @Serializable
    data object BackupSettings : ZyntaRoute()

    /** POS-specific settings (receipt format, payment defaults). */
    @Serializable
    data object PosSettings : ZyntaRoute()

    /** System health diagnostics (memory, disk, database, runtime). */
    @Serializable
    data object SystemHealthSettings : ZyntaRoute()

    /** Security policy settings (PIN, session timeout, RBAC). */
    @Serializable
    data object SecuritySettings : ZyntaRoute()

    /** Role and permission matrix management (RBAC). */
    @Serializable
    data object RbacManagement : ZyntaRoute()

    /**
     * In-app developer console (debug builds only).
     * Access is gated at two levels:
     *   1. [AppInfoProvider.isDebug] — composable only registered when true
     *   2. [Role.ADMIN] — RBAC check inside the screen
     */
    @Serializable
    data object Debug : ZyntaRoute()

    // ─────────────────────────────────────────────────────────────────
    // CRM GROUP
    // ─────────────────────────────────────────────────────────────────

    /** Customer directory list with search and loyalty info. */
    @Serializable
    data object CustomerList : ZyntaRoute()

    /**
     * Customer profile detail / edit.
     *
     * @param customerId Existing ID for edit; `null` for create.
     */
    @Serializable
    data class CustomerDetail(val customerId: String? = null) : ZyntaRoute()

    /** Customer group management list (pricing tiers, discounts). */
    @Serializable
    data object CustomerGroupList : ZyntaRoute()

    /** Customer wallet and transaction history. */
    @Serializable
    data class CustomerWallet(val customerId: String) : ZyntaRoute()

    // ─────────────────────────────────────────────────────────────────
    // COUPONS GROUP
    // ─────────────────────────────────────────────────────────────────

    /** Coupon and promotion management list. */
    @Serializable
    data object CouponList : ZyntaRoute()

    /**
     * Coupon detail / edit.
     *
     * @param couponId Existing coupon ID for edit; `null` for create.
     */
    @Serializable
    data class CouponDetail(val couponId: String? = null) : ZyntaRoute()

    // ─────────────────────────────────────────────────────────────────
    // EXPENSES GROUP
    // ─────────────────────────────────────────────────────────────────

    /** Expense log with filter and date range picker. */
    @Serializable
    data object ExpenseList : ZyntaRoute()

    /**
     * Expense detail / edit with receipt attachment and approval actions.
     *
     * @param expenseId Existing expense ID for edit; `null` for create.
     */
    @Serializable
    data class ExpenseDetail(val expenseId: String? = null) : ZyntaRoute()

    /** Expense category management. */
    @Serializable
    data object ExpenseCategoryList : ZyntaRoute()

    // ─────────────────────────────────────────────────────────────────
    // MULTI-STORE GROUP
    // ─────────────────────────────────────────────────────────────────

    /** Warehouse list for the current store. */
    @Serializable
    data object WarehouseList : ZyntaRoute()

    /**
     * Warehouse detail / edit.
     *
     * @param warehouseId Existing ID for edit; `null` for create.
     */
    @Serializable
    data class WarehouseDetail(val warehouseId: String? = null) : ZyntaRoute()

    /** Stock transfer list — pending and committed transfers. */
    @Serializable
    data object StockTransferList : ZyntaRoute()

    /**
     * New stock transfer creation screen.
     *
     * @param sourceWarehouseId Pre-selected source warehouse, if any.
     */
    @Serializable
    data class NewStockTransfer(val sourceWarehouseId: String? = null) : ZyntaRoute()

    // ─────────────────────────────────────────────────────────────────
    // NOTIFICATIONS
    // ─────────────────────────────────────────────────────────────────

    /** In-app notification inbox. */
    @Serializable
    data object NotificationInbox : ZyntaRoute()

    // ─────────────────────────────────────────────────────────────────
    // STAFF / HR GROUP  (Phase 3 — Sprint 8-12)
    // ─────────────────────────────────────────────────────────────────

    /** Graph route for the Staff/HR sub-graph. */
    @Serializable data object StaffGraph : ZyntaRoute()

    /** Employee directory list with search and status filter. */
    @Serializable
    data object EmployeeList : ZyntaRoute()

    /**
     * Employee profile detail / edit.
     *
     * @param employeeId Existing ID for edit; `null` for create.
     */
    @Serializable
    data class EmployeeDetail(val employeeId: String? = null) : ZyntaRoute()

    /** Attendance management — daily clock-in/out for a store. */
    @Serializable
    data object AttendanceDashboard : ZyntaRoute()

    /** Attendance history for a single employee. */
    @Serializable
    data class AttendanceHistory(val employeeId: String) : ZyntaRoute()

    /** Leave request management — approve / reject pending requests. */
    @Serializable
    data object LeaveManagement : ZyntaRoute()

    /** Submit a new leave request. */
    @Serializable
    data class SubmitLeave(val employeeId: String) : ZyntaRoute()

    /** Weekly shift scheduler — drag-and-drop shift grid. */
    @Serializable
    data object ShiftScheduler : ZyntaRoute()

    /** Payroll overview — current period, pending payments. */
    @Serializable
    data object PayrollDashboard : ZyntaRoute()

    /** Payroll detail for a specific employee + period. */
    @Serializable
    data class PayrollDetail(val employeeId: String, val periodStart: String) : ZyntaRoute()

    // ─────────────────────────────────────────────────────────────────
    // ADMIN GROUP  (Phase 3 — Sprint 13-15)
    // ─────────────────────────────────────────────────────────────────

    /** Graph route for the Admin sub-graph. */
    @Serializable data object AdminGraph : ZyntaRoute()

    /** System health dashboard — memory, DB size, sync status, connectivity. */
    @Serializable
    data object SystemHealthDashboard : ZyntaRoute()

    /** Database statistics and maintenance — row counts, VACUUM, purge. */
    @Serializable
    data object DatabaseMaintenance : ZyntaRoute()

    /** Backup management list — create, restore, delete, export. */
    @Serializable
    data object BackupManagement : ZyntaRoute()

    /** Audit log viewer — security events and data changes. */
    @Serializable
    data object AuditLogViewer : ZyntaRoute()

    // ─────────────────────────────────────────────────────────────────
    // MEDIA GROUP  (Phase 3 — Sprint 16-17)
    // ─────────────────────────────────────────────────────────────────

    /** Graph route for the Media sub-graph. */
    @Serializable data object MediaGraph : ZyntaRoute()

    /** Media library — grid view of all uploaded assets. */
    @Serializable
    data object MediaLibrary : ZyntaRoute()

    /**
     * Media picker — select or capture an image to attach to an entity.
     *
     * @param entityType Polymorphic entity type ('Product', 'Employee', etc.).
     * @param entityId   ID of the entity the image will be attached to.
     */
    @Serializable
    data class MediaPicker(val entityType: String, val entityId: String) : ZyntaRoute()

    // ─────────────────────────────────────────────────────────────────
    // ACCOUNTING / E-INVOICE GROUP  (Phase 3 — Sprint 18-24)
    // ─────────────────────────────────────────────────────────────────

    /** Graph route for the Accounting/E-Invoice sub-graph. */
    @Serializable data object AccountingGraph : ZyntaRoute()

    /** Double-entry accounting ledger — period view, account balances. */
    @Serializable
    data object AccountingLedger : ZyntaRoute()

    /** Account detail — all entries for a single account in a fiscal period. */
    @Serializable
    data class AccountDetail(val accountCode: String, val fiscalPeriod: String) : ZyntaRoute()

    /** E-Invoice list — status filter (DRAFT, SUBMITTED, ACCEPTED, REJECTED). */
    @Serializable
    data object EInvoiceList : ZyntaRoute()

    /**
     * E-Invoice detail / creation screen.
     *
     * @param invoiceId Existing invoice ID for detail view; `null` to create from an order.
     * @param orderId   Source order ID when creating a new invoice.
     */
    @Serializable
    data class EInvoiceDetail(
        val invoiceId: String? = null,
        val orderId: String? = null,
    ) : ZyntaRoute()

    // ─────────────────────────────────────────────────────────────────
    // WAREHOUSE RACKS GROUP  (Phase 3)
    // ─────────────────────────────────────────────────────────────────

    /** Warehouse rack list for a specific warehouse. */
    @Serializable
    data class WarehouseRackList(val warehouseId: String) : ZyntaRoute()

    /**
     * Warehouse rack detail / edit.
     *
     * @param rackId     Existing rack ID for edit; `null` for create.
     * @param warehouseId Parent warehouse ID (required for create mode).
     */
    @Serializable
    data class WarehouseRackDetail(
        val rackId: String? = null,
        val warehouseId: String,
    ) : ZyntaRoute()

    // ─────────────────────────────────────────────────────────────────
    // DEEP-LINK TARGETS (not primary nav destinations)
    // ─────────────────────────────────────────────────────────────────

    /**
     * Order history detail — navigated to via notification deep-link.
     * Deep-link: `zyntapos://order/{orderId}`
     *
     * @param orderId Order to display.
     */
    @Serializable
    data class OrderHistory(val orderId: String) : ZyntaRoute()
}
