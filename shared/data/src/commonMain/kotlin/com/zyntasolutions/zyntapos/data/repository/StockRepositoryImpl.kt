package com.zyntasolutions.zyntapos.data.repository

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import com.zyntasolutions.zyntapos.core.result.DatabaseException
import com.zyntasolutions.zyntapos.core.result.Result
import com.zyntasolutions.zyntapos.data.local.SyncEnqueuer
import com.zyntasolutions.zyntapos.data.local.mapper.ProductMapper
import com.zyntasolutions.zyntapos.data.local.mapper.StockMapper
import com.zyntasolutions.zyntapos.data.remote.dto.StockAdjustmentSyncPayload
import com.zyntasolutions.zyntapos.data.util.SyncJson
import com.zyntasolutions.zyntapos.data.util.dbCall
import com.zyntasolutions.zyntapos.db.ZyntaDatabase
import com.zyntasolutions.zyntapos.domain.model.Product
import com.zyntasolutions.zyntapos.domain.model.StockAdjustment
import com.zyntasolutions.zyntapos.domain.model.SyncOperation
import com.zyntasolutions.zyntapos.domain.repository.StockRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlin.time.Clock

/**
 * Concrete implementation of [StockRepository].
 *
 * [adjustStock] is fully atomic: the `stock_adjustments` insert and the
 * `products.stock_qty` update happen in a **single transaction**, ensuring
 * the adjustment log is always consistent with the live stock level.
 *
 * ### Responsibility boundary
 * This repository is a pure persistence layer — it does NOT validate business rules
 * such as "disallow negative stock". That responsibility belongs to the domain
 * use case layer (see [AdjustStockUseCase] + [StockValidator]). The repository
 * trusts that callers have already validated the adjustment before calling [adjustStock].
 *
 * Low-stock alerts are surfaced reactively via [getAlerts], which queries the
 * `products` table directly (`stock_qty <= min_stock_qty`). No separate alert
 * table write is needed.
 */
class StockRepositoryImpl(
    private val db: ZyntaDatabase,
    private val syncEnqueuer: SyncEnqueuer,
) : StockRepository {

    private val aq get() = db.stockQueries
    private val pq get() = db.productsQueries

    // ── Sync (server-originated) ────────────────────────────────────────

    /**
     * Applies a server-authoritative stock adjustment record from a sync delta payload.
     *
     * Only inserts the adjustment log row — does NOT update product stock_qty.
     * The server sends authoritative product snapshots separately (via PRODUCT deltas).
     * Does NOT enqueue a [SyncOperation] — server data must not be re-pushed.
     */
    suspend fun upsertFromSync(payload: String) = withContext(Dispatchers.IO) {
        val dto = SyncJson.decodeFromString<StockAdjustmentSyncPayload>(payload)
        val exists = aq.getAdjustmentsByProduct(dto.productId)
            .executeAsList()
            .any { it.id == dto.id }
        if (!exists) {
            aq.insertAdjustment(
                id = dto.id, product_id = dto.productId, type = dto.type,
                quantity = dto.quantity, reason = dto.reason ?: "",
                adjusted_by = dto.adjustedBy ?: "", reference_id = dto.referenceId,
                timestamp = dto.timestamp, sync_status = "SYNCED",
            )
        }
    }

    /**
     * Recomputes `products.stock_qty` from the adjustment ledger (G-Counter pattern).
     *
     * Stock adjustments are append-only — each device inserts its own adjustments independently.
     * This method sums all adjustments (INCREASE adds, DECREASE/TRANSFER subtracts) to derive
     * the authoritative stock quantity. Called after sync to reconcile concurrent adjustments
     * from multiple devices.
     *
     * @param productId The product to recompute.
     * @return The recomputed net quantity.
     */
    suspend fun recomputeStockQty(productId: String): Double = withContext(Dispatchers.IO) {
        val netQty = aq.computeNetStockQty(productId).executeAsOne()
        pq.updateStockQty(netQty, Clock.System.now().toEpochMilliseconds(), productId)
        netQty
    }

    /**
     * Persists a stock adjustment and updates the product's on-hand quantity atomically.
     *
     * Callers (use cases) are responsible for validating that the adjustment does not
     * produce negative stock before calling this method. See [StockValidator.validateAdjustment].
     */
    override suspend fun adjustStock(adjustment: StockAdjustment): Result<Unit> = dbCall("adjustStock") {
        val product = pq.getProductById(adjustment.productId).executeAsOneOrNull()
            ?: throw DatabaseException("Product not found: ${adjustment.productId}", operation = "adjustStock")
        val isDecrease = adjustment.type == StockAdjustment.Type.DECREASE ||
                adjustment.type == StockAdjustment.Type.TRANSFER
        val newQty = if (isDecrease) product.stock_qty - adjustment.quantity
                     else            product.stock_qty + adjustment.quantity
        val p = StockMapper.toInsertParams(adjustment)
        val now = Clock.System.now().toEpochMilliseconds()
        db.transaction {
            aq.insertAdjustment(
                id = p.id, product_id = p.productId, type = p.type,
                quantity = p.quantity, reason = p.reason, adjusted_by = p.adjustedBy,
                reference_id = p.referenceId, timestamp = p.timestamp, sync_status = p.syncStatus,
            )
            pq.updateStockQty(stock_qty = newQty, updated_at = now, id = adjustment.productId)
            syncEnqueuer.enqueue(SyncOperation.EntityType.STOCK_ADJUSTMENT, adjustment.id, SyncOperation.Operation.INSERT)
        }
    }

    override fun getMovements(productId: String): Flow<List<StockAdjustment>> =
        aq.getAdjustmentsByProduct(productId)
            .asFlow()
            .mapToList(Dispatchers.IO)
            .map { rows -> rows.map(StockMapper::toDomain) }

    override fun getAlerts(threshold: Double?): Flow<List<Product>> =
        if (threshold != null) {
            pq.getLowStockProducts()
                .asFlow()
                .mapToList(Dispatchers.IO)
                .map { rows ->
                    rows.filter { it.stock_qty < threshold }
                        .map(ProductMapper::toDomain)
                }
        } else {
            // Use per-product min_stock_qty (SQL: stock_qty <= min_stock_qty AND min_stock_qty > 0)
            pq.getLowStockProducts()
                .asFlow()
                .mapToList(Dispatchers.IO)
                .map { rows -> rows.map(ProductMapper::toDomain) }
        }
}
