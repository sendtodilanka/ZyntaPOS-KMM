package com.zyntasolutions.zyntapos.domain.usecase.multistore

import com.zyntasolutions.zyntapos.core.result.Result
import com.zyntasolutions.zyntapos.domain.model.WarehouseStock
import com.zyntasolutions.zyntapos.domain.repository.WarehouseStockRepository
import kotlinx.coroutines.flow.Flow

/**
 * Returns a live [Flow] of all warehouses holding [productId], with per-warehouse
 * quantities. Useful for the cross-warehouse stock distribution view.
 */
class GetStockByProductUseCase(
    private val repo: WarehouseStockRepository,
) {
    /** Live flow of per-warehouse stock for [productId]. */
    operator fun invoke(productId: String): Flow<List<WarehouseStock>> =
        repo.getByProduct(productId)

    /** One-shot aggregated total stock across all warehouses for [productId]. */
    suspend fun totalStock(productId: String): Result<Double> =
        repo.getTotalStock(productId)
}
