package com.zyntasolutions.zyntapos.domain.usecase.multistore

import com.zyntasolutions.zyntapos.core.result.Result
import com.zyntasolutions.zyntapos.core.result.ValidationException
import com.zyntasolutions.zyntapos.core.utils.IdGenerator
import com.zyntasolutions.zyntapos.domain.model.WarehouseStock
import com.zyntasolutions.zyntapos.domain.repository.WarehouseStockRepository

/**
 * Sets the absolute stock quantity for a (warehouse, product) pair.
 *
 * Use for:
 * - Initial stock seeding when a product is added to a warehouse.
 * - Stocktake corrections (physical count results).
 * - Updating the reorder threshold ([minQuantity]).
 */
class SetWarehouseStockUseCase(
    private val repo: WarehouseStockRepository,
) {
    suspend operator fun invoke(
        warehouseId: String,
        productId: String,
        quantity: Double,
        minQuantity: Double = 0.0,
    ): Result<Unit> {
        if (warehouseId.isBlank()) return Result.Error(ValidationException("Warehouse ID required"))
        if (productId.isBlank()) return Result.Error(ValidationException("Product ID required"))
        if (quantity < 0.0) return Result.Error(ValidationException("Quantity cannot be negative"))
        if (minQuantity < 0.0) return Result.Error(ValidationException("Min quantity cannot be negative"))

        val existing = when (val r = repo.getEntry(warehouseId, productId)) {
            is Result.Success -> r.data
            is Result.Error -> return r
            is Result.Loading -> return Result.Loading
        }

        val now = kotlin.time.Clock.System.now().toEpochMilliseconds()
        val stock = WarehouseStock(
            id = existing?.id ?: IdGenerator.newId(),
            warehouseId = warehouseId,
            productId = productId,
            quantity = quantity,
            minQuantity = minQuantity,
            updatedAt = now,
        )
        return repo.upsert(stock)
    }
}
