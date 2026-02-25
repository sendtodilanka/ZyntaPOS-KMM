package com.zyntasolutions.zyntapos.domain.usecase.einvoice

import com.zyntasolutions.zyntapos.core.result.Result
import com.zyntasolutions.zyntapos.domain.model.EInvoice
import com.zyntasolutions.zyntapos.domain.repository.EInvoiceRepository

/**
 * Returns the e-invoice linked to a specific order.
 * Returns null inside [Result.Success] if no e-invoice has been generated for the order.
 */
class GetEInvoiceByOrderUseCase(
    private val eInvoiceRepository: EInvoiceRepository,
) {
    suspend operator fun invoke(orderId: String): Result<EInvoice?> =
        eInvoiceRepository.getByOrderId(orderId)
}
