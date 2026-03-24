package com.zyntasolutions.zyntapos.feature.accounting

import com.zyntasolutions.zyntapos.domain.model.EInvoiceStatus

/**
 * All user actions for the Accounting / E-Invoice feature.
 */
sealed interface EInvoiceIntent {

    // ── List ───────────────────────────────────────────────────────────────
    /** Apply a status filter; null clears the filter (shows all). */
    data class FilterByStatus(val status: EInvoiceStatus?) : EInvoiceIntent

    // ── Detail ─────────────────────────────────────────────────────────────
    /** Load an existing invoice by ID for detail view. */
    data class LoadInvoice(val invoiceId: String) : EInvoiceIntent

    // ── IRD Submission ─────────────────────────────────────────────────────
    /** Submit the currently selected DRAFT or REJECTED invoice to the IRD API. */
    data class SubmitToIrd(val invoiceId: String) : EInvoiceIntent

    // ── Cancel ─────────────────────────────────────────────────────────────
    /** Show the cancel confirmation dialog for the given invoice. */
    data class RequestCancel(val invoice: com.zyntasolutions.zyntapos.domain.model.EInvoice) : EInvoiceIntent
    /** Confirm and execute the cancellation. */
    data object ConfirmCancel : EInvoiceIntent
    /** Dismiss the cancel confirmation dialog. */
    data object DismissCancel : EInvoiceIntent

    // ── Global ─────────────────────────────────────────────────────────────
    data object DismissError : EInvoiceIntent
    data object DismissSuccess : EInvoiceIntent
}
