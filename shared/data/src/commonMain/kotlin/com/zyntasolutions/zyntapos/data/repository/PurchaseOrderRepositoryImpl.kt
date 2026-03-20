package com.zyntasolutions.zyntapos.data.repository

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import com.zyntasolutions.zyntapos.core.result.DatabaseException
import com.zyntasolutions.zyntapos.core.result.Result
import com.zyntasolutions.zyntapos.core.result.ValidationException
import com.zyntasolutions.zyntapos.db.ZyntaDatabase
import com.zyntasolutions.zyntapos.data.local.SyncEnqueuer
import com.zyntasolutions.zyntapos.domain.model.PurchaseOrder
import com.zyntasolutions.zyntapos.domain.model.PurchaseOrderItem
import com.zyntasolutions.zyntapos.domain.model.SyncOperation
import com.zyntasolutions.zyntapos.domain.repository.PurchaseOrderRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlin.time.Clock

/**
 * SQLDelight-backed implementation of [PurchaseOrderRepository] (C1.3 / C1.5).
 *
 * All writes are wrapped in SQLDelight transactions and enqueue a [SyncOperation]
 * for the offline-sync engine.
 */
class PurchaseOrderRepositoryImpl(
    private val db: ZyntaDatabase,
    private val syncEnqueuer: SyncEnqueuer,
) : PurchaseOrderRepository {

    private val poq get() = db.purchase_ordersQueries

    // ── Read ─────────────────────────────────────────────────────────────────

    override fun getAll(): Flow<List<PurchaseOrder>> =
        poq.getAllPurchaseOrders()
            .asFlow()
            .mapToList(Dispatchers.IO)
            .map { rows ->
                rows.map { row ->
                    PurchaseOrder(
                        id = row.id,
                        supplierId = row.supplier_id,
                        orderNumber = row.order_number,
                        status = safeStatus(row.status),
                        orderDate = row.order_date,
                        expectedDate = row.expected_date,
                        receivedDate = row.received_date,
                        totalAmount = row.total_amount,
                        currency = row.currency,
                        notes = row.notes,
                        createdBy = row.created_by,
                    )
                }
            }

    override suspend fun getById(id: String): Result<PurchaseOrder> = withContext(Dispatchers.IO) {
        runCatching {
            val row = poq.getPurchaseOrderById(id).executeAsOneOrNull()
                ?: return@withContext Result.Error(DatabaseException("PurchaseOrder not found: $id"))
            val items = poq.getPurchaseOrderItems(id).executeAsList().map(::toItemDomain)
            PurchaseOrder(
                id = row.id,
                supplierId = row.supplier_id,
                orderNumber = row.order_number,
                status = safeStatus(row.status),
                orderDate = row.order_date,
                expectedDate = row.expected_date,
                receivedDate = row.received_date,
                totalAmount = row.total_amount,
                currency = row.currency,
                notes = row.notes,
                createdBy = row.created_by,
                items = items,
            )
        }.fold(
            onSuccess = { Result.Success(it) },
            onFailure = { t -> Result.Error(DatabaseException(t.message ?: "DB error", cause = t)) },
        )
    }

    override suspend fun getByDateRange(startDate: Long, endDate: Long): Result<List<PurchaseOrder>> =
        withContext(Dispatchers.IO) {
            runCatching {
                poq.getPurchaseOrdersByDateRange(startDate, endDate).executeAsList().map { row ->
                    PurchaseOrder(
                        id = row.id,
                        supplierId = row.supplier_id,
                        orderNumber = row.order_number,
                        status = safeStatus(row.status),
                        orderDate = row.order_date,
                        expectedDate = row.expected_date,
                        receivedDate = row.received_date,
                        totalAmount = row.total_amount,
                        currency = row.currency,
                        notes = row.notes,
                        createdBy = row.created_by,
                    )
                }
            }.fold(
                onSuccess = { Result.Success(it) },
                onFailure = { t -> Result.Error(DatabaseException(t.message ?: "DB error", cause = t)) },
            )
        }

    override suspend fun getBySupplierId(supplierId: String): Result<List<PurchaseOrder>> =
        withContext(Dispatchers.IO) {
            runCatching {
                poq.getAllPurchaseOrders().executeAsList()
                    .filter { it.supplier_id == supplierId }
                    .map { row ->
                        PurchaseOrder(
                            id = row.id,
                            supplierId = row.supplier_id,
                            orderNumber = row.order_number,
                            status = safeStatus(row.status),
                            orderDate = row.order_date,
                            expectedDate = row.expected_date,
                            receivedDate = row.received_date,
                            totalAmount = row.total_amount,
                            currency = row.currency,
                            notes = row.notes,
                            createdBy = row.created_by,
                        )
                    }
            }.fold(
                onSuccess = { Result.Success(it) },
                onFailure = { t -> Result.Error(DatabaseException(t.message ?: "DB error", cause = t)) },
            )
        }

    override suspend fun getByStatus(status: PurchaseOrder.Status): Result<List<PurchaseOrder>> =
        withContext(Dispatchers.IO) {
            runCatching {
                poq.getAllPurchaseOrders().executeAsList()
                    .filter { it.status == status.name }
                    .map { row ->
                        PurchaseOrder(
                            id = row.id,
                            supplierId = row.supplier_id,
                            orderNumber = row.order_number,
                            status = safeStatus(row.status),
                            orderDate = row.order_date,
                            expectedDate = row.expected_date,
                            receivedDate = row.received_date,
                            totalAmount = row.total_amount,
                            currency = row.currency,
                            notes = row.notes,
                            createdBy = row.created_by,
                        )
                    }
            }.fold(
                onSuccess = { Result.Success(it) },
                onFailure = { t -> Result.Error(DatabaseException(t.message ?: "DB error", cause = t)) },
            )
        }

    // ── Write ─────────────────────────────────────────────────────────────────

    override suspend fun create(order: PurchaseOrder): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val now = Clock.System.now().toEpochMilliseconds()
            db.transaction {
                poq.insertPurchaseOrder(
                    id = order.id,
                    supplier_id = order.supplierId,
                    order_number = order.orderNumber,
                    status = PurchaseOrder.Status.PENDING.name,
                    order_date = order.orderDate,
                    expected_date = order.expectedDate,
                    received_date = null,
                    total_amount = order.totalAmount,
                    currency = order.currency,
                    notes = order.notes,
                    created_by = order.createdBy,
                    created_at = now,
                    updated_at = now,
                    sync_status = "PENDING",
                )
                for (item in order.items) {
                    poq.insertPurchaseOrderItem(
                        id = item.id,
                        purchase_order_id = order.id,
                        product_id = item.productId,
                        quantity_ordered = item.quantityOrdered,
                        quantity_received = 0.0,
                        unit_cost = item.unitCost,
                        line_total = item.lineTotal,
                        notes = item.notes,
                    )
                }
            }
            syncEnqueuer.enqueue(SyncOperation.EntityType.PURCHASE_ORDER, order.id, SyncOperation.Operation.INSERT)
        }.fold(
            onSuccess = { Result.Success(Unit) },
            onFailure = { t -> Result.Error(DatabaseException(t.message ?: "Create PO failed", cause = t)) },
        )
    }

    override suspend fun receiveItems(
        purchaseOrderId: String,
        receivedItems: Map<String, Double>,
        receivedBy: String,
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val existingItems = poq.getPurchaseOrderItems(purchaseOrderId).executeAsList()

            if (existingItems.isEmpty()) {
                return@withContext Result.Error(DatabaseException("No items found for PO: $purchaseOrderId"))
            }

            val itemIds = existingItems.map { it.id }.toSet()
            val invalidIds = receivedItems.keys - itemIds
            if (invalidIds.isNotEmpty()) {
                return@withContext Result.Error(ValidationException("Unknown item IDs: $invalidIds"))
            }

            val now = Clock.System.now().toEpochMilliseconds()
            db.transaction {
                for ((itemId, qty) in receivedItems) {
                    val item = existingItems.first { it.id == itemId }
                    val newQty = (item.quantity_received + qty).coerceAtMost(item.quantity_ordered)
                    poq.updatePurchaseOrderItemReceived(quantity_received = newQty, id = itemId)
                }

                val updatedItems = poq.getPurchaseOrderItems(purchaseOrderId).executeAsList()
                val allReceived = updatedItems.all { it.quantity_received >= it.quantity_ordered }
                val newStatus = if (allReceived) PurchaseOrder.Status.RECEIVED else PurchaseOrder.Status.PARTIAL

                poq.updatePurchaseOrderStatus(
                    status = newStatus.name,
                    received_date = if (allReceived) now else null,
                    updated_at = now,
                    id = purchaseOrderId,
                )
            }
            syncEnqueuer.enqueue(SyncOperation.EntityType.PURCHASE_ORDER, purchaseOrderId, SyncOperation.Operation.UPDATE)
        }.fold(
            onSuccess = { Result.Success(Unit) },
            onFailure = { t -> Result.Error(DatabaseException(t.message ?: "Receive items failed", cause = t)) },
        )
    }

    override suspend fun cancel(purchaseOrderId: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val po = poq.getPurchaseOrderById(purchaseOrderId).executeAsOneOrNull()
                ?: return@withContext Result.Error(DatabaseException("PO not found: $purchaseOrderId"))

            if (po.status == PurchaseOrder.Status.RECEIVED.name || po.status == PurchaseOrder.Status.CANCELLED.name) {
                return@withContext Result.Error(
                    ValidationException("PO $purchaseOrderId is already ${po.status} — cannot cancel")
                )
            }

            val now = Clock.System.now().toEpochMilliseconds()
            poq.updatePurchaseOrderStatus(
                status = PurchaseOrder.Status.CANCELLED.name,
                received_date = null,
                updated_at = now,
                id = purchaseOrderId,
            )
            syncEnqueuer.enqueue(SyncOperation.EntityType.PURCHASE_ORDER, purchaseOrderId, SyncOperation.Operation.UPDATE)
        }.fold(
            onSuccess = { Result.Success(Unit) },
            onFailure = { t -> Result.Error(DatabaseException(t.message ?: "Cancel PO failed", cause = t)) },
        )
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private fun safeStatus(value: String): PurchaseOrder.Status =
        runCatching { PurchaseOrder.Status.valueOf(value) }.getOrDefault(PurchaseOrder.Status.PENDING)

    private fun toItemDomain(row: com.zyntasolutions.zyntapos.db.GetPurchaseOrderItems) = PurchaseOrderItem(
        id = row.id,
        purchaseOrderId = row.purchase_order_id,
        productId = row.product_id,
        quantityOrdered = row.quantity_ordered,
        quantityReceived = row.quantity_received,
        unitCost = row.unit_cost,
        lineTotal = row.line_total,
        notes = row.notes,
    )
}
