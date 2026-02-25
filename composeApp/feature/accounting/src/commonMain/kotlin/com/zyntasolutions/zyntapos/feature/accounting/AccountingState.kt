package com.zyntasolutions.zyntapos.feature.accounting

import com.zyntasolutions.zyntapos.domain.model.AccountSummary

/**
 * Immutable UI state for the Accounting Ledger screen (double-entry journal).
 *
 * @property summaries Aggregated account balances for the selected [period].
 * @property period    Currently selected fiscal period in YYYY-MM format.
 * @property isLoading True while fetching period summaries.
 * @property error     Non-null on repository/network error.
 */
data class AccountingState(
    val summaries: List<AccountSummary> = emptyList(),
    val period: String = "",
    val isLoading: Boolean = false,
    val error: String? = null,
)
