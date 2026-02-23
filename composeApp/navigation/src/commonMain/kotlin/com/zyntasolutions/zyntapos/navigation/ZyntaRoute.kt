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

    // ─────────────────────────────────────────────────────────────────
    // AUTH GROUP
    // ─────────────────────────────────────────────────────────────────

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
