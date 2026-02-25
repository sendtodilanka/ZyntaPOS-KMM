package com.zyntasolutions.zyntapos.domain.usecase.einvoice

import com.zyntasolutions.zyntapos.domain.model.EInvoice
import com.zyntasolutions.zyntapos.domain.repository.EInvoiceRepository
import kotlinx.coroutines.flow.Flow

/** Returns a reactive stream of all e-invoices for a store, most recent first. */
class GetEInvoicesUseCase(
    private val eInvoiceRepository: EInvoiceRepository,
) {
    operator fun invoke(storeId: String): Flow<List<EInvoice>> =
        eInvoiceRepository.getAll(storeId)
}
