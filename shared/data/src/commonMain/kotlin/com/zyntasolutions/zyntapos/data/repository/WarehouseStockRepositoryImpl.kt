package com.zyntasolutions.zyntapos.data.repository

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import com.zyntasolutions.zyntapos.core.result.DatabaseException
import com.zyntasolutions.zyntapos.core.result.Result
import com.zyntasolutions.zyntapos.core.result.ValidationException
import com.zyntasolutions.zyntapos.core.utils.IdGenerator
import com.zyntasolutions.zyntapos.data.local.SyncEnqueuer
import com.zyntasolutions.zyntapos.db.ZyntaDatabase
import com.zyntasolutions.zyntapos.domain.model.SyncOperation
import com.zyntasolutions.zyntapos.domain.model.WarehouseStock
import com.zyntasolutions.zyntapos.domain.repository.WarehouseStockRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlin.time.Clock

/**
 * SQLDelight-backed implementation of [WarehouseStockRepository].
 *
 * All read queries are mapped from the auto-generated JOIN result types produced by
 * [getStockByWarehouse], [getStockByProduct], [getLowStockByWarehouse], and
 * [getAllLowStock] in `warehouse_stock.sq`.
 *
 * Write operations enqueue sync entries so the background [SyncEngine] can push
 * changes to the cloud when connectivity is available.
 */
class WarehouseStockRepositoryImpl(
    private val db: ZyntaDatabase,
    private val syncEnqueuer: SyncEnqueuer,
) : WarehouseStockRepository {

    private val q get() = db.warehouse_stockQueries

    // ── Read ─────────────────────────────────────────────────────────────────

    override fun getByWarehouse(warehouseId: String): Flow<List<WarehouseStock>> =
        q.getStockByWarehouse(warehouseId)
            .asFlow()
            .mapToList(Dispatchers.IO)
            .map { rows ->
                rows.map { r ->
                    WarehouseStock(
                        id = r.id,
                        warehouseId = r.warehouse_id,
                        productId = r.product_id,
                        quantity = r.quantity,
                        minQuantity = r.min_quantity,
                        updatedAt = r.updated_at,
                        productName = r.product_name,
                        productSku = r.product_sku,
                        productBarcode = r.product_barcode,
                        productImageUrl = r.product_image_url,
                    )
                }
            }

    override fun getByProduct(productId: String): Flow<List<WarehouseStock>> =
        q.getStockByProduct(productId)
            .asFlow()
            .mapToList(Dispatchers.IO)
            .map { rows ->
                rows.map { r ->
                    WarehouseStock(
                        id = r.id,
                        warehouseId = r.warehouse_id,
                        productId = r.product_id,
                        quantity = r.quantity,
                        minQuantity = r.min_quantity,
                        updatedAt = r.updated_at,
                        warehouseName = r.warehouse_name,
                    )
                }
            }

    override suspend fun getEntry(warehouseId: String, productId: String): Result<WarehouseStock?> =
        withContext(Dispatchers.IO) {
            runCatching {
                q.getStockEntry(warehouseId = warehouseId, productId = productId)
                    .executeAsOneOrNull()
                    ?.let { r ->
                        WarehouseStock(
                            id = r.id,
                            warehouseId = r.warehouse_id,
                            productId = r.product_id,
                            quantity = r.quantity,
                            minQuantity = r.min_quantity,
                            updatedAt = r.updated_at,
                        )
                    }
            }.fold(
                onSuccess = { Result.Success(it) },
                onFailure = { t -> Result.Error(DatabaseException(t.message ?: "DB error", cause = t)) },
            )
        }

    override suspend fun getTotalStock(productId: String): Result<Double> =
        withContext(Dispatchers.IO) {
            runCatching {
                q.getTotalStockForProduct(productId).executeAsOne()
            }.fold(
                onSuccess = { Result.Success(it) },
                onFailure = { t -> Result.Error(DatabaseException(t.message ?: "DB error", cause = t)) },
            )
        }

    override fun getLowStockByWarehouse(warehouseId: String): Flow<List<WarehouseStock>> =
        q.getLowStockByWarehouse(warehouseId)
            .asFlow()
            .mapToList(Dispatchers.IO)
            .map { rows ->
                rows.map { r ->
                    WarehouseStock(
                        id = r.id,
                        warehouseId = r.warehouse_id,
                        productId = r.product_id,
                        quantity = r.quantity,
                        minQuantity = r.min_quantity,
                        updatedAt = r.updated_at,
                        productName = r.product_name,
                        productSku = r.product_sku,
                        productBarcode = r.product_barcode,
                        productImageUrl = r.product_image_url,
                    )
                }
            }

    override fun getAllLowStock(): Flow<List<WarehouseStock>> =
        q.getAllLowStock()
            .asFlow()
            .mapToList(Dispatchers.IO)
            .map { rows ->
                rows.map { r ->
                    WarehouseStock(
                        id = r.id,
                        warehouseId = r.warehouse_id,
                        productId = r.product_id,
                        quantity = r.quantity,
                        minQuantity = r.min_quantity,
                        updatedAt = r.updated_at,
                        productName = r.product_name,
                        productSku = r.product_sku,
                        productBarcode = r.product_barcode,
                        productImageUrl = r.product_image_url,
                        warehouseName = r.warehouse_name,
                    )
                }
            }

    // ── Write ────────────────────────────────────────────────────────────────

    override suspend fun upsert(stock: WarehouseStock): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                val now = Clock.System.now().toEpochMilliseconds()
                q.upsertStock(
                    id = stock.id,
                    warehouseId = stock.warehouseId,
                    productId = stock.productId,
                    quantity = stock.quantity,
                    minQuantity = stock.minQuantity,
                    createdAt = now,
                    updatedAt = now,
                )
                syncEnqueuer.enqueue(
                    SyncOperation.EntityType.WAREHOUSE_STOCK,
                    stock.id,
                    SyncOperation.Operation.UPDATE,
                )
            }.fold(
                onSuccess = { Result.Success(Unit) },
                onFailure = { t -> Result.Error(DatabaseException(t.message ?: "Upsert failed", cause = t)) },
            )
        }

    override suspend fun adjustStock(
        warehouseId: String,
        productId: String,
        delta: Double,
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val now = Clock.System.now().toEpochMilliseconds()
            db.transaction {
                // Ensure row exists before adjusting — insert zero-quantity row if missing.
                val existing = q.getStockEntry(
                    warehouseId = warehouseId,
                    productId = productId,
                ).executeAsOneOrNull()
                if (existing == null) {
                    q.upsertStock(
                        id = IdGenerator.newId(),
                        warehouseId = warehouseId,
                        productId = productId,
                        quantity = 0.0,
                        minQuantity = 0.0,
                        createdAt = now,
                        updatedAt = now,
                    )
                }
                q.adjustStock(
                    delta = delta,
                    updatedAt = now,
                    warehouseId = warehouseId,
                    productId = productId,
                )
            }
            // Look up ID for sync enqueue
            val id = q.getStockEntry(warehouseId = warehouseId, productId = productId)
                .executeAsOneOrNull()?.id ?: IdGenerator.newId()
            syncEnqueuer.enqueue(
                SyncOperation.EntityType.WAREHOUSE_STOCK,
                id,
                SyncOperation.Operation.UPDATE,
            )
        }.fold(
            onSuccess = { Result.Success(Unit) },
            onFailure = { t -> Result.Error(DatabaseException(t.message ?: "Adjust failed", cause = t)) },
        )
    }

    override suspend fun transferStock(
        sourceWarehouseId: String,
        destWarehouseId: String,
        productId: String,
        quantity: Double,
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            // Validate source has sufficient stock before entering the transaction.
            val sourceQty = q.getStockEntry(
                warehouseId = sourceWarehouseId,
                productId = productId,
            ).executeAsOneOrNull()?.quantity ?: 0.0

            if (sourceQty < quantity) {
                return@withContext Result.Error(
                    ValidationException(
                        "Insufficient stock at source warehouse: " +
                            "available $sourceQty, requested $quantity"
                    )
                )
            }

            val now = Clock.System.now().toEpochMilliseconds()
            db.transaction {
                // Deduct from source.
                q.adjustStock(
                    delta = -quantity,
                    updatedAt = now,
                    warehouseId = sourceWarehouseId,
                    productId = productId,
                )
                // Ensure destination row exists, then add to it.
                val destExists = q.getStockEntry(
                    warehouseId = destWarehouseId,
                    productId = productId,
                ).executeAsOneOrNull()
                if (destExists == null) {
                    q.upsertStock(
                        id = IdGenerator.newId(),
                        warehouseId = destWarehouseId,
                        productId = productId,
                        quantity = 0.0,
                        minQuantity = 0.0,
                        createdAt = now,
                        updatedAt = now,
                    )
                }
                q.adjustStock(
                    delta = quantity,
                    updatedAt = now,
                    warehouseId = destWarehouseId,
                    productId = productId,
                )
            }
            // Enqueue sync for both affected rows.
            val sourceId = q.getStockEntry(warehouseId = sourceWarehouseId, productId = productId)
                .executeAsOneOrNull()?.id ?: IdGenerator.newId()
            val destId = q.getStockEntry(warehouseId = destWarehouseId, productId = productId)
                .executeAsOneOrNull()?.id ?: IdGenerator.newId()
            syncEnqueuer.enqueue(SyncOperation.EntityType.WAREHOUSE_STOCK, sourceId, SyncOperation.Operation.UPDATE)
            syncEnqueuer.enqueue(SyncOperation.EntityType.WAREHOUSE_STOCK, destId, SyncOperation.Operation.UPDATE)
        }.fold(
            onSuccess = { Result.Success(Unit) },
            onFailure = { t -> Result.Error(DatabaseException(t.message ?: "Transfer failed", cause = t)) },
        )
    }

    override suspend fun deleteEntry(warehouseId: String, productId: String): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                q.deleteEntry(warehouseId = warehouseId, productId = productId)
            }.fold(
                onSuccess = { Result.Success(Unit) },
                onFailure = { t -> Result.Error(DatabaseException(t.message ?: "Delete failed", cause = t)) },
            )
        }
}
