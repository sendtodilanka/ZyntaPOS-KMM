package com.zyntasolutions.zyntapos.feature.reports

/**
 * MVI — one-time side effects for the reports feature.
 */
sealed interface ReportsEffect {
    data class ShowSnackbar(val message: String) : ReportsEffect
    data class ExportComplete(val filePath: String) : ReportsEffect
    data object PrintJobSent : ReportsEffect
    data class NavigateToSalesReport(val range: DateRange) : ReportsEffect
    data object NavigateToStockReport : ReportsEffect
}
