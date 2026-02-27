package com.zyntasolutions.zyntapos.feature.accounting

import com.zyntasolutions.zyntapos.core.result.Result
import com.zyntasolutions.zyntapos.domain.model.Account
import com.zyntasolutions.zyntapos.domain.model.AccountType
import com.zyntasolutions.zyntapos.domain.usecase.accounting.DeactivateAccountUseCase
import com.zyntasolutions.zyntapos.domain.usecase.accounting.GetAccountsUseCase
import com.zyntasolutions.zyntapos.domain.usecase.accounting.SeedDefaultChartOfAccountsUseCase
import androidx.lifecycle.viewModelScope
import com.zyntasolutions.zyntapos.ui.core.mvi.BaseViewModel
import kotlin.time.Clock
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

// ── State ─────────────────────────────────────────────────────────────────────

/**
 * Immutable UI state for the Chart of Accounts screen.
 *
 * @property accounts Filtered list of accounts matching [searchQuery] and [selectedType].
 * @property isLoading True while a mutating operation (deactivate, seed) is in progress.
 * @property searchQuery Current search string applied to account code and name.
 * @property selectedType When non-null, only accounts of this [AccountType] are shown.
 * @property error Non-null when a use-case operation fails.
 */
data class ChartOfAccountsState(
    val accounts: List<Account> = emptyList(),
    val isLoading: Boolean = false,
    val searchQuery: String = "",
    val selectedType: AccountType? = null,
    val error: String? = null,
)

// ── Intent ────────────────────────────────────────────────────────────────────

/** User-initiated events for the Chart of Accounts screen. */
sealed class ChartOfAccountsIntent {
    /** Refresh the account list from the repository. */
    data object LoadAccounts : ChartOfAccountsIntent()

    /** Filter accounts by [query] (matches account code or name). */
    data class SearchAccounts(val query: String) : ChartOfAccountsIntent()

    /** Restrict the displayed list to accounts of [type]; pass null to show all. */
    data class FilterByType(val type: AccountType?) : ChartOfAccountsIntent()

    /** Soft-delete the account identified by [accountId]. */
    data class DeactivateAccount(val accountId: String) : ChartOfAccountsIntent()

    /** Populate the store's chart of accounts with the standard system defaults. */
    data object SeedDefaultAccounts : ChartOfAccountsIntent()
}

// ── Effect ────────────────────────────────────────────────────────────────────

/** One-shot side-effects for the Chart of Accounts screen. */
sealed class ChartOfAccountsEffect {
    /** Display a snackbar-style error message. */
    data class ShowError(val message: String) : ChartOfAccountsEffect()

    /** Display a snackbar-style success message. */
    data class ShowSuccess(val message: String) : ChartOfAccountsEffect()

    /**
     * Navigate to the Account Detail screen.
     * A null [accountId] signals that the UI should open the "create new account" form.
     */
    data class NavigateToAccountDetail(val accountId: String?) : ChartOfAccountsEffect()
}

// ── ViewModel ─────────────────────────────────────────────────────────────────

/**
 * ViewModel for the Chart of Accounts screen.
 *
 * Reactively observes all accounts for the current store via [GetAccountsUseCase].
 * Client-side filtering by [ChartOfAccountsState.searchQuery] and
 * [ChartOfAccountsState.selectedType] is applied in the [onEach] collector so the
 * UI always reflects the latest filter state without extra DB round-trips.
 *
 * @param getAccountsUseCase Provides a reactive [kotlinx.coroutines.flow.Flow] of accounts.
 * @param deactivateAccountUseCase Soft-deletes an account (marks it inactive).
 * @param seedDefaultChartOfAccountsUseCase Inserts the canonical set of system accounts.
 */
class ChartOfAccountsViewModel(
    private val getAccountsUseCase: GetAccountsUseCase,
    private val deactivateAccountUseCase: DeactivateAccountUseCase,
    private val seedDefaultChartOfAccountsUseCase: SeedDefaultChartOfAccountsUseCase,
) : BaseViewModel<ChartOfAccountsState, ChartOfAccountsIntent, ChartOfAccountsEffect>(
    initialState = ChartOfAccountsState(),
) {
    // Store ID resolved at DI construction time; "default-store" is used until
    // a multi-store session resolver is wired in a later sprint.
    private val storeId = "default-store"

    init {
        observeAccounts()
    }

    override suspend fun handleIntent(intent: ChartOfAccountsIntent) {
        when (intent) {
            is ChartOfAccountsIntent.LoadAccounts -> observeAccounts()
            is ChartOfAccountsIntent.SearchAccounts -> updateState { copy(searchQuery = intent.query) }
            is ChartOfAccountsIntent.FilterByType -> updateState { copy(selectedType = intent.type) }
            is ChartOfAccountsIntent.DeactivateAccount -> deactivateAccount(intent.accountId)
            is ChartOfAccountsIntent.SeedDefaultAccounts -> seedAccounts()
        }
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private fun observeAccounts() {
        getAccountsUseCase.execute(storeId)
            .onEach { accounts ->
                updateState {
                    val filtered = accounts.filter { account ->
                        val matchesQuery = searchQuery.isBlank() ||
                            account.accountName.contains(searchQuery, ignoreCase = true) ||
                            account.accountCode.contains(searchQuery, ignoreCase = true)
                        val matchesType = selectedType == null || account.accountType == selectedType
                        matchesQuery && matchesType
                    }
                    copy(accounts = filtered, isLoading = false)
                }
            }
            .catch { e ->
                sendEffect(ChartOfAccountsEffect.ShowError(e.message ?: "Failed to load accounts"))
            }
            .launchIn(viewModelScope)
    }

    private suspend fun deactivateAccount(accountId: String) {
        val now = Clock.System.now().toEpochMilliseconds()
        when (val result = deactivateAccountUseCase.execute(accountId, now)) {
            is Result.Success -> sendEffect(ChartOfAccountsEffect.ShowSuccess("Account deactivated"))
            is Result.Error -> sendEffect(
                ChartOfAccountsEffect.ShowError(result.exception.message ?: "Failed to deactivate account"),
            )
            is Result.Loading -> Unit
        }
    }

    private suspend fun seedAccounts() {
        updateState { copy(isLoading = true) }
        val now = Clock.System.now().toEpochMilliseconds()
        when (val result = seedDefaultChartOfAccountsUseCase.execute(storeId, now)) {
            is Result.Success -> sendEffect(ChartOfAccountsEffect.ShowSuccess("Default chart of accounts loaded"))
            is Result.Error -> sendEffect(
                ChartOfAccountsEffect.ShowError(result.exception.message ?: "Failed to seed chart of accounts"),
            )
            is Result.Loading -> Unit
        }
        updateState { copy(isLoading = false) }
    }
}
