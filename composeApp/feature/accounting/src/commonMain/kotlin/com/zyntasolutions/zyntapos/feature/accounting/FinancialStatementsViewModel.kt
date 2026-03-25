package com.zyntasolutions.zyntapos.feature.accounting

import com.zyntasolutions.zyntapos.core.result.Result
import com.zyntasolutions.zyntapos.domain.model.FinancialStatement
import com.zyntasolutions.zyntapos.domain.usecase.accounting.GetBalanceSheetUseCase
import com.zyntasolutions.zyntapos.domain.usecase.accounting.GetCashFlowStatementUseCase
import com.zyntasolutions.zyntapos.domain.usecase.accounting.GetProfitAndLossUseCase
import com.zyntasolutions.zyntapos.domain.usecase.accounting.GetTrialBalanceUseCase
import com.zyntasolutions.zyntapos.ui.core.mvi.BaseViewModel
import kotlin.time.Clock
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

// ── Tab enum ──────────────────────────────────────────────────────────────────

/** The four primary financial statement tabs shown on the Financial Statements screen. */
enum class FinancialStatementTab {
    /** Profit and Loss (Income) Statement — for a date range. */
    PROFIT_LOSS,

    /** Balance Sheet — as of a specific date. */
    BALANCE_SHEET,

    /** Trial Balance — as of a specific date. */
    TRIAL_BALANCE,

    /** Cash Flow Statement (Direct Method) — for a date range. */
    CASH_FLOW,
}

/** Identifies which date picker field is currently active. */
enum class DatePickerField { FROM, TO, AS_OF, NONE }

// ── State ─────────────────────────────────────────────────────────────────────

/**
 * Immutable UI state for the Financial Statements screen.
 *
 * Each statement type is stored independently so that switching tabs does not
 * discard already-loaded data.
 *
 * @property activeTab The currently visible statement tab.
 * @property isLoading True while any statement fetch is in progress.
 * @property pAndL Cached Profit and Loss statement result; null until loaded.
 * @property balanceSheet Cached Balance Sheet result; null until loaded.
 * @property trialBalance Cached Trial Balance result; null until loaded.
 * @property storeId The store scope for all statements.
 * @property fromDate Start of the P&L reporting period (ISO: YYYY-MM-DD).
 * @property toDate End of the P&L reporting period (ISO: YYYY-MM-DD).
 * @property asOfDate Point-in-time date for Balance Sheet and Trial Balance (ISO: YYYY-MM-DD).
 * @property activeDatePicker Which date picker is currently open (NONE = closed).
 * @property error Non-null when a statement computation fails.
 */
data class FinancialStatementsState(
    val activeTab: FinancialStatementTab = FinancialStatementTab.PROFIT_LOSS,
    val isLoading: Boolean = false,
    val pAndL: FinancialStatement.PAndL? = null,
    val balanceSheet: FinancialStatement.BalanceSheet? = null,
    val trialBalance: FinancialStatement.TrialBalance? = null,
    val cashFlow: FinancialStatement.CashFlow? = null,
    val storeId: String = "default-store",
    val fromDate: String = "",
    val toDate: String = "",
    val asOfDate: String = "",
    val activeDatePicker: DatePickerField = DatePickerField.NONE,
    val error: String? = null,
)

// ── Intent ────────────────────────────────────────────────────────────────────

/** User-initiated events for the Financial Statements screen. */
sealed class FinancialStatementsIntent {
    /**
     * Compute and load the Profit and Loss statement for [storeId]
     * from [fromDate] to [toDate] (ISO: YYYY-MM-DD).
     */
    data class LoadPandL(
        val storeId: String,
        val fromDate: String,
        val toDate: String,
    ) : FinancialStatementsIntent()

    /**
     * Compute and load the Balance Sheet for [storeId] as of [asOfDate] (ISO: YYYY-MM-DD).
     */
    data class LoadBalanceSheet(
        val storeId: String,
        val asOfDate: String,
    ) : FinancialStatementsIntent()

    /**
     * Compute and load the Trial Balance for [storeId] as of [asOfDate] (ISO: YYYY-MM-DD).
     */
    data class LoadTrialBalance(
        val storeId: String,
        val asOfDate: String,
    ) : FinancialStatementsIntent()

    /** Switch the visible tab to [tab], loading data if not yet cached. */
    data class SwitchTab(val tab: FinancialStatementTab) : FinancialStatementsIntent()

    /** Update the P&L date range and re-fetch if the P&L tab is active. */
    data class SetDateRange(val fromDate: String, val toDate: String) : FinancialStatementsIntent()

    /** Update the as-of date and re-fetch if the Balance Sheet or Trial Balance tab is active. */
    data class SetAsOfDate(val asOfDate: String) : FinancialStatementsIntent()

    /**
     * Compute and load the Cash Flow Statement for [storeId]
     * from [fromDate] to [toDate] (ISO: YYYY-MM-DD).
     */
    data class LoadCashFlow(
        val storeId: String,
        val fromDate: String,
        val toDate: String,
    ) : FinancialStatementsIntent()

    /** Open the date picker for the given [field]. */
    data class ShowDatePicker(val field: DatePickerField) : FinancialStatementsIntent()

    /** Close any open date picker without applying a date. */
    data object HideDatePicker : FinancialStatementsIntent()

    /** Export the currently visible statement as CSV. */
    data object ExportCsv : FinancialStatementsIntent()
}

// ── Effect ────────────────────────────────────────────────────────────────────

/** One-shot side-effects for the Financial Statements screen. */
sealed class FinancialStatementsEffect {
    /** Display a snackbar-style error message. */
    data class ShowError(val message: String) : FinancialStatementsEffect()

    /**
     * Share / display the exported statement content.
     *
     * @property content Full UTF-8 CSV text.
     * @property fileName Suggested file name (e.g., `pnl_2026-03-01_2026-03-25.csv`).
     */
    data class ShareExport(
        val content: String,
        val fileName: String,
    ) : FinancialStatementsEffect()
}

// ── ViewModel ─────────────────────────────────────────────────────────────────

/**
 * ViewModel for the Financial Statements screen.
 *
 * Manages four independent statement datasets (P&L, Balance Sheet, Trial Balance, Cash Flow).
 * Switching tabs via [FinancialStatementsIntent.SwitchTab] triggers a lazy load of
 * the selected statement if not yet computed. Date range changes invalidate and re-fetch
 * the relevant cached statements. Date pickers ([ShowDatePicker] / [HideDatePicker]) are
 * managed via [FinancialStatementsState.activeDatePicker]. CSV export is available per tab
 * via [FinancialStatementsIntent.ExportCsv].
 */
class FinancialStatementsViewModel(
    private val getProfitAndLossUseCase: GetProfitAndLossUseCase,
    private val getBalanceSheetUseCase: GetBalanceSheetUseCase,
    private val getTrialBalanceUseCase: GetTrialBalanceUseCase,
    private val getCashFlowStatementUseCase: GetCashFlowStatementUseCase,
) : BaseViewModel<FinancialStatementsState, FinancialStatementsIntent, FinancialStatementsEffect>(
    initialState = FinancialStatementsState().let { initial ->
        // Default the date fields to the current calendar month on first load.
        val today = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date
        val firstOfMonth = "${today.year}-${today.monthNumber.toString().padStart(2, '0')}-01"
        initial.copy(
            fromDate = firstOfMonth,
            toDate = today.toString(),
            asOfDate = today.toString(),
        )
    },
) {
    override suspend fun handleIntent(intent: FinancialStatementsIntent) {
        when (intent) {
            is FinancialStatementsIntent.LoadPandL -> {
                updateState { copy(storeId = intent.storeId, fromDate = intent.fromDate, toDate = intent.toDate) }
                fetchPandL(intent.storeId, intent.fromDate, intent.toDate)
            }

            is FinancialStatementsIntent.LoadBalanceSheet -> {
                updateState { copy(storeId = intent.storeId, asOfDate = intent.asOfDate) }
                fetchBalanceSheet(intent.storeId, intent.asOfDate)
            }

            is FinancialStatementsIntent.LoadTrialBalance -> {
                updateState { copy(storeId = intent.storeId, asOfDate = intent.asOfDate) }
                fetchTrialBalance(intent.storeId, intent.asOfDate)
            }

            is FinancialStatementsIntent.SwitchTab -> {
                updateState { copy(activeTab = intent.tab) }
                lazyLoadForTab(intent.tab)
            }

            is FinancialStatementsIntent.SetDateRange -> {
                updateState { copy(fromDate = intent.fromDate, toDate = intent.toDate, pAndL = null, cashFlow = null) }
                val state = currentState
                when (state.activeTab) {
                    FinancialStatementTab.PROFIT_LOSS -> fetchPandL(state.storeId, intent.fromDate, intent.toDate)
                    FinancialStatementTab.CASH_FLOW   -> fetchCashFlow(state.storeId, intent.fromDate, intent.toDate)
                    else -> Unit
                }
            }

            is FinancialStatementsIntent.SetAsOfDate -> {
                updateState { copy(asOfDate = intent.asOfDate, balanceSheet = null, trialBalance = null) }
                val state = currentState
                when (state.activeTab) {
                    FinancialStatementTab.BALANCE_SHEET -> fetchBalanceSheet(state.storeId, intent.asOfDate)
                    FinancialStatementTab.TRIAL_BALANCE -> fetchTrialBalance(state.storeId, intent.asOfDate)
                    else -> Unit
                }
            }

            is FinancialStatementsIntent.LoadCashFlow -> {
                updateState { copy(storeId = intent.storeId, fromDate = intent.fromDate, toDate = intent.toDate) }
                fetchCashFlow(intent.storeId, intent.fromDate, intent.toDate)
            }

            is FinancialStatementsIntent.ShowDatePicker -> {
                updateState { copy(activeDatePicker = intent.field) }
            }

            is FinancialStatementsIntent.HideDatePicker -> {
                updateState { copy(activeDatePicker = DatePickerField.NONE) }
            }

            is FinancialStatementsIntent.ExportCsv -> {
                exportCurrentTab()
            }
        }
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    /**
     * Triggers a data load for [tab] only if the corresponding cached value is null.
     * This implements lazy loading when the user first navigates to a tab.
     */
    private suspend fun lazyLoadForTab(tab: FinancialStatementTab) {
        val state = currentState
        when (tab) {
            FinancialStatementTab.PROFIT_LOSS -> {
                if (state.pAndL == null && state.fromDate.isNotBlank() && state.toDate.isNotBlank()) {
                    fetchPandL(state.storeId, state.fromDate, state.toDate)
                }
            }
            FinancialStatementTab.BALANCE_SHEET -> {
                if (state.balanceSheet == null && state.asOfDate.isNotBlank()) {
                    fetchBalanceSheet(state.storeId, state.asOfDate)
                }
            }
            FinancialStatementTab.TRIAL_BALANCE -> {
                if (state.trialBalance == null && state.asOfDate.isNotBlank()) {
                    fetchTrialBalance(state.storeId, state.asOfDate)
                }
            }
            FinancialStatementTab.CASH_FLOW -> {
                if (state.cashFlow == null && state.fromDate.isNotBlank() && state.toDate.isNotBlank()) {
                    fetchCashFlow(state.storeId, state.fromDate, state.toDate)
                }
            }
        }
    }

    private suspend fun fetchPandL(storeId: String, fromDate: String, toDate: String) {
        updateState { copy(isLoading = true, error = null) }
        when (val result = getProfitAndLossUseCase.execute(storeId, fromDate, toDate)) {
            is Result.Success -> updateState { copy(isLoading = false, pAndL = result.data) }
            is Result.Error -> {
                updateState { copy(isLoading = false, error = result.exception.message) }
                sendEffect(FinancialStatementsEffect.ShowError(result.exception.message ?: "Failed to load P&L statement"))
            }
            is Result.Loading -> Unit
        }
    }

    private suspend fun fetchBalanceSheet(storeId: String, asOfDate: String) {
        updateState { copy(isLoading = true, error = null) }
        when (val result = getBalanceSheetUseCase.execute(storeId, asOfDate)) {
            is Result.Success -> updateState { copy(isLoading = false, balanceSheet = result.data) }
            is Result.Error -> {
                updateState { copy(isLoading = false, error = result.exception.message) }
                sendEffect(FinancialStatementsEffect.ShowError(result.exception.message ?: "Failed to load balance sheet"))
            }
            is Result.Loading -> Unit
        }
    }

    private suspend fun fetchTrialBalance(storeId: String, asOfDate: String) {
        updateState { copy(isLoading = true, error = null) }
        when (val result = getTrialBalanceUseCase.execute(storeId, asOfDate)) {
            is Result.Success -> updateState { copy(isLoading = false, trialBalance = result.data) }
            is Result.Error -> {
                updateState { copy(isLoading = false, error = result.exception.message) }
                sendEffect(FinancialStatementsEffect.ShowError(result.exception.message ?: "Failed to load trial balance"))
            }
            is Result.Loading -> Unit
        }
    }

    private suspend fun fetchCashFlow(storeId: String, fromDate: String, toDate: String) {
        updateState { copy(isLoading = true, error = null) }
        when (val result = getCashFlowStatementUseCase.execute(storeId, fromDate, toDate)) {
            is Result.Success -> updateState { copy(isLoading = false, cashFlow = result.data) }
            is Result.Error -> {
                updateState { copy(isLoading = false, error = result.exception.message) }
                sendEffect(FinancialStatementsEffect.ShowError(result.exception.message ?: "Failed to load cash flow statement"))
            }
            is Result.Loading -> Unit
        }
    }

    // ── Export helpers ────────────────────────────────────────────────────────

    private fun exportCurrentTab() {
        val state = currentState
        val (content, fileName) = when (state.activeTab) {
            FinancialStatementTab.PROFIT_LOSS -> {
                val pAndL = state.pAndL ?: run {
                    viewModelScope.let { sendEffect(FinancialStatementsEffect.ShowError("Generate P&L first before exporting")) }
                    return
                }
                generatePandLCsv(pAndL) to "pnl_${state.fromDate}_${state.toDate}.csv"
            }
            FinancialStatementTab.BALANCE_SHEET -> {
                val bs = state.balanceSheet ?: run {
                    viewModelScope.let { sendEffect(FinancialStatementsEffect.ShowError("Generate Balance Sheet first before exporting")) }
                    return
                }
                generateBalanceSheetCsv(bs) to "balance_sheet_${state.asOfDate}.csv"
            }
            FinancialStatementTab.TRIAL_BALANCE -> {
                val tb = state.trialBalance ?: run {
                    viewModelScope.let { sendEffect(FinancialStatementsEffect.ShowError("Generate Trial Balance first before exporting")) }
                    return
                }
                generateTrialBalanceCsv(tb) to "trial_balance_${state.asOfDate}.csv"
            }
            FinancialStatementTab.CASH_FLOW -> {
                val cf = state.cashFlow ?: run {
                    viewModelScope.let { sendEffect(FinancialStatementsEffect.ShowError("Generate Cash Flow first before exporting")) }
                    return
                }
                generateCashFlowCsv(cf) to "cash_flow_${state.fromDate}_${state.toDate}.csv"
            }
        }
        sendEffect(FinancialStatementsEffect.ShareExport(content, fileName))
    }

    private fun generatePandLCsv(pAndL: FinancialStatement.PAndL): String {
        val sb = StringBuilder()
        sb.appendLine("section,accountCode,accountName,amount")
        pAndL.revenueLines.forEach { sb.appendLine("Revenue,${it.accountCode},${it.accountName.csvCell()},${it.amount}") }
        sb.appendLine("Revenue Summary,,Total Revenue,${pAndL.totalRevenue}")
        pAndL.cogsLines.forEach { sb.appendLine("COGS,${it.accountCode},${it.accountName.csvCell()},${it.amount}") }
        sb.appendLine("COGS Summary,,Total COGS,${pAndL.totalCogs}")
        sb.appendLine("Summary,,Gross Profit,${pAndL.grossProfit}")
        pAndL.expenseLines.forEach { sb.appendLine("Expenses,${it.accountCode},${it.accountName.csvCell()},${it.amount}") }
        sb.appendLine("Expenses Summary,,Total Expenses,${pAndL.totalExpenses}")
        sb.appendLine("Summary,,Net Profit,${pAndL.netProfit}")
        sb.appendLine("Summary,,Gross Margin %,${pAndL.grossMarginPct}")
        return sb.toString()
    }

    private fun generateBalanceSheetCsv(bs: FinancialStatement.BalanceSheet): String {
        val sb = StringBuilder()
        sb.appendLine("section,accountCode,accountName,amount")
        bs.assetLines.forEach { sb.appendLine("Assets,${it.accountCode},${it.accountName.csvCell()},${it.amount}") }
        sb.appendLine("Assets Summary,,Total Assets,${bs.totalAssets}")
        bs.liabilityLines.forEach { sb.appendLine("Liabilities,${it.accountCode},${it.accountName.csvCell()},${it.amount}") }
        sb.appendLine("Liabilities Summary,,Total Liabilities,${bs.totalLiabilities}")
        bs.equityLines.forEach { sb.appendLine("Equity,${it.accountCode},${it.accountName.csvCell()},${it.amount}") }
        sb.appendLine("Equity Summary,,Retained Earnings,${bs.retainedEarnings}")
        sb.appendLine("Equity Summary,,Total Equity,${bs.totalEquity}")
        return sb.toString()
    }

    private fun generateTrialBalanceCsv(tb: FinancialStatement.TrialBalance): String {
        val sb = StringBuilder()
        sb.appendLine("accountCode,accountName,accountType,normalBalance,totalDebits,totalCredits,balance")
        tb.lines.forEach { line ->
            sb.appendLine(
                "${line.accountCode},${line.accountName.csvCell()}," +
                    "${line.accountType},${line.normalBalance}," +
                    "${line.totalDebits},${line.totalCredits},${line.balance}",
            )
        }
        sb.appendLine(",,,,${tb.totalDebits},${tb.totalCredits},")
        return sb.toString()
    }

    private fun generateCashFlowCsv(cf: FinancialStatement.CashFlow): String {
        val sb = StringBuilder()
        sb.appendLine("section,label,inflow,outflow,net")
        cf.operatingLines.forEach { sb.appendLine("Operating,${it.label.csvCell()},${it.inflow},${it.outflow},${it.net}") }
        sb.appendLine("Operating Summary,Net Cash from Operations,,,${cf.netOperating}")
        cf.investingLines.forEach { sb.appendLine("Investing,${it.label.csvCell()},${it.inflow},${it.outflow},${it.net}") }
        sb.appendLine("Investing Summary,Net Cash from Investing,,,${cf.netInvesting}")
        cf.financingLines.forEach { sb.appendLine("Financing,${it.label.csvCell()},${it.inflow},${it.outflow},${it.net}") }
        sb.appendLine("Financing Summary,Net Cash from Financing,,,${cf.netFinancing}")
        sb.appendLine("Summary,Net Change in Cash,,,${cf.netChange}")
        sb.appendLine("Summary,Opening Cash Balance,,,${cf.openingCash}")
        sb.appendLine("Summary,Closing Cash Balance,,,${cf.closingCash}")
        return sb.toString()
    }

    private fun String.csvCell(): String = if (contains(',') || contains('"') || contains('\n')) {
        "\"${replace("\"", "\"\"")}\""
    } else this
}

// ── Date conversion helpers ───────────────────────────────────────────────────

/** Convert a YYYY-MM-DD string to epoch milliseconds (UTC midnight). Returns null on parse error. */
fun String.toEpochMillisOrNull(): Long? = try {
    val date = LocalDate.parse(this)
    // kotlinx-datetime: LocalDate → Instant at UTC midnight
    val instant = date.atStartOfDayIn(kotlinx.datetime.TimeZone.UTC)
    instant.toEpochMilliseconds()
} catch (_: Exception) {
    null
}

/** Convert epoch milliseconds to a YYYY-MM-DD string (UTC). */
fun Long.toLocalDateString(): String {
    val instant = kotlinx.datetime.Instant.fromEpochMilliseconds(this)
    return instant.toLocalDateTime(kotlinx.datetime.TimeZone.UTC).date.toString()
}
