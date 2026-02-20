package com.zyntasolutions.zyntapos.domain.validation

import com.zyntasolutions.zyntapos.core.result.Result
import com.zyntasolutions.zyntapos.core.result.ValidationException
import com.zyntasolutions.zyntapos.domain.model.StockAdjustment

/**
 * Validates stock-level business rules to prevent data integrity issues.
 *
 * All methods are pure functions (no I/O) and may be called synchronously.
 */
object StockValidator {

    /**
     * Validates an initial stock quantity when a product is created.
     *
     * ### Rules
     * 1. [stockQty] must be ≥ 0 (products can be created with zero stock).
     *
     * @param stockQty The initial stock quantity.
     * @return [Result.Success] or [Result.Error] with [ValidationException].
     */
    fun validateInitialStock(stockQty: Double): Result<Unit> {
        if (stockQty < 0.0) {
            return Result.Error(
                ValidationException(
                    "Initial stock quantity must be ≥ 0. Got $stockQty.",
                    field = "stockQty",
                    rule = "NEGATIVE_STOCK",
                ),
            )
        }
        return Result.Success(Unit)
    }

    /**
     * Validates a manual stock adjustment to prevent the stock level going negative.
     *
     * ### Rules
     * 1. [quantity] (absolute value) must be > 0.
     * 2. For [StockAdjustment.Type.DECREASE]: `currentStock - quantity` must be ≥ 0.
     * 3. For [StockAdjustment.Type.INCREASE]: always valid if quantity > 0.
     * 4. [StockAdjustment.Type.TRANSFER] is treated the same as DECREASE for source validation.
     *
     * @param type         Direction of the adjustment.
     * @param quantity     Absolute quantity change (must be > 0).
     * @param currentStock Current on-hand stock before the adjustment.
     * @return [Result.Success] or [Result.Error].
     */
    fun validateAdjustment(
        type: StockAdjustment.Type,
        quantity: Double,
        currentStock: Double,
    ): Result<Unit> {
        if (quantity <= 0.0) {
            return Result.Error(
                ValidationException(
                    "Adjustment quantity must be > 0. Got $quantity.",
                    field = "quantity",
                    rule = "MIN_VALUE",
                ),
            )
        }

        if (type == StockAdjustment.Type.DECREASE || type == StockAdjustment.Type.TRANSFER) {
            if (currentStock - quantity < 0.0) {
                return Result.Error(
                    ValidationException(
                        "Adjustment would result in negative stock. " +
                            "Current: $currentStock, decrease: $quantity.",
                        field = "quantity",
                        rule = "NEGATIVE_STOCK",
                    ),
                )
            }
        }

        return Result.Success(Unit)
    }

    /**
     * Validates that a minimum stock quantity threshold is non-negative.
     *
     * @param minStockQty The minimum stock threshold to validate.
     * @return [Result.Success] or [Result.Error].
     */
    fun validateMinStock(minStockQty: Double): Result<Unit> {
        if (minStockQty < 0.0) {
            return Result.Error(
                ValidationException(
                    "Minimum stock quantity must be ≥ 0. Got $minStockQty.",
                    field = "minStockQty",
                    rule = "NEGATIVE_MIN_STOCK",
                ),
            )
        }
        return Result.Success(Unit)
    }
}
