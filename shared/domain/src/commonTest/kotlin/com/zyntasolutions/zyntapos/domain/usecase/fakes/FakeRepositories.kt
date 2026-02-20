package com.zyntasolutions.zyntapos.domain.usecase.fakes

import com.zyntasolutions.zyntapos.core.result.AuthException
import com.zyntasolutions.zyntapos.core.result.AuthFailureReason
import com.zyntasolutions.zyntapos.core.result.DatabaseException
import com.zyntasolutions.zyntapos.core.result.Result
import com.zyntasolutions.zyntapos.core.result.ValidationException
import com.zyntasolutions.zyntapos.domain.model.CartItem
import com.zyntasolutions.zyntapos.domain.model.CashMovement
import com.zyntasolutions.zyntapos.domain.model.Category
import com.zyntasolutions.zyntapos.domain.model.Customer
import com.zyntasolutions.zyntapos.domain.model.DiscountType
import com.zyntasolutions.zyntapos.domain.model.Order
import com.zyntasolutions.zyntapos.domain.model.OrderItem
import com.zyntasolutions.zyntapos.domain.model.OrderStatus
import com.zyntasolutions.zyntapos.domain.model.OrderTotals
import com.zyntasolutions.zyntapos.domain.model.OrderType
import com.zyntasolutions.zyntapos.domain.model.PaymentMethod
import com.zyntasolutions.zyntapos.domain.model.Product
import com.zyntasolutions.zyntapos.domain.model.RegisterSession
import com.zyntasolutions.zyntapos.domain.model.Role
import com.zyntasolutions.zyntapos.domain.model.StockAdjustment
import com.zyntasolutions.zyntapos.domain.model.Supplier
import com.zyntasolutions.zyntapos.domain.model.SyncOperation
import com.zyntasolutions.zyntapos.domain.model.SyncStatus
import com.zyntasolutions.zyntapos.domain.model.User
import com.zyntasolutions.zyntapos.domain.repository.AuthRepository
import com.zyntasolutions.zyntapos.domain.repository.CategoryRepository
import com.zyntasolutions.zyntapos.domain.repository.CustomerRepository
import com.zyntasolutions.zyntapos.domain.repository.OrderRepository
import com.zyntasolutions.zyntapos.domain.repository.ProductRepository
import com.zyntasolutions.zyntapos.domain.repository.RegisterRepository
import com.zyntasolutions.zyntapos.domain.repository.SettingsRepository
import com.zyntasolutions.zyntapos.domain.repository.StockRepository
import com.zyntasolutions.zyntapos.domain.repository.SupplierRepository
import com.zyntasolutions.zyntapos.domain.repository.SyncRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant

// ─────────────────────────────────────────────────────────────────────────────
// Test Fixtures
// ─────────────────────────────────────────────────────────────────────────────

/** Builds a test [User] with sensible defaults. */
fun buildUser(
    id: String = "user-01",
    name: String = "Test User",
    email: String = "test@zentapos.com",
    role: Role = Role.CASHIER,
    isActive: Boolean = true,
) = User(id = id, name = name, email = email, role = role, storeId = "store-01",
    isActive = isActive, pinHash = "1234", createdAt = Clock.System.now(),
    updatedAt = Clock.System.now())

/** Builds a test [Product] with sensible defaults. */
fun buildProduct(
    id: String = "prod-01",
    name: String = "Test Product",
    barcode: String = "1234567890",
    sku: String = "SKU-001",
    price: Double = 10.0,
    costPrice: Double = 5.0,
    stockQty: Double = 100.0,
    minStockQty: Double = 5.0,
    isActive: Boolean = true,
    categoryId: String = "cat-01",
) = Product(id = id, name = name, barcode = barcode, sku = sku, categoryId = categoryId,
    unitId = "unit-01", price = price, costPrice = costPrice, taxGroupId = "tax-01",
    stockQty = stockQty, minStockQty = minStockQty, imageUrl = null,
    description = "Test product description", isActive = isActive,
    createdAt = Clock.System.now(), updatedAt = Clock.System.now(),
    syncStatus = SyncStatus(state = SyncStatus.State.SYNCED))

/** Builds a [CartItem] with sensible defaults. */
fun buildCartItem(
    productId: String = "prod-01",
    productName: String = "Test Product",
    unitPrice: Double = 10.0,
    quantity: Double = 1.0,
    discount: Double = 0.0,
    discountType: DiscountType = DiscountType.FIXED,
    taxRate: Double = 0.0,
) = CartItem(productId = productId, productName = productName, unitPrice = unitPrice,
    quantity = quantity, discount = discount, discountType = discountType, taxRate = taxRate)

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
    updatedAt = Clock.System.now(), syncStatus = SyncStatus(state = SyncStatus.State.SYNCED)
)

/** Builds an [OrderItem] from a [CartItem]. */
fun CartItem.toOrderItem(orderId: String = "order-01") = OrderItem(
    id = "item-${productId}", orderId = orderId, productId = productId,
    productName = productName, unitPrice = unitPrice, quantity = quantity,
    discount = discount, discountType = discountType, taxRate = taxRate,
    taxAmount = 0.0, lineTotal = unitPrice * quantity
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
    actualBalance = actualBalance, openedAt = Clock.System.now(), closedAt = null, status = status
)

// ─────────────────────────────────────────────────────────────────────────────
// FakeAuthRepository
// ─────────────────────────────────────────────────────────────────────────────

class FakeAuthRepository : AuthRepository {
    var userToReturn: User? = buildUser()
    var shouldFailLogin: Boolean = false
    var loginFailureReason: AuthFailureReason = AuthFailureReason.INVALID_CREDENTIALS
    var logoutCalled: Boolean = false
    var pinToAccept: String = "1234"

    private val _session = MutableStateFlow<User?>(null)

    override suspend fun login(email: String, password: String): Result<User> {
        if (shouldFailLogin) {
            return Result.Error(AuthException("Login failed", reason = loginFailureReason))
        }
        val user = userToReturn ?: return Result.Error(AuthException("No user"))
        if (!user.isActive) {
            return Result.Error(AuthException("Account disabled", reason = AuthFailureReason.ACCOUNT_DISABLED))
        }
        _session.value = user
        return Result.Success(user)
    }

    override suspend fun logout(): Result<Unit> {
        logoutCalled = true
        _session.value = null
        return Result.Success(Unit)
    }

    override fun getSession(): Flow<User?> = _session

    override suspend fun refreshToken(): Result<Unit> = Result.Success(Unit)

    var shouldFailUpdatePin: Boolean = false

    override suspend fun updatePin(userId: String, pin: String): Result<Unit> {
        if (shouldFailUpdatePin || (pinToAccept.isNotEmpty() && pin != pinToAccept)) {
            return Result.Error(AuthException("Invalid PIN", reason = AuthFailureReason.INVALID_CREDENTIALS))
        }
        pinToAccept = pin
        return Result.Success(Unit)
    }

    fun setActiveUser(user: User?) { _session.value = user }
}

// ─────────────────────────────────────────────────────────────────────────────
// FakeProductRepository
// ─────────────────────────────────────────────────────────────────────────────

class FakeProductRepository : ProductRepository {
    val products = mutableListOf<Product>()
    var shouldFailGetById: Boolean = false

    private val _products = MutableStateFlow<List<Product>>(emptyList())

    fun addProduct(product: Product) {
        products.add(product)
        _products.value = products.toList()
    }

    override fun getAll(): Flow<List<Product>> = _products

    override suspend fun getById(id: String): Result<Product> {
        if (shouldFailGetById) return Result.Error(DatabaseException("DB error"))
        val product = products.firstOrNull { it.id == id }
            ?: return Result.Error(DatabaseException("Product '$id' not found"))
        return Result.Success(product)
    }

    override fun search(query: String, categoryId: String?): Flow<List<Product>> =
        _products.map { list ->
            list.filter { p ->
                p.name.contains(query, ignoreCase = true) ||
                    p.barcode.contains(query) ||
                    p.sku.contains(query, ignoreCase = true)
            }.let { filtered ->
                if (categoryId != null) filtered.filter { it.categoryId == categoryId } else filtered
            }
        }

    override suspend fun getByBarcode(barcode: String): Result<Product> {
        val product = products.firstOrNull { it.barcode == barcode }
            ?: return Result.Error(DatabaseException("Barcode '$barcode' not found"))
        return Result.Success(product)
    }

    override suspend fun insert(product: Product): Result<Unit> {
        val barcodeConflict = products.any { it.barcode == product.barcode && it.id != product.id }
        if (barcodeConflict) return Result.Error(ValidationException("Barcode duplicate", field = "barcode", rule = "BARCODE_DUPLICATE"))
        val skuConflict = products.any { it.sku == product.sku && it.id != product.id }
        if (skuConflict) return Result.Error(ValidationException("SKU duplicate", field = "sku", rule = "SKU_DUPLICATE"))
        products.add(product)
        _products.value = products.toList()
        return Result.Success(Unit)
    }

    override suspend fun update(product: Product): Result<Unit> {
        val index = products.indexOfFirst { it.id == product.id }
        if (index == -1) return Result.Error(DatabaseException("Product not found"))
        products[index] = product
        _products.value = products.toList()
        return Result.Success(Unit)
    }

    override suspend fun delete(id: String): Result<Unit> {
        products.removeAll { it.id == id }
        _products.value = products.toList()
        return Result.Success(Unit)
    }

    override suspend fun getCount(): Int = products.size
}
