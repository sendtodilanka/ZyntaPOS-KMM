package com.zyntasolutions.zyntapos.feature.dashboard

import app.cash.turbine.test
import com.zyntasolutions.zyntapos.core.result.DatabaseException
import com.zyntasolutions.zyntapos.core.result.Result
import com.zyntasolutions.zyntapos.domain.model.CartItem
import com.zyntasolutions.zyntapos.domain.model.CashMovement
import com.zyntasolutions.zyntapos.domain.model.CashRegister
import com.zyntasolutions.zyntapos.domain.model.Order
import com.zyntasolutions.zyntapos.domain.model.OrderStatus
import com.zyntasolutions.zyntapos.domain.model.OrderType
import com.zyntasolutions.zyntapos.domain.model.PaymentMethod
import com.zyntasolutions.zyntapos.domain.model.Product
import com.zyntasolutions.zyntapos.domain.model.RegisterSession
import com.zyntasolutions.zyntapos.domain.model.Role
import com.zyntasolutions.zyntapos.domain.model.SyncStatus
import com.zyntasolutions.zyntapos.domain.model.User
import com.zyntasolutions.zyntapos.domain.repository.AuthRepository
import com.zyntasolutions.zyntapos.domain.repository.OrderRepository
import com.zyntasolutions.zyntapos.domain.repository.ProductRepository
import com.zyntasolutions.zyntapos.domain.repository.RegisterRepository
import com.zyntasolutions.zyntapos.feature.dashboard.mvi.DashboardEffect
import com.zyntasolutions.zyntapos.feature.dashboard.mvi.DashboardIntent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.time.Clock
import kotlinx.datetime.Instant
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Unit tests for [DashboardViewModel].
 *
 * Uses hand-rolled fakes to avoid KSP/Mockative incompatibilities with Kotlin 2.3+.
 * All tests run under [StandardTestDispatcher] for deterministic coroutine execution.
 *
 * Test scenarios:
 * - Loading state transitions
 * - KPI aggregation (today's sales, order count)
 * - Low-stock detection and limit (top-5)
 * - Active register detection
 * - Recent-order projection (last 10, sorted by date)
 * - Weekly sales bucketing
 * - Empty data states (no orders, no products, no register)
 * - Logout delegates to AuthRepository
 * - Error handling emits [DashboardEffect.ShowError]
 */
@OptIn(ExperimentalCoroutinesApi::class)
class DashboardViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    // ── Fake collaborators ─────────────────────────────────────────────────────

    private val sessionFlow = MutableStateFlow<User?>(null)
    private var ordersByDateRange: List<Order> = emptyList()
    private var allOrders: List<Order> = emptyList()
    private var allProducts: List<Product> = emptyList()
    private var activeSession: RegisterSession? = null
    private var logoutCalled = false
    private var orderRepositoryThrows = false

    private val fakeOrderRepository = object : OrderRepository {
        override suspend fun create(order: Order): Result<Order> = Result.Success(order)
        override suspend fun getById(id: String): Result<Order> = Result.Error(DatabaseException("Not found"))
        override fun getAll(filters: Map<String, String>): Flow<List<Order>> = flowOf(allOrders)
        override suspend fun update(order: Order): Result<Unit> = Result.Success(Unit)
        override suspend fun void(id: String, reason: String): Result<Unit> = Result.Success(Unit)
        override fun getByDateRange(from: Instant, to: Instant): Flow<List<Order>> {
            if (orderRepositoryThrows) throw RuntimeException("Simulated error")
            return flowOf(ordersByDateRange)
        }
        override suspend fun holdOrder(cart: List<CartItem>): Result<String> = Result.Success("hold-id")
        override suspend fun retrieveHeld(holdId: String): Result<Order> = Result.Error(DatabaseException("Not found"))
    }

    private val fakeProductRepository = object : ProductRepository {
        override fun getAll(): Flow<List<Product>> = flowOf(allProducts)
        override suspend fun getById(id: String): Result<Product> = Result.Error(DatabaseException("Not found"))
        override fun search(query: String, categoryId: String?): Flow<List<Product>> = flowOf(emptyList())
        override suspend fun getByBarcode(barcode: String): Result<Product> = Result.Error(DatabaseException("Not found"))
        override suspend fun insert(product: Product): Result<Unit> = Result.Success(Unit)
        override suspend fun update(product: Product): Result<Unit> = Result.Success(Unit)
        override suspend fun delete(id: String): Result<Unit> = Result.Success(Unit)
        override suspend fun getCount(): Int = allProducts.size
    }

    private val fakeRegisterRepository = object : RegisterRepository {
        override fun getActive(): Flow<RegisterSession?> = flowOf(activeSession)
        override fun getRegisters(): Flow<List<CashRegister>> = flowOf(emptyList())
        override suspend fun openSession(registerId: String, openingBalance: Double, userId: String): Result<RegisterSession> = Result.Error(DatabaseException("Error"))
        override suspend fun closeSession(sessionId: String, actualBalance: Double, userId: String): Result<RegisterSession> = Result.Error(DatabaseException("Error"))
        override suspend fun addCashMovement(movement: CashMovement): Result<Unit> = Result.Success(Unit)
        override fun getMovements(sessionId: String): Flow<List<CashMovement>> = flowOf(emptyList())
    }

    private val fakeAuthRepository = object : AuthRepository {
        override suspend fun login(email: String, password: String): Result<User> = Result.Error(DatabaseException("Error"))
        override suspend fun logout() { logoutCalled = true; sessionFlow.value = null }
        override fun getSession(): Flow<User?> = sessionFlow
        override suspend fun refreshToken(): Result<Unit> = Result.Success(Unit)
        override suspend fun updatePin(userId: String, pin: String): Result<Unit> = Result.Success(Unit)
        override suspend fun validatePin(userId: String, pin: String): Result<Boolean> = Result.Success(true)
    }

    private lateinit var viewModel: DashboardViewModel

    private val fakeUser = User(
        id = "user-001",
        name = "Admin User",
        email = "admin@test.com",
        role = Role.ADMIN,
        storeId = "store-001",
        isActive = true,
        pinHash = null,
        createdAt = Clock.System.now(),
        updatedAt = Clock.System.now(),
    )

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        sessionFlow.value = fakeUser
        ordersByDateRange = emptyList()
        allOrders = emptyList()
        allProducts = emptyList()
        activeSession = null
        logoutCalled = false
        orderRepositoryThrows = false
        viewModel = DashboardViewModel(
            orderRepository = fakeOrderRepository,
            productRepository = fakeProductRepository,
            registerRepository = fakeRegisterRepository,
            authRepository = fakeAuthRepository,
        )
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // ── Loading state ──────────────────────────────────────────────────────────

    @Test
    fun `initial state has isLoading = true`() = runTest {
        assertTrue(viewModel.state.value.isLoading)
    }

    @Test
    fun `LoadDashboard clears isLoading when data loads successfully`() = runTest {
        viewModel.dispatch(DashboardIntent.LoadDashboard)
        advanceUntilIdle()
        assertFalse(viewModel.state.value.isLoading)
    }

    // ── Current user ───────────────────────────────────────────────────────────

    @Test
    fun `LoadDashboard populates currentUser from session`() = runTest {
        viewModel.dispatch(DashboardIntent.LoadDashboard)
        advanceUntilIdle()
        assertEquals(fakeUser, viewModel.state.value.currentUser)
    }

    @Test
    fun `LoadDashboard sets currentUser to null when no session`() = runTest {
        sessionFlow.value = null
        viewModel.dispatch(DashboardIntent.LoadDashboard)
        advanceUntilIdle()
        assertNull(viewModel.state.value.currentUser)
    }

    // ── KPI: Today's sales + order count ─────────────────────────────────────

    @Test
    fun `LoadDashboard aggregates todays sales from completed orders only`() = runTest {
        val now = Clock.System.now()
        ordersByDateRange = listOf(
            makeOrder(id = "o1", total = 100.0, status = OrderStatus.COMPLETED, createdAt = now),
            makeOrder(id = "o2", total = 50.0, status = OrderStatus.COMPLETED, createdAt = now),
            makeOrder(id = "o3", total = 200.0, status = OrderStatus.VOIDED, createdAt = now),
        )
        viewModel.dispatch(DashboardIntent.LoadDashboard)
        advanceUntilIdle()

        assertEquals(150.0, viewModel.state.value.todaysSales)
        assertEquals(2L, viewModel.state.value.totalOrders)
    }

    @Test
    fun `LoadDashboard returns zero sales when no completed orders`() = runTest {
        ordersByDateRange = listOf(
            makeOrder(id = "o1", total = 500.0, status = OrderStatus.VOIDED),
        )
        viewModel.dispatch(DashboardIntent.LoadDashboard)
        advanceUntilIdle()

        assertEquals(0.0, viewModel.state.value.todaysSales)
        assertEquals(0L, viewModel.state.value.totalOrders)
    }

    @Test
    fun `LoadDashboard returns zero sales when no orders at all`() = runTest {
        ordersByDateRange = emptyList()
        viewModel.dispatch(DashboardIntent.LoadDashboard)
        advanceUntilIdle()

        assertEquals(0.0, viewModel.state.value.todaysSales)
        assertEquals(0L, viewModel.state.value.totalOrders)
    }

    // ── KPI: Low stock ────────────────────────────────────────────────────────

    @Test
    fun `LoadDashboard counts products at or below minimum stock qty`() = runTest {
        allProducts = listOf(
            makeProduct(id = "p1", stockQty = 5.0, minStockQty = 10.0),  // low
            makeProduct(id = "p2", stockQty = 10.0, minStockQty = 10.0), // low (at min)
            makeProduct(id = "p3", stockQty = 20.0, minStockQty = 5.0),  // ok
        )
        viewModel.dispatch(DashboardIntent.LoadDashboard)
        advanceUntilIdle()

        assertEquals(2L, viewModel.state.value.lowStockCount)
    }

    @Test
    fun `LoadDashboard lowStockNames includes at most 5 products`() = runTest {
        allProducts = (1..8).map { i ->
            makeProduct(id = "p$i", name = "Product $i", stockQty = 0.0, minStockQty = 5.0)
        }
        viewModel.dispatch(DashboardIntent.LoadDashboard)
        advanceUntilIdle()

        assertEquals(8L, viewModel.state.value.lowStockCount)
        assertEquals(5, viewModel.state.value.lowStockNames.size)
    }

    @Test
    fun `LoadDashboard lowStockCount is zero when all products are well stocked`() = runTest {
        allProducts = listOf(
            makeProduct(id = "p1", stockQty = 100.0, minStockQty = 5.0),
            makeProduct(id = "p2", stockQty = 50.0, minStockQty = 0.0),
        )
        viewModel.dispatch(DashboardIntent.LoadDashboard)
        advanceUntilIdle()

        assertEquals(0L, viewModel.state.value.lowStockCount)
        assertTrue(viewModel.state.value.lowStockNames.isEmpty())
    }

    // ── KPI: Active registers ─────────────────────────────────────────────────

    @Test
    fun `LoadDashboard sets activeRegisters to 1 when a session is open`() = runTest {
        activeSession = makeRegisterSession()
        viewModel.dispatch(DashboardIntent.LoadDashboard)
        advanceUntilIdle()

        assertEquals(1L, viewModel.state.value.activeRegisters)
    }

    @Test
    fun `LoadDashboard sets activeRegisters to 0 when no session is open`() = runTest {
        activeSession = null
        viewModel.dispatch(DashboardIntent.LoadDashboard)
        advanceUntilIdle()

        assertEquals(0L, viewModel.state.value.activeRegisters)
    }

    // ── Recent orders ─────────────────────────────────────────────────────────

    @Test
    fun `LoadDashboard returns at most 10 recent completed orders`() = runTest {
        val now = Clock.System.now()
        allOrders = (1..15).map { i ->
            makeOrder(id = "o$i", status = OrderStatus.COMPLETED,
                createdAt = Instant.fromEpochMilliseconds(now.toEpochMilliseconds() - i * 1000L))
        }
        viewModel.dispatch(DashboardIntent.LoadDashboard)
        advanceUntilIdle()

        assertEquals(10, viewModel.state.value.recentOrders.size)
    }

    @Test
    fun `LoadDashboard sorts recent orders newest first`() = runTest {
        val now = Clock.System.now()
        allOrders = listOf(
            makeOrder(id = "old", orderNumber = "ORD-002", status = OrderStatus.COMPLETED,
                createdAt = Instant.fromEpochMilliseconds(now.toEpochMilliseconds() - 5000L)),
            makeOrder(id = "new", orderNumber = "ORD-001", status = OrderStatus.COMPLETED,
                createdAt = now),
        )
        viewModel.dispatch(DashboardIntent.LoadDashboard)
        advanceUntilIdle()

        assertEquals("ORD-001", viewModel.state.value.recentOrders.first().orderNumber)
    }

    @Test
    fun `LoadDashboard excludes voided orders from recent activity`() = runTest {
        allOrders = listOf(
            makeOrder(id = "o1", status = OrderStatus.COMPLETED),
            makeOrder(id = "o2", status = OrderStatus.VOIDED),
        )
        viewModel.dispatch(DashboardIntent.LoadDashboard)
        advanceUntilIdle()

        assertEquals(1, viewModel.state.value.recentOrders.size)
    }

    @Test
    fun `LoadDashboard returns empty recentOrders when no orders exist`() = runTest {
        allOrders = emptyList()
        viewModel.dispatch(DashboardIntent.LoadDashboard)
        advanceUntilIdle()

        assertTrue(viewModel.state.value.recentOrders.isEmpty())
    }

    // ── Weekly sales chart ────────────────────────────────────────────────────

    @Test
    fun `LoadDashboard populates weeklySalesData with 7 data points`() = runTest {
        viewModel.dispatch(DashboardIntent.LoadDashboard)
        advanceUntilIdle()

        assertEquals(7, viewModel.state.value.weeklySalesData.size)
    }

    // ── Logout ────────────────────────────────────────────────────────────────

    @Test
    fun `Logout intent calls authRepository logout`() = runTest {
        viewModel.dispatch(DashboardIntent.Logout)
        advanceUntilIdle()

        assertTrue(logoutCalled)
    }

    // ── Error handling ────────────────────────────────────────────────────────

    @Test
    fun `LoadDashboard emits ShowError effect when repository throws`() = runTest {
        orderRepositoryThrows = true

        viewModel.effects.test {
            viewModel.dispatch(DashboardIntent.LoadDashboard)
            advanceUntilIdle()

            val effect = awaitItem()
            assertTrue(effect is DashboardEffect.ShowError)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `LoadDashboard clears isLoading on error`() = runTest {
        orderRepositoryThrows = true
        viewModel.dispatch(DashboardIntent.LoadDashboard)
        advanceUntilIdle()

        assertFalse(viewModel.state.value.isLoading)
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun makeOrder(
        id: String = "order-id",
        orderNumber: String = "ORD-001",
        total: Double = 100.0,
        status: OrderStatus = OrderStatus.COMPLETED,
        paymentMethod: PaymentMethod = PaymentMethod.CASH,
        createdAt: Instant = Clock.System.now(),
    ) = Order(
        id = id,
        orderNumber = orderNumber,
        type = OrderType.SALE,
        status = status,
        items = emptyList(),
        subtotal = total,
        taxAmount = 0.0,
        discountAmount = 0.0,
        total = total,
        paymentMethod = paymentMethod,
        amountTendered = total,
        changeAmount = 0.0,
        cashierId = "user-001",
        storeId = "store-001",
        registerSessionId = "session-001",
        createdAt = createdAt,
        updatedAt = createdAt,
        syncStatus = SyncStatus(state = SyncStatus.State.PENDING),
    )

    private fun makeProduct(
        id: String = "product-id",
        name: String = "Product",
        stockQty: Double = 10.0,
        minStockQty: Double = 5.0,
    ) = Product(
        id = id,
        name = name,
        categoryId = "cat-001",
        unitId = "unit-001",
        price = 9.99,
        stockQty = stockQty,
        minStockQty = minStockQty,
        createdAt = Clock.System.now(),
        updatedAt = Clock.System.now(),
    )

    private fun makeRegisterSession() = RegisterSession(
        id = "session-001",
        registerId = "register-001",
        openedBy = "user-001",
        openingBalance = 100.0,
        expectedBalance = 100.0,
        openedAt = Clock.System.now(),
        status = RegisterSession.Status.OPEN,
    )
}
