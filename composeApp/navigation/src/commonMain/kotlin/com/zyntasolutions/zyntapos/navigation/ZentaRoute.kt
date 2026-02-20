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
 *   `zyntapos://product/{barcode}`  → [ZentaRoute.ProductDetail]
 *   `zyntapos://order/{orderId}`    → [ZentaRoute.OrderHistory]
 *
 * @see ZentaNavGraph
 * @see NavigationController
 */
sealed class ZentaRoute {

    // ─────────────────────────────────────────────────────────────────
    // AUTH GROUP
    // ─────────────────────────────────────────────────────────────────

    /**
     * Email / password login screen.
     * Start destination before a valid session is established.
     */
    @Serializable
    data object Login : ZentaRoute()

    /**
     * PIN lock screen displayed after an idle timeout while authenticated.
     * Navigated to automatically by [NavigationController.lockScreen].
     */
    @Serializable
    data object PinLock : ZentaRoute()

    // ─────────────────────────────────────────────────────────────────
    // MAIN GROUP
    // ─────────────────────────────────────────────────────────────────

    /** Home dashboard — KPI cards, recent activity, quick actions. */
    @Serializable
    data object Dashboard : ZentaRoute()

    /** POS checkout interface — product grid, cart, barcode scan. */
    @Serializable
    data object Pos : ZentaRoute()

    /**
     * Payment flow modal — payment method selection, numpad, change calc.
     *
     * @param orderId Identifier of the active order being paid.
     */
    @Serializable
    data class Payment(val orderId: String) : ZentaRoute()

    // ─────────────────────────────────────────────────────────────────
    // INVENTORY GROUP
    // ─────────────────────────────────────────────────────────────────

    /** Product list/grid with search and filter. */
    @Serializable
    data object ProductList : ZentaRoute()

    /**
     * Product detail / edit screen.
     *
     * @param productId Existing product ID for edit mode; `null` for create mode.
     *                  Deep-link: `zyntapos://product/{barcode}` passes barcode as productId.
     */
    @Serializable
    data class ProductDetail(val productId: String? = null) : ZentaRoute()

    /** Category management list. */
    @Serializable
    data object CategoryList : ZentaRoute()

    /** Supplier management list. */
    @Serializable
    data object SupplierList : ZentaRoute()

    // ─────────────────────────────────────────────────────────────────
    // REGISTER GROUP
    // ─────────────────────────────────────────────────────────────────

    /** Register dashboard — current session status, quick actions. */
    @Serializable
    data object RegisterDashboard : ZentaRoute()

    /** Open-register wizard — starting float entry, confirmation. */
    @Serializable
    data object OpenRegister : ZentaRoute()

    /** Close-register wizard — count, Z-report preview, finalization. */
    @Serializable
    data object CloseRegister : ZentaRoute()

    // ─────────────────────────────────────────────────────────────────
    // REPORTS GROUP
    // ─────────────────────────────────────────────────────────────────

    /** Sales report — period selector, revenue, transaction count, export. */
    @Serializable
    data object SalesReport : ZentaRoute()

    /** Stock report — inventory value, low-stock alerts, movement history. */
    @Serializable
    data object StockReport : ZentaRoute()

    // ─────────────────────────────────────────────────────────────────
    // SETTINGS GROUP
    // ─────────────────────────────────────────────────────────────────

    /** Top-level settings menu. */
    @Serializable
    data object Settings : ZentaRoute()

    /** Thermal printer and hardware configuration. */
    @Serializable
    data object PrinterSettings : ZentaRoute()

    /** Tax group and rate configuration. */
    @Serializable
    data object TaxSettings : ZentaRoute()

    /** User account management (admin only). */
    @Serializable
    data object UserManagement : ZentaRoute()

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
    data class OrderHistory(val orderId: String) : ZentaRoute()
}
