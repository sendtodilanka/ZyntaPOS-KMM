package com.zyntasolutions.zyntapos.data.repository

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import com.zyntasolutions.zyntapos.core.result.DatabaseException
import com.zyntasolutions.zyntapos.core.result.Result
import com.zyntasolutions.zyntapos.core.utils.IdGenerator
import com.zyntasolutions.zyntapos.data.local.SyncEnqueuer
import com.zyntasolutions.zyntapos.data.local.mapper.OrderMapper
import com.zyntasolutions.zyntapos.db.ZyntaDatabase
import com.zyntasolutions.zyntapos.domain.model.CartItem
import com.zyntasolutions.zyntapos.domain.model.Order
import com.zyntasolutions.zyntapos.domain.model.OrderStatus
import com.zyntasolutions.zyntapos.domain.model.OrderType
import com.zyntasolutions.zyntapos.domain.model.PaymentMethod
import com.zyntasolutions.zyntapos.domain.model.SyncOperation
import com.zyntasolutions.zyntapos.domain.model.SyncStatus
import com.zyntasolutions.zyntapos.domain.repository.OrderRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant

/**
 * Concrete implementation of [OrderRepository].
 *
 * Order creation is fully atomic: the `orders` row and all `order_items` rows plus
 * the stock decrement plus the sync enqueue happen in a **single** SQLDelight
 * `transaction {}` block, guaranteeing consistency even on process kill mid-write.
 */
class OrderRepositoryImpl(
    private val db: ZyntaDatabase,
    private val syncEnqueuer: SyncEnqueuer,
) : OrderRepository {

    private val q  get() = db.ordersQueries
    private val iq get() = db.orderItemsQueries
    private val pq get() = db.productsQueries

    // ── Read ─────────────────────────────────────────────────────────────────

    override fun getAll(filters: Map<String, String>): Flow<List<Order>> =
        q.getAllOrders()
            .asFlow()
            .mapToList(Dispatchers.IO)
            .map { rows ->
                rows.filter { row -> matchesFilters(row, filters) }
                    .map { row ->
                        val items = iq.getItemsByOrderId(row.id).executeAsList()
                        OrderMapper.toDomain(row, items)
                    }
            }

    override suspend fun getById(id: String): Result<Order> = withContext(Dispatchers.IO) {
        runCatching {
            val row = q.getOrderById(id).executeAsOneOrNull()
                ?: return@withContext Result.Error(
                    DatabaseException("Order not found: $id", operation = "getOrderById")
                )
            val items = iq.getItemsByOrderId(id).executeAsList()
            OrderMapper.toDomain(row, items)
        }.fold(
            onSuccess = { Result.Success(it) },
            onFailure = { t -> Result.Error(DatabaseException(t.message ?: "DB error", cause = t)) },
        )
    }

    override fun getByDateRange(from: Instant, to: Instant): Flow<List<Order>> =
        q.getOrdersByDateRange(from.toEpochMilliseconds(), to.toEpochMilliseconds())
            .asFlow()
            .mapToList(Dispatchers.IO)
            .map { rows ->
                rows.map { row ->
                    val items = iq.getItemsByOrderId(row.id).executeAsList()
                    OrderMapper.toDomain(row, items)
                }
            }

    // ── Write ────────────────────────────────────────────────────────────────

    /**
     * Atomically:
     * 1. Inserts the order header.
     * 2. Inserts each [OrderItem].
     * 3. Decrements `products.stock_qty` for each line.
     * 4. Enqueues a sync operation.
     */
    override suspend fun create(order: Order): Result<Order> = withContext(Dispatchers.IO) {
        runCatching {
            val splits = OrderMapper.serializePaymentSplits(order.paymentSplits)
            val now    = Clock.System.now().toEpochMilliseconds()
            db.transaction {
                q.insertOrder(
                    id = order.id, order_number = order.orderNumber,
                    type = order.type.name, status = order.status.name,
                    customer_id = order.customerId, cashier_id = order.cashierId,
                    store_id = order.storeId, register_session_id = order.registerSessionId.ifBlank { null },
                    subtotal = order.subtotal, tax_amount = order.taxAmount,
                    discount_amount = order.discountAmount, total = order.total,
                    payment_method = order.paymentMethod.name, payment_splits_json = splits,
                    amount_tendered = order.amountTendered, change_amount = order.changeAmount,
                    notes = order.notes, reference = order.reference,
                    created_at = now, updated_at = now, sync_status = "PENDING",
                )
                order.items.forEach { item ->
                    iq.insertOrderItem(
                        id = item.id, order_id = order.id, product_id = item.productId,
                        product_name = item.productName, unit_price = item.unitPrice,
                        quantity = item.quantity, discount = item.discount,
                        discount_type = item.discountType.name, tax_rate = item.taxRate,
                        tax_amount = item.taxAmount, line_total = item.lineTotal,
                    )
                    // Decrement stock atomically
                    val currentQty = pq.getProductById(item.productId)
                        .executeAsOneOrNull()?.stock_qty ?: 0.0
                    pq.updateStockQty(
                        stock_qty  = (currentQty - item.quantity).coerceAtLeast(0.0),
                        updated_at = now,
                        id         = item.productId,
                    )
                }
                syncEnqueuer.enqueue(SyncOperation.EntityType.ORDER, order.id, SyncOperation.Operation.INSERT)
            }
        }.fold(
            onSuccess = { Result.Success(order) },
            onFailure = { t -> Result.Error(DatabaseException(t.message ?: "Create failed", operation = "insertOrder", cause = t)) },
        )
    }

    override suspend fun update(order: Order): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val now = Clock.System.now().toEpochMilliseconds()
            db.transaction {
                q.voidOrder(notes = order.notes, updated_at = now, id = order.id)
                syncEnqueuer.enqueue(SyncOperation.EntityType.ORDER, order.id, SyncOperation.Operation.UPDATE)
            }
        }.fold(
            onSuccess = { Result.Success(Unit) },
            onFailure = { t -> Result.Error(DatabaseException(t.message ?: "Update failed", cause = t)) },
        )
    }

    override suspend fun void(id: String, reason: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val order = q.getOrderById(id).executeAsOneOrNull()
                ?: return@withContext Result.Error(
                    DatabaseException("Order not found: $id", operation = "voidOrder")
                )
            val items = iq.getItemsByOrderId(id).executeAsList()
            val now   = Clock.System.now().toEpochMilliseconds()
            db.transaction {
                q.voidOrder(notes = reason, updated_at = now, id = id)
                // Reverse stock decrements
                items.forEach { item ->
                    val currentQty = pq.getProductById(item.product_id)
                        .executeAsOneOrNull()?.stock_qty ?: 0.0
                    pq.updateStockQty(
                        stock_qty  = currentQty + item.quantity,
                        updated_at = now,
                        id         = item.product_id,
                    )
                }
                syncEnqueuer.enqueue(SyncOperation.EntityType.ORDER, id, SyncOperation.Operation.UPDATE)
            }
        }.fold(
            onSuccess = { Result.Success(Unit) },
            onFailure = { t -> Result.Error(DatabaseException(t.message ?: "Void failed", operation = "voidOrder", cause = t)) },
        )
    }

    override suspend fun holdOrder(cart: List<CartItem>): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val holdId = IdGenerator.newId()
            val holdNumber = IdGenerator.newPrefixedId("HLD")
            val now = Clock.System.now().toEpochMilliseconds()
            db.transaction {
                q.insertOrder(
                    id = holdId, order_number = holdNumber,
                    type = OrderType.SALE.name, status = OrderStatus.HELD.name,
                    customer_id = null, cashier_id = cart.firstOrNull()?.let { "" } ?: "",
                    store_id = "", register_session_id = null,
                    subtotal = cart.sumOf { it.lineTotal }, tax_amount = 0.0,
                    discount_amount = 0.0, total = cart.sumOf { it.lineTotal },
                    payment_method = PaymentMethod.CASH.name, payment_splits_json = null,
                    amount_tendered = 0.0, change_amount = 0.0,
                    notes = "HELD", reference = null,
                    created_at = now, updated_at = now, sync_status = "PENDING",
                )
                cart.forEach { item ->
                    iq.insertOrderItem(
                        id = IdGenerator.newId(), order_id = holdId,
                        product_id = item.productId, product_name = item.productName,
                        unit_price = item.unitPrice, quantity = item.quantity,
                        discount = item.discount, discount_type = item.discountType.name,
                        tax_rate = 0.0, tax_amount = 0.0, line_total = item.lineTotal,
                    )
                }
            }
            holdId
        }.fold(
            onSuccess = { Result.Success(it) },
            onFailure = { t -> Result.Error(DatabaseException(t.message ?: "Hold failed", cause = t)) },
        )
    }

    override suspend fun retrieveHeld(holdId: String): Result<Order> = withContext(Dispatchers.IO) {
        runCatching {
            val row = q.getOrderById(holdId).executeAsOneOrNull()
                ?: return@withContext Result.Error(
                    DatabaseException("Held order not found: $holdId", operation = "retrieveHeld")
                )
            val items = iq.getItemsByOrderId(holdId).executeAsList()
            OrderMapper.toDomain(row, items)
        }.fold(
            onSuccess = { Result.Success(it) },
            onFailure = { t -> Result.Error(DatabaseException(t.message ?: "DB error", cause = t)) },
        )
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private fun matchesFilters(row: com.zyntasolutions.zyntapos.db.Orders, filters: Map<String, String>): Boolean {
        if (filters.isEmpty()) return true
        return filters.all { (key, value) ->
            when (key) {
                "status"              -> row.status == value
                "cashier_id"          -> row.cashier_id == value
                "customer_id"         -> row.customer_id == value
                "register_session_id" -> row.register_session_id == value
                "payment_method"      -> row.payment_method == value
                else                  -> true
            }
        }
    }
}
