package com.zyntasolutions.zyntapos.feature.reports

import com.zyntasolutions.zyntapos.domain.usecase.reports.GenerateSalesReportUseCase
import com.zyntasolutions.zyntapos.domain.usecase.reports.GenerateStockReportUseCase
import com.zyntasolutions.zyntapos.domain.usecase.reports.PrintReportUseCase
import com.zyntasolutions.zyntapos.ui.core.mvi.BaseViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Duration.Companion.days

/**
 * MVI ViewModel for the Reports feature (Sprint 22).
 *
 * Manages state for [ReportsHomeScreen], [SalesReportScreen], and [StockReportScreen].
 * Results are cached in state — re-opening a screen does not trigger a re-query
 * unless the user explicitly changes the date range or pulls to refresh.
 *
 * Extends [BaseViewModel] which provides:
 *  - [updateState] for atomic state mutations
 *  - [sendEffect] for one-shot side-effects
 *  - [dispatch] as the UI entry-point (launches [handleIntent] in viewModelScope)
 *
 * @param generateSalesReport Aggregates sales data for a date range.
 * @param generateStockReport Fetches current stock levels with low/dead stock categorisation.
 * @param printReport         Sends a condensed report summary to the thermal printer.
 * @param reportExporter      Platform-specific CSV/PDF export implementation.
 */
class ReportsViewModel(
    private val generateSalesReport: GenerateSalesReportUseCase,
    private val generateStockReport: GenerateStockReportUseCase,
    private val printReport: PrintReportUseCase,
    private val reportExporter: ReportExporter,
) : BaseViewModel<ReportsState, ReportsIntent, ReportsEffect>(ReportsState()) {

    private var salesJob: Job? = null
    private var stockJob: Job? = null

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
        val (from, to) = resolveDateRange()
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

    // ── Date range resolution ────────────────────────────────────────────────

    private fun resolveDateRange(): Pair<Instant, Instant> {
        val tz    = TimeZone.currentSystemDefault()
        val now   = Clock.System.now()
        val today = now.toLocalDateTime(tz).date

        return when (currentState.salesReport.selectedRange) {
            DateRange.TODAY      -> today.atStartOfDayIn(tz) to now
            DateRange.THIS_WEEK  -> (today - today.dayOfWeek.ordinal.toLong().days).atStartOfDayIn(tz) to now
            DateRange.THIS_MONTH -> kotlinx.datetime.LocalDate(today.year, today.monthNumber, 1).atStartOfDayIn(tz) to now
            DateRange.CUSTOM     -> (currentState.salesReport.customFrom ?: now) to
                                    (currentState.salesReport.customTo   ?: now)
        }
    }
}

// helper to subtract days from kotlinx-datetime LocalDate
private operator fun kotlinx.datetime.LocalDate.minus(durationDays: kotlin.time.Duration): kotlinx.datetime.LocalDate {
    val epoch = this.toEpochDays() - durationDays.inWholeDays.toInt()
    return kotlinx.datetime.LocalDate.fromEpochDays(epoch)
}
