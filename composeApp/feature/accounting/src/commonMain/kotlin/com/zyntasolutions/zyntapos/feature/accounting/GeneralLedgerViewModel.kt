package com.zyntasolutions.zyntapos.feature.accounting

import androidx.lifecycle.viewModelScope
import com.zyntasolutions.zyntapos.core.result.Result
import com.zyntasolutions.zyntapos.domain.model.Account
import com.zyntasolutions.zyntapos.domain.model.GeneralLedgerEntry
import com.zyntasolutions.zyntapos.domain.usecase.accounting.GetAccountsUseCase
import com.zyntasolutions.zyntapos.domain.usecase.accounting.GetGeneralLedgerUseCase
import com.zyntasolutions.zyntapos.ui.core.mvi.BaseViewModel
import kotlin.time.Clock
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

// ── State ─────────────────────────────────────────────────────────────────────

/**
 * Immutable UI state for the General Ledger screen.
 *
 * The screen consists of two panels:
 * 1. An account selector populated from [accounts].
 * 2. A ledger detail panel showing [entries] for the [selectedAccountId].
 *
 * @property accounts All accounts available for selection (reactive from the repository).
 * @property selectedAccountId UUID of the account whose ledger entries are currently displayed.
 * @property entries General Ledger entries for [selectedAccountId] in the current date range.
 * @property isLoading True while accounts are being fetched or ledger entries are computing.
 * @property storeId The store scope for all queries.
 * @property fromDate Start of the ledger date range (ISO: YYYY-MM-DD).
 * @property toDate End of the ledger date range (ISO: YYYY-MM-DD).
 * @property error Non-null when a use-case or repository error occurs.
 */
data class GeneralLedgerState(
    val accounts: List<Account> = emptyList(),
    val selectedAccountId: String? = null,
    val entries: List<GeneralLedgerEntry> = emptyList(),
    val isLoading: Boolean = false,
    val storeId: String = "default-store",
    val fromDate: String = "",
    val toDate: String = "",
    val error: String? = null,
)

// ── Intent ────────────────────────────────────────────────────────────────────

/** User-initiated events for the General Ledger screen. */
sealed class GeneralLedgerIntent {
    /**
     * Start observing the account list for [storeId] and pre-populate
     * default date range fields.
     */
    data class LoadAccounts(val storeId: String) : GeneralLedgerIntent()

    /**
     * Select an account and fetch its ledger entries for the current date range.
     * If no date range is set, the current calendar month is used.
     */
    data class SelectAccount(val accountId: String) : GeneralLedgerIntent()

    /**
     * Apply a new date range filter.
     * If an account is already selected, the ledger entries are re-fetched immediately.
     */
    data class SetDateRange(val fromDate: String, val toDate: String) : GeneralLedgerIntent()

    /**
     * Re-fetch ledger entries for the currently selected account and date range.
     * No-op if no account is selected.
     */
    data object Refresh : GeneralLedgerIntent()
}

// ── Effect ────────────────────────────────────────────────────────────────────

/** One-shot side-effects for the General Ledger screen. */
sealed class GeneralLedgerEffect {
    /** Display a snackbar-style error message. */
    data class ShowError(val message: String) : GeneralLedgerEffect()
}

// ── ViewModel ─────────────────────────────────────────────────────────────────

/**
 * ViewModel for the General Ledger screen.
 *
 * The account list is observed reactively via [GetAccountsUseCase.execute] so any
 * changes to the chart of accounts (new accounts, deactivations) are reflected live.
 * Ledger entry retrieval is a one-shot suspend call via [GetGeneralLedgerUseCase] and
 * is triggered whenever the account selection or date range changes.
 *
 * @param getAccountsUseCase Provides a reactive [kotlinx.coroutines.flow.Flow] of accounts.
 * @param getGeneralLedgerUseCase Computes the general ledger for a specific account and range.
 */
class GeneralLedgerViewModel(
    private val getAccountsUseCase: GetAccountsUseCase,
    private val getGeneralLedgerUseCase: GetGeneralLedgerUseCase,
) : BaseViewModel<GeneralLedgerState, GeneralLedgerIntent, GeneralLedgerEffect>(
    initialState = GeneralLedgerState(),
) {
    override suspend fun handleIntent(intent: GeneralLedgerIntent) {
        when (intent) {
            is GeneralLedgerIntent.LoadAccounts -> {
                updateState { copy(storeId = intent.storeId) }
                initDefaultDates()
                observeAccounts(intent.storeId)
            }

            is GeneralLedgerIntent.SelectAccount -> {
                updateState { copy(selectedAccountId = intent.accountId, entries = emptyList()) }
                val state = currentState
                fetchLedgerEntries(state.storeId, intent.accountId, state.fromDate, state.toDate)
            }

            is GeneralLedgerIntent.SetDateRange -> {
                updateState { copy(fromDate = intent.fromDate, toDate = intent.toDate, entries = emptyList()) }
                val state = currentState
                val accountId = state.selectedAccountId
                if (accountId != null) {
                    fetchLedgerEntries(state.storeId, accountId, intent.fromDate, intent.toDate)
                }
            }

            is GeneralLedgerIntent.Refresh -> {
                val state = currentState
                val accountId = state.selectedAccountId
                if (accountId != null) {
                    fetchLedgerEntries(state.storeId, accountId, state.fromDate, state.toDate)
                }
            }
        }
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    /**
     * Sets default date range to the current calendar month if not already populated.
     */
    private fun initDefaultDates() {
        val state = currentState
        if (state.fromDate.isBlank() || state.toDate.isBlank()) {
            val today = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date
            val firstOfMonth = "${today.year}-${today.monthNumber.toString().padStart(2, '0')}-01"
            updateState { copy(fromDate = firstOfMonth, toDate = today.toString()) }
        }
    }

    private fun observeAccounts(storeId: String) {
        updateState { copy(isLoading = true) }
        getAccountsUseCase.execute(storeId)
            .onEach { accounts -> updateState { copy(accounts = accounts, isLoading = false) } }
            .catch { e ->
                updateState { copy(isLoading = false) }
                sendEffect(GeneralLedgerEffect.ShowError(e.message ?: "Failed to load accounts"))
            }
            .launchIn(viewModelScope)
    }

    private suspend fun fetchLedgerEntries(
        storeId: String,
        accountId: String,
        fromDate: String,
        toDate: String,
    ) {
        if (fromDate.isBlank() || toDate.isBlank()) {
            sendEffect(GeneralLedgerEffect.ShowError("Please set a date range before viewing ledger entries"))
            return
        }
        updateState { copy(isLoading = true, error = null) }
        when (val result = getGeneralLedgerUseCase.execute(storeId, accountId, fromDate, toDate)) {
            is Result.Success -> updateState { copy(isLoading = false, entries = result.data) }
            is Result.Error -> {
                updateState { copy(isLoading = false, error = result.exception.message) }
                sendEffect(GeneralLedgerEffect.ShowError(result.exception.message ?: "Failed to load ledger entries"))
            }
            is Result.Loading -> Unit
        }
    }
}
