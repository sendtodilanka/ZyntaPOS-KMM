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
    val successMessage: String? = null,
    val consolidatedPnL: ConsolidatedPnLState = ConsolidatedPnLState(),

    // ── Account Reconciliation (G9) ─────────────────────────────────────
    val reconciliation: ReconciliationState = ReconciliationState(),
)

/**
 * State for the account reconciliation workflow (G9).
 *
 * Reconciliation compares the GL balance of an account against an external
 * balance (e.g., bank statement) and identifies unmatched entries.
 */
data class ReconciliationState(
    val isLoading: Boolean = false,
    val showDialog: Boolean = false,
    /** The account code being reconciled. */
    val accountCode: String = "",
    val accountName: String = "",
    /** GL balance for the account in the selected period. */
    val glBalance: Double = 0.0,
    /** External balance entered by the user (e.g., from bank statement). */
    val externalBalance: String = "",
    /** Difference: GL balance - external balance. */
    val difference: Double = 0.0,
    /** True when the reconciliation is matched (difference == 0). */
    val isReconciled: Boolean = false,
    /** Notes about the reconciliation. */
    val notes: String = "",
    /** List of recent reconciliation records. */
    val history: List<ReconciliationRecord> = emptyList(),
    val error: String? = null,
)

/**
 * A single reconciliation record — snapshot of a completed reconciliation.
 */
data class ReconciliationRecord(
    val accountCode: String,
    val accountName: String,
    val glBalance: Double,
    val externalBalance: Double,
    val difference: Double,
    val isReconciled: Boolean,
    val notes: String,
    val reconciledAt: Long,
    val reconciledBy: String,
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
