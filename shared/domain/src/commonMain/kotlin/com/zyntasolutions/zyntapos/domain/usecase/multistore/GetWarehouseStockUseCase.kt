package com.zyntasolutions.zyntapos.domain.usecase.multistore

import com.zyntasolutions.zyntapos.domain.model.WarehouseStock
import com.zyntasolutions.zyntapos.domain.repository.WarehouseStockRepository
import kotlinx.coroutines.flow.Flow

/**
 * Returns a live [Flow] of all [WarehouseStock] entries for [warehouseId].
 * Each entry is joined with product name, SKU, and barcode for display.
 */
class GetWarehouseStockUseCase(
    private val repo: WarehouseStockRepository,
) {
    operator fun invoke(warehouseId: String): Flow<List<WarehouseStock>> =
        repo.getByWarehouse(warehouseId)
}
