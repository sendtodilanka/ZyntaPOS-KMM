package com.zyntasolutions.zyntapos.domain.usecase.einvoice

import com.zyntasolutions.zyntapos.core.result.Result
import com.zyntasolutions.zyntapos.domain.model.IrdSubmissionResult
import com.zyntasolutions.zyntapos.domain.repository.EInvoiceRepository

/**
 * Submits a DRAFT e-invoice to the IRD (Inland Revenue Department) API.
 *
 * On success, the invoice status transitions to SUBMITTED and the IRD
 * reference number is stored for future audit trail queries.
 *
 * @param id E-invoice record ID to submit.
 * @param submittedAt Epoch millis of submission.
 */
class SubmitEInvoiceToIrdUseCase(
    private val eInvoiceRepository: EInvoiceRepository,
) {
    suspend operator fun invoke(id: String, submittedAt: Long): Result<IrdSubmissionResult> =
        eInvoiceRepository.submitToIrd(id, submittedAt)
}
