package com.zyntasolutions.zyntapos.domain.usecase.multistore

import com.zyntasolutions.zyntapos.domain.model.WarehouseStock
import com.zyntasolutions.zyntapos.domain.repository.WarehouseStockRepository
import kotlinx.coroutines.flow.Flow

/**
 * Returns a live [Flow] of low-stock [WarehouseStock] entries.
 *
 * An entry is considered low-stock when its quantity is at or below its
 * [WarehouseStock.minQuantity] threshold (and the threshold is > 0).
 * Results are ordered by stock shortfall descending (most critical first).
 */
class GetLowStockByWarehouseUseCase(
    private val repo: WarehouseStockRepository,
) {
    /** Low-stock items scoped to a single [warehouseId]. */
    operator fun invoke(warehouseId: String): Flow<List<WarehouseStock>> =
        repo.getLowStockByWarehouse(warehouseId)

    /** Low-stock items across ALL active warehouses. */
    fun allWarehouses(): Flow<List<WarehouseStock>> =
        repo.getAllLowStock()
}
