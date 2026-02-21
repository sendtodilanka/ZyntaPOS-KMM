package com.zyntasolutions.zyntapos.feature.reports

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zyntasolutions.zyntapos.domain.usecase.reports.GenerateSalesReportUseCase
import com.zyntasolutions.zyntapos.domain.usecase.reports.GenerateStockReportUseCase
import com.zyntasolutions.zyntapos.domain.usecase.reports.PrintReportUseCase
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
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
 * Results are cached in [_state] — re-opening a screen does not trigger a re-query
 * unless the user explicitly changes the date range or pulls to refresh.
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
) : ViewModel() {

    private val _state = MutableStateFlow(ReportsState())
    val state: StateFlow<ReportsState> = _state.asStateFlow()

    private val _effect = MutableSharedFlow<ReportsEffect>(extraBufferCapacity = 16)
    val effect: SharedFlow<ReportsEffect> = _effect.asSharedFlow()

    private var salesJob: Job? = null
    private var stockJob: Job? = null

    // ── Intent dispatch ──────────────────────────────────────────────────────

    fun onIntent(intent: ReportsIntent) {
        when (intent) {
            is ReportsIntent.SelectSalesRange    -> selectSalesRange(intent.range)
            is ReportsIntent.SetCustomSalesRange -> setCustomRange(intent.from, intent.to)
            ReportsIntent.LoadSalesReport        -> loadSalesReport()
            ReportsIntent.ExportSalesReportCsv  -> exportSales(pdf = false)
            ReportsIntent.ExportSalesReportPdf  -> exportSales(pdf = true)
            ReportsIntent.PrintSalesReport       -> printSalesReport()
            ReportsIntent.DismissSalesError      -> _state.update { it.copy(salesReport = it.salesReport.copy(error = null)) }
            ReportsIntent.LoadStockReport        -> loadStockReport()
            is ReportsIntent.FilterStockByCategory -> filterStock(intent.categoryId)
            is ReportsIntent.SortStock           -> sortStock(intent.column, intent.ascending)
            ReportsIntent.ExportStockReportCsv  -> exportStock(pdf = false)
            ReportsIntent.ExportStockReportPdf  -> exportStock(pdf = true)
            ReportsIntent.DismissStockError      -> _state.update { it.copy(stockReport = it.stockReport.copy(error = null)) }
        }
    }

    // ── Sales ────────────────────────────────────────────────────────────────

    private fun selectSalesRange(range: DateRange) {
        _state.update { it.copy(salesReport = it.salesReport.copy(selectedRange = range)) }
        loadSalesReport()
    }

    private fun setCustomRange(from: Instant, to: Instant) {
        _state.update {
            it.copy(
                salesReport = it.salesReport.copy(
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
        _state.update { it.copy(salesReport = it.salesReport.copy(isLoading = true, error = null)) }

        salesJob = viewModelScope.launch {
            generateSalesReport(from, to)
                .catch { e ->
                    _state.update { s ->
                        s.copy(salesReport = s.salesReport.copy(isLoading = false, error = e.message))
                    }
                }
                .collect { report ->
                    _state.update { s ->
                        s.copy(
                            salesReport = s.salesReport.copy(
                                isLoading = false,
                                report = report,
                                error = null,
                            ),
                            reportsHome = s.reportsHome.copy(lastSalesReportAt = Clock.System.now()),
                        )
                    }
                }
        }
    }

    private fun exportSales(pdf: Boolean) {
        val report = _state.value.salesReport.report ?: return
        _state.update { it.copy(salesReport = it.salesReport.copy(isExporting = true)) }
        viewModelScope.launch {
            try {
                val path = if (pdf) {
                    reportExporter.exportSalesPdf(report)
                } else {
                    reportExporter.exportSalesCsv(report)
                }
                _effect.emit(ReportsEffect.ExportComplete(path))
            } catch (e: Exception) {
                _effect.emit(ReportsEffect.ShowSnackbar("Export failed: ${e.message}"))
            } finally {
                _state.update { it.copy(salesReport = it.salesReport.copy(isExporting = false)) }
            }
        }
    }

    private fun printSalesReport() {
        val report = _state.value.salesReport.report ?: return
        _state.update { it.copy(salesReport = it.salesReport.copy(isPrinting = true)) }
        viewModelScope.launch {
            printReport.printSalesSummary(report).fold(
                onSuccess = { _effect.emit(ReportsEffect.PrintJobSent) },
                onFailure = { e -> _effect.emit(ReportsEffect.ShowSnackbar("Print failed: ${e.message}")) },
            )
            _state.update { it.copy(salesReport = it.salesReport.copy(isPrinting = false)) }
        }
    }

    // ── Stock ────────────────────────────────────────────────────────────────

    private fun loadStockReport() {
        stockJob?.cancel()
        _state.update { it.copy(stockReport = it.stockReport.copy(isLoading = true, error = null)) }

        stockJob = viewModelScope.launch {
            generateStockReport()
                .catch { e ->
                    _state.update { s ->
                        s.copy(stockReport = s.stockReport.copy(isLoading = false, error = e.message))
                    }
                }
                .collect { report ->
                    _state.update { s ->
                        s.copy(
                            stockReport = s.stockReport.copy(
                                isLoading = false,
                                allProducts = report.allProducts,
                                lowStockItems = report.lowStockItems,
                                deadStockItems = report.deadStockItems,
                                error = null,
                            ),
                            reportsHome = s.reportsHome.copy(lastStockReportAt = Clock.System.now()),
                        )
                    }
                }
        }
    }

    private fun filterStock(categoryId: String?) {
        _state.update { it.copy(stockReport = it.stockReport.copy(selectedCategory = categoryId)) }
    }

    private fun sortStock(column: StockSortColumn, ascending: Boolean) {
        _state.update { it.copy(stockReport = it.stockReport.copy(sortColumn = column, sortAscending = ascending)) }
    }

    private fun exportStock(pdf: Boolean) {
        val s = _state.value.stockReport
        _state.update { it.copy(stockReport = it.stockReport.copy(isExporting = true)) }
        viewModelScope.launch {
            try {
                val report = GenerateStockReportUseCase.StockReport(
                    allProducts = s.allProducts,
                    lowStockItems = s.lowStockItems,
                    deadStockItems = s.deadStockItems,
                )
                val path = if (pdf) reportExporter.exportStockPdf(report)
                           else reportExporter.exportStockCsv(report)
                _effect.emit(ReportsEffect.ExportComplete(path))
            } catch (e: Exception) {
                _effect.emit(ReportsEffect.ShowSnackbar("Export failed: ${e.message}"))
            } finally {
                _state.update { it.copy(stockReport = it.stockReport.copy(isExporting = false)) }
            }
        }
    }

    // ── Date range resolution ────────────────────────────────────────────────

    private fun resolveDateRange(): Pair<Instant, Instant> {
        val tz   = TimeZone.currentSystemDefault()
        val now  = Clock.System.now()
        val today = now.toLocalDateTime(tz).date

        return when (_state.value.salesReport.selectedRange) {
            DateRange.TODAY      -> today.atStartOfDayIn(tz) to now
            DateRange.THIS_WEEK  -> (today - today.dayOfWeek.ordinal.days).atStartOfDayIn(tz) to now
            DateRange.THIS_MONTH -> today.let {
                it.copy(dayOfMonth = 1).atStartOfDayIn(tz) to now
            }
            DateRange.CUSTOM     -> (_state.value.salesReport.customFrom ?: now) to
                                    (_state.value.salesReport.customTo   ?: now)
        }
    }
}

// helper to subtract days from kotlinx-datetime LocalDate
private val Int.days get() = kotlin.time.Duration.days(this.toLong())
private operator fun kotlinx.datetime.LocalDate.minus(days: kotlin.time.Duration): kotlinx.datetime.LocalDate {
    val epoch = this.toEpochDays() - days.inWholeDays.toInt()
    return kotlinx.datetime.LocalDate.fromEpochDays(epoch)
}
