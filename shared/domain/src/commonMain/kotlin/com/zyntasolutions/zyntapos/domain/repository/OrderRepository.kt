package com.zyntasolutions.zyntapos.domain.repository

import com.zyntasolutions.zyntapos.core.result.Result
import com.zyntasolutions.zyntapos.domain.model.CartItem
import com.zyntasolutions.zyntapos.domain.model.Order
import kotlinx.coroutines.flow.Flow
import kotlinx.datetime.Instant

/**
 * Contract for sales order lifecycle management.
 *
 * Orders are append-only once [Order.status] reaches [OrderStatus.COMPLETED] or
 * [OrderStatus.VOIDED] — only the status and void reason may change after that point.
 * The [holdOrder] / [retrieveHeld] pair handles the in-progress cart serialisation
 * pattern used for multi-order juggling at the POS.
 */
interface OrderRepository {

    /**
     * Persists a new [order] and its line items atomically (single transaction).
     *
     * After a successful insert the implementation must also:
     * 1. Decrement `products.stock_qty` for each [Order.items] entry.
     * 2. Enqueue a [SyncOperation] for the remote server.
     *
     * @return [Result.Success] with the persisted [Order] (id and orderNumber are set by the
     *         data layer), or [Result.Error] on any constraint violation.
     */
    suspend fun create(order: Order): Result<Order>

    /**
     * Returns a single [Order] (including its [Order.items]) by UUID [id].
     *
     * @return [Result.Error] with [com.zyntasolutions.zyntapos.core.result.ZyntaException.DatabaseException]
     *         if no order with that ID exists.
     */
    suspend fun getById(id: String): Result<Order>

    /**
     * Emits a list of orders matching the supplied [filters].
     *
     * [filters] is a map of column name → value predicates (e.g., `"status" to "COMPLETED"`).
     * Passing an empty map returns all orders. Re-emits on any write to the orders table.
     *
     * @param filters Optional predicate map. Use [OrderQueryFilters] constants for keys.
     */
    fun getAll(filters: Map<String, String> = emptyMap()): Flow<List<Order>>

    /**
     * Persists a partial update (e.g., adding a note or reference number) to an existing order.
     *
     * Only mutable fields ([Order.notes], [Order.reference]) may be updated after creation.
     * Status transitions must use [void] instead.
     */
    suspend fun update(order: Order): Result<Unit>

    /**
     * Transitions the order identified by [id] to [OrderStatus.VOIDED].
     *
     * The data layer must:
     * 1. Record the [reason] for the void in the audit log.
     * 2. Reverse stock decrements that were applied at [create] time.
     * 3. Enqueue a sync operation for the remote server.
     *
     * @param id     UUID of the order to void.
     * @param reason Mandatory operator-supplied explanation (cannot be blank).
     */
    suspend fun void(id: String, reason: String): Result<Unit>

    /**
     * Emits orders whose [Order.createdAt] falls within [[from], [to]] (inclusive).
     *
     * Used by [GenerateSalesReportUseCase] for time-bounded aggregation.
     * Re-emits on writes within the queried window.
     */
    fun getByDateRange(from: Instant, to: Instant): Flow<List<Order>>

    /**
     * Serialises the in-progress [cart] to a [OrderStatus.HELD] order and persists it.
     *
     * @return [Result.Success] with the generated holdId (UUID), or [Result.Error] on failure.
     */
    suspend fun holdOrder(cart: List<CartItem>): Result<String>

    /**
     * Retrieves a previously-held order by its [holdId] and converts it back to an [Order].
     *
     * The held order record remains in the database with status HELD until the cashier
     * either completes or explicitly discards it.
     *
     * @return [Result.Error] with [com.zyntasolutions.zyntapos.core.result.ZyntaException.DatabaseException]
     *         if no held order with that ID exists.
     */
    suspend fun retrieveHeld(holdId: String): Result<Order>
}

/** Predicate key constants for use with [OrderRepository.getAll]. */
object OrderQueryFilters {
    const val STATUS = "status"
    const val CASHIER_ID = "cashier_id"
    const val CUSTOMER_ID = "customer_id"
    const val REGISTER_SESSION_ID = "register_session_id"
    const val PAYMENT_METHOD = "payment_method"
}
