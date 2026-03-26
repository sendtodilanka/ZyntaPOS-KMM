package com.zyntasolutions.zyntapos.feature.accounting

/**
 * User-initiated events for the Accounting Ledger screen.
 */
sealed interface AccountingIntent {
    /** Load or refresh summaries for the given fiscal period (YYYY-MM). */
    data class LoadPeriod(val period: String) : AccountingIntent

    /** Dismiss the current error banner. */
    data object DismissError : AccountingIntent

    /** Load consolidated P&L across all stores for the current fiscal period. */
    data object LoadConsolidatedPnL : AccountingIntent

    // ── Account Reconciliation (G9) ─────────────────────────────────────
    /** Open the reconciliation dialog for the given account. */
    data class StartReconciliation(val accountCode: String, val accountName: String) : AccountingIntent

    /** Update the external balance entered by the user. */
    data class UpdateExternalBalance(val balance: String) : AccountingIntent

    /** Update reconciliation notes. */
    data class UpdateReconciliationNotes(val notes: String) : AccountingIntent

    /** Complete and save the reconciliation. */
    data object SaveReconciliation : AccountingIntent

    /** Dismiss the reconciliation dialog. */
    data object DismissReconciliation : AccountingIntent

    /** Load reconciliation history for the current period. */
    data object LoadReconciliationHistory : AccountingIntent
}
