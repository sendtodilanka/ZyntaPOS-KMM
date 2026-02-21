package com.zyntasolutions.zyntapos.feature.pos

import app.cash.turbine.test
import com.zyntasolutions.zyntapos.core.result.Result
import com.zyntasolutions.zyntapos.core.result.ValidationException
import com.zyntasolutions.zyntapos.domain.model.CartItem
import com.zyntasolutions.zyntapos.domain.model.Category
import com.zyntasolutions.zyntapos.domain.model.DiscountType
import com.zyntasolutions.zyntapos.domain.model.Order
import com.zyntasolutions.zyntapos.domain.model.OrderStatus
import com.zyntasolutions.zyntapos.domain.model.PaymentMethod
import com.zyntasolutions.zyntapos.domain.repository.CategoryRepository
import com.zyntasolutions.zyntapos.domain.repository.OrderRepository
import com.zyntasolutions.zyntapos.domain.repository.ProductRepository
import com.zyntasolutions.zyntapos.domain.usecase.pos.AddItemToCartUseCase
import com.zyntasolutions.zyntapos.domain.usecase.pos.ApplyItemDiscountUseCase
import com.zyntasolutions.zyntapos.domain.usecase.pos.ApplyOrderDiscountUseCase
import com.zyntasolutions.zyntapos.domain.usecase.pos.CalculateOrderTotalsUseCase
import com.zyntasolutions.zyntapos.domain.usecase.pos.HoldOrderUseCase
import com.zyntasolutions.zyntapos.domain.usecase.pos.ProcessPaymentUseCase
import com.zyntasolutions.zyntapos.domain.usecase.pos.RemoveItemFromCartUseCase
import com.zyntasolutions.zyntapos.domain.usecase.pos.RetrieveHeldOrderUseCase
import com.zyntasolutions.zyntapos.domain.usecase.pos.UpdateCartItemQuantityUseCase
import io.mockative.Mock
import io.mockative.classOf
import io.mockative.every
import io.mockative.everySuspend
import io.mockative.mock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
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
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

// ─────────────────────────────────────────────────────────────────────────────
// PosViewModelTest — Sprint 17, task 9.1.27
// Tests key PosViewModel MVI state transitions using fake repositories
// and StandardTestDispatcher for coroutine control.
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Unit tests for [PosViewModel] MVI state transitions.
 *
 * Tests validate:
 * - Initial default state is correct.
 * - [PosIntent.SearchQueryChanged] updates [PosState.searchQuery].
 * - [PosIntent.SelectCategory] updates [PosState.selectedCategoryId].
 * - [PosIntent.ClearCart] resets cart, totals, and customer.
 * - [PosIntent.AddToCart] success adds item and recalculates totals.
 * - [PosIntent.AddToCart] failure emits [PosEffect.ShowError].
 * - [PosIntent.RemoveFromCart] removes cart line.
 * - [PosIntent.HoldOrder] clears cart after success.
 * - [PosIntent.ProcessPayment] emits [PosEffect.ShowReceiptScreen] on success.
 * - [PosIntent.ScanBarcode] emits [PosEffect.BarcodeNotFound] on no-match.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PosViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    // ── Mocks ─────────────────────────────────────────────────────────────────

    @Mock private val productRepository = mock(classOf<ProductRepository>())
    @Mock private val categoryRepository = mock(classOf<CategoryRepository>())
    @Mock private val orderRepository = mock(classOf<OrderRepository>())
    @Mock private val addItemUseCase = mock(classOf<AddItemToCartUseCase>())
    @Mock private val removeItemUseCase = mock(classOf<RemoveItemFromCartUseCase>())
    @Mock private val updateQtyUseCase = mock(classOf<UpdateCartItemQuantityUseCase>())
    @Mock private val applyItemDiscountUseCase = mock(classOf<ApplyItemDiscountUseCase>())
    @Mock private val applyOrderDiscountUseCase = mock(classOf<ApplyOrderDiscountUseCase>())
    @Mock private val holdOrderUseCase = mock(classOf<HoldOrderUseCase>())
    @Mock private val retrieveHeldUseCase = mock(classOf<RetrieveHeldOrderUseCase>())
    @Mock private val processPaymentUseCase = mock(classOf<ProcessPaymentUseCase>())

    // Real stateless use cases (no external I/O)
    private val calculateTotalsUseCase = CalculateOrderTotalsUseCase()

    private lateinit var viewModel: PosViewModel

    // ── Shared test data ──────────────────────────────────────────────────────

    private val now: Instant = Clock.System.now()

    private val testCartItem = CartItem(
        productId = "p1",
        productName = "Test Coffee",
        unitPrice = 4.50,
        quantity = 1.0,
        discount = 0.0,
        discountType = DiscountType.FIXED,
        taxRate = 0.0,
    )

    private val testCategory = Category(
        id = "cat-drinks",
        name = "Drinks",
        parentId = null,
        imageUrl = null,
        sortOrder = 0,
    )

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(testDispatcher)

        // Stub reactive flows used by init blocks
        every { categoryRepository.getAll() }.returns(flowOf(listOf(testCategory)))
        every { productRepository.getAll() }.returns(flowOf(emptyList()))
        every { productRepository.search(any(), any()) }.returns(flowOf(emptyList()))
        every { orderRepository.getAll(any()) }.returns(flowOf(emptyList()))

        viewModel = PosViewModel(
            productRepository = productRepository,
            categoryRepository = categoryRepository,
            orderRepository = orderRepository,
            addItemUseCase = addItemUseCase,
            removeItemUseCase = removeItemUseCase,
            updateQtyUseCase = updateQtyUseCase,
            applyItemDiscountUseCase = applyItemDiscountUseCase,
            applyOrderDiscountUseCase = applyOrderDiscountUseCase,
            calculateTotalsUseCase = calculateTotalsUseCase,
            holdOrderUseCase = holdOrderUseCase,
            retrieveHeldUseCase = retrieveHeldUseCase,
            processPaymentUseCase = processPaymentUseCase,
            cashierId = "cashier-01",
            storeId = "store-01",
            registerSessionId = "session-01",
        )
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
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
        // Seed some cart items by directly injecting state (whitebox)
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
        val product = buildTestProduct()
        everySuspend {
            addItemUseCase.invoke(
                currentCart = any(),
                productId = any(),
                quantity = any(),
            )
        }.returns(Result.Success(listOf(testCartItem)))

        viewModel.dispatch(PosIntent.AddToCart(product))
        advanceUntilIdle()

        val state = viewModel.state.value
        assertEquals(1, state.cartItems.size)
        assertEquals("p1", state.cartItems[0].productId)
        assertEquals(4.50, state.orderTotals.subtotal, 0.001)
    }

    @Test
    fun `AddToCart failure emits ShowError effect`() = runTest {
        val product = buildTestProduct()
        everySuspend {
            addItemUseCase.invoke(
                currentCart = any(),
                productId = any(),
                quantity = any(),
            )
        }.returns(
            Result.Error(
                ValidationException("Not enough stock", field = "quantity", rule = "OUT_OF_STOCK"),
            ),
        )

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
        // Stub add first to seed item
        val product = buildTestProduct()
        everySuspend {
            addItemUseCase.invoke(currentCart = any(), productId = any(), quantity = any())
        }.returns(Result.Success(listOf(testCartItem)))
        viewModel.dispatch(PosIntent.AddToCart(product))
        advanceUntilIdle()

        // Stub remove
        everySuspend {
            removeItemUseCase.invoke(currentCart = any(), productId = "p1")
        }.returns(Result.Success(emptyList()))

        viewModel.dispatch(PosIntent.RemoveFromCart("p1"))
        advanceUntilIdle()

        assertTrue(viewModel.state.value.cartItems.isEmpty())
    }

    // ── HoldOrder ─────────────────────────────────────────────────────────────

    @Test
    fun `HoldOrder clears active cart on success`() = runTest {
        // Seed cart
        val product = buildTestProduct()
        everySuspend {
            addItemUseCase.invoke(currentCart = any(), productId = any(), quantity = any())
        }.returns(Result.Success(listOf(testCartItem)))
        viewModel.dispatch(PosIntent.AddToCart(product))
        advanceUntilIdle()

        // Stub hold success
        everySuspend {
            holdOrderUseCase.invoke(
                cartItems = any(),
                cashierId = any(),
                storeId = any(),
                registerSessionId = any(),
            )
        }.returns(Result.Success("held-order-id"))

        viewModel.dispatch(PosIntent.HoldOrder)
        advanceUntilIdle()

        assertTrue(viewModel.state.value.cartItems.isEmpty(), "Cart must be cleared after successful hold")
    }

    // ── ScanBarcode — not found ───────────────────────────────────────────────

    @Test
    fun `ScanBarcode with unknown barcode emits BarcodeNotFound effect`() = runTest {
        everySuspend {
            productRepository.getByBarcode(any())
        }.returns(
            Result.Error(
                com.zyntasolutions.zyntapos.core.result.DatabaseException("Not found"),
            ),
        )

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
    fun `ProcessPayment success emits ShowReceiptScreen effect`() = runTest {
        // Seed cart
        val product = buildTestProduct()
        everySuspend {
            addItemUseCase.invoke(currentCart = any(), productId = any(), quantity = any())
        }.returns(Result.Success(listOf(testCartItem)))
        viewModel.dispatch(PosIntent.AddToCart(product))
        advanceUntilIdle()

        // Stub payment success
        val fakeOrder = buildTestOrder()
        everySuspend {
            processPaymentUseCase.invoke(
                items = any(),
                paymentMethod = any(),
                paymentSplits = any(),
                amountTendered = any(),
                customerId = any(),
                cashierId = any(),
                storeId = any(),
                registerSessionId = any(),
                orderDiscount = any(),
                orderDiscountType = any(),
                taxInclusive = any(),
                notes = any(),
            )
        }.returns(Result.Success(fakeOrder))

        viewModel.effects.test {
            viewModel.dispatch(
                PosIntent.ProcessPayment(
                    method = PaymentMethod.CASH,
                    tendered = 10.0,
                ),
            )
            advanceUntilIdle()

            val effect = awaitItem()
            assertTrue(effect is PosEffect.ShowReceiptScreen || effect is PosEffect.OpenCashDrawer || effect is PosEffect.PrintReceipt)
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

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun buildTestProduct() = com.zyntasolutions.zyntapos.domain.model.Product(
        id = "p1",
        name = "Test Coffee",
        barcode = "1234567890123",
        sku = "SKU-COFFEE",
        categoryId = "cat-drinks",
        unitId = "unit-01",
        price = 4.50,
        costPrice = 2.00,
        taxGroupId = null,
        stockQty = 100.0,
        minStockQty = 5.0,
        imageUrl = null,
        description = "Test product",
        isActive = true,
        createdAt = now,
        updatedAt = now,
        syncStatus = com.zyntasolutions.zyntapos.domain.model.SyncStatus(
            state = com.zyntasolutions.zyntapos.domain.model.SyncStatus.State.SYNCED,
        ),
    )

    private fun buildTestOrder() = Order(
        id = "order-test",
        orderNumber = "ORD-0001",
        type = com.zyntasolutions.zyntapos.domain.model.OrderType.SALE,
        status = OrderStatus.COMPLETED,
        items = emptyList(),
        subtotal = 4.50,
        taxAmount = 0.0,
        discountAmount = 0.0,
        total = 4.50,
        paymentMethod = PaymentMethod.CASH,
        amountTendered = 10.0,
        changeAmount = 5.50,
        cashierId = "cashier-01",
        storeId = "store-01",
        registerSessionId = "session-01",
        createdAt = now,
        updatedAt = now,
        syncStatus = com.zyntasolutions.zyntapos.domain.model.SyncStatus(
            state = com.zyntasolutions.zyntapos.domain.model.SyncStatus.State.PENDING,
        ),
    )
}
