package com.zyntasolutions.zyntapos.domain.model

/**
 * An electronic invoice submitted to the IRD (Inland Revenue Department) for tax compliance.
 *
 * @property id Unique identifier (UUID v4).
 * @property orderId FK to the originating [Order].
 * @property storeId Store that issued the invoice.
 * @property invoiceNumber Sequential invoice number assigned by the store.
 * @property invoiceDate ISO date: YYYY-MM-DD.
 * @property customerName Customer name (or "Walk-in Customer").
 * @property customerTaxId Customer VAT/TIN number. Null for non-business customers.
 * @property lineItems Itemised list of products/services.
 * @property subtotal Pre-tax total.
 * @property taxBreakdown Per-tax-rate breakdown.
 * @property totalTax Total tax amount.
 * @property total Grand total (subtotal + totalTax).
 * @property currency ISO 4217 currency code (e.g., "LKR").
 * @property status IRD submission status.
 * @property irdReferenceNumber IRD-assigned reference on acceptance. Null until accepted.
 * @property submittedAt Epoch millis of IRD submission. Null if not yet submitted.
 * @property acceptedAt Epoch millis of IRD acceptance. Null if not yet accepted.
 * @property rejectionReason IRD rejection reason text. Null unless rejected.
 * @property createdAt Epoch millis of record creation.
 * @property updatedAt Epoch millis of last update.
 */
data class EInvoice(
    val id: String,
    val orderId: String,
    val storeId: String,
    val invoiceNumber: String,
    val invoiceDate: String,
    val customerName: String,
    val customerTaxId: String? = null,
    val lineItems: List<EInvoiceLineItem>,
    val subtotal: Double,
    val taxBreakdown: List<TaxBreakdownItem>,
    val totalTax: Double,
    val total: Double,
    val currency: String = "LKR",
    val status: EInvoiceStatus = EInvoiceStatus.DRAFT,
    val irdReferenceNumber: String? = null,
    val submittedAt: Long? = null,
    val acceptedAt: Long? = null,
    val rejectionReason: String? = null,
    val createdAt: Long,
    val updatedAt: Long,
)

/**
 * A single line item within an e-invoice.
 */
data class EInvoiceLineItem(
    val productId: String,
    val description: String,
    val quantity: Double,
    val unitPrice: Double,
    val taxRate: Double,
    val taxAmount: Double,
    val lineTotal: Double,
)

/**
 * Per-tax-rate breakdown for the e-invoice total.
 */
data class TaxBreakdownItem(
    val taxRate: Double,
    val taxablAmount: Double,
    val taxAmount: Double,
)

/**
 * Result of submitting an e-invoice to the IRD API.
 */
data class IrdSubmissionResult(
    val success: Boolean,
    val referenceNumber: String? = null,
    val errorCode: String? = null,
    val errorMessage: String? = null,
    val submittedAt: Long,
)

/** IRD e-invoice submission status lifecycle. */
enum class EInvoiceStatus {
    DRAFT,
    SUBMITTED,
    ACCEPTED,
    REJECTED,
    CANCELLED,
}
