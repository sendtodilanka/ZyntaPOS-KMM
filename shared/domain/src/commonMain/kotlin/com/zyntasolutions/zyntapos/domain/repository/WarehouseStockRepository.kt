package com.zyntasolutions.zyntapos.domain.repository

import com.zyntasolutions.zyntapos.core.result.Result
import com.zyntasolutions.zyntapos.domain.model.WarehouseStock
import kotlinx.coroutines.flow.Flow

/**
 * Contract for per-warehouse product stock level management (C1.2).
 *
 * Each (warehouse, product) pair has exactly one [WarehouseStock] row.
 * Use [upsert] to initialise or update a level; use [adjustStock] for
 * atomic delta adjustments (sales, receipts, transfers).
 */
interface WarehouseStockRepository {

    /**
     * Emits the full stock list for [warehouseId], joined with product info.
     * Re-emits whenever any row for this warehouse changes.
     */
    fun getByWarehouse(warehouseId: String): Flow<List<WarehouseStock>>

    /**
     * Emits all warehouses that hold [productId], joined with warehouse info.
     * Re-emits whenever any row for this product changes.
     */
    fun getByProduct(productId: String): Flow<List<WarehouseStock>>

    /**
     * Returns the single stock entry for ([warehouseId], [productId]).
     * Returns [Result.Success] with `null` when no entry exists yet.
     */
    suspend fun getEntry(warehouseId: String, productId: String): Result<WarehouseStock?>

    /**
     * Returns the sum of all per-warehouse quantities for [productId].
     * Useful for displaying an aggregate "total stock" figure globally.
     */
    suspend fun getTotalStock(productId: String): Result<Double>

    /**
     * Emits low-stock entries for [warehouseId] (quantity ≤ minQuantity > 0).
     * Re-emits whenever the stock level or threshold changes.
     */
    fun getLowStockByWarehouse(warehouseId: String): Flow<List<WarehouseStock>>

    /**
     * Emits all low-stock entries across every active warehouse.
     * Re-emits on any change.
     */
    fun getAllLowStock(): Flow<List<WarehouseStock>>

    /**
     * Inserts or updates the stock entry for ([stock.warehouseId], [stock.productId]).
     * Use this to set an absolute quantity (e.g., stocktake result).
     */
    suspend fun upsert(stock: WarehouseStock): Result<Unit>

    /**
     * Atomically adjusts the quantity for ([warehouseId], [productId]) by [delta].
     * Positive delta = stock in; negative delta = stock out.
     * Fails with [ValidationException] if the resulting quantity would be negative.
     */
    suspend fun adjustStock(
        warehouseId: String,
        productId: String,
        delta: Double,
    ): Result<Unit>

    /**
     * Transfers [quantity] units of [productId] from [sourceWarehouseId] to
     * [destWarehouseId] atomically. Both rows are updated in a single DB transaction.
     * Fails with [ValidationException] if source has insufficient stock.
     */
    suspend fun transferStock(
        sourceWarehouseId: String,
        destWarehouseId: String,
        productId: String,
        quantity: Double,
    ): Result<Unit>

    /**
     * Hard-deletes the stock entry for ([warehouseId], [productId]).
     * Reserved for data-reset / cleanup operations.
     */
    suspend fun deleteEntry(warehouseId: String, productId: String): Result<Unit>
}
