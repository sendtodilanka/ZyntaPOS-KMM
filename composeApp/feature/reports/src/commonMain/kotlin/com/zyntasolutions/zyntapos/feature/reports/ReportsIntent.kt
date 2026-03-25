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
}
