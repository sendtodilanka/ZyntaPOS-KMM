package com.zyntasolutions.zyntapos.domain.usecase.rack

import com.zyntasolutions.zyntapos.domain.model.RackProduct
import com.zyntasolutions.zyntapos.domain.repository.RackProductRepository
import kotlinx.coroutines.flow.Flow

/**
 * Returns a live [Flow] of all [RackProduct] entries for a given rack,
 * joined with product name, SKU, and barcode for display.
 */
class GetRackProductsUseCase(
    private val repo: RackProductRepository,
) {
    operator fun invoke(rackId: String): Flow<List<RackProduct>> =
        repo.getByRack(rackId)
}
