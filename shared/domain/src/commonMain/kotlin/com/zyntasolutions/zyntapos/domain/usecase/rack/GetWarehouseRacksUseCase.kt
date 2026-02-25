package com.zyntasolutions.zyntapos.domain.usecase.rack

import com.zyntasolutions.zyntapos.domain.model.WarehouseRack
import com.zyntasolutions.zyntapos.domain.repository.WarehouseRackRepository
import kotlinx.coroutines.flow.Flow

/**
 * Returns a reactive stream of warehouse racks for a specific warehouse,
 * ordered by name.
 *
 * @param warehouseId The parent warehouse ID.
 */
class GetWarehouseRacksUseCase(
    private val warehouseRackRepository: WarehouseRackRepository,
) {
    operator fun invoke(warehouseId: String): Flow<List<WarehouseRack>> =
        warehouseRackRepository.getByWarehouse(warehouseId)
}
