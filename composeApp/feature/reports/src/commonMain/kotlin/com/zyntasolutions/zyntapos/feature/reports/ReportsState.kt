package com.zyntasolutions.zyntapos.feature.reports

import androidx.compose.runtime.Immutable
import com.zyntasolutions.zyntapos.domain.model.PaymentMethod
import com.zyntasolutions.zyntapos.domain.model.Product
import com.zyntasolutions.zyntapos.domain.model.Store
import com.zyntasolutions.zyntapos.domain.usecase.reports.GenerateCustomerReportUseCase
import com.zyntasolutions.zyntapos.domain.model.report.StoreSalesData
import com.zyntasolutions.zyntapos.domain.usecase.reports.GenerateExpenseReportUseCase
import com.zyntasolutions.zyntapos.domain.usecase.reports.GenerateSalesReportUseCase
import com.zyntasolutions.zyntapos.core.utils.DateTimeUtils
import kotlinx.datetime.Instant

/**
 * MVI — UI state for the reports feature.
 *
 * @property dateFormat      User-preferred date format pattern from GeneralSettings (G20).
 * @property reportsHome     State for [ReportsHomeScreen] (tile grid).
 * @property salesReport     State for [SalesReportScreen].
 * @property stockReport     State for [StockReportScreen].
 * @property customerReport  State for [CustomerReportScreen].
 * @property expenseReport   State for [ExpenseReportScreen].
 * @property availableStores Stores available for the store filter dropdown (G6).
 * @property selectedStoreId Currently selected store filter — null means "All Stores" (G6).
 */
@Immutable
data class ReportsState(
    val dateFormat: String = DateTimeUtils.DEFAULT_DATE_FORMAT,
    val reportsHome: HomeState = HomeState(),
    val salesReport: SalesState = SalesState(),
    val stockReport: StockState = StockState(),
    val customerReport: CustomerReportState = CustomerReportState(),
    val expenseReport: ExpenseReportState = ExpenseReportState(),
    val storeComparison: StoreComparisonState = StoreComparisonState(),
    val availableStores: List<Store> = emptyList(),
    val selectedStoreId: String? = null,
    // ── C6.3: Timezone ──────────────────────────────────────────────────
    /** Store timezone for date range conversion (C6.3). Null = system default. */
    val reportTimezone: String? = null,
    // ── C5.1: Multi-Currency Consolidation ──────────────────────────────
    val consolidatedCurrency: CurrencyConsolidationState = CurrencyConsolidationState(),
    // ── Report Scheduling (G6) ───────────────────────────────────────────
    val scheduling: ReportSchedulingState = ReportSchedulingState(),
) {
    /** State slice for the reports home tile grid. */
    data class HomeState(
        val lastSalesReportAt: Instant? = null,
        val lastStockReportAt: Instant? = null,
        val lastCustomerReportAt: Instant? = null,
        val lastExpenseReportAt: Instant? = null,
    )

    /** State slice for the sales report screen. */
    data class SalesState(
        val isLoading: Boolean = false,
        val selectedRange: DateRange = DateRange.TODAY,
        val customFrom: Instant? = null,
        val customTo: Instant? = null,
        val report: GenerateSalesReportUseCase.SalesReport? = null,
        val error: String? = null,
        val isExporting: Boolean = false,
        val isPrinting: Boolean = false,
        /** Category filter chip selection for the stock table (not used in sales). */
        val selectedCategory: String? = null,
        // ── Drill-down (G6-3) ─────────────────────────────────────────────
        /** When non-null, the user has drilled into a specific chart data point (e.g., a day or product). */
        val drillDownLabel: String? = null,
        /** Transaction IDs/order numbers for the drilled-down data point. */
        val drillDownOrderIds: List<String> = emptyList(),
        /** True when loading drill-down data. */
        val isDrillDownLoading: Boolean = false,
    )

    /** State slice for the stock report screen. */
    data class StockState(
        val isLoading: Boolean = false,
        val allProducts: List<Product> = emptyList(),
        val lowStockItems: List<Product> = emptyList(),
        val deadStockItems: List<Product> = emptyList(),
        val selectedCategory: String? = null,
        val error: String? = null,
        val isExporting: Boolean = false,
        val sortColumn: StockSortColumn = StockSortColumn.NAME,
        val sortAscending: Boolean = true,
        // ── Pagination (G6-4) ─────────────────────────────────────────────
        val currentPage: Int = 0,
        val pageSize: Int = 50,
        val totalItems: Int = 0,
    )

    /** State slice for the customer report screen. */
    data class CustomerReportState(
        val isLoading: Boolean = false,
        val report: GenerateCustomerReportUseCase.CustomerReport? = null,
        val error: String? = null,
        val isExporting: Boolean = false,
    )

    /**
     * State slice for the expense report screen.
     *
     * Uses a separate [selectedRange] / [customFrom] / [customTo] from the sales report
     * so each report screen retains independent date picker state.
     */
    data class ExpenseReportState(
        val isLoading: Boolean = false,
        val selectedRange: DateRange = DateRange.THIS_MONTH,
        val customFrom: Instant? = null,
        val customTo: Instant? = null,
        val report: GenerateExpenseReportUseCase.ExpenseReport? = null,
        val error: String? = null,
        val isExporting: Boolean = false,
    )

    /** State slice for the store comparison report screen (C5.2). */
    data class StoreComparisonState(
        val isLoading: Boolean = false,
        val isExporting: Boolean = false,
        val selectedRange: DateRange = DateRange.THIS_MONTH,
        val stores: List<StoreSalesData> = emptyList(),
        val totalRevenue: Double = 0.0,
        val totalOrders: Int = 0,
        val error: String? = null,
    )

    /** State for multi-currency revenue consolidation (C5.1). */
    data class CurrencyConsolidationState(
        val isLoading: Boolean = false,
        /** Base currency for consolidated reporting (e.g., "LKR"). */
        val baseCurrency: String = "LKR",
        /** Per-store revenue converted to base currency. */
        val storeRevenuesInBase: List<StoreRevenueInBase> = emptyList(),
        /** Total consolidated revenue in base currency across all stores. */
        val totalConsolidatedRevenue: Double = 0.0,
        val error: String? = null,
    )
}

/** Preset date ranges for the sales and expense reports. */
enum class DateRange { TODAY, THIS_WEEK, THIS_MONTH, CUSTOM }

/** Stock table sort columns. */
enum class StockSortColumn { NAME, CATEGORY, QTY, VALUE, STATUS }

/** Top-product row in the sales table. */
data class SalesProductRow(
    val productId: String,
    val productName: String,
    val qtySold: Double,
    val revenue: Double,
)

/** Payment method bar chart entry. */
data class PaymentBreakdownEntry(
    val method: PaymentMethod,
    val amount: Double,
    val fraction: Float,   // 0f–1f relative to max
)

/** Per-store revenue converted to base currency (C5.1). */
data class StoreRevenueInBase(
    val storeId: String,
    val storeName: String,
    val originalCurrency: String,
    val originalRevenue: Double,
    val exchangeRate: Double,
    val revenueInBase: Double,
)

/** Scheduled report frequency. */
enum class ReportScheduleFrequency { DAILY, WEEKLY, MONTHLY }

/** Which report type is being scheduled. */
enum class ScheduledReportType { SALES, STOCK, CUSTOMER, EXPENSE, STORE_COMPARISON }

/**
 * State for report scheduling configuration (G6).
 *
 * Allows users to configure periodic report generation and email delivery.
 */
data class ReportSchedulingState(
    val showDialog: Boolean = false,
    val isEnabled: Boolean = false,
    val frequency: ReportScheduleFrequency = ReportScheduleFrequency.DAILY,
    val reportType: ScheduledReportType = ScheduledReportType.SALES,
    val emailRecipient: String = "",
    val scheduleHour: Int = 8,
    val schedules: List<ReportScheduleEntry> = emptyList(),
    val error: String? = null,
)

/**
 * A single report schedule entry.
 */
data class ReportScheduleEntry(
    val reportType: ScheduledReportType,
    val frequency: ReportScheduleFrequency,
    val emailRecipient: String,
    val scheduleHour: Int,
    val isEnabled: Boolean,
    val lastRunAt: Long? = null,
)
