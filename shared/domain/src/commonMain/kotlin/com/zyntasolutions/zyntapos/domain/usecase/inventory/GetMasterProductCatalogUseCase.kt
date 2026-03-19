package com.zyntasolutions.zyntapos.domain.usecase.inventory

import com.zyntasolutions.zyntapos.domain.model.MasterProduct
import com.zyntasolutions.zyntapos.domain.repository.MasterProductRepository
import kotlinx.coroutines.flow.Flow

/**
 * Returns the master product catalog for the catalog browser UI.
 *
 * Master products are read-only on POS devices — this use case
 * provides access to the centrally-managed global catalog.
 */
class GetMasterProductCatalogUseCase(
    private val masterProductRepository: MasterProductRepository,
) {
    /** Emits the full active master product list. */
    fun getAll(): Flow<List<MasterProduct>> = masterProductRepository.getAll()

    /** Searches master products by name, SKU, or barcode. */
    fun search(query: String): Flow<List<MasterProduct>> = masterProductRepository.search(query)
}
