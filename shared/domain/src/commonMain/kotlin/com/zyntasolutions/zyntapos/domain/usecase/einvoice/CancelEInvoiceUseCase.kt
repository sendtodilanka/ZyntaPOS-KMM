package com.zyntasolutions.zyntapos.domain.usecase.einvoice

import com.zyntasolutions.zyntapos.core.result.Result
import com.zyntasolutions.zyntapos.domain.repository.EInvoiceRepository

/**
 * Cancels a DRAFT or SUBMITTED e-invoice.
 *
 * Cancelled invoices are retained for audit purposes but excluded from
 * active invoice listings and IRD reporting totals.
 */
class CancelEInvoiceUseCase(
    private val eInvoiceRepository: EInvoiceRepository,
) {
    suspend operator fun invoke(id: String, updatedAt: Long): Result<Unit> =
        eInvoiceRepository.cancel(id, updatedAt)
}
