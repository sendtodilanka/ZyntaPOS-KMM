package com.zyntasolutions.zyntapos.data.repository

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import com.zyntasolutions.zyntapos.core.result.DatabaseException
import com.zyntasolutions.zyntapos.core.result.Result
import com.zyntasolutions.zyntapos.core.result.ValidationException
import com.zyntasolutions.zyntapos.core.utils.IdGenerator
import com.zyntasolutions.zyntapos.data.local.SyncEnqueuer
import com.zyntasolutions.zyntapos.data.local.mapper.ProductMapper
import com.zyntasolutions.zyntapos.data.local.mapper.StockMapper
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
 * Low-stock alerts are upserted into `stock_alerts` after every DECREASE or
 * TRANSFER adjustment so the dashboard banner always reflects the current state.
 *
 * Negative stock prevention respects the `allow_negative_stock` settings key;
 * however, since [SettingsRepository] is not available here, the guard is
 * conservative: it ALWAYS rejects adjustments that would produce qty < 0.
 * Use-cases requiring negative-stock support should override this at the
 * domain layer via a settings check before calling [adjustStock].
 */
class StockRepositoryImpl(
    private val db: ZyntaDatabase,
    private val syncEnqueuer: SyncEnqueuer,
) : StockRepository {

    private val aq get() = db.stockQueries
    private val pq get() = db.productsQueries
    private val lq get() = db.stockQueries

    override suspend fun adjustStock(adjustment: StockAdjustment): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val product = pq.getProductById(adjustment.productId).executeAsOneOrNull()
                ?: return@withContext Result.Error(
                    DatabaseException("Product not found: ${adjustment.productId}", operation = "adjustStock")
                )
            val isDecrease = adjustment.type == StockAdjustment.Type.DECREASE ||
                    adjustment.type == StockAdjustment.Type.TRANSFER
            val newQty = if (isDecrease) {
                (product.stock_qty - adjustment.quantity)
            } else {
                product.stock_qty + adjustment.quantity
            }
            if (newQty < 0.0) {
                return@withContext Result.Error(
                    ValidationException(
                        message = "Insufficient stock: available ${product.stock_qty}, requested ${adjustment.quantity}",
                        field   = "quantity",
                        rule    = "NEGATIVE_STOCK",
                    )
                )
            }
            val p = StockMapper.toInsertParams(adjustment)
            val now = Clock.System.now().toEpochMilliseconds()
            db.transaction {
                aq.insertAdjustment(
                    id = p.id, product_id = p.productId, type = p.type,
                    quantity = p.quantity, reason = p.reason, adjusted_by = p.adjustedBy,
                    reference_id = p.referenceId, timestamp = p.timestamp, sync_status = p.syncStatus,
                )
                pq.updateStockQty(stock_qty = newQty, updated_at = now, id = adjustment.productId)
                // Upsert or delete low-stock alert
                if (newQty <= product.min_stock_qty && product.min_stock_qty > 0.0) {
                    lq.upsertAlert(
                        id = IdGenerator.newId(), product_id = adjustment.productId,
                        current_qty = newQty, threshold_qty = product.min_stock_qty,
                        triggered_at = now,
                    )
                } else {
                    lq.deleteAlert(adjustment.productId)
                }
                syncEnqueuer.enqueue(SyncOperation.EntityType.STOCK_ADJUSTMENT, adjustment.id, SyncOperation.Operation.INSERT)
            }
        }.fold(
            onSuccess = { Result.Success(Unit) },
            onFailure = { t ->
                if (t is ValidationException) Result.Error(t)
                else Result.Error(DatabaseException(t.message ?: "Adjust stock failed", cause = t))
            },
        )
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
            // Use per-product min_stock_qty (query already handles this)
            pq.getLowStockProducts()
                .asFlow()
                .mapToList(Dispatchers.IO)
                .map { rows -> rows.map(ProductMapper::toDomain) }
        }
}
