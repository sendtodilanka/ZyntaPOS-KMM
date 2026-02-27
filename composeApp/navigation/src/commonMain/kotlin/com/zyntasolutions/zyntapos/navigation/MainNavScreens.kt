package com.zyntasolutions.zyntapos.navigation

import androidx.compose.runtime.Composable

/**
 * Composable factory contract for all screens in the authenticated area.
 *
 * This data class acts as a dependency-injection point for screen composables,
 * keeping [MainNavGraph] fully decoupled from feature module implementations.
 * Each feature module supplies its composable via this contract at the app level
 * (`App.kt` / `MainActivity.kt` / `main.kt`).
 *
 * Callback naming convention: `onNavigate*` for outgoing navigation events,
 * `onComplete` / `onCancel` for terminal actions within a flow.
 *
 * NOTE: All lambdas are `@Composable` because they are invoked inside a
 * `composable {}` scope within the NavGraph. Non-composable lambdas would
 * break the Compose runtime's recomposition tracking.
 */
data class MainNavScreens(

    // ── Main group ────────────────────────────────────────────────────────────
    val dashboard: @Composable (
        onNavigateToPos: () -> Unit,
        onNavigateToRegister: () -> Unit,
        onNavigateToReports: () -> Unit,
        onNavigateToSettings: () -> Unit,
    ) -> Unit,

    val pos: @Composable (
        onNavigateToPayment: (orderId: String) -> Unit,
    ) -> Unit,

    val payment: @Composable (
        orderId: String,
        onPaymentComplete: () -> Unit,
        onCancel: () -> Unit,
    ) -> Unit,

    // ── Inventory sub-graph ───────────────────────────────────────────────────
    val productList: @Composable (
        onNavigateToDetail: (productId: String?) -> Unit,
        onNavigateToCategories: () -> Unit,
        onNavigateToSuppliers: () -> Unit,
        onNavigateToPrintLabels: () -> Unit,
    ) -> Unit,

    val productDetail: @Composable (
        productId: String?,
        onNavigateUp: () -> Unit,
    ) -> Unit,

    val categoryList: @Composable (
        onNavigateUp: () -> Unit,
    ) -> Unit,

    val supplierList: @Composable (
        onNavigateUp: () -> Unit,
    ) -> Unit,

    val barcodeLabelPrint: @Composable (
        initialProductId: String?,
        onNavigateBack: () -> Unit,
    ) -> Unit,

    // ── Register sub-graph ────────────────────────────────────────────────────
    val registerDashboard: @Composable (
        onOpenRegister: () -> Unit,
        onCloseRegister: () -> Unit,
    ) -> Unit,

    val openRegister: @Composable (
        onComplete: () -> Unit,
    ) -> Unit,

    val closeRegister: @Composable (
        onComplete: () -> Unit,
    ) -> Unit,

    // ── Reports sub-graph ─────────────────────────────────────────────────────
    val salesReport: @Composable () -> Unit,
    val stockReport: @Composable () -> Unit,
    val customerReport: @Composable () -> Unit,
    val expenseReport: @Composable () -> Unit,

    // ── Settings sub-graph ────────────────────────────────────────────────────
    val settings: @Composable (
        onNavigateToRoute: (settingsRoute: String) -> Unit,
    ) -> Unit,

    val printerSettings: @Composable (
        onNavigateUp: () -> Unit,
    ) -> Unit,

    val taxSettings: @Composable (
        onNavigateUp: () -> Unit,
    ) -> Unit,

    val userManagement: @Composable (
        onNavigateUp: () -> Unit,
    ) -> Unit,

    val generalSettings: @Composable (
        onNavigateUp: () -> Unit,
    ) -> Unit,

    val appearanceSettings: @Composable (
        onNavigateUp: () -> Unit,
    ) -> Unit,

    val aboutSettings: @Composable (
        onNavigateUp: () -> Unit,
    ) -> Unit,

    val backupSettings: @Composable (
        onNavigateUp: () -> Unit,
    ) -> Unit,

    val posSettings: @Composable (
        onNavigateUp: () -> Unit,
    ) -> Unit,

    val systemHealthSettings: @Composable (
        onNavigateUp: () -> Unit,
    ) -> Unit,

    val securitySettings: @Composable (
        onNavigateUp: () -> Unit,
        onNavigateToRbacManagement: () -> Unit,
    ) -> Unit,

    val rbacManagement: @Composable (
        onNavigateUp: () -> Unit,
    ) -> Unit,

    // ── Deep-link target ──────────────────────────────────────────────────────
    val orderHistory: @Composable (
        orderId: String,
        onNavigateUp: () -> Unit,
    ) -> Unit,

    // ── CRM sub-graph ─────────────────────────────────────────────────────────
    val customerList: @Composable (
        onNavigateToDetail: (customerId: String?) -> Unit,
        onNavigateToGroups: () -> Unit,
    ) -> Unit,

    val customerDetail: @Composable (
        customerId: String?,
        onNavigateUp: () -> Unit,
        onNavigateToWallet: (customerId: String) -> Unit,
    ) -> Unit,

    val customerGroupList: @Composable (
        onNavigateUp: () -> Unit,
    ) -> Unit,

    val customerWallet: @Composable (
        customerId: String,
        onNavigateUp: () -> Unit,
    ) -> Unit,

    // ── Coupons sub-graph ─────────────────────────────────────────────────────
    val couponList: @Composable (
        onNavigateToDetail: (couponId: String?) -> Unit,
    ) -> Unit,

    val couponDetail: @Composable (
        couponId: String?,
        onNavigateUp: () -> Unit,
    ) -> Unit,

    // ── Expenses sub-graph ────────────────────────────────────────────────────
    val expenseList: @Composable (
        onNavigateToDetail: (expenseId: String?) -> Unit,
        onNavigateToCategories: () -> Unit,
    ) -> Unit,

    val expenseDetail: @Composable (
        expenseId: String?,
        onNavigateUp: () -> Unit,
    ) -> Unit,

    val expenseCategoryList: @Composable (
        onNavigateUp: () -> Unit,
    ) -> Unit,

    // ── Multi-store sub-graph ─────────────────────────────────────────────────
    val warehouseList: @Composable (
        onNavigateToDetail: (warehouseId: String?) -> Unit,
        onNavigateToTransfers: () -> Unit,
    ) -> Unit,

    val warehouseDetail: @Composable (
        warehouseId: String?,
        onNavigateUp: () -> Unit,
    ) -> Unit,

    val stockTransferList: @Composable (
        onNavigateToNewTransfer: (sourceWarehouseId: String?) -> Unit,
        onNavigateUp: () -> Unit,
    ) -> Unit,

    val newStockTransfer: @Composable (
        sourceWarehouseId: String?,
        onComplete: () -> Unit,
        onCancel: () -> Unit,
    ) -> Unit,

    // ── Warehouse Racks sub-graph  (Sprint 18) ────────────────────────────────
    val warehouseRackList: @Composable (
        warehouseId: String,
        onNavigateToDetail: (rackId: String?, warehouseId: String) -> Unit,
        onNavigateUp: () -> Unit,
    ) -> Unit,

    val warehouseRackDetail: @Composable (
        rackId: String?,
        warehouseId: String,
        onNavigateUp: () -> Unit,
    ) -> Unit,

    // ── Accounting / E-Invoice sub-graph  (Sprint 18-24) ─────────────────────
    val accountingLedger: @Composable (
        onNavigateToDetail: (accountCode: String, fiscalPeriod: String) -> Unit,
        onNavigateUp: () -> Unit,
    ) -> Unit,

    val accountDetail: @Composable (
        accountCode: String,
        fiscalPeriod: String,
        onNavigateUp: () -> Unit,
    ) -> Unit,

    val eInvoiceList: @Composable (
        onNavigateToDetail: (invoiceId: String) -> Unit,
    ) -> Unit,

    val eInvoiceDetail: @Composable (
        invoiceId: String?,
        onNavigateUp: () -> Unit,
    ) -> Unit,

    // ── Wave 4B: Chart of Accounts, Journal Entries, Financial Statements ──────
    val chartOfAccounts: @Composable (
        onNavigateToAccountDetail: (accountId: String?) -> Unit,
        onNavigateBack: () -> Unit,
    ) -> Unit,

    val accountManagementDetail: @Composable (
        accountId: String?,
        storeId: String,
        onNavigateBack: () -> Unit,
    ) -> Unit,

    val journalEntryList: @Composable (
        storeId: String,
        onNavigateToEntry: (entryId: String?) -> Unit,
        onNavigateBack: () -> Unit,
    ) -> Unit,

    val journalEntryDetail: @Composable (
        entryId: String?,
        storeId: String,
        createdBy: String,
        onNavigateBack: () -> Unit,
        onNavigateToEntry: (entryId: String) -> Unit,
    ) -> Unit,

    val financialStatements: @Composable (
        storeId: String,
        onNavigateBack: () -> Unit,
    ) -> Unit,

    val generalLedger: @Composable (
        storeId: String,
        initialAccountId: String?,
        onNavigateBack: () -> Unit,
    ) -> Unit,

    // ── Admin sub-graph  (Sprint 13-15) ──────────────────────────────────────
    val adminScreen: @Composable (
        onNavigateUp: () -> Unit,
    ) -> Unit,

    // ── Staff sub-graph  (Sprint 8-12) ────────────────────────────────────────
    val staffScreen: @Composable (
        onNavigateUp: () -> Unit,
    ) -> Unit,

    // ── Notifications ─────────────────────────────────────────────────────────
    val notificationInbox: @Composable (
        onNavigateUp: () -> Unit,
    ) -> Unit,
)
