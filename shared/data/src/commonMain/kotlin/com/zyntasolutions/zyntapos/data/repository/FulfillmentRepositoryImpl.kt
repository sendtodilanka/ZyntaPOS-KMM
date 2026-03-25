package com.zyntasolutions.zyntapos.data.repository

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import com.zyntasolutions.zyntapos.core.result.DatabaseException
import com.zyntasolutions.zyntapos.core.result.Result
import com.zyntasolutions.zyntapos.db.ZyntaDatabase
import com.zyntasolutions.zyntapos.domain.model.FulfillmentStatus
import com.zyntasolutions.zyntapos.domain.repository.FulfillmentOrder
import com.zyntasolutions.zyntapos.domain.repository.FulfillmentRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlin.time.Clock

/**
 * Concrete implementation of [FulfillmentRepository] backed by SQLDelight.
 *
 * All mutations mark the row as `PENDING` sync so the SyncEngine picks it
 * up on the next cycle and pushes it to the backend.
 */
class FulfillmentRepositoryImpl(
    private val db: ZyntaDatabase,
) : FulfillmentRepository {

    override fun getPendingPickups(storeId: String): Flow<List<FulfillmentOrder>> =
        db.fulfillmentOrdersQueries
            .getPendingPickups(storeId)
            .asFlow()
            .mapToList(Dispatchers.Default)
            .map { rows -> rows.map { it.toDomain() } }

    override suspend fun getByOrderId(orderId: String): Result<FulfillmentOrder> =
        withContext(Dispatchers.Default) {
            try {
                val row = db.fulfillmentOrdersQueries.getByOrderId(orderId).executeAsOneOrNull()
                    ?: return@withContext Result.Error(DatabaseException("Fulfillment not found: $orderId"))
                Result.Success(row.toDomain())
            } catch (e: Exception) {
                Result.Error(DatabaseException(e.message ?: "DB error"))
            }
        }

    override suspend fun create(fulfillment: FulfillmentOrder): Result<Unit> =
        withContext(Dispatchers.Default) {
            try {
                db.fulfillmentOrdersQueries.insert(
                    order_id          = fulfillment.orderId,
                    store_id          = fulfillment.storeId,
                    customer_id       = fulfillment.customerId,
                    status            = fulfillment.status.name,
                    pickup_deadline   = fulfillment.pickupDeadline,
                    customer_notified = if (fulfillment.customerNotified) 1L else 0L,
                    notes             = null,
                    sync_status       = "PENDING",
                    created_at        = fulfillment.createdAt,
                    updated_at        = fulfillment.updatedAt,
                )
                Result.Success(Unit)
            } catch (e: Exception) {
                Result.Error(DatabaseException(e.message ?: "Insert failed"))
            }
        }

    override suspend fun updateStatus(
        orderId: String,
        newStatus: FulfillmentStatus,
        notifyCustomer: Boolean,
    ): Result<Unit> = withContext(Dispatchers.Default) {
        try {
            db.fulfillmentOrdersQueries.updateStatus(
                status            = newStatus.name,
                customerNotified  = if (notifyCustomer) 1L else 0L,
                updatedAt         = Clock.System.now().toEpochMilliseconds(),
                orderId           = orderId,
            )
            Result.Success(Unit)
        } catch (e: Exception) {
            Result.Error(DatabaseException(e.message ?: "Update failed"))
        }
    }

    override suspend fun expireOverdueOrders(storeId: String, timeoutEpochMillis: Long): Result<Int> =
        withContext(Dispatchers.Default) {
            try {
                db.fulfillmentOrdersQueries.expireOverdueOrders(
                    now                = Clock.System.now().toEpochMilliseconds(),
                    storeId            = storeId,
                    timeoutEpochMillis = timeoutEpochMillis,
                )
                val expired = db.fulfillmentOrdersQueries.countExpired(storeId).executeAsOne()
                Result.Success(expired.toInt())
            } catch (e: Exception) {
                Result.Error(DatabaseException(e.message ?: "Expire failed"))
            }
        }
}

// ── Mapper ───────────────────────────────────────────────────────────────────

private fun com.zyntasolutions.zyntapos.db.Fulfillment_orders.toDomain() = FulfillmentOrder(
    orderId           = order_id,
    storeId           = store_id,
    customerId        = customer_id,
    status            = runCatching { FulfillmentStatus.valueOf(status) }.getOrDefault(FulfillmentStatus.RECEIVED),
    pickupDeadline    = pickup_deadline,
    customerNotified  = customer_notified != 0L,
    createdAt         = created_at,
    updatedAt         = updated_at,
)
