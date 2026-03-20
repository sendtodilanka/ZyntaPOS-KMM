package com.zyntasolutions.zyntapos.domain.usecase.multistore

import com.zyntasolutions.zyntapos.core.result.Result
import com.zyntasolutions.zyntapos.core.result.ValidationException
import com.zyntasolutions.zyntapos.domain.repository.WarehouseStockRepository

/**
 * Atomically adjusts warehouse stock by a signed delta.
 *
 * - Positive [delta] → stock in (goods received, return, etc.)
 * - Negative [delta] → stock out (sale, wastage, etc.)
 *
 * Fails with [ValidationException] if:
 * - IDs are blank
 * - Delta is zero
 * - Resulting quantity would be negative (insufficient stock)
 */
class AdjustWarehouseStockUseCase(
    private val repo: WarehouseStockRepository,
) {
    suspend operator fun invoke(
        warehouseId: String,
        productId: String,
        delta: Double,
    ): Result<Unit> {
        if (warehouseId.isBlank()) return Result.Error(ValidationException("Warehouse ID required"))
        if (productId.isBlank()) return Result.Error(ValidationException("Product ID required"))
        if (delta == 0.0) return Result.Error(ValidationException("Delta cannot be zero"))

        if (delta < 0.0) {
            val current = when (val r = repo.getEntry(warehouseId, productId)) {
                is Result.Success -> r.data?.quantity ?: 0.0
                is Result.Error -> return r
                is Result.Loading -> return Result.Loading
            }
            if (current + delta < 0.0) {
                return Result.Error(
                    ValidationException(
                        "Insufficient stock at warehouse $warehouseId: " +
                            "available $current, requested ${-delta}"
                    )
                )
            }
        }

        return repo.adjustStock(warehouseId, productId, delta)
    }
}
