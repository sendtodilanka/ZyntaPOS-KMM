package com.zyntasolutions.zyntapos.feature.accounting

/**
 * One-shot side effects for the Accounting Ledger screen.
 */
sealed interface AccountingEffect {
    /** Navigate to the detail view for a specific account. */
    data class NavigateToDetail(val accountCode: String, val fiscalPeriod: String) : AccountingEffect

    /** Show a snackbar error message. */
    data class ShowError(val message: String) : AccountingEffect
}
