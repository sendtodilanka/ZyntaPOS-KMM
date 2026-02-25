package com.zyntasolutions.zyntapos.feature.accounting

/**
 * User-initiated events for the Accounting Ledger screen.
 */
sealed interface AccountingIntent {
    /** Load or refresh summaries for the given fiscal period (YYYY-MM). */
    data class LoadPeriod(val period: String) : AccountingIntent

    /** Dismiss the current error banner. */
    data object DismissError : AccountingIntent
}
