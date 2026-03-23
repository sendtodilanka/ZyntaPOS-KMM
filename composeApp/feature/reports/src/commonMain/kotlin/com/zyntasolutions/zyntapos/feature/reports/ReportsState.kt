package com.zyntasolutions.zyntapos.feature.reports

import androidx.compose.runtime.Immutable
import com.zyntasolutions.zyntapos.domain.model.PaymentMethod
import com.zyntasolutions.zyntapos.domain.model.Product
import com.zyntasolutions.zyntapos.domain.usecase.reports.GenerateCustomerReportUseCase
import com.zyntasolutions.zyntapos.domain.model.report.StoreSalesData
import com.zyntasolutions.zyntapos.domain.usecase.reports.GenerateExpenseReportUseCase
import com.zyntasolutions.zyntapos.domain.usecase.reports.GenerateSalesReportUseCase
import kotlinx.datetime.Instant

/**
 * MVI — UI state for the reports feature.
 *
 * @property reportsHome     State for [ReportsHomeScreen] (tile grid).
 * @property salesReport     State for [SalesReportScreen].
 * @property stockReport     State for [StockReportScreen].
 * @property customerReport  State for [CustomerReportScreen].
 * @property expenseReport   State for [ExpenseReportScreen].
 */
@Immutable
data class ReportsState(
    val reportsHome: HomeState = HomeState(),
    val salesReport: SalesState = SalesState(),
    val stockReport: StockState = StockState(),
    val customerReport: CustomerReportState = CustomerReportState(),
    val expenseReport: ExpenseReportState = ExpenseReportState(),
    val storeComparison: StoreComparisonState = StoreComparisonState(),
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
        val selectedRange: DateRange = DateRange.THIS_MONTH,
        val stores: List<StoreSalesData> = emptyList(),
        val totalRevenue: Double = 0.0,
        val totalOrders: Int = 0,
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
