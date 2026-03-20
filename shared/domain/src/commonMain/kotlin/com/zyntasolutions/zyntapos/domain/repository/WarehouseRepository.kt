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
     * Commits a pending transfer (legacy warehouse-level two-phase commit):
     * 1. Validates status = PENDING and sufficient stock
     * 2. Records TRANSFER_OUT and TRANSFER_IN audit adjustments
     * 3. Sets transfer status = COMMITTED
     * All steps are atomic.
     */
    suspend fun commitTransfer(transferId: String, confirmedBy: String): Result<Unit>

    /** Cancels a PENDING or APPROVED transfer without adjusting stock. */
    suspend fun cancelTransfer(transferId: String): Result<Unit>

    // ── IST Multi-step workflow (C1.3) ────────────────────────────────────────

    /**
     * Approves a PENDING transfer (manager sign-off).
     * Transitions: PENDING → APPROVED
     */
    suspend fun approveTransfer(transferId: String, approvedBy: String): Result<Unit>

    /**
     * Dispatches an APPROVED transfer (goods leave source warehouse).
     * Transitions: APPROVED → IN_TRANSIT
     * Records a TRANSFER_OUT stock adjustment at the source warehouse.
     */
    suspend fun dispatchTransfer(transferId: String, dispatchedBy: String): Result<Unit>

    /**
     * Marks an IN_TRANSIT transfer as received (goods arrive at destination).
     * Transitions: IN_TRANSIT → RECEIVED
     * Records a TRANSFER_IN stock adjustment at the destination warehouse.
     */
    suspend fun receiveTransfer(transferId: String, receivedBy: String): Result<Unit>

    /** Returns all transfers matching the given [status]. */
    suspend fun getTransfersByStatus(status: StockTransfer.Status): Result<List<StockTransfer>>
}
