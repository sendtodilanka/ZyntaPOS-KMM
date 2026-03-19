package com.zyntasolutions.zyntapos.data.repository

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import com.zyntasolutions.zyntapos.core.result.DatabaseException
import com.zyntasolutions.zyntapos.core.result.Result
import com.zyntasolutions.zyntapos.core.result.ValidationException
import com.zyntasolutions.zyntapos.core.utils.IdGenerator
import com.zyntasolutions.zyntapos.data.local.SyncEnqueuer
import com.zyntasolutions.zyntapos.db.Stock_transfers
import com.zyntasolutions.zyntapos.db.Warehouses
import com.zyntasolutions.zyntapos.db.ZyntaDatabase
import com.zyntasolutions.zyntapos.domain.model.StockTransfer
import com.zyntasolutions.zyntapos.domain.model.SyncOperation
import com.zyntasolutions.zyntapos.domain.model.Warehouse
import com.zyntasolutions.zyntapos.domain.repository.WarehouseRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlin.time.Clock

class WarehouseRepositoryImpl(
    private val db: ZyntaDatabase,
    private val syncEnqueuer: SyncEnqueuer,
) : WarehouseRepository {

    private val wq get() = db.warehousesQueries
    private val tq get() = db.stock_transfersQueries
    private val sq get() = db.stockQueries
    private val pq get() = db.productsQueries

    override fun getByStore(storeId: String): Flow<List<Warehouse>> =
        wq.getWarehousesByStore(storeId)
            .asFlow()
            .mapToList(Dispatchers.IO)
            .map { rows -> rows.map(::toWarehouseDomain) }

    override suspend fun getDefault(storeId: String): Result<Warehouse?> = withContext(Dispatchers.IO) {
        runCatching {
            wq.getDefaultWarehouseForStore(storeId).executeAsOneOrNull()?.let(::toWarehouseDomain)
        }.fold(
            onSuccess = { Result.Success(it) },
            onFailure = { t -> Result.Error(DatabaseException(t.message ?: "DB error", cause = t)) },
        )
    }

    override suspend fun getById(id: String): Result<Warehouse> = withContext(Dispatchers.IO) {
        runCatching {
            wq.getWarehouseById(id).executeAsOneOrNull()
                ?: return@withContext Result.Error(DatabaseException("Warehouse not found: $id"))
        }.fold(
            onSuccess = { Result.Success(toWarehouseDomain(it)) },
            onFailure = { t -> Result.Error(DatabaseException(t.message ?: "DB error", cause = t)) },
        )
    }

    override suspend fun insert(warehouse: Warehouse): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val now = Clock.System.now().toEpochMilliseconds()
            db.transaction {
                if (warehouse.isDefault) {
                    wq.clearDefaultForStore(updated_at = now, store_id = warehouse.storeId)
                }
                wq.insertWarehouse(
                    id = warehouse.id, store_id = warehouse.storeId, name = warehouse.name,
                    manager_id = warehouse.managerId,
                    is_active = if (warehouse.isActive) 1L else 0L,
                    is_default = if (warehouse.isDefault) 1L else 0L,
                    address = warehouse.address,
                    created_at = now, updated_at = now, sync_status = "PENDING",
                )
            }
            syncEnqueuer.enqueue(SyncOperation.EntityType.WAREHOUSE, warehouse.id, SyncOperation.Operation.INSERT)
        }.fold(
            onSuccess = { Result.Success(Unit) },
            onFailure = { t -> Result.Error(DatabaseException(t.message ?: "Insert failed", cause = t)) },
        )
    }

    override suspend fun update(warehouse: Warehouse): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val now = Clock.System.now().toEpochMilliseconds()
            db.transaction {
                if (warehouse.isDefault) {
                    wq.clearDefaultForStore(updated_at = now, store_id = warehouse.storeId)
                }
                wq.updateWarehouse(
                    name = warehouse.name, manager_id = warehouse.managerId,
                    is_active = if (warehouse.isActive) 1L else 0L,
                    is_default = if (warehouse.isDefault) 1L else 0L,
                    address = warehouse.address,
                    updated_at = now, sync_status = "PENDING", id = warehouse.id,
                )
            }
            syncEnqueuer.enqueue(SyncOperation.EntityType.WAREHOUSE, warehouse.id, SyncOperation.Operation.UPDATE)
        }.fold(
            onSuccess = { Result.Success(Unit) },
            onFailure = { t -> Result.Error(DatabaseException(t.message ?: "Update failed", cause = t)) },
        )
    }

    override fun getTransfersByWarehouse(warehouseId: String): Flow<List<StockTransfer>> =
        tq.getTransfersByWarehouse(warehouseId, warehouseId)
            .asFlow()
            .mapToList(Dispatchers.IO)
            .map { rows -> rows.map(::toTransferDomain) }

    override suspend fun getTransferById(id: String): Result<StockTransfer> = withContext(Dispatchers.IO) {
        runCatching {
            tq.getTransferById(id).executeAsOneOrNull()
                ?: return@withContext Result.Error(DatabaseException("StockTransfer not found: $id"))
        }.fold(
            onSuccess = { Result.Success(toTransferDomain(it)) },
            onFailure = { t -> Result.Error(DatabaseException(t.message ?: "DB error", cause = t)) },
        )
    }

    override suspend fun getPendingTransfers(): Result<List<StockTransfer>> = withContext(Dispatchers.IO) {
        runCatching {
            tq.getPendingTransfers().executeAsList().map(::toTransferDomain)
        }.fold(
            onSuccess = { Result.Success(it) },
            onFailure = { t -> Result.Error(DatabaseException(t.message ?: "DB error", cause = t)) },
        )
    }

    override suspend fun createTransfer(transfer: StockTransfer): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val now = Clock.System.now().toEpochMilliseconds()
            tq.insertStockTransfer(
                id = transfer.id,
                source_warehouse_id = transfer.sourceWarehouseId,
                dest_warehouse_id = transfer.destWarehouseId,
                product_id = transfer.productId,
                quantity = transfer.quantity,
                status = StockTransfer.Status.PENDING.name,
                notes = transfer.notes,
                transferred_by = transfer.transferredBy,
                created_by = transfer.createdBy,
                created_at = now, updated_at = now, sync_status = "PENDING",
            )
            syncEnqueuer.enqueue(SyncOperation.EntityType.STOCK_TRANSFER, transfer.id, SyncOperation.Operation.INSERT)
        }.fold(
            onSuccess = { Result.Success(Unit) },
            onFailure = { t -> Result.Error(DatabaseException(t.message ?: "Insert failed", cause = t)) },
        )
    }

    override suspend fun commitTransfer(transferId: String, confirmedBy: String): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                val transfer = tq.getTransferById(transferId).executeAsOneOrNull()
                    ?: return@withContext Result.Error(DatabaseException("Transfer not found: $transferId"))

                if (transfer.status != StockTransfer.Status.PENDING.name) {
                    return@withContext Result.Error(
                        ValidationException("Transfer $transferId is already ${transfer.status}")
                    )
                }

                val product = pq.getProductById(transfer.product_id).executeAsOneOrNull()
                    ?: return@withContext Result.Error(DatabaseException("Product not found: ${transfer.product_id}"))

                if (product.stock_qty < transfer.quantity) {
                    return@withContext Result.Error(
                        ValidationException(
                            "Insufficient stock: available ${product.stock_qty}, requested ${transfer.quantity}"
                        )
                    )
                }

                val now = Clock.System.now().toEpochMilliseconds()
                val newQty = product.stock_qty  // net-zero inter-warehouse move; per-warehouse tracking is Phase 3

                db.transaction {
                    // Mark transfer committed with confirmedBy timestamp
                    tq.commitTransfer(transferred_at = now, updated_at = now, id = transferId)

                    // Record audit trail adjustments for traceability
                    sq.insertAdjustment(
                        id = IdGenerator.newId(),
                        product_id = transfer.product_id,
                        type = "TRANSFER_OUT",
                        quantity = -transfer.quantity,
                        reason = "Stock transfer $transferId to warehouse ${transfer.dest_warehouse_id}",
                        adjusted_by = confirmedBy,
                        reference_id = transferId,
                        timestamp = now,
                        sync_status = "PENDING",
                    )
                    sq.insertAdjustment(
                        id = IdGenerator.newId(),
                        product_id = transfer.product_id,
                        type = "TRANSFER_IN",
                        quantity = transfer.quantity,
                        reason = "Stock transfer $transferId from warehouse ${transfer.source_warehouse_id}",
                        adjusted_by = confirmedBy,
                        reference_id = transferId,
                        timestamp = now,
                        sync_status = "PENDING",
                    )

                    // Global stock is unchanged (same product, different location)
                    pq.updateStockQty(stock_qty = newQty, updated_at = now, id = transfer.product_id)
                    syncEnqueuer.enqueue(SyncOperation.EntityType.STOCK_TRANSFER, transferId, SyncOperation.Operation.UPDATE)
                }
            }.fold(
                onSuccess = { Result.Success(Unit) },
                onFailure = { t -> Result.Error(DatabaseException(t.message ?: "Commit failed", cause = t)) },
            )
        }

    override suspend fun cancelTransfer(transferId: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val transfer = tq.getTransferById(transferId).executeAsOneOrNull()
                ?: return@withContext Result.Error(DatabaseException("Transfer not found: $transferId"))

            val status = runCatching { StockTransfer.Status.valueOf(transfer.status) }
                .getOrDefault(StockTransfer.Status.PENDING)
            if (!status.isCancellable) {
                return@withContext Result.Error(
                    ValidationException("Transfer $transferId is already ${transfer.status} — cannot cancel")
                )
            }

            val now = Clock.System.now().toEpochMilliseconds()
            tq.cancelTransfer(updated_at = now, id = transferId)
            syncEnqueuer.enqueue(SyncOperation.EntityType.STOCK_TRANSFER, transferId, SyncOperation.Operation.UPDATE)
        }.fold(
            onSuccess = { Result.Success(Unit) },
            onFailure = { t -> Result.Error(DatabaseException(t.message ?: "Cancel failed", cause = t)) },
        )
    }

    // ── IST Multi-step workflow (C1.3) ────────────────────────────────────────

    override suspend fun approveTransfer(transferId: String, approvedBy: String): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                val transfer = tq.getTransferById(transferId).executeAsOneOrNull()
                    ?: return@withContext Result.Error(DatabaseException("Transfer not found: $transferId"))

                if (transfer.status != StockTransfer.Status.PENDING.name) {
                    return@withContext Result.Error(
                        ValidationException("Transfer $transferId must be PENDING to approve (current: ${transfer.status})")
                    )
                }

                val now = Clock.System.now().toEpochMilliseconds()
                tq.approveTransfer(
                    approved_by = approvedBy,
                    approved_at = now,
                    updated_at = now,
                    id = transferId,
                )
                syncEnqueuer.enqueue(SyncOperation.EntityType.STOCK_TRANSFER, transferId, SyncOperation.Operation.UPDATE)
            }.fold(
                onSuccess = { Result.Success(Unit) },
                onFailure = { t -> Result.Error(DatabaseException(t.message ?: "Approve failed", cause = t)) },
            )
        }

    override suspend fun dispatchTransfer(transferId: String, dispatchedBy: String): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                val transfer = tq.getTransferById(transferId).executeAsOneOrNull()
                    ?: return@withContext Result.Error(DatabaseException("Transfer not found: $transferId"))

                if (transfer.status != StockTransfer.Status.APPROVED.name) {
                    return@withContext Result.Error(
                        ValidationException("Transfer $transferId must be APPROVED to dispatch (current: ${transfer.status})")
                    )
                }

                val product = pq.getProductById(transfer.product_id).executeAsOneOrNull()
                    ?: return@withContext Result.Error(DatabaseException("Product not found: ${transfer.product_id}"))

                if (product.stock_qty < transfer.quantity) {
                    return@withContext Result.Error(
                        ValidationException(
                            "Insufficient stock: available ${product.stock_qty}, requested ${transfer.quantity}"
                        )
                    )
                }

                val now = Clock.System.now().toEpochMilliseconds()
                db.transaction {
                    tq.dispatchTransfer(
                        dispatched_by = dispatchedBy,
                        dispatched_at = now,
                        updated_at = now,
                        id = transferId,
                    )
                    // TRANSFER_OUT at dispatch: stock leaves source
                    sq.insertAdjustment(
                        id = IdGenerator.newId(),
                        product_id = transfer.product_id,
                        type = "TRANSFER_OUT",
                        quantity = -transfer.quantity,
                        reason = "IST dispatch: transfer $transferId to warehouse ${transfer.dest_warehouse_id}",
                        adjusted_by = dispatchedBy,
                        reference_id = transferId,
                        timestamp = now,
                        sync_status = "PENDING",
                    )
                    // Decrement global stock while goods are in transit
                    pq.updateStockQty(
                        stock_qty = product.stock_qty - transfer.quantity,
                        updated_at = now,
                        id = transfer.product_id,
                    )
                    syncEnqueuer.enqueue(SyncOperation.EntityType.STOCK_TRANSFER, transferId, SyncOperation.Operation.UPDATE)
                }
            }.fold(
                onSuccess = { Result.Success(Unit) },
                onFailure = { t -> Result.Error(DatabaseException(t.message ?: "Dispatch failed", cause = t)) },
            )
        }

    override suspend fun receiveTransfer(transferId: String, receivedBy: String): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                val transfer = tq.getTransferById(transferId).executeAsOneOrNull()
                    ?: return@withContext Result.Error(DatabaseException("Transfer not found: $transferId"))

                if (transfer.status != StockTransfer.Status.IN_TRANSIT.name) {
                    return@withContext Result.Error(
                        ValidationException("Transfer $transferId must be IN_TRANSIT to receive (current: ${transfer.status})")
                    )
                }

                val product = pq.getProductById(transfer.product_id).executeAsOneOrNull()
                    ?: return@withContext Result.Error(DatabaseException("Product not found: ${transfer.product_id}"))

                val now = Clock.System.now().toEpochMilliseconds()
                db.transaction {
                    tq.receiveTransfer(
                        received_by = receivedBy,
                        received_at = now,
                        updated_at = now,
                        id = transferId,
                    )
                    // TRANSFER_IN at receipt: stock arrives at destination
                    sq.insertAdjustment(
                        id = IdGenerator.newId(),
                        product_id = transfer.product_id,
                        type = "TRANSFER_IN",
                        quantity = transfer.quantity,
                        reason = "IST receipt: transfer $transferId from warehouse ${transfer.source_warehouse_id}",
                        adjusted_by = receivedBy,
                        reference_id = transferId,
                        timestamp = now,
                        sync_status = "PENDING",
                    )
                    // Restore global stock at destination
                    pq.updateStockQty(
                        stock_qty = product.stock_qty + transfer.quantity,
                        updated_at = now,
                        id = transfer.product_id,
                    )
                    syncEnqueuer.enqueue(SyncOperation.EntityType.STOCK_TRANSFER, transferId, SyncOperation.Operation.UPDATE)
                }
            }.fold(
                onSuccess = { Result.Success(Unit) },
                onFailure = { t -> Result.Error(DatabaseException(t.message ?: "Receive failed", cause = t)) },
            )
        }

    override suspend fun getTransfersByStatus(status: StockTransfer.Status): Result<List<StockTransfer>> =
        withContext(Dispatchers.IO) {
            runCatching {
                tq.getTransfersByStatus(status.name).executeAsList().map(::toTransferDomain)
            }.fold(
                onSuccess = { Result.Success(it) },
                onFailure = { t -> Result.Error(DatabaseException(t.message ?: "DB error", cause = t)) },
            )
        }

    private fun toWarehouseDomain(row: Warehouses) = Warehouse(
        id = row.id, storeId = row.store_id, name = row.name,
        managerId = row.manager_id,
        isActive = row.is_active == 1L,
        isDefault = row.is_default == 1L,
        address = row.address,
    )

    private fun toTransferDomain(row: Stock_transfers) = StockTransfer(
        id = row.id,
        sourceWarehouseId = row.source_warehouse_id,
        destWarehouseId = row.dest_warehouse_id,
        productId = row.product_id,
        quantity = row.quantity,
        status = runCatching { StockTransfer.Status.valueOf(row.status) }.getOrDefault(StockTransfer.Status.PENDING),
        notes = row.notes,
        createdBy = row.created_by,
        approvedBy = row.approved_by,
        approvedAt = row.approved_at,
        dispatchedBy = row.dispatched_by,
        dispatchedAt = row.dispatched_at,
        receivedBy = row.received_by,
        receivedAt = row.received_at,
        transferredBy = row.transferred_by,
        transferredAt = row.transferred_at,
    )
}
