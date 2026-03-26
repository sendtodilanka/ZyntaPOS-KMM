package com.zyntasolutions.zyntapos.feature.accounting

import com.zyntasolutions.zyntapos.core.utils.DateTimeUtils
import com.zyntasolutions.zyntapos.domain.model.AccountSummary

/**
 * Immutable UI state for the Accounting Ledger screen (double-entry journal).
 *
 * @property dateFormat User-preferred date format pattern from GeneralSettings (G20).
 * @property summaries  Aggregated account balances for the selected [period].
 * @property period     Currently selected fiscal period in YYYY-MM format.
 * @property isLoading  True while fetching period summaries.
 * @property error      Non-null on repository/network error.
 */
data class AccountingState(
    val dateFormat: String = DateTimeUtils.DEFAULT_DATE_FORMAT,
    val summaries: List<AccountSummary> = emptyList(),
    val period: String = "",
    val isLoading: Boolean = false,
    val error: String? = null,
    val consolidatedPnL: ConsolidatedPnLState = ConsolidatedPnLState(),
)

/**
 * State for the multi-store consolidated Profit & Loss view.
 *
 * @property isLoading        True while fetching P&L data across stores.
 * @property storeBreakdowns  Per-store revenue, expenses, and profit breakdown.
 * @property consolidatedRevenue  Sum of revenue across all stores.
 * @property consolidatedExpenses Sum of expenses across all stores.
 * @property consolidatedProfit   Sum of net profit across all stores.
 * @property error             Non-null on repository/network error.
 */
data class ConsolidatedPnLState(
    val isLoading: Boolean = false,
    val storeBreakdowns: List<StorePnLBreakdown> = emptyList(),
    val consolidatedRevenue: Double = 0.0,
    val consolidatedExpenses: Double = 0.0,
    val consolidatedProfit: Double = 0.0,
    val error: String? = null,
)

/**
 * Per-store breakdown of Profit & Loss figures.
 *
 * @property storeId   Unique identifier of the store.
 * @property storeName Display name of the store.
 * @property revenue   Total revenue for the store in the selected period.
 * @property expenses  Total expenses (COGS + operating) for the store.
 * @property profit    Net profit (revenue - expenses) for the store.
 */
data class StorePnLBreakdown(
    val storeId: String,
    val storeName: String,
    val revenue: Double,
    val expenses: Double,
    val profit: Double,
)
