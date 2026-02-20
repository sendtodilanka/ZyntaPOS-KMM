package com.zyntasolutions.zyntapos.domain.usecase.inventory

import com.zyntasolutions.zyntapos.core.result.Result
import com.zyntasolutions.zyntapos.core.result.ValidationException
import com.zyntasolutions.zyntapos.core.utils.IdGenerator
import com.zyntasolutions.zyntapos.domain.model.Product
import com.zyntasolutions.zyntapos.domain.model.StockAdjustment
import com.zyntasolutions.zyntapos.domain.model.SyncStatus
import com.zyntasolutions.zyntapos.domain.repository.StockRepository
import com.zyntasolutions.zyntapos.domain.validation.StockValidator
import kotlinx.datetime.Clock

/**
 * Records a manual stock adjustment and triggers a low-stock alert if the resulting
 * quantity falls below [Product.minStockQty].
 *
 * ### Business Rules
 * 1. [StockAdjustment.quantity] must be > 0 (absolute value; direction is [StockAdjustment.Type]).
 * 2. DECREASE adjustments may not reduce stock below zero; validated by [StockValidator].
 * 3. If `product.stockQty - adjustment.quantity < product.minStockQty` after a DECREASE,
 *    a low-stock alert is emitted via [StockRepository.getAlerts] (reactive Flow).
 * 4. [StockAdjustment.reason] must not be blank.
 * 5. The adjustment record is persisted and enqueued for sync.
 *
 * @param stockRepository Persistence for stock adjustments and low-stock alerts.
 */
class AdjustStockUseCase(
    private val stockRepository: StockRepository,
) {
    /**
     * @param productId      The product whose stock is being adjusted.
     * @param type           [StockAdjustment.Type.INCREASE] or [StockAdjustment.Type.DECREASE].
     * @param quantity       Absolute quantity change (must be > 0).
     * @param reason         Mandatory explanation for the adjustment.
     * @param adjustedBy     FK to the user performing the adjustment.
     * @param currentStock   The product's current stock quantity (used for negative-stock check).
     * @param minStockQty    The product's minimum stock threshold (used for alert detection).
     * @return [Result.Success] with [Unit], or [Result.Error] on violation.
     */
    suspend operator fun invoke(
        productId: String,
        type: StockAdjustment.Type,
        quantity: Double,
        reason: String,
        adjustedBy: String,
        currentStock: Double,
        minStockQty: Double = 0.0,
    ): Result<Unit> {
        if (reason.isBlank()) {
            return Result.Error(
                ValidationException("Adjustment reason must not be blank.", field = "reason", rule = "REQUIRED"),
            )
        }

        val stockValidation = StockValidator.validateAdjustment(type, quantity, currentStock)
        if (stockValidation is Result.Error) return stockValidation

        val adjustment = StockAdjustment(
            id = IdGenerator.newId(),
            productId = productId,
            type = type,
            quantity = quantity,
            reason = reason,
            adjustedBy = adjustedBy,
            timestamp = Clock.System.now(),
            syncStatus = SyncStatus(state = SyncStatus.State.PENDING),
        )

        return stockRepository.adjustStock(adjustment)
        // Low-stock alert is automatically emitted by the repository's reactive
        // getAlerts(threshold) flow when stock drops below minStockQty.
    }
}
