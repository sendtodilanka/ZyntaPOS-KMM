package com.zyntasolutions.zyntapos.feature.reports

import com.zyntasolutions.zyntapos.core.analytics.AnalyticsTracker
import com.zyntasolutions.zyntapos.domain.usecase.reports.GenerateCustomerReportUseCase
import com.zyntasolutions.zyntapos.domain.usecase.reports.GenerateExpenseReportUseCase
import com.zyntasolutions.zyntapos.domain.usecase.reports.GenerateSalesReportUseCase
import com.zyntasolutions.zyntapos.domain.port.SyncStatusPort
import com.zyntasolutions.zyntapos.domain.usecase.reports.GenerateStockReportUseCase
import kotlinx.coroutines.delay
import com.zyntasolutions.zyntapos.domain.usecase.reports.PrintReportUseCase
import com.zyntasolutions.zyntapos.domain.usecase.reports.enterprise.GenerateMultiStoreComparisonReportUseCase
import com.zyntasolutions.zyntapos.ui.core.mvi.BaseViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import kotlin.time.Clock
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Duration.Companion.days

/**
 * MVI ViewModel for the Reports feature.
 *
 * Manages state for [ReportsHomeScreen], [SalesReportScreen], [StockReportScreen],
 * [CustomerReportScreen], and [ExpenseReportScreen].
 *
 * Results are cached in state — re-opening a screen does not trigger a re-query
 * unless the user explicitly changes the date range or pulls to refresh.
 *
 * Extends [BaseViewModel] which provides:
 *  - [updateState] for atomic state mutations
 *  - [sendEffect] for one-shot side-effects
 *  - [dispatch] as the UI entry-point (launches [handleIntent] in viewModelScope)
 *
 * @param generateSalesReport    Aggregates sales data for a date range.
 * @param generateStockReport    Fetches current stock levels with low/dead stock categorisation.
 * @param generateCustomerReport Aggregates customer base statistics (live snapshot).
 * @param generateExpenseReport  Aggregates expense totals by status and category for a date range.
 * @param printReport            Sends a condensed report summary to the thermal printer.
 * @param reportExporter         Platform-specific CSV/PDF export implementation.
 */
class ReportsViewModel(
    private val generateSalesReport: GenerateSalesReportUseCase,
    private val generateStockReport: GenerateStockReportUseCase,
    private val generateCustomerReport: GenerateCustomerReportUseCase,
    private val generateExpenseReport: GenerateExpenseReportUseCase,
    private val printReport: PrintReportUseCase,
    private val reportExporter: ReportExporter,
    private val generateStoreComparison: GenerateMultiStoreComparisonReportUseCase,
    private val analytics: AnalyticsTracker,
    private val syncStatusPort: SyncStatusPort,
) : BaseViewModel<ReportsState, ReportsIntent, ReportsEffect>(ReportsState()) {

    init {
        analytics.logScreenView("Reports", "ReportsViewModel")

        // Refresh active reports on every sync cycle completion (real-time report updates — G6).
        viewModelScope.launch {
            syncStatusPort.onSyncComplete.collect { refreshLoadedReports() }
        }

        // 30-second periodic fallback refresh.
        viewModelScope.launch {
            while (true) {
                delay(AUTO_REFRESH_INTERVAL_MS)
                refreshLoadedReports()
            }
        }
    }

    /** Silently reloads whichever report tabs the user has already opened. */
    private fun refreshLoadedReports() {
        val s = currentState
        if (s.salesReport.report != null) loadSalesReport()
        if (s.stockReport.allProducts.isNotEmpty() || s.stockReport.lowStockItems.isNotEmpty()) loadStockReport()
        if (s.customerReport.report != null) loadCustomerReport()
        if (s.expenseReport.report != null) loadExpenseReport()
        if (s.storeComparison.stores.isNotEmpty()) loadStoreComparison()
    }

    companion object {
        private const val AUTO_REFRESH_INTERVAL_MS = 30_000L
    }

    private var salesJob: Job? = null
    private var stockJob: Job? = null
    private var customerJob: Job? = null
    private var expenseJob: Job? = null

    // ── Intent dispatch ──────────────────────────────────────────────────────

    override suspend fun handleIntent(intent: ReportsIntent) {
        when (intent) {
            is ReportsIntent.SelectSalesRange      -> selectSalesRange(intent.range)
            is ReportsIntent.SetCustomSalesRange   -> setCustomRange(intent.from, intent.to)
            ReportsIntent.LoadSalesReport          -> loadSalesReport()
            ReportsIntent.ExportSalesReportCsv     -> exportSales(pdf = false)
            ReportsIntent.ExportSalesReportPdf     -> exportSales(pdf = true)
            ReportsIntent.PrintSalesReport         -> printSalesReport()
            ReportsIntent.DismissSalesError        -> updateState {
                copy(salesReport = salesReport.copy(error = null))
            }
            ReportsIntent.LoadStockReport          -> loadStockReport()
            is ReportsIntent.FilterStockByCategory -> filterStock(intent.categoryId)
            is ReportsIntent.SortStock             -> sortStock(intent.column, intent.ascending)
            ReportsIntent.ExportStockReportCsv     -> exportStock(pdf = false)
            ReportsIntent.ExportStockReportPdf     -> exportStock(pdf = true)
            ReportsIntent.DismissStockError        -> updateState {
                copy(stockReport = stockReport.copy(error = null))
            }
            ReportsIntent.LoadCustomerReport       -> loadCustomerReport()
            ReportsIntent.ExportCustomerReportCsv  -> exportCustomer()
            ReportsIntent.DismissCustomerError     -> updateState {
                copy(customerReport = customerReport.copy(error = null))
            }
            ReportsIntent.LoadExpenseReport        -> loadExpenseReport()
            is ReportsIntent.SelectExpenseRange    -> selectExpenseRange(intent.range)
            is ReportsIntent.SetCustomExpenseRange -> setCustomExpenseRange(intent.from, intent.to)
            ReportsIntent.ExportExpenseReportCsv   -> exportExpense()
            ReportsIntent.DismissExpenseError      -> updateState {
                copy(expenseReport = expenseReport.copy(error = null))
            }
            // ── Store Comparison (C5.2) ──────────────────────────────────────
            ReportsIntent.LoadStoreComparison      -> loadStoreComparison()
            is ReportsIntent.SelectStoreComparisonRange -> selectStoreComparisonRange(intent.range)
            ReportsIntent.ExportStoreComparisonCsv -> exportStoreComparison()
            ReportsIntent.DismissStoreComparisonError -> updateState {
                copy(storeComparison = storeComparison.copy(error = null))
            }
        }
    }

    // ── Sales ────────────────────────────────────────────────────────────────

    private fun selectSalesRange(range: DateRange) {
        updateState { copy(salesReport = salesReport.copy(selectedRange = range)) }
        loadSalesReport()
    }

    private fun setCustomRange(from: Instant, to: Instant) {
        updateState {
            copy(
                salesReport = salesReport.copy(
                    selectedRange = DateRange.CUSTOM,
                    customFrom = from,
                    customTo = to,
                ),
            )
        }
        loadSalesReport()
    }

    private fun loadSalesReport() {
        salesJob?.cancel()
        val (from, to) = resolveSalesDateRange()
        updateState { copy(salesReport = salesReport.copy(isLoading = true, error = null)) }

        salesJob = viewModelScope.launch {
            generateSalesReport(from, to)
                .catch { e ->
                    updateState {
                        copy(salesReport = salesReport.copy(isLoading = false, error = e.message))
                    }
                }
                .collect { report ->
                    updateState {
                        copy(
                            salesReport = salesReport.copy(
                                isLoading = false,
                                report = report,
                                error = null,
                            ),
                            reportsHome = reportsHome.copy(lastSalesReportAt = Clock.System.now()),
                        )
                    }
                }
        }
    }

    private fun exportSales(pdf: Boolean) {
        val report = currentState.salesReport.report ?: return
        updateState { copy(salesReport = salesReport.copy(isExporting = true)) }
        viewModelScope.launch {
            try {
                val path = if (pdf) {
                    reportExporter.exportSalesPdf(report)
                } else {
                    reportExporter.exportSalesCsv(report)
                }
                sendEffect(ReportsEffect.ExportComplete(path))
            } catch (e: Exception) {
                sendEffect(ReportsEffect.ShowSnackbar("Export failed: ${e.message}"))
            } finally {
                updateState { copy(salesReport = salesReport.copy(isExporting = false)) }
            }
        }
    }

    private fun printSalesReport() {
        val report = currentState.salesReport.report ?: return
        updateState { copy(salesReport = salesReport.copy(isPrinting = true)) }
        viewModelScope.launch {
            printReport.printSalesSummary(report).fold(
                onSuccess = { sendEffect(ReportsEffect.PrintJobSent) },
                onFailure = { e -> sendEffect(ReportsEffect.ShowSnackbar("Print failed: ${e.message}")) },
            )
            updateState { copy(salesReport = salesReport.copy(isPrinting = false)) }
        }
    }

    // ── Stock ────────────────────────────────────────────────────────────────

    private fun loadStockReport() {
        stockJob?.cancel()
        updateState { copy(stockReport = stockReport.copy(isLoading = true, error = null)) }

        stockJob = viewModelScope.launch {
            generateStockReport()
                .catch { e ->
                    updateState {
                        copy(stockReport = stockReport.copy(isLoading = false, error = e.message))
                    }
                }
                .collect { report ->
                    updateState {
                        copy(
                            stockReport = stockReport.copy(
                                isLoading = false,
                                allProducts = report.allProducts,
                                lowStockItems = report.lowStockItems,
                                deadStockItems = report.deadStockItems,
                                error = null,
                            ),
                            reportsHome = reportsHome.copy(lastStockReportAt = Clock.System.now()),
                        )
                    }
                }
        }
    }

    private fun filterStock(categoryId: String?) {
        updateState { copy(stockReport = stockReport.copy(selectedCategory = categoryId)) }
    }

    private fun sortStock(column: StockSortColumn, ascending: Boolean) {
        updateState { copy(stockReport = stockReport.copy(sortColumn = column, sortAscending = ascending)) }
    }

    private fun exportStock(pdf: Boolean) {
        val s = currentState.stockReport
        updateState { copy(stockReport = stockReport.copy(isExporting = true)) }
        viewModelScope.launch {
            try {
                val report = GenerateStockReportUseCase.StockReport(
                    allProducts = s.allProducts,
                    lowStockItems = s.lowStockItems,
                    deadStockItems = s.deadStockItems,
                )
                val path = if (pdf) reportExporter.exportStockPdf(report)
                           else reportExporter.exportStockCsv(report)
                sendEffect(ReportsEffect.ExportComplete(path))
            } catch (e: Exception) {
                sendEffect(ReportsEffect.ShowSnackbar("Export failed: ${e.message}"))
            } finally {
                updateState { copy(stockReport = stockReport.copy(isExporting = false)) }
            }
        }
    }

    // ── Customer ─────────────────────────────────────────────────────────────

    private fun loadCustomerReport() {
        customerJob?.cancel()
        updateState { copy(customerReport = customerReport.copy(isLoading = true, error = null)) }

        customerJob = viewModelScope.launch {
            generateCustomerReport()
                .catch { e ->
                    updateState {
                        copy(customerReport = customerReport.copy(isLoading = false, error = e.message))
                    }
                }
                .collect { report ->
                    updateState {
                        copy(
                            customerReport = customerReport.copy(
                                isLoading = false,
                                report = report,
                                error = null,
                            ),
                            reportsHome = reportsHome.copy(lastCustomerReportAt = Clock.System.now()),
                        )
                    }
                }
        }
    }

    private fun exportCustomer() {
        val report = currentState.customerReport.report ?: return
        updateState { copy(customerReport = customerReport.copy(isExporting = true)) }
        viewModelScope.launch {
            try {
                val path = reportExporter.exportCustomerCsv(report)
                sendEffect(ReportsEffect.ExportComplete(path))
            } catch (e: Exception) {
                sendEffect(ReportsEffect.ShowSnackbar("Export failed: ${e.message}"))
            } finally {
                updateState { copy(customerReport = customerReport.copy(isExporting = false)) }
            }
        }
    }

    // ── Expense ───────────────────────────────────────────────────────────────

    private fun selectExpenseRange(range: DateRange) {
        updateState { copy(expenseReport = expenseReport.copy(selectedRange = range)) }
        loadExpenseReport()
    }

    private fun setCustomExpenseRange(from: Instant, to: Instant) {
        updateState {
            copy(
                expenseReport = expenseReport.copy(
                    selectedRange = DateRange.CUSTOM,
                    customFrom = from,
                    customTo = to,
                ),
            )
        }
        loadExpenseReport()
    }

    private fun loadExpenseReport() {
        expenseJob?.cancel()
        val (from, to) = resolveExpenseDateRange()
        updateState { copy(expenseReport = expenseReport.copy(isLoading = true, error = null)) }

        expenseJob = viewModelScope.launch {
            generateExpenseReport(from.toEpochMilliseconds(), to.toEpochMilliseconds())
                .catch { e ->
                    updateState {
                        copy(expenseReport = expenseReport.copy(isLoading = false, error = e.message))
                    }
                }
                .collect { report ->
                    updateState {
                        copy(
                            expenseReport = expenseReport.copy(
                                isLoading = false,
                                report = report,
                                error = null,
                            ),
                            reportsHome = reportsHome.copy(lastExpenseReportAt = Clock.System.now()),
                        )
                    }
                }
        }
    }

    private fun exportExpense() {
        val report = currentState.expenseReport.report ?: return
        updateState { copy(expenseReport = expenseReport.copy(isExporting = true)) }
        viewModelScope.launch {
            try {
                val path = reportExporter.exportExpenseCsv(report)
                sendEffect(ReportsEffect.ExportComplete(path))
            } catch (e: Exception) {
                sendEffect(ReportsEffect.ShowSnackbar("Export failed: ${e.message}"))
            } finally {
                updateState { copy(expenseReport = expenseReport.copy(isExporting = false)) }
            }
        }
    }

    // ── Date range resolution ────────────────────────────────────────────────

    private fun resolveSalesDateRange(): Pair<Instant, Instant> =
        resolveDateRange(
            range     = currentState.salesReport.selectedRange,
            customFrom = currentState.salesReport.customFrom,
            customTo   = currentState.salesReport.customTo,
        )

    private fun resolveExpenseDateRange(): Pair<Instant, Instant> =
        resolveDateRange(
            range     = currentState.expenseReport.selectedRange,
            customFrom = currentState.expenseReport.customFrom,
            customTo   = currentState.expenseReport.customTo,
        )

    // ── Store Comparison (C5.2) ─────────────────────────────────────────────

    private var storeComparisonJob: Job? = null

    private fun selectStoreComparisonRange(range: DateRange) {
        updateState { copy(storeComparison = storeComparison.copy(selectedRange = range)) }
        loadStoreComparison()
    }

    private fun loadStoreComparison() {
        storeComparisonJob?.cancel()
        val (from, to) = resolveDateRange(
            range = currentState.storeComparison.selectedRange,
            customFrom = null,
            customTo = null,
        )
        updateState { copy(storeComparison = storeComparison.copy(isLoading = true, error = null)) }
        storeComparisonJob = viewModelScope.launch {
            generateStoreComparison(from, to)
                .catch { e ->
                    updateState {
                        copy(storeComparison = storeComparison.copy(
                            isLoading = false,
                            error = e.message ?: "Failed to load store comparison",
                        ))
                    }
                }
                .collect { stores ->
                    val totalRevenue = stores.sumOf { it.totalRevenue }
                    val totalOrders = stores.sumOf { it.orderCount }
                    updateState {
                        copy(storeComparison = storeComparison.copy(
                            isLoading = false,
                            stores = stores,
                            totalRevenue = totalRevenue,
                            totalOrders = totalOrders,
                        ))
                    }
                }
        }
    }

    private fun exportStoreComparison() {
        val stores = currentState.storeComparison.stores
        if (stores.isEmpty()) return
        updateState { copy(storeComparison = storeComparison.copy(isExporting = true)) }
        viewModelScope.launch {
            try {
                val path = reportExporter.exportStoreComparisonCsv(stores)
                sendEffect(ReportsEffect.ExportComplete(path))
            } catch (e: Exception) {
                sendEffect(ReportsEffect.ShowSnackbar("Export failed: ${e.message}"))
            } finally {
                updateState { copy(storeComparison = storeComparison.copy(isExporting = false)) }
            }
        }
    }

    // ── Date Range Utilities ─────────────────────────────────────────────────

    private fun resolveDateRange(
        range: DateRange,
        customFrom: Instant?,
        customTo: Instant?,
    ): Pair<Instant, Instant> {
        val tz    = TimeZone.currentSystemDefault()
        val now   = Clock.System.now()
        val today = now.toLocalDateTime(tz).date

        return when (range) {
            DateRange.TODAY      -> today.atStartOfDayIn(tz) to now
            DateRange.THIS_WEEK  -> (today - today.dayOfWeek.ordinal.toLong().days).atStartOfDayIn(tz) to now
            DateRange.THIS_MONTH -> kotlinx.datetime.LocalDate(today.year, today.monthNumber, 1).atStartOfDayIn(tz) to now
            DateRange.CUSTOM     -> (customFrom ?: now) to (customTo ?: now)
        }
    }
}

// helper to subtract days from kotlinx-datetime LocalDate
private operator fun kotlinx.datetime.LocalDate.minus(durationDays: kotlin.time.Duration): kotlinx.datetime.LocalDate {
    val epoch = this.toEpochDays() - durationDays.inWholeDays.toInt()
    return kotlinx.datetime.LocalDate.fromEpochDays(epoch)
}
