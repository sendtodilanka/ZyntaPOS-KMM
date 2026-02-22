package com.zyntasolutions.zyntapos.feature.pos

import app.cash.turbine.test
import com.zyntasolutions.zyntapos.core.result.DatabaseException
import com.zyntasolutions.zyntapos.core.result.Result
import com.zyntasolutions.zyntapos.core.utils.CurrencyFormatter
import com.zyntasolutions.zyntapos.domain.formatter.ReceiptFormatter
import com.zyntasolutions.zyntapos.domain.model.CartItem
import com.zyntasolutions.zyntapos.domain.model.Category
import com.zyntasolutions.zyntapos.domain.model.DiscountType
import com.zyntasolutions.zyntapos.domain.model.Order
import com.zyntasolutions.zyntapos.domain.model.OrderStatus
import com.zyntasolutions.zyntapos.domain.model.OrderType
import com.zyntasolutions.zyntapos.domain.model.PaymentMethod
import com.zyntasolutions.zyntapos.domain.model.Product
import com.zyntasolutions.zyntapos.domain.model.StockAdjustment
import com.zyntasolutions.zyntapos.domain.model.SyncStatus
import com.zyntasolutions.zyntapos.domain.model.User
import com.zyntasolutions.zyntapos.domain.printer.ReceiptPrinterPort
import com.zyntasolutions.zyntapos.domain.repository.CategoryRepository
import com.zyntasolutions.zyntapos.domain.repository.OrderRepository
import com.zyntasolutions.zyntapos.domain.repository.ProductRepository
import com.zyntasolutions.zyntapos.domain.repository.SettingsRepository
import com.zyntasolutions.zyntapos.domain.repository.StockRepository
import com.zyntasolutions.zyntapos.domain.usecase.auth.CheckPermissionUseCase
import com.zyntasolutions.zyntapos.domain.usecase.pos.AddItemToCartUseCase
import com.zyntasolutions.zyntapos.domain.usecase.pos.ApplyItemDiscountUseCase
import com.zyntasolutions.zyntapos.domain.usecase.pos.ApplyOrderDiscountUseCase
import com.zyntasolutions.zyntapos.domain.usecase.pos.CalculateOrderTotalsUseCase
import com.zyntasolutions.zyntapos.domain.usecase.pos.HoldOrderUseCase
import com.zyntasolutions.zyntapos.domain.usecase.pos.PrintReceiptUseCase
import com.zyntasolutions.zyntapos.domain.usecase.pos.ProcessPaymentUseCase
import com.zyntasolutions.zyntapos.domain.usecase.pos.RemoveItemFromCartUseCase
import com.zyntasolutions.zyntapos.domain.usecase.pos.RetrieveHeldOrderUseCase
import com.zyntasolutions.zyntapos.domain.usecase.pos.UpdateCartItemQuantityUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

// ─────────────────────────────────────────────────────────────────────────────
// PosViewModelTest — Sprint 17, task 9.1.27
// Tests key PosViewModel MVI state transitions using hand-rolled fake
// repositories and real use case instances.
// Mockative replaced with pure Kotlin stubs (KSP1 incompatible with Kotlin 2.3+).
// ─────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalCoroutinesApi::class)
class PosViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private val now: Instant = Clock.System.now()

    // ── Fake Repositories ──────────────────────────────────────────────────────

    private val productsMap = mutableMapOf<String, Product>()
    private val productsFlow = MutableStateFlow<List<Product>>(emptyList())

    private val fakeProductRepository = object : ProductRepository {
        override fun getAll(): Flow<List<Product>> = productsFlow

        override suspend fun getById(id: String): Result<Product> {
            return productsMap[id]?.let { Result.Success(it) }
                ?: Result.Error(DatabaseException("Product not found"))
        }

        override fun search(query: String, categoryId: String?): Flow<List<Product>> {
            return productsFlow.map { list ->
                list.filter { p ->
                    (categoryId == null || p.categoryId == categoryId) &&
                        (query.isBlank() || p.name.contains(query, ignoreCase = true))
                }
            }
        }

        override suspend fun getByBarcode(barcode: String): Result<Product> {
            return productsMap.values.firstOrNull { it.barcode == barcode }
                ?.let { Result.Success(it) }
                ?: Result.Error(DatabaseException("Not found"))
        }

        override suspend fun insert(product: Product): Result<Unit> = Result.Success(Unit)
        override suspend fun update(product: Product): Result<Unit> = Result.Success(Unit)
        override suspend fun delete(id: String): Result<Unit> = Result.Success(Unit)
        override suspend fun getCount(): Int = productsMap.size
    }

    private val categoriesFlow = MutableStateFlow<List<Category>>(emptyList())

    private val fakeCategoryRepository = object : CategoryRepository {
        override fun getAll(): Flow<List<Category>> = categoriesFlow

        override suspend fun getById(id: String): Result<Category> {
            return categoriesFlow.value.firstOrNull { it.id == id }
                ?.let { Result.Success(it) }
                ?: Result.Error(DatabaseException("Category not found"))
        }

        override suspend fun insert(category: Category): Result<Unit> = Result.Success(Unit)
        override suspend fun update(category: Category): Result<Unit> = Result.Success(Unit)
        override suspend fun delete(id: String): Result<Unit> = Result.Success(Unit)
        override fun getTree(): Flow<List<Category>> = categoriesFlow
    }

    private val createdOrders = mutableListOf<Order>()
    private val ordersFlow = MutableStateFlow<List<Order>>(emptyList())

    private val fakeOrderRepository = object : OrderRepository {
        override suspend fun create(order: Order): Result<Order> {
            val saved = order.copy(orderNumber = "ORD-${createdOrders.size + 1}")
            createdOrders.add(saved)
            ordersFlow.value = createdOrders.toList()
            return Result.Success(saved)
        }

        override suspend fun getById(id: String): Result<Order> {
            return ordersFlow.value.firstOrNull { it.id == id }
                ?.let { Result.Success(it) }
                ?: Result.Error(DatabaseException("Order not found"))
        }

        override fun getAll(filters: Map<String, String>): Flow<List<Order>> {
            return ordersFlow.map { orders ->
                orders.filter { order ->
                    filters.all { (key, value) ->
                        when (key) {
                            "status" -> order.status.name == value
                            else -> true
                        }
                    }
                }
            }
        }

        override suspend fun update(order: Order): Result<Unit> = Result.Success(Unit)
        override suspend fun void(id: String, reason: String): Result<Unit> = Result.Success(Unit)

        override fun getByDateRange(from: Instant, to: Instant): Flow<List<Order>> {
            return ordersFlow.map { orders ->
                orders.filter { it.createdAt in from..to }
            }
        }

        override suspend fun holdOrder(cart: List<CartItem>): Result<String> =
            Result.Success("held-order-id")

        override suspend fun retrieveHeld(holdId: String): Result<Order> =
            Result.Error(DatabaseException("Not implemented"))
    }

    private val fakeStockRepository = object : StockRepository {
        override suspend fun adjustStock(adjustment: StockAdjustment): Result<Unit> =
            Result.Success(Unit)

        override fun getMovements(productId: String): Flow<List<StockAdjustment>> =
            MutableStateFlow(emptyList())

        override fun getAlerts(threshold: Double?): Flow<List<Product>> =
            MutableStateFlow(emptyList())
    }

    private val fakeSettingsRepository = object : SettingsRepository {
        private val store = mutableMapOf<String, String>()
        override suspend fun get(key: String): String? = store[key]
        override suspend fun set(key: String, value: String): Result<Unit> {
            store[key] = value
            return Result.Success(Unit)
        }
        override suspend fun getAll(): Map<String, String> = store.toMap()
        override fun observe(key: String): Flow<String?> = MutableStateFlow(store[key])
    }

    private val fakeReceiptPrinterPort = object : ReceiptPrinterPort {
        override suspend fun print(order: Order, cashierId: String): Result<Unit> =
            Result.Success(Unit)
    }

    // ── Real use cases wired to fake repositories ──────────────────────────────

    private val calculateTotalsUseCase = CalculateOrderTotalsUseCase()

    private lateinit var viewModel: PosViewModel

    private val testCategory = Category(
        id = "cat-drinks",
        name = "Drinks",
        parentId = null,
        imageUrl = null,
        displayOrder = 0,
    )

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(testDispatcher)

        productsMap.clear()
        productsFlow.value = emptyList()
        categoriesFlow.value = listOf(testCategory)
        createdOrders.clear()
        ordersFlow.value = emptyList()

        val sessionFlow = MutableStateFlow<User?>(null)
        val checkPermissionUseCase = CheckPermissionUseCase(sessionFlow)

        viewModel = PosViewModel(
            productRepository = fakeProductRepository,
            categoryRepository = fakeCategoryRepository,
            orderRepository = fakeOrderRepository,
            addItemUseCase = AddItemToCartUseCase(fakeProductRepository),
            removeItemUseCase = RemoveItemFromCartUseCase(),
            updateQtyUseCase = UpdateCartItemQuantityUseCase(fakeProductRepository),
            applyItemDiscountUseCase = ApplyItemDiscountUseCase(checkPermissionUseCase),
            applyOrderDiscountUseCase = ApplyOrderDiscountUseCase(fakeSettingsRepository, calculateTotalsUseCase),
            calculateTotalsUseCase = calculateTotalsUseCase,
            holdOrderUseCase = HoldOrderUseCase(fakeOrderRepository),
            retrieveHeldUseCase = RetrieveHeldOrderUseCase(fakeOrderRepository),
            processPaymentUseCase = ProcessPaymentUseCase(fakeOrderRepository, fakeStockRepository, calculateTotalsUseCase),
            printReceiptUseCase = PrintReceiptUseCase(fakeReceiptPrinterPort),
            receiptFormatter = ReceiptFormatter(CurrencyFormatter()),
            cashierId = "cashier-01",
            storeId = "store-01",
            registerSessionId = "session-01",
        )
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun addTestProduct(
        id: String = "p1",
        name: String = "Test Coffee",
        price: Double = 4.50,
        stockQty: Double = 100.0,
    ): Product {
        val product = Product(
            id = id,
            name = name,
            barcode = "1234567890123",
            sku = "SKU-COFFEE",
            categoryId = "cat-drinks",
            unitId = "unit-01",
            price = price,
            costPrice = 2.00,
            taxGroupId = null,
            stockQty = stockQty,
            minStockQty = 5.0,
            imageUrl = null,
            description = "Test product",
            isActive = true,
            createdAt = now,
            updatedAt = now,
        )
        productsMap[product.id] = product
        productsFlow.value = productsMap.values.toList()
        return product
    }

    // ── Initial state ─────────────────────────────────────────────────────────

    @Test
    fun `initial state has empty cart and zero totals`() {
        val state = viewModel.state.value
        assertTrue(state.cartItems.isEmpty())
        assertEquals(0.0, state.orderTotals.total, 0.001)
        assertEquals(0.0, state.orderTotals.subtotal, 0.001)
        assertNull(state.selectedCustomer)
        assertNull(state.error)
        assertFalse(state.isLoading)
    }

    @Test
    fun `initial state has no selected category`() {
        assertNull(viewModel.state.value.selectedCategoryId)
    }

    // ── SearchQueryChanged ────────────────────────────────────────────────────

    @Test
    fun `SearchQueryChanged updates searchQuery state immediately`() = runTest {
        viewModel.dispatch(PosIntent.SearchQueryChanged("coffee"))
        advanceUntilIdle()
        assertEquals("coffee", viewModel.state.value.searchQuery)
    }

    @Test
    fun `SearchQueryChanged to empty string resets searchQuery`() = runTest {
        viewModel.dispatch(PosIntent.SearchQueryChanged("latte"))
        advanceUntilIdle()
        viewModel.dispatch(PosIntent.SearchQueryChanged(""))
        advanceUntilIdle()
        assertEquals("", viewModel.state.value.searchQuery)
    }

    // ── SearchFocusChanged ────────────────────────────────────────────────────

    @Test
    fun `SearchFocusChanged true sets isSearchFocused`() = runTest {
        viewModel.dispatch(PosIntent.SearchFocusChanged(true))
        advanceUntilIdle()
        assertTrue(viewModel.state.value.isSearchFocused)
    }

    @Test
    fun `SearchFocusChanged false clears isSearchFocused`() = runTest {
        viewModel.dispatch(PosIntent.SearchFocusChanged(true))
        viewModel.dispatch(PosIntent.SearchFocusChanged(false))
        advanceUntilIdle()
        assertFalse(viewModel.state.value.isSearchFocused)
    }

    // ── SelectCategory ────────────────────────────────────────────────────────

    @Test
    fun `SelectCategory sets selectedCategoryId`() = runTest {
        viewModel.dispatch(PosIntent.SelectCategory("cat-drinks"))
        advanceUntilIdle()
        assertEquals("cat-drinks", viewModel.state.value.selectedCategoryId)
    }

    @Test
    fun `SelectCategory null clears selectedCategoryId`() = runTest {
        viewModel.dispatch(PosIntent.SelectCategory("cat-drinks"))
        viewModel.dispatch(PosIntent.SelectCategory(null))
        advanceUntilIdle()
        assertNull(viewModel.state.value.selectedCategoryId)
    }

    // ── ClearCart ─────────────────────────────────────────────────────────────

    @Test
    fun `ClearCart empties cart and resets totals`() = runTest {
        viewModel.dispatch(PosIntent.ClearCart)
        advanceUntilIdle()
        val state = viewModel.state.value
        assertTrue(state.cartItems.isEmpty())
        assertEquals(0.0, state.orderTotals.total, 0.001)
        assertNull(state.selectedCustomer)
        assertEquals(0.0, state.orderDiscount, 0.001)
    }

    // ── AddToCart — success ───────────────────────────────────────────────────

    @Test
    fun `AddToCart success adds item to cartItems and recalculates totals`() = runTest {
        val product = addTestProduct()

        viewModel.dispatch(PosIntent.AddToCart(product))
        advanceUntilIdle()

        val state = viewModel.state.value
        assertEquals(1, state.cartItems.size)
        assertEquals("p1", state.cartItems[0].productId)
        assertEquals(4.50, state.orderTotals.subtotal, 0.001)
    }

    @Test
    fun `AddToCart failure emits ShowError effect`() = runTest {
        // Product with zero stock triggers OUT_OF_STOCK validation error
        val product = addTestProduct(stockQty = 0.0)

        viewModel.effects.test {
            viewModel.dispatch(PosIntent.AddToCart(product))
            advanceUntilIdle()

            val effect = awaitItem()
            assertTrue(effect is PosEffect.ShowError)
            cancelAndIgnoreRemainingEvents()
        }
    }

    // ── RemoveFromCart ────────────────────────────────────────────────────────

    @Test
    fun `RemoveFromCart removes item from cartItems`() = runTest {
        val product = addTestProduct()

        // Seed item via AddToCart
        viewModel.dispatch(PosIntent.AddToCart(product))
        advanceUntilIdle()
        assertEquals(1, viewModel.state.value.cartItems.size)

        // Remove the item
        viewModel.dispatch(PosIntent.RemoveFromCart("p1"))
        advanceUntilIdle()

        assertTrue(viewModel.state.value.cartItems.isEmpty())
    }

    // ── HoldOrder ─────────────────────────────────────────────────────────────

    @Test
    fun `HoldOrder clears active cart on success`() = runTest {
        val product = addTestProduct()

        viewModel.dispatch(PosIntent.AddToCart(product))
        advanceUntilIdle()

        viewModel.dispatch(PosIntent.HoldOrder)
        advanceUntilIdle()

        assertTrue(viewModel.state.value.cartItems.isEmpty(), "Cart must be cleared after successful hold")
    }

    // ── ScanBarcode — not found ───────────────────────────────────────────────

    @Test
    fun `ScanBarcode with unknown barcode emits BarcodeNotFound effect`() = runTest {
        viewModel.effects.test {
            viewModel.dispatch(PosIntent.ScanBarcode("9999999999999"))
            advanceUntilIdle()

            val effect = awaitItem()
            assertTrue(effect is PosEffect.BarcodeNotFound)
            assertEquals("9999999999999", (effect as PosEffect.BarcodeNotFound).barcode)
            cancelAndIgnoreRemainingEvents()
        }
    }

    // ── ProcessPayment ────────────────────────────────────────────────────────

    @Test
    fun `ProcessPayment success emits receipt or cash drawer effect`() = runTest {
        val product = addTestProduct()

        viewModel.dispatch(PosIntent.AddToCart(product))
        advanceUntilIdle()

        viewModel.effects.test {
            viewModel.dispatch(
                PosIntent.ProcessPayment(
                    method = PaymentMethod.CASH,
                    tendered = 10.0,
                ),
            )
            advanceUntilIdle()

            val effect = awaitItem()
            assertTrue(
                effect is PosEffect.ShowReceiptScreen ||
                    effect is PosEffect.OpenCashDrawer ||
                    effect is PosEffect.PrintReceipt,
            )
            cancelAndIgnoreRemainingEvents()
        }
    }

    // ── SetScannerActive ──────────────────────────────────────────────────────

    @Test
    fun `SetScannerActive true sets scannerActive state`() = runTest {
        viewModel.dispatch(PosIntent.SetScannerActive(true))
        advanceUntilIdle()
        assertTrue(viewModel.state.value.scannerActive)
    }

    @Test
    fun `SetScannerActive false clears scannerActive state`() = runTest {
        viewModel.dispatch(PosIntent.SetScannerActive(true))
        viewModel.dispatch(PosIntent.SetScannerActive(false))
        advanceUntilIdle()
        assertFalse(viewModel.state.value.scannerActive)
    }
}
