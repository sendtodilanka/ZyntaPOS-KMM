package com.zyntasolutions.zyntapos.domain.repository

import com.zyntasolutions.zyntapos.core.result.Result
import com.zyntasolutions.zyntapos.domain.model.Product
import com.zyntasolutions.zyntapos.domain.model.StockAdjustment
import kotlinx.coroutines.flow.Flow

/**
 * Contract for stock-level mutation and movement history.
 *
 * All adjustments are append-only; on-hand quantities in the products table
 * are updated transactionally alongside the [StockAdjustment] insert.
 * This ensures the adjustment log is always consistent with the current stock level.
 */
interface StockRepository {

    /**
     * Applies [adjustment] to the product's on-hand stock quantity.
     *
     * The data layer must execute the following operations atomically:
     * 1. Insert the [StockAdjustment] record.
     * 2. Increment or decrement `products.stock_qty` based on [StockAdjustment.type].
     * 3. Enqueue a sync operation for both affected records.
     *
     * @return [Result.Error] with [com.zyntasolutions.zyntapos.core.result.ZentaException.ValidationException]
     *         if the resulting stock would be negative and negative stock is disabled in settings.
     */
    suspend fun adjustStock(adjustment: StockAdjustment): Result<Unit>

    /**
     * Emits all [StockAdjustment] records for [productId], ordered by timestamp descending.
     * Re-emits when new adjustments are inserted for that product.
     */
    fun getMovements(productId: String): Flow<List<StockAdjustment>>

    /**
     * Emits [Product] records whose `stock_qty < [threshold]`.
     *
     * When [threshold] is omitted the per-product `min_stock_qty` column is used as
     * the comparison value instead of a global threshold.
     *
     * Used by [LowStockAlertBanner] and [GenerateStockReportUseCase]. Re-emits whenever
     * any product's stock quantity changes.
     *
     * @param threshold Global override. Pass `null` to use each product's own `minStockQty`.
     */
    fun getAlerts(threshold: Double? = null): Flow<List<Product>>
}
