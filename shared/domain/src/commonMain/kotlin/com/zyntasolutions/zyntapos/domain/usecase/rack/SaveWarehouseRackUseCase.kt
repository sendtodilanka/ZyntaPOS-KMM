package com.zyntasolutions.zyntapos.domain.usecase.rack

import com.zyntasolutions.zyntapos.core.result.Result
import com.zyntasolutions.zyntapos.core.result.ValidationException
import com.zyntasolutions.zyntapos.domain.model.WarehouseRack
import com.zyntasolutions.zyntapos.domain.repository.WarehouseRackRepository

/**
 * Saves a warehouse rack (insert or update).
 *
 * ### Business Rules
 * 1. [WarehouseRack.name] must not be blank.
 * 2. [WarehouseRack.warehouseId] must not be blank.
 * 3. [WarehouseRack.capacity], if provided, must be positive.
 *
 * @param rack The rack to persist.
 * @param isUpdate `true` to update an existing record; `false` to insert a new one.
 */
class SaveWarehouseRackUseCase(
    private val warehouseRackRepository: WarehouseRackRepository,
) {
    suspend operator fun invoke(rack: WarehouseRack, isUpdate: Boolean = false): Result<Unit> {
        if (rack.name.isBlank()) {
            return Result.Error(
                ValidationException("Rack name must not be blank.", field = "name", rule = "REQUIRED"),
            )
        }
        if (rack.warehouseId.isBlank()) {
            return Result.Error(
                ValidationException("Warehouse ID must not be blank.", field = "warehouseId", rule = "REQUIRED"),
            )
        }
        rack.capacity?.let { cap ->
            if (cap <= 0) {
                return Result.Error(
                    ValidationException(
                        "Capacity must be a positive integer.",
                        field = "capacity",
                        rule = "MIN_VALUE",
                    ),
                )
            }
        }
        return if (isUpdate) warehouseRackRepository.update(rack) else warehouseRackRepository.insert(rack)
    }
}
