// ─────────────────────────────────────────────────────────────────────────────
// FakeOrderRepository
// ─────────────────────────────────────────────────────────────────────────────

package com.zyntasolutions.zyntapos.domain.usecase.fakes

import com.zyntasolutions.zyntapos.core.result.DatabaseException
import com.zyntasolutions.zyntapos.core.result.ValidationException
import com.zyntasolutions.zyntapos.core.result.Result
import com.zyntasolutions.zyntapos.domain.model.CartItem
import com.zyntasolutions.zyntapos.domain.model.DiscountType
import com.zyntasolutions.zyntapos.domain.model.Order
import com.zyntasolutions.zyntapos.domain.model.OrderItem
import com.zyntasolutions.zyntapos.domain.model.OrderStatus
import com.zyntasolutions.zyntapos.domain.model.OrderType
import com.zyntasolutions.zyntapos.domain.model.PaymentMethod
import com.zyntasolutions.zyntapos.domain.model.SyncStatus
import com.zyntasolutions.zyntapos.domain.repository.OrderRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant

class FakeOrderRepository : OrderRepository {
    val orders = mutableListOf<Order>()
    private val _orders = MutableStateFlow<List<Order>>(emptyList())
    var nextOrderNumber = 1
    var shouldFailCreate: Boolean = false

    override suspend fun create(order: Order): Result<Order> {
        if (shouldFailCreate) return Result.Error(DatabaseException("Create failed"))
        val saved = order.copy(
            orderNumber = "ORD-${nextOrderNumber.toString().padStart(4, '0')}",
            items = order.items.map { it.copy(orderId = order.id) }
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

    override fun getAll(filters: Map<String, String>?): Flow<List<Order>> = _orders

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
        _orders.map { list ->
            list.filter { order ->
                order.createdAt >= from && order.createdAt <= to
            }
        }

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
                    lineTotal = cart.unitPrice * cart.quantity
                )
            },
            subtotal = items.sumOf { it.unitPrice * it.quantity },
            taxAmount = 0.0, discountAmount = 0.0,
            total = items.sumOf { it.unitPrice * it.quantity },
            paymentMethod = PaymentMethod.CASH, paymentSplits = emptyList(),
            amountTendered = 0.0, changeAmount = 0.0, customerId = null,
            cashierId = "system", storeId = "store-01", registerSessionId = "session-01",
            notes = null, reference = null, createdAt = Clock.System.now(),
            updatedAt = Clock.System.now(), syncStatus = SyncStatus(state = SyncStatus.State.PENDING)
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

    override fun getActive(): Flow<RegisterSession?> = _active

    override suspend fun openSession(
        registerId: String,
        openingBalance: Double,
        userId: String
    ): Result<RegisterSession> {
        if (shouldFailOpen) return Result.Error(DatabaseException("DB error"))
        if (activeSessionAlreadyExists) {
            return Result.Error(
                ValidationException(
                    "An active session already exists for register '$registerId'.",
                    field = "registerId", rule = "SESSION_ALREADY_OPEN"
                )
            )
        }
        val session = buildRegisterSession(
            registerId = registerId, openedBy = userId,
            openingBalance = openingBalance, expectedBalance = openingBalance,
            status = RegisterSession.Status.OPEN
        )
        sessions.add(session)
        _active.value = session
        activeSession = session
        return Result.Success(session)
    }

    override suspend fun closeSession(
        sessionId: String,
        actualBalance: Double,
        userId: String
    ): Result<RegisterSession> {
        val session = sessions.firstOrNull { it.id == sessionId }
            ?: return Result.Error(DatabaseException("Session not found"))
        if (session.status == RegisterSession.Status.CLOSED) {
            return Result.Error(
                ValidationException("Session already closed", field = "sessionId", rule = "SESSION_ALREADY_CLOSED")
            )
        }
        val cashIn = movements.filter { it.sessionId == sessionId && it.type == CashMovement.Type.IN }.sumOf { it.amount }
        val cashOut = movements.filter { it.sessionId == sessionId && it.type == CashMovement.Type.OUT }.sumOf { it.amount }
        val expectedBalance = session.openingBalance + cashIn - cashOut
        val closed = session.copy(
            closedBy = userId, closingBalance = actualBalance,
            actualBalance = actualBalance, expectedBalance = expectedBalance,
            closedAt = Clock.System.now(), status = RegisterSession.Status.CLOSED
        )
        val index = sessions.indexOfFirst { it.id == sessionId }
        sessions[index] = closed
        _active.value = null
        activeSession = null
        return Result.Success(closed)
    }

    override suspend fun addCashMovement(movement: CashMovement): Result<Unit> {
        movements.add(movement)
        return Result.Success(Unit)
    }

    override fun getMovements(sessionId: String): Flow<List<CashMovement>> =
        MutableStateFlow(movements.filter { it.sessionId == sessionId })
}

// ─────────────────────────────────────────────────────────────────────────────
// FakeStockRepository
// ─────────────────────────────────────────────────────────────────────────────

class FakeStockRepository : StockRepository {
    val adjustments = mutableListOf<StockAdjustment>()
    var shouldFailAdjust: Boolean = false

    override suspend fun adjustStock(adjustment: StockAdjustment): Result<Unit> {
        if (shouldFailAdjust) return Result.Error(DatabaseException("Stock adjust failed"))
        adjustments.add(adjustment)
        return Result.Success(Unit)
    }

    override fun getMovements(productId: String): Flow<List<StockAdjustment>> =
        MutableStateFlow(adjustments.filter { it.productId == productId })

    override fun getAlerts(threshold: Double): Flow<List<com.zyntasolutions.zyntapos.domain.model.Product>> =
        MutableStateFlow(emptyList())
}

// ─────────────────────────────────────────────────────────────────────────────
// FakeSettingsRepository
// ─────────────────────────────────────────────────────────────────────────────

class FakeSettingsRepository : SettingsRepository {
    private val store = mutableMapOf<String, String>()

    fun put(key: String, value: String) { store[key] = value }

    override fun get(key: String): String? = store[key]

    override suspend fun set(key: String, value: String): Result<Unit> {
        store[key] = value
        return Result.Success(Unit)
    }

    override fun getAll(): Map<String, String> = store.toMap()

    override fun observe(key: String): Flow<String?> =
        MutableStateFlow(store[key])
}

// ─────────────────────────────────────────────────────────────────────────────
// FakeCategoryRepository
// ─────────────────────────────────────────────────────────────────────────────

class FakeCategoryRepository : CategoryRepository {
    val categories = mutableListOf<Category>()
    private val _cats = MutableStateFlow<List<Category>>(emptyList())

    override fun getAll(): Flow<List<Category>> = _cats

    override suspend fun getById(id: String): Result<Category> {
        return categories.firstOrNull { it.id == id }
            ?.let { Result.Success(it) }
            ?: Result.Error(DatabaseException("Category not found"))
    }

    override suspend fun insert(category: Category): Result<Unit> {
        categories.add(category)
        _cats.value = categories.toList()
        return Result.Success(Unit)
    }

    override suspend fun update(category: Category): Result<Unit> {
        val index = categories.indexOfFirst { it.id == category.id }
        if (index == -1) return Result.Error(DatabaseException("Not found"))
        categories[index] = category
        _cats.value = categories.toList()
        return Result.Success(Unit)
    }

    override suspend fun delete(id: String): Result<Unit> {
        categories.removeAll { it.id == id }
        _cats.value = categories.toList()
        return Result.Success(Unit)
    }

    override fun getTree(): Flow<List<Category>> = _cats
}

// ─────────────────────────────────────────────────────────────────────────────
// FakeCustomerRepository, FakeSupplierRepository, FakeSyncRepository
// ─────────────────────────────────────────────────────────────────────────────

class FakeCustomerRepository : CustomerRepository {
    val customers = mutableListOf<Customer>()
    private val _flow = MutableStateFlow<List<Customer>>(emptyList())
    override fun getAll(): Flow<List<Customer>> = _flow
    override suspend fun getById(id: String): Result<Customer> =
        customers.firstOrNull { it.id == id }?.let { Result.Success(it) }
            ?: Result.Error(DatabaseException("Not found"))
    override fun search(query: String): Flow<List<Customer>> =
        _flow.map { list -> list.filter { it.name.contains(query, true) || it.phone.contains(query) } }
    override suspend fun insert(customer: Customer): Result<Unit> {
        customers.add(customer)
        _flow.value = customers.toList()
        return Result.Success(Unit)
    }
    override suspend fun update(customer: Customer): Result<Unit> {
        val i = customers.indexOfFirst { it.id == customer.id }
        if (i == -1) return Result.Error(DatabaseException("Not found"))
        customers[i] = customer
        _flow.value = customers.toList()
        return Result.Success(Unit)
    }
    override suspend fun delete(id: String): Result<Unit> {
        customers.removeAll { it.id == id }
        _flow.value = customers.toList()
        return Result.Success(Unit)
    }
}

class FakeSupplierRepository : SupplierRepository {
    val suppliers = mutableListOf<Supplier>()
    private val _flow = MutableStateFlow<List<Supplier>>(emptyList())
    override fun getAll(): Flow<List<Supplier>> = _flow
    override suspend fun getById(id: String): Result<Supplier> =
        suppliers.firstOrNull { it.id == id }?.let { Result.Success(it) }
            ?: Result.Error(DatabaseException("Not found"))
    override suspend fun insert(supplier: Supplier): Result<Unit> {
        suppliers.add(supplier)
        _flow.value = suppliers.toList()
        return Result.Success(Unit)
    }
    override suspend fun update(supplier: Supplier): Result<Unit> {
        val i = suppliers.indexOfFirst { it.id == supplier.id }
        if (i == -1) return Result.Error(DatabaseException("Not found"))
        suppliers[i] = supplier
        _flow.value = suppliers.toList()
        return Result.Success(Unit)
    }
    override suspend fun delete(id: String): Result<Unit> {
        suppliers.removeAll { it.id == id }
        _flow.value = suppliers.toList()
        return Result.Success(Unit)
    }
}

class FakeSyncRepository : SyncRepository {
    val operations = mutableListOf<SyncOperation>()
    override suspend fun getPendingOperations(): List<SyncOperation> = operations.toList()
    override suspend fun markSynced(ids: List<String>): Result<Unit> {
        operations.removeAll { it.id in ids }
        return Result.Success(Unit)
    }
    override suspend fun pushToServer(ops: List<SyncOperation>): Result<Unit> = Result.Success(Unit)
    override suspend fun pullFromServer(lastSyncTs: Long): Result<List<SyncOperation>> = Result.Success(emptyList())
}
