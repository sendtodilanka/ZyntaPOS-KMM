package com.zyntasolutions.zyntapos.feature.accounting

import com.zyntasolutions.zyntapos.domain.model.EInvoice
import com.zyntasolutions.zyntapos.domain.model.EInvoiceStatus

/**
 * Immutable UI state for the Accounting / E-Invoice screens (Sprint 18-24).
 *
 * Consumed by [EInvoiceListScreen] and [EInvoiceDetailScreen].
 *
 * @property invoices  All e-invoices for the current store, most recent first.
 * @property statusFilter Active status filter; null = show all.
 * @property selectedInvoice The invoice loaded for detail view / submission.
 * @property isSubmitting True while an IRD submission API call is in flight.
 * @property isLoading True while initial data is loading.
 * @property error Transient error message; null = no error.
 * @property successMessage Transient success message; null = no message.
 * @property showCancelConfirm Non-null while a cancel confirmation dialog is displayed.
 */
data class EInvoiceState(
    val invoices: List<EInvoice> = emptyList(),
    val statusFilter: EInvoiceStatus? = null,
    val selectedInvoice: EInvoice? = null,
    val isSubmitting: Boolean = false,
    val isLoading: Boolean = false,
    val error: String? = null,
    val successMessage: String? = null,
    val showCancelConfirm: EInvoice? = null,
)
