package com.zyntasolutions.zyntapos.domain.repository

import com.zyntasolutions.zyntapos.core.result.Result
import com.zyntasolutions.zyntapos.domain.model.StockTransfer
import com.zyntasolutions.zyntapos.domain.model.Warehouse
import kotlinx.coroutines.flow.Flow

/**
 * Contract for warehouse management and inter-warehouse stock transfers.
 */
interface WarehouseRepository {

    // ── Warehouses ────────────────────────────────────────────────────────────

    /** Emits all active warehouses for the given [storeId], default warehouse first. */
    fun getByStore(storeId: String): Flow<List<Warehouse>>

    /** Returns the default warehouse for [storeId]. */
    suspend fun getDefault(storeId: String): Result<Warehouse?>

    /** Returns a warehouse by [id]. */
    suspend fun getById(id: String): Result<Warehouse>

    /** Inserts a new warehouse and enqueues a sync operation. */
    suspend fun insert(warehouse: Warehouse): Result<Unit>

    /** Updates a warehouse and enqueues a sync operation. */
    suspend fun update(warehouse: Warehouse): Result<Unit>

    // ── Stock Transfers ───────────────────────────────────────────────────────

    /** Emits transfers involving the given [warehouseId] (source or dest). */
    fun getTransfersByWarehouse(warehouseId: String): Flow<List<StockTransfer>>

    /** Returns a transfer by [id]. */
    suspend fun getTransferById(id: String): Result<StockTransfer>

    /** Returns all pending transfers. */
    suspend fun getPendingTransfers(): Result<List<StockTransfer>>

    /**
     * Creates a pending transfer and enqueues a sync operation.
     * Does NOT adjust stock quantities — call [commitTransfer] to apply.
     */
    suspend fun createTransfer(transfer: StockTransfer): Result<Unit>

    /**
     * Commits a pending transfer:
     * 1. Decrements source warehouse stock
     * 2. Increments dest warehouse stock
     * 3. Sets transfer status = COMMITTED
     * All steps are atomic.
     */
    suspend fun commitTransfer(transferId: String, confirmedBy: String): Result<Unit>

    /** Cancels a pending transfer without adjusting stock. */
    suspend fun cancelTransfer(transferId: String): Result<Unit>
}
