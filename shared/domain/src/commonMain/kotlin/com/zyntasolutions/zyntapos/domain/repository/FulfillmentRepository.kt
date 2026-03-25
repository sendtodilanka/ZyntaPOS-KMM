package com.zyntasolutions.zyntapos.domain.repository

import com.zyntasolutions.zyntapos.core.result.Result
import com.zyntasolutions.zyntapos.domain.model.FulfillmentStatus
import kotlinx.coroutines.flow.Flow

/**
 * Contract for Click & Collect (BOPIS) fulfillment order management.
 *
 * Tracks the in-store pickup lifecycle for [com.zyntasolutions.zyntapos.domain.model.OrderType.CLICK_AND_COLLECT]
 * orders from receipt through customer collection.
 *
 * Implementation note: The fulfillment record is created when an order is placed
 * with type [com.zyntasolutions.zyntapos.domain.model.OrderType.CLICK_AND_COLLECT].
 */
interface FulfillmentRepository {

    /**
     * Emits the list of active pickup orders for a store, ordered by creation time ascending.
     * Re-emits on status changes.
     *
     * @param storeId The store handling the pickup.
     */
    fun getPendingPickups(storeId: String): Flow<List<FulfillmentOrder>>

    /**
     * Retrieves a specific fulfillment record by its associated order ID.
     *
     * @param orderId The order that originated the fulfillment request.
     * @return [Result.Success] with the [FulfillmentOrder], or [Result.Error] if not found.
     */
    suspend fun getByOrderId(orderId: String): Result<FulfillmentOrder>

    /**
     * Creates a new fulfillment record for a Click & Collect order.
     *
     * @param fulfillment The fulfillment order to persist. Status must be [FulfillmentStatus.RECEIVED].
     * @return [Result.Success] on success, [Result.Error] on DB failure.
     */
    suspend fun create(fulfillment: FulfillmentOrder): Result<Unit>

    /**
     * Updates the fulfillment status and records the timestamp of the transition.
     *
     * @param orderId The associated order ID.
     * @param newStatus The target fulfillment state.
     * @param notifyCustomer Whether to trigger a customer notification for this transition.
     * @return [Result.Success] on success, [Result.Error] on DB failure or invalid transition.
     */
    suspend fun updateStatus(
        orderId: String,
        newStatus: FulfillmentStatus,
        notifyCustomer: Boolean = false,
    ): Result<Unit>

    /**
     * Marks all RECEIVED or PREPARING orders that have exceeded [timeoutEpochMillis] as EXPIRED.
     *
     * Intended to be called by a periodic background job.
     *
     * @param storeId The store to scan for expired orders.
     * @param timeoutEpochMillis Epoch millis threshold — orders with `pickupDeadline` before this
     *        value and not yet PICKED_UP are marked EXPIRED.
     * @return [Result.Success] with the count of expired orders, or [Result.Error] on DB failure.
     */
    suspend fun expireOverdueOrders(storeId: String, timeoutEpochMillis: Long): Result<Int>
}

/**
 * Represents a single Click & Collect fulfillment record.
 *
 * @property orderId FK to the originating [com.zyntasolutions.zyntapos.domain.model.Order].
 * @property storeId The store where the customer will collect the order.
 * @property customerId FK to the placing customer.
 * @property status Current fulfillment lifecycle state.
 * @property pickupDeadline Epoch millis after which the order expires if not collected.
 * @property customerNotified True when the customer has been notified of the READY_FOR_PICKUP state.
 * @property createdAt Epoch millis when the order was placed.
 * @property updatedAt Epoch millis of the last status update.
 */
data class FulfillmentOrder(
    val orderId: String,
    val storeId: String,
    val customerId: String,
    val status: FulfillmentStatus,
    val pickupDeadline: Long,
    val customerNotified: Boolean = false,
    val createdAt: Long,
    val updatedAt: Long,
)
