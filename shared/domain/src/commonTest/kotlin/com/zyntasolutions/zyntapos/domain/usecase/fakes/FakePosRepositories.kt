package com.zyntasolutions.zyntapos.domain.usecase.fakes

import com.zyntasolutions.zyntapos.core.result.DatabaseException
import com.zyntasolutions.zyntapos.core.result.ValidationException
import com.zyntasolutions.zyntapos.core.result.Result
import com.zyntasolutions.zyntapos.domain.model.CartItem
import com.zyntasolutions.zyntapos.domain.model.CashMovement
import com.zyntasolutions.zyntapos.domain.model.CashRegister
import com.zyntasolutions.zyntapos.domain.model.DiscountType
import com.zyntasolutions.zyntapos.domain.model.Order
import com.zyntasolutions.zyntapos.domain.model.OrderItem
import com.zyntasolutions.zyntapos.domain.model.OrderStatus
import com.zyntasolutions.zyntapos.domain.model.OrderType
import com.zyntasolutions.zyntapos.domain.model.PaymentMethod
import com.zyntasolutions.zyntapos.domain.model.RegisterSession
import com.zyntasolutions.zyntapos.domain.model.SyncStatus
import com.zyntasolutions.zyntapos.domain.repository.OrderRepository
import com.zyntasolutions.zyntapos.domain.repository.RegisterRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlin.time.Clock
import kotlinx.datetime.Instant

// ─────────────────────────────────────────────────────────────────────────────
// Fixtures
// ─────────────────────────────────────────────────────────────────────────────

/** Builds a [CartItem] with sensible defaults. */
fun buildCartItem(
    productId: String = "prod-01",
    productName: String = "Test Product",
    unitPrice: Double = 10.0,
    quantity: Double = 1.0,
    discount: Double = 0.0,
    discountType: DiscountType = DiscountType.FIXED,
    taxRate: Double = 0.0,
) = CartItem(
    productId = productId, productName = productName, unitPrice = unitPrice,
    quantity = quantity, discount = discount, discountType = discountType, taxRate = taxRate,
)

/** Builds a test [Order] with sensible defaults. */
fun buildOrder(
    id: String = "order-01",
    orderNumber: String = "ORD-0001",
    status: OrderStatus = OrderStatus.COMPLETED,
    items: List<OrderItem> = emptyList(),
    total: Double = 10.0,
    paymentMethod: PaymentMethod = PaymentMethod.CASH,
    cashierId: String = "user-01",
) = Order(
    id = id, orderNumber = orderNumber, type = OrderType.SALE, status = status,
    items = items, subtotal = total, taxAmount = 0.0, discountAmount = 0.0,
    total = total, paymentMethod = paymentMethod, paymentSplits = emptyList(),
    amountTendered = total, changeAmount = 0.0, customerId = null,
    cashierId = cashierId, storeId = "store-01", registerSessionId = "session-01",
    notes = null, reference = null, createdAt = Clock.System.now(),
    updatedAt = Clock.System.now(), syncStatus = SyncStatus(state = SyncStatus.State.SYNCED),
)

/** Builds an [OrderItem] from a [CartItem]. */
fun CartItem.toOrderItem(orderId: String = "order-01") = OrderItem(
    id = "item-${productId}", orderId = orderId, productId = productId,
    productName = productName, unitPrice = unitPrice, quantity = quantity,
    discount = discount, discountType = discountType, taxRate = taxRate,
    taxAmount = 0.0, lineTotal = unitPrice * quantity,
)

/** Builds a [RegisterSession] with sensible defaults. */
fun buildRegisterSession(
    id: String = "session-01",
    registerId: String = "register-01",
    openedBy: String = "user-01",
    status: RegisterSession.Status = RegisterSession.Status.OPEN,
    openingBalance: Double = 500.0,
    expectedBalance: Double = 500.0,
    actualBalance: Double? = null,
) = RegisterSession(
    id = id, registerId = registerId, openedBy = openedBy, closedBy = null,
    openingBalance = openingBalance, closingBalance = null, expectedBalance = expectedBalance,
    actualBalance = actualBalance, openedAt = Clock.System.now(), closedAt = null, status = status,
)

// ─────────────────────────────────────────────────────────────────────────────
// FakeOrderRepository
// ─────────────────────────────────────────────────────────────────────────────

class FakeOrderRepository : OrderRepository {
    val orders = mutableListOf<Order>()
    private val _orders = MutableStateFlow<List<Order>>(emptyList())
    var nextOrderNumber = 1
    var shouldFailCreate: Boolean = false

    override suspend fun create(order: Order): Result<Order> {
        if (shouldFailCreate) return Result.Error(DatabaseException("Create failed"))
        val saved = order.copy(
            orderNumber = "ORD-${nextOrderNumber.toString().padStart(4, '0')}",
            items = order.items.map { it.copy(orderId = order.id) },
        )
        nextOrderNumber++
        orders.add(saved)
        _orders.value = orders.toList()
        return Result.Success(saved)
    }

    override suspend fun getById(id: String): Result<Order> {
        val order = orders.firstOrNull { it.id == id }
            ?: return Result.Error(DatabaseException("Order '$id' not found"))
        return Result.Success(order)
    }

    override fun getAll(filters: Map<String, String>): Flow<List<Order>> = _orders

    override suspend fun update(order: Order): Result<Unit> {
        val index = orders.indexOfFirst { it.id == order.id }
        if (index == -1) return Result.Error(DatabaseException("Order not found"))
        orders[index] = order
        _orders.value = orders.toList()
        return Result.Success(Unit)
    }

    override suspend fun void(id: String, reason: String): Result<Unit> {
        val index = orders.indexOfFirst { it.id == id }
        if (index == -1) return Result.Error(DatabaseException("Order not found"))
        orders[index] = orders[index].copy(status = OrderStatus.VOIDED)
        _orders.value = orders.toList()
        return Result.Success(Unit)
    }

    override fun getByDateRange(from: Instant, to: Instant): Flow<List<Order>> =
        _orders.map { list -> list.filter { it.createdAt >= from && it.createdAt <= to } }

    override suspend fun holdOrder(items: List<CartItem>): Result<String> {
        val holdId = "hold-${orders.size + 1}"
        val holdOrder = Order(
            id = holdId, orderNumber = "HOLD-${orders.size + 1}",
            type = OrderType.HOLD, status = OrderStatus.HELD,
            items = items.map { cart ->
                OrderItem(
                    id = "item-${cart.productId}", orderId = holdId,
                    productId = cart.productId, productName = cart.productName,
                    unitPrice = cart.unitPrice, quantity = cart.quantity,
                    discount = cart.discount, discountType = cart.discountType,
                    taxRate = cart.taxRate, taxAmount = 0.0,
                    lineTotal = cart.unitPrice * cart.quantity,
                )
            },
            subtotal = items.sumOf { it.unitPrice * it.quantity },
            taxAmount = 0.0, discountAmount = 0.0,
            total = items.sumOf { it.unitPrice * it.quantity },
            paymentMethod = PaymentMethod.CASH, paymentSplits = emptyList(),
            amountTendered = 0.0, changeAmount = 0.0, customerId = null,
            cashierId = "system", storeId = "store-01", registerSessionId = "session-01",
            notes = null, reference = null, createdAt = Clock.System.now(),
            updatedAt = Clock.System.now(), syncStatus = SyncStatus(state = SyncStatus.State.PENDING),
        )
        orders.add(holdOrder)
        _orders.value = orders.toList()
        return Result.Success(holdId)
    }

    override suspend fun retrieveHeld(holdId: String): Result<Order> {
        val order = orders.firstOrNull { it.id == holdId && it.status == OrderStatus.HELD }
            ?: return Result.Error(DatabaseException("Hold '$holdId' not found"))
        return Result.Success(order)
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// FakeRegisterRepository
// ─────────────────────────────────────────────────────────────────────────────

class FakeRegisterRepository : RegisterRepository {
    val sessions = mutableListOf<RegisterSession>()
    val movements = mutableListOf<CashMovement>()
    var activeSession: RegisterSession? = null
    var shouldFailOpen: Boolean = false
    var activeSessionAlreadyExists: Boolean = false

    private val _active = MutableStateFlow<RegisterSession?>(null)

    override fun getRegisters(): Flow<List<CashRegister>> = flowOf(emptyList())
    override fun getActive(): Flow<RegisterSession?> = _active

    override suspend fun openSession(
        registerId: String,
        openingBalance: Double,
        userId: String,
    ): Result<RegisterSession> {
        if (shouldFailOpen) return Result.Error(DatabaseException("DB error"))
        if (activeSessionAlreadyExists) {
            return Result.Error(
                ValidationException(
                    "An active session already exists for register '$registerId'.",
                    field = "registerId", rule = "SESSION_ALREADY_OPEN",
                ),
            )
        }
        val session = buildRegisterSession(
            registerId = registerId, openedBy = userId,
            openingBalance = openingBalance, expectedBalance = openingBalance,
            status = RegisterSession.Status.OPEN,
        )
        sessions.add(session)
        _active.value = session
        activeSession = session
        return Result.Success(session)
    }

    override suspend fun closeSession(
        sessionId: String,
        actualBalance: Double,
        userId: String,
    ): Result<RegisterSession> {
        val session = sessions.firstOrNull { it.id == sessionId }
            ?: return Result.Error(DatabaseException("Session not found"))
        if (session.status == RegisterSession.Status.CLOSED) {
            return Result.Error(
                ValidationException(
                    "Session already closed",
                    field = "sessionId", rule = "SESSION_ALREADY_CLOSED",
                ),
            )
        }
        val cashIn = movements
            .filter { it.sessionId == sessionId && it.type == CashMovement.Type.IN }
            .sumOf { it.amount }
        val cashOut = movements
            .filter { it.sessionId == sessionId && it.type == CashMovement.Type.OUT }
            .sumOf { it.amount }
        val expectedBalance = session.openingBalance + cashIn - cashOut
        val closed = session.copy(
            closedBy = userId, closingBalance = actualBalance,
            actualBalance = actualBalance, expectedBalance = expectedBalance,
            closedAt = Clock.System.now(), status = RegisterSession.Status.CLOSED,
        )
        val index = sessions.indexOfFirst { it.id == sessionId }
        sessions[index] = closed
        _active.value = null
        activeSession = null
        return Result.Success(closed)
    }

    override suspend fun getSession(sessionId: String): Result<RegisterSession> {
        val session = sessions.firstOrNull { it.id == sessionId }
            ?: return Result.Error(DatabaseException("Session '$sessionId' not found"))
        return Result.Success(session)
    }

    override suspend fun addCashMovement(movement: CashMovement): Result<Unit> {
        movements.add(movement)
        return Result.Success(Unit)
    }

    override fun getMovements(sessionId: String): Flow<List<CashMovement>> =
        MutableStateFlow(movements.filter { it.sessionId == sessionId })
}
