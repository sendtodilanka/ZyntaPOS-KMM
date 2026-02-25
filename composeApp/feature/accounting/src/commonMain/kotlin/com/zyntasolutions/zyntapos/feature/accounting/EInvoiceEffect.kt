package com.zyntasolutions.zyntapos.feature.accounting

/**
 * One-shot side effects for the Accounting / E-Invoice feature.
 */
sealed interface EInvoiceEffect {
    data class ShowSnackbar(val message: String) : EInvoiceEffect
    data class NavigateToDetail(val invoiceId: String) : EInvoiceEffect
    data object NavigateToList : EInvoiceEffect
}
