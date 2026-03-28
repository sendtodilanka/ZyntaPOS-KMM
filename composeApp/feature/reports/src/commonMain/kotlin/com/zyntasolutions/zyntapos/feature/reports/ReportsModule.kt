package com.zyntasolutions.zyntapos.feature.reports

import com.zyntasolutions.zyntapos.domain.printer.ReportPrinterPort
import com.zyntasolutions.zyntapos.domain.usecase.reports.GenerateCustomerReportUseCase
import com.zyntasolutions.zyntapos.domain.usecase.reports.GenerateExpenseReportUseCase
import com.zyntasolutions.zyntapos.domain.usecase.reports.GenerateSalesReportUseCase
import com.zyntasolutions.zyntapos.domain.usecase.reports.GenerateStockReportUseCase
import com.zyntasolutions.zyntapos.domain.usecase.reports.PrintReportUseCase
import com.zyntasolutions.zyntapos.domain.usecase.reports.enterprise.GenerateAccountingLedgerReportUseCase
import com.zyntasolutions.zyntapos.domain.usecase.reports.enterprise.GenerateAnnualSalesTrendReportUseCase
import com.zyntasolutions.zyntapos.domain.usecase.reports.enterprise.GenerateBackupHistoryReportUseCase
import com.zyntasolutions.zyntapos.domain.usecase.reports.enterprise.GenerateCOGSReportUseCase
import com.zyntasolutions.zyntapos.domain.usecase.reports.enterprise.GenerateCustomerLoyaltyReportUseCase
import com.zyntasolutions.zyntapos.domain.usecase.reports.enterprise.GenerateCustomerRetentionReportUseCase
import com.zyntasolutions.zyntapos.domain.usecase.reports.enterprise.GenerateExpenseSummaryByDeptReportUseCase
import com.zyntasolutions.zyntapos.domain.usecase.reports.enterprise.GenerateGrossMarginReportUseCase
import com.zyntasolutions.zyntapos.domain.usecase.reports.enterprise.GenerateHourlySalesReportUseCase
import com.zyntasolutions.zyntapos.domain.usecase.reports.enterprise.GenerateInterStoreTransferReportUseCase
import com.zyntasolutions.zyntapos.domain.usecase.reports.enterprise.GenerateInventoryValuationReportUseCase
import com.zyntasolutions.zyntapos.domain.usecase.reports.enterprise.GenerateLeaveBalanceReportUseCase
import com.zyntasolutions.zyntapos.domain.usecase.reports.enterprise.GenerateLowStockAlertReportUseCase
import com.zyntasolutions.zyntapos.domain.usecase.reports.enterprise.GenerateMultiStoreComparisonReportUseCase
import com.zyntasolutions.zyntapos.domain.usecase.reports.enterprise.GeneratePayrollSummaryReportUseCase
import com.zyntasolutions.zyntapos.domain.usecase.reports.enterprise.GeneratePermissionAuditReportUseCase
import com.zyntasolutions.zyntapos.domain.usecase.reports.enterprise.GeneratePurchaseOrderReportUseCase
import com.zyntasolutions.zyntapos.domain.usecase.reports.enterprise.GenerateReturnRefundReportUseCase
import com.zyntasolutions.zyntapos.domain.usecase.reports.enterprise.GenerateSalesByCashierReportUseCase
import com.zyntasolutions.zyntapos.domain.usecase.reports.enterprise.GenerateShiftCoverageReportUseCase
import com.zyntasolutions.zyntapos.domain.usecase.reports.enterprise.GenerateStaffAttendanceReportUseCase
import com.zyntasolutions.zyntapos.domain.usecase.reports.enterprise.GenerateStaffClockInOutReportUseCase
import com.zyntasolutions.zyntapos.domain.usecase.reports.enterprise.GenerateStaffPerformanceReportUseCase
import com.zyntasolutions.zyntapos.domain.usecase.reports.enterprise.GenerateStockAgingReportUseCase
import com.zyntasolutions.zyntapos.domain.usecase.reports.enterprise.GenerateStockReorderReportUseCase
import com.zyntasolutions.zyntapos.domain.usecase.reports.enterprise.GenerateSupplierPurchaseReportUseCase
import com.zyntasolutions.zyntapos.domain.usecase.reports.enterprise.GenerateSystemAuditLogReportUseCase
import com.zyntasolutions.zyntapos.domain.usecase.reports.enterprise.GenerateWalletBalanceReportUseCase
import com.zyntasolutions.zyntapos.domain.usecase.reports.enterprise.GenerateWarehouseInventoryReportUseCase
import com.zyntasolutions.zyntapos.feature.reports.printer.ReportPrinterAdapter
import com.zyntasolutions.zyntapos.hal.printer.PrinterManager
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

/**
 * Koin DI module for the :composeApp:feature:reports feature.
 *
 * Provides:
 * - [GenerateSalesReportUseCase]    — aggregates sales by date range, payment method, product.
 * - [GenerateStockReportUseCase]    — current stock, low-stock, dead-stock classification.
 * - [GenerateCustomerReportUseCase] — customer base statistics (totals, top-by-loyalty, groups).
 * - [GenerateExpenseReportUseCase]  — expense totals by status and category for a date range.
 * - [ReportPrinterAdapter]          — infrastructure adapter bound to [ReportPrinterPort].
 * - [PrintReportUseCase]            — condensed thermal Z-report summary via the port.
 * - [ReportExporter]                — platform-specific CSV/PDF export (see platform modules).
 * - [ReportsViewModel]              — MVI ViewModel for all report screens.
 * - Enterprise report use cases (30) — backed by [ReportRepository], [AuditRepository],
 *   [BackupRepository] provided by :shared:data dataModule.
 *
 * ### Dependency chain
 * ```
 * ReportsViewModel
 *   ├─ GenerateSalesReportUseCase    → OrderRepository
 *   ├─ GenerateStockReportUseCase    → ProductRepository + StockRepository
 *   ├─ GenerateCustomerReportUseCase → CustomerRepository
 *   ├─ GenerateExpenseReportUseCase  → ExpenseRepository
 *   ├─ PrintReportUseCase            → ReportPrinterPort (→ ReportPrinterAdapter)
 *   │   └─ PrinterManager            (bound in :shared:hal module)
 *   └─ ReportExporter                → bound per platform (jvmMain / androidMain)
 * Enterprise use cases → ReportRepository (bound in dataModule, created by Agent 2/3)
 * ```
 */
val reportsModule = module {
    // ── Port adapter ─────────────────────────────────────────────────────────
    single<ReportPrinterPort> { ReportPrinterAdapter(get<PrinterManager>()) }

    // ── Standard domain use cases ─────────────────────────────────────────────
    factory { GenerateSalesReportUseCase(get()) }
    factory { GenerateStockReportUseCase(get(), get()) }
    factory { GenerateCustomerReportUseCase(get()) }
    factory { GenerateExpenseReportUseCase(get()) }
    factory { PrintReportUseCase(get()) }

    // ── Enterprise report use cases (30) ─────────────────────────────────────
    // ReportRepository is provided by :shared:data dataModule (Agent 2/3).
    // AuditRepository and BackupRepository are bound in dataModule.
    // GeneratePermissionAuditReportUseCase has no repository dependency.
    factory { GenerateAccountingLedgerReportUseCase(get()) }
    factory { GenerateAnnualSalesTrendReportUseCase(get()) }
    factory { GenerateBackupHistoryReportUseCase(get()) }
    factory { GenerateCOGSReportUseCase(get()) }
    factory { GenerateCustomerLoyaltyReportUseCase(get()) }
    factory { GenerateCustomerRetentionReportUseCase(get()) }
    factory { GenerateExpenseSummaryByDeptReportUseCase(get()) }
    factory { GenerateGrossMarginReportUseCase(get()) }
    factory { GenerateHourlySalesReportUseCase(get()) }
    factory { GenerateInterStoreTransferReportUseCase(get()) }
    factory { GenerateInventoryValuationReportUseCase(get()) }
    factory { GenerateLeaveBalanceReportUseCase(get()) }
    factory { GenerateLowStockAlertReportUseCase(get()) }
    factory { GenerateMultiStoreComparisonReportUseCase(get()) }
    factory { GeneratePayrollSummaryReportUseCase(get()) }
    factory { GeneratePermissionAuditReportUseCase() }
    factory { GeneratePurchaseOrderReportUseCase(get()) }
    factory { GenerateReturnRefundReportUseCase(get()) }
    factory { GenerateSalesByCashierReportUseCase(get()) }
    factory { GenerateShiftCoverageReportUseCase(get()) }
    factory { GenerateStaffAttendanceReportUseCase(get()) }
    factory { GenerateStaffClockInOutReportUseCase(get()) }
    factory { GenerateStaffPerformanceReportUseCase(get()) }
    factory { GenerateStockAgingReportUseCase(get()) }
    factory { GenerateStockReorderReportUseCase(get()) }
    factory { GenerateSupplierPurchaseReportUseCase(get()) }
    factory { GenerateSystemAuditLogReportUseCase(get()) }
    factory { GenerateWalletBalanceReportUseCase(get()) }
    factory { GenerateWarehouseInventoryReportUseCase(get()) }

    // ── ViewModel ────────────────────────────────────────────────────────────
    viewModelOf(::ReportsViewModel)
}
