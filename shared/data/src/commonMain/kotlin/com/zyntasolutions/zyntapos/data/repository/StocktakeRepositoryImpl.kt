package com.zyntasolutions.zyntapos.data.repository

import com.zyntasolutions.zyntapos.core.result.DatabaseException
import com.zyntasolutions.zyntapos.core.result.Result
import com.zyntasolutions.zyntapos.core.utils.IdGenerator
import com.zyntasolutions.zyntapos.db.ZyntaDatabase
import com.zyntasolutions.zyntapos.domain.model.StocktakeCount
import com.zyntasolutions.zyntapos.domain.model.StocktakeSession
import com.zyntasolutions.zyntapos.domain.model.StocktakeStatus
import com.zyntasolutions.zyntapos.domain.repository.StocktakeRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.time.Clock

class StocktakeRepositoryImpl(
    private val db: ZyntaDatabase,
) : StocktakeRepository {

    private val sq get() = db.stocktake_sessionsQueries

    override suspend fun startSession(userId: String): Result<StocktakeSession> =
        withContext(Dispatchers.IO) {
            runCatching {
                val id = IdGenerator.newId()
                val now = Clock.System.now().toEpochMilliseconds()
                sq.insertSession(id = id, started_by = userId, started_at = now)
                StocktakeSession(
                    id        = id,
                    startedBy = userId,
                    startedAt = now,
                    status    = StocktakeStatus.IN_PROGRESS,
                )
            }.fold(
                onSuccess = { session -> Result.Success(session) },
                onFailure = { t -> Result.Error(DatabaseException(t.message ?: "Failed to start stocktake session", cause = t)) },
            )
        }

    override suspend fun updateCount(
        sessionId: String,
        barcode: String,
        qty: Int,
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            db.transaction {
                val now = Clock.System.now().toEpochMilliseconds()

                // Look up product to get id, name, and system qty
                val product = db.productsQueries.getProductByBarcode(barcode).executeAsOneOrNull()
                    ?: error("Product not found for barcode: $barcode")

                // INSERT OR IGNORE: captures systemQty only on first scan for this barcode
                sq.insertCountIfAbsent(
                    id           = IdGenerator.newId(),
                    session_id   = sessionId,
                    product_id   = product.id,
                    barcode      = barcode,
                    product_name = product.name,
                    system_qty   = product.stock_qty.toLong(),
                    counted_qty  = qty.toLong(),
                    scanned_at   = now,
                )

                // Always update counted_qty (covers both first and subsequent scans)
                sq.updateCount(
                    counted_qty = qty.toLong(),
                    scanned_at  = now,
                    session_id  = sessionId,
                    barcode     = barcode,
                )
            }
        }.fold(
            onSuccess = { Result.Success(Unit) },
            onFailure = { t -> Result.Error(DatabaseException(t.message ?: "Failed to update stocktake count", cause = t)) },
        )
    }

    override suspend fun getSession(id: String): Result<StocktakeSession> =
        withContext(Dispatchers.IO) {
            runCatching {
                val sessionRow = sq.getSessionById(id).executeAsOneOrNull()
                    ?: return@withContext Result.Error(DatabaseException("Stocktake session not found: $id"))

                val countRows = sq.getCountsForSession(id).executeAsList()
                val countsMap = countRows.associate { row ->
                    row.product_id to (row.counted_qty?.toInt() ?: 0)
                }

                StocktakeSession(
                    id          = sessionRow.id,
                    startedBy   = sessionRow.started_by,
                    startedAt   = sessionRow.started_at,
                    status      = StocktakeStatus.valueOf(sessionRow.status),
                    counts      = countsMap,
                    completedAt = sessionRow.completed_at,
                )
            }.fold(
                onSuccess = { session -> Result.Success(session) },
                onFailure = { t -> Result.Error(DatabaseException(t.message ?: "Failed to load stocktake session", cause = t)) },
            )
        }

    override suspend fun getCountsForSession(sessionId: String): Result<List<StocktakeCount>> =
        withContext(Dispatchers.IO) {
            runCatching {
                sq.getCountsForSession(sessionId).executeAsList().map { row ->
                    StocktakeCount(
                        productId   = row.product_id,
                        barcode     = row.barcode,
                        productName = row.product_name,
                        systemQty   = row.system_qty.toInt(),
                        countedQty  = row.counted_qty?.toInt(),
                        scannedAt   = row.scanned_at,
                    )
                }
            }.fold(
                onSuccess = { counts -> Result.Success(counts) },
                onFailure = { t -> Result.Error(DatabaseException(t.message ?: "Failed to load stocktake counts", cause = t)) },
            )
        }

    override suspend fun complete(sessionId: String): Result<Map<String, Int>> =
        withContext(Dispatchers.IO) {
            runCatching {
                db.transactionWithResult {
                    val now = Clock.System.now().toEpochMilliseconds()
                    sq.updateSessionStatus(
                        status       = StocktakeStatus.COMPLETED.name,
                        completed_at = now,
                        id           = sessionId,
                    )

                    // Calculate variances: countedQty - systemQty for each scanned product
                    sq.getCountsForSession(sessionId).executeAsList().associate { row ->
                        val counted = row.counted_qty ?: row.system_qty
                        val variance = (counted - row.system_qty).toInt()
                        row.product_id to variance
                    }
                }
            }.fold(
                onSuccess = { variances -> Result.Success(variances) },
                onFailure = { t -> Result.Error(DatabaseException(t.message ?: "Failed to complete stocktake session", cause = t)) },
            )
        }

    override suspend fun cancel(sessionId: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val now = Clock.System.now().toEpochMilliseconds()
            sq.updateSessionStatus(
                status       = StocktakeStatus.CANCELLED.name,
                completed_at = now,
                id           = sessionId,
            )
        }.fold(
            onSuccess = { Result.Success(Unit) },
            onFailure = { t -> Result.Error(DatabaseException(t.message ?: "Failed to cancel stocktake session", cause = t)) },
        )
    }
}
