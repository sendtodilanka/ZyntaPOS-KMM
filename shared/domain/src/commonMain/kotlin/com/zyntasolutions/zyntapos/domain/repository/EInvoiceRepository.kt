package com.zyntasolutions.zyntapos.domain.repository

import com.zyntasolutions.zyntapos.core.result.Result
import com.zyntasolutions.zyntapos.domain.model.EInvoice
import com.zyntasolutions.zyntapos.domain.model.EInvoiceStatus
import com.zyntasolutions.zyntapos.domain.model.IrdSubmissionResult
import kotlinx.coroutines.flow.Flow

/**
 * Contract for e-invoice management and IRD submission.
 */
interface EInvoiceRepository {

    /** Emits all e-invoices for [storeId], most recent first. Re-emits on change. */
    fun getAll(storeId: String): Flow<List<EInvoice>>

    /** Returns e-invoices filtered by [status]. */
    fun getByStatus(storeId: String, status: EInvoiceStatus): Flow<List<EInvoice>>

    /** Returns a single e-invoice by [id]. */
    suspend fun getById(id: String): Result<EInvoice>

    /** Returns the e-invoice linked to [orderId], if one exists. */
    suspend fun getByOrderId(orderId: String): Result<EInvoice?>

    /** Inserts a new e-invoice in DRAFT status. */
    suspend fun insert(invoice: EInvoice): Result<Unit>

    /**
     * Submits the e-invoice to the IRD API.
     *
     * On success, updates the invoice status to SUBMITTED and stores the IRD reference number.
     *
     * @return [IrdSubmissionResult] with success/error details.
     */
    suspend fun submitToIrd(id: String, submittedAt: Long): Result<IrdSubmissionResult>

    /**
     * Updates the e-invoice status after IRD processes the submission
     * (typically via webhook or polling).
     */
    suspend fun updateStatus(
        id: String,
        status: EInvoiceStatus,
        irdReferenceNumber: String? = null,
        rejectionReason: String? = null,
        updatedAt: Long,
    ): Result<Unit>

    /** Cancels a DRAFT or SUBMITTED e-invoice. */
    suspend fun cancel(id: String, updatedAt: Long): Result<Unit>
}
