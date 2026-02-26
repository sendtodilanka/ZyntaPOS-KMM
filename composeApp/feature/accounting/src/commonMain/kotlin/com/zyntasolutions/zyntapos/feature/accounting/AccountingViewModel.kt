package com.zyntasolutions.zyntapos.feature.accounting

import com.zyntasolutions.zyntapos.core.result.Result
import com.zyntasolutions.zyntapos.domain.repository.AuthRepository
import com.zyntasolutions.zyntapos.domain.usecase.accounting.GetPeriodSummaryUseCase
import androidx.lifecycle.viewModelScope
import com.zyntasolutions.zyntapos.ui.core.mvi.BaseViewModel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
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
    private val authRepository: AuthRepository,
) : BaseViewModel<AccountingState, AccountingIntent, AccountingEffect>(
    AccountingState(period = currentFiscalPeriod())
) {
    private var storeId: String = "default"

    init {
        // Resolve the store ID from the active session without blocking the main thread.
        viewModelScope.launch {
            storeId = authRepository.getSession().first()?.storeId ?: "default"
            dispatch(AccountingIntent.LoadPeriod(currentFiscalPeriod()))
        }
    }

    override suspend fun handleIntent(intent: AccountingIntent) {
        when (intent) {
            is AccountingIntent.LoadPeriod -> loadPeriod(intent.period)
            is AccountingIntent.DismissError -> updateState { copy(error = null) }
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

    private companion object {
        fun currentFiscalPeriod(): String {
            val ldt = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())
            return "%04d-%02d".format(ldt.year, ldt.monthNumber)
        }
    }
}
