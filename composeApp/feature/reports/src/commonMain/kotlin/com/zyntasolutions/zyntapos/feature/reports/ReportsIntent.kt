package com.zyntasolutions.zyntapos.feature.reports

import kotlinx.datetime.Instant

/**
 * MVI — user intents for the reports feature.
 */
sealed interface ReportsIntent {
    // ── Sales Report ────────────────────────────────────────────────────────
    data class SelectSalesRange(val range: DateRange) : ReportsIntent
    data class SetCustomSalesRange(val from: Instant, val to: Instant) : ReportsIntent
    data object LoadSalesReport : ReportsIntent
    data object ExportSalesReportCsv : ReportsIntent
    data object ExportSalesReportPdf : ReportsIntent
    data object PrintSalesReport : ReportsIntent
    data object DismissSalesError : ReportsIntent

    // ── Stock Report ────────────────────────────────────────────────────────
    data object LoadStockReport : ReportsIntent
    data class FilterStockByCategory(val categoryId: String?) : ReportsIntent
    data class SortStock(val column: StockSortColumn, val ascending: Boolean) : ReportsIntent
    data object ExportStockReportCsv : ReportsIntent
    data object ExportStockReportPdf : ReportsIntent
    data object DismissStockError : ReportsIntent

    // ── Customer Report ─────────────────────────────────────────────────────
    data object LoadCustomerReport : ReportsIntent
    data object ExportCustomerReportCsv : ReportsIntent
    data object DismissCustomerError : ReportsIntent

    // ── Expense Report ──────────────────────────────────────────────────────
    data object LoadExpenseReport : ReportsIntent
    data class SelectExpenseRange(val range: DateRange) : ReportsIntent
    data class SetCustomExpenseRange(val from: Instant, val to: Instant) : ReportsIntent
    data object ExportExpenseReportCsv : ReportsIntent
    data object DismissExpenseError : ReportsIntent

    // ── Store Comparison Report (C5.2) ──────────────────────────────────────
    data object LoadStoreComparison : ReportsIntent
    data class SelectStoreComparisonRange(val range: DateRange) : ReportsIntent
    data object ExportStoreComparisonCsv : ReportsIntent
    data object DismissStoreComparisonError : ReportsIntent

    // ── Multi-Store Filter (G6) ─────────────────────────────────────────────
    /** Load the list of available stores for the store filter dropdown. */
    data object LoadAvailableStores : ReportsIntent

    /** Select a store to filter all reports by. Pass null for "All Stores". */
    data class SelectReportStore(val storeId: String?) : ReportsIntent

    // ── Drill-Down (G6-3) ─────────────────────────────────────────────────
    /** Drill into a chart data point (e.g., click a day's bar to see transactions). */
    data class DrillDownSalesDataPoint(val label: String) : ReportsIntent
    /** Close the drill-down view and return to the summary. */
    data object CloseDrillDown : ReportsIntent

    // ── Pagination (G6-4) ─────────────────────────────────────────────────
    /** Navigate to the next page of the stock report table. */
    data object StockNextPage : ReportsIntent
    /** Navigate to the previous page of the stock report table. */
    data object StockPreviousPage : ReportsIntent

    // ── C6.3: Timezone-Aware Date Range ──────────────────────────────────
    /**
     * Convert "Today"/"This Week"/"This Month" ranges to the selected store's
     * timezone before querying. Ensures date boundaries match the store's local day.
     */
    data class SetReportTimezone(val timezoneId: String) : ReportsIntent

    // ── C5.1: Multi-Currency Consolidation ──────────────────────────────
    /** Load consolidated multi-currency report (convert all store revenues to base currency). */
    data object LoadConsolidatedCurrencyReport : ReportsIntent
    /** Set the base currency for consolidation (e.g., "LKR", "USD"). */
    data class SetConsolidationBaseCurrency(val currencyCode: String) : ReportsIntent

    // ── Report Scheduling (G6) ────────────────────────────────────────────
    /** Show the report scheduling dialog. */
    data object ShowScheduleDialog : ReportsIntent
    /** Dismiss the report scheduling dialog. */
    data object DismissScheduleDialog : ReportsIntent
    /** Set the report type for scheduling. */
    data class SetScheduleReportType(val type: ScheduledReportType) : ReportsIntent
    /** Set the schedule frequency. */
    data class SetScheduleFrequency(val frequency: ReportScheduleFrequency) : ReportsIntent
    /** Set the email recipient for scheduled reports. */
    data class SetScheduleEmailRecipient(val email: String) : ReportsIntent
    /** Set the hour of day for scheduled report generation. */
    data class SetScheduleHour(val hour: Int) : ReportsIntent
    /** Save the current schedule configuration. */
    data object SaveSchedule : ReportsIntent
    /** Toggle a schedule on/off by report type. */
    data class ToggleSchedule(val reportType: ScheduledReportType) : ReportsIntent
    /** Load all saved schedules. */
    data object LoadSchedules : ReportsIntent
    /** Delete a saved schedule. */
    data class DeleteSchedule(val reportType: ScheduledReportType) : ReportsIntent
}
