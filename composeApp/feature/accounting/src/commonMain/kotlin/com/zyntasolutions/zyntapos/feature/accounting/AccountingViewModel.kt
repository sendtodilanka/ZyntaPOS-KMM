package com.zyntasolutions.zyntapos.feature.accounting

import com.zyntasolutions.zyntapos.core.analytics.AnalyticsTracker
import com.zyntasolutions.zyntapos.core.result.Result
import com.zyntasolutions.zyntapos.domain.repository.AuthRepository
import com.zyntasolutions.zyntapos.domain.repository.StoreRepository
import com.zyntasolutions.zyntapos.domain.usecase.accounting.GetPeriodSummaryUseCase
import com.zyntasolutions.zyntapos.domain.usecase.accounting.GetProfitAndLossUseCase
import com.zyntasolutions.zyntapos.domain.repository.SettingsRepository
import androidx.lifecycle.viewModelScope
import com.zyntasolutions.zyntapos.ui.core.mvi.BaseViewModel
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlin.time.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/**
 * ViewModel for the Accounting Ledger screen (Sprint 18 — AccountingGraph).
 *
 * Loads aggregated account summaries for the current fiscal period and
 * exposes navigation to per-account detail.
 *
 * @param getPeriodSummaryUseCase Retrieves DEBIT/CREDIT totals per account for a period range.
 * @param currentStoreId          Store scope resolved at DI construction time.
 */
class AccountingViewModel(
    private val getPeriodSummaryUseCase: GetPeriodSummaryUseCase,
    private val getProfitAndLossUseCase: GetProfitAndLossUseCase,
    private val storeRepository: StoreRepository,
    private val authRepository: AuthRepository,
    private val settingsRepository: SettingsRepository,
    private val analytics: AnalyticsTracker,
) : BaseViewModel<AccountingState, AccountingIntent, AccountingEffect>(
    AccountingState(period = currentFiscalPeriod())
) {
    private var storeId: String = "default"
    private var currentUserId: String = "unknown"

    init {
        analytics.logScreenView("Accounting", "AccountingViewModel")
        // Resolve the store ID and user from the active session without blocking the main thread.
        viewModelScope.launch {
            val session = authRepository.getSession().first()
            storeId = session?.storeId ?: "default"
            currentUserId = session?.id ?: "unknown"
            dispatch(AccountingIntent.LoadPeriod(currentFiscalPeriod()))
        }
    }

    override suspend fun handleIntent(intent: AccountingIntent) {
        when (intent) {
            is AccountingIntent.LoadPeriod -> loadPeriod(intent.period)
            is AccountingIntent.DismissError -> updateState { copy(error = null) }
            is AccountingIntent.LoadConsolidatedPnL -> loadConsolidatedPnL()
            is AccountingIntent.StartReconciliation -> onStartReconciliation(intent.accountCode, intent.accountName)
            is AccountingIntent.UpdateExternalBalance -> onUpdateExternalBalance(intent.balance)
            is AccountingIntent.UpdateReconciliationNotes -> updateState {
                copy(reconciliation = reconciliation.copy(notes = intent.notes))
            }
            is AccountingIntent.SaveReconciliation -> onSaveReconciliation()
            is AccountingIntent.DismissReconciliation -> updateState {
                copy(reconciliation = ReconciliationState())
            }
            is AccountingIntent.LoadReconciliationHistory -> onLoadReconciliationHistory()
        }
    }

    private suspend fun loadPeriod(period: String) {
        updateState { copy(isLoading = true, period = period) }
        when (val result = getPeriodSummaryUseCase(storeId, period, period)) {
            is Result.Success -> updateState { copy(isLoading = false, summaries = result.data) }
            is Result.Error -> {
                updateState { copy(isLoading = false) }
                sendEffect(AccountingEffect.ShowError(result.exception.message ?: "Failed to load ledger"))
            }
            is Result.Loading -> {}
        }
    }

    /**
     * Load P&L data for every active store and aggregate into a consolidated view.
     *
     * Uses the current fiscal period (first day to last day of current month) to
     * query [GetProfitAndLossUseCase] per store. Revenue is [PAndL.totalRevenue],
     * expenses is [PAndL.totalCogs] + [PAndL.totalExpenses], profit is [PAndL.netProfit].
     */
    private suspend fun loadConsolidatedPnL() {
        updateState { copy(consolidatedPnL = consolidatedPnL.copy(isLoading = true, error = null)) }

        try {
            val stores = storeRepository.getAllStores().first()
            if (stores.isEmpty()) {
                updateState {
                    copy(
                        consolidatedPnL = consolidatedPnL.copy(
                            isLoading = false,
                            error = "No stores available",
                        ),
                    )
                }
                return
            }

            // Determine date range for the current fiscal period (full month).
            val now = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())
            val fromDate = "%04d-%02d-01".format(now.year, now.monthNumber)
            val lastDay = when (now.monthNumber) {
                2 -> if (now.year % 4 == 0 && (now.year % 100 != 0 || now.year % 400 == 0)) 29 else 28
                4, 6, 9, 11 -> 30
                else -> 31
            }
            val toDate = "%04d-%02d-%02d".format(now.year, now.monthNumber, lastDay)

            // Fetch P&L for each store concurrently.
            val breakdowns = coroutineScope {
                stores.map { store ->
                    async {
                        val result = getProfitAndLossUseCase.execute(store.id, fromDate, toDate)
                        when (result) {
                            is Result.Success -> {
                                val pnl = result.data
                                StorePnLBreakdown(
                                    storeId = store.id,
                                    storeName = store.name,
                                    revenue = pnl.totalRevenue,
                                    expenses = pnl.totalCogs + pnl.totalExpenses,
                                    profit = pnl.netProfit,
                                )
                            }
                            is Result.Error -> {
                                // Include store with zeroed figures on error so it still appears.
                                StorePnLBreakdown(
                                    storeId = store.id,
                                    storeName = store.name,
                                    revenue = 0.0,
                                    expenses = 0.0,
                                    profit = 0.0,
                                )
                            }
                            is Result.Loading -> null
                        }
                    }
                }.awaitAll().filterNotNull()
            }

            val totalRevenue = breakdowns.sumOf { it.revenue }
            val totalExpenses = breakdowns.sumOf { it.expenses }
            val totalProfit = breakdowns.sumOf { it.profit }

            updateState {
                copy(
                    consolidatedPnL = ConsolidatedPnLState(
                        isLoading = false,
                        storeBreakdowns = breakdowns,
                        consolidatedRevenue = totalRevenue,
                        consolidatedExpenses = totalExpenses,
                        consolidatedProfit = totalProfit,
                        error = null,
                    ),
                )
            }
        } catch (e: Exception) {
            updateState {
                copy(
                    consolidatedPnL = consolidatedPnL.copy(
                        isLoading = false,
                        error = e.message ?: "Failed to load consolidated P&L",
                    ),
                )
            }
        }
    }

    // ── Account Reconciliation (G9) ─────────────────────────────────────

    private suspend fun onStartReconciliation(accountCode: String, accountName: String) {
        // Look up the GL balance for this account from the current period summaries.
        val summary = currentState.summaries.find { it.accountCode == accountCode }
        val glBalance = summary?.total ?: 0.0

        updateState {
            copy(
                reconciliation = ReconciliationState(
                    showDialog = true,
                    accountCode = accountCode,
                    accountName = accountName,
                    glBalance = glBalance,
                ),
            )
        }
    }

    private fun onUpdateExternalBalance(balance: String) {
        val externalValue = balance.toDoubleOrNull() ?: 0.0
        val glBalance = currentState.reconciliation.glBalance
        val diff = glBalance - externalValue
        updateState {
            copy(
                reconciliation = reconciliation.copy(
                    externalBalance = balance,
                    difference = diff,
                    isReconciled = kotlin.math.abs(diff) < 0.01,
                ),
            )
        }
    }

    private suspend fun onSaveReconciliation() {
        val recon = currentState.reconciliation
        if (recon.externalBalance.isBlank()) {
            updateState {
                copy(reconciliation = reconciliation.copy(error = "External balance is required"))
            }
            return
        }

        val now = Clock.System.now().toEpochMilliseconds()
        val record = ReconciliationRecord(
            accountCode = recon.accountCode,
            accountName = recon.accountName,
            glBalance = recon.glBalance,
            externalBalance = recon.externalBalance.toDoubleOrNull() ?: 0.0,
            difference = recon.difference,
            isReconciled = recon.isReconciled,
            notes = recon.notes,
            reconciledAt = now,
            reconciledBy = currentUserId,
        )

        // Persist reconciliation record via settings (key pattern: reconciliation.<period>.<accountCode>)
        val key = "reconciliation.${currentState.period}.${recon.accountCode}"
        val value = buildString {
            append(record.glBalance).append("|")
            append(record.externalBalance).append("|")
            append(record.difference).append("|")
            append(record.isReconciled).append("|")
            append(record.notes).append("|")
            append(record.reconciledAt).append("|")
            append(record.reconciledBy)
        }
        settingsRepository.set(key, value)

        updateState {
            copy(
                reconciliation = ReconciliationState(),
                successMessage = if (record.isReconciled) {
                    "${recon.accountName} reconciled successfully"
                } else {
                    "${recon.accountName} saved with variance of ${recon.difference}"
                },
            )
        }
        sendEffect(AccountingEffect.ShowError(
            if (record.isReconciled) "Account reconciled" else "Reconciliation saved with variance"
        ))
    }

    private suspend fun onLoadReconciliationHistory() {
        updateState { copy(reconciliation = reconciliation.copy(isLoading = true)) }

        val prefix = "reconciliation.${currentState.period}."
        val records = currentState.summaries.mapNotNull { summary ->
            val key = "$prefix${summary.accountCode}"
            val value = settingsRepository.get(key) ?: return@mapNotNull null
            val parts = value.split("|")
            if (parts.size < 7) return@mapNotNull null
            ReconciliationRecord(
                accountCode = summary.accountCode,
                accountName = summary.accountName,
                glBalance = parts[0].toDoubleOrNull() ?: 0.0,
                externalBalance = parts[1].toDoubleOrNull() ?: 0.0,
                difference = parts[2].toDoubleOrNull() ?: 0.0,
                isReconciled = parts[3].toBooleanStrictOrNull() ?: false,
                notes = parts[4],
                reconciledAt = parts[5].toLongOrNull() ?: 0L,
                reconciledBy = parts[6],
            )
        }

        updateState {
            copy(reconciliation = reconciliation.copy(isLoading = false, history = records))
        }
    }

    private companion object {
        fun currentFiscalPeriod(): String {
            val ldt = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())
            return "%04d-%02d".format(ldt.year, ldt.monthNumber)
        }
    }
}
