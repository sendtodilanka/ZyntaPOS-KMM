package com.zyntasolutions.zyntapos.feature.inventory

import app.cash.turbine.test
import com.zyntasolutions.zyntapos.core.result.Result
import com.zyntasolutions.zyntapos.domain.model.Permission
import com.zyntasolutions.zyntapos.domain.model.Product
import com.zyntasolutions.zyntapos.domain.model.Role
import com.zyntasolutions.zyntapos.domain.model.StockAdjustment
import com.zyntasolutions.zyntapos.domain.model.StocktakeCount
import com.zyntasolutions.zyntapos.domain.model.StocktakeSession
import com.zyntasolutions.zyntapos.domain.model.StocktakeStatus
import com.zyntasolutions.zyntapos.domain.model.User
import com.zyntasolutions.zyntapos.domain.repository.AuthRepository
import com.zyntasolutions.zyntapos.domain.repository.ProductRepository
import com.zyntasolutions.zyntapos.domain.repository.StocktakeRepository
import com.zyntasolutions.zyntapos.domain.repository.StockRepository
import com.zyntasolutions.zyntapos.domain.usecase.auth.CheckPermissionUseCase
import com.zyntasolutions.zyntapos.domain.usecase.inventory.CompleteStocktakeUseCase
import com.zyntasolutions.zyntapos.domain.usecase.inventory.ScanStocktakeItemUseCase
import com.zyntasolutions.zyntapos.domain.usecase.inventory.StartStocktakeUseCase
import com.zyntasolutions.zyntapos.feature.inventory.stocktake.StocktakeEffect
import com.zyntasolutions.zyntapos.feature.inventory.stocktake.StocktakeIntent
import com.zyntasolutions.zyntapos.feature.inventory.stocktake.StocktakeViewModel
import com.zyntasolutions.zyntapos.hal.scanner.BarcodeScanner
import com.zyntasolutions.zyntapos.hal.scanner.ScanResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.Instant
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Unit tests for [StocktakeViewModel].
 */
@OptIn(ExperimentalCoroutinesApi::class)
class StocktakeViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    private val adminUser = User(
        id = "user-001",
        name = "Admin User",
        email = "admin@zyntapos.com",
        role = Role.ADMIN,
        storeId = "store-001",
        isSystemAdmin = true,
        createdAt = Instant.fromEpochMilliseconds(1_700_000_000_000L),
        updatedAt = Instant.fromEpochMilliseconds(1_700_000_000_000L),
    )

    private val mockSession = StocktakeSession(
        id = "session-001",
        startedBy = "user-001",
        startedAt = 1_700_000_000_000L,
        status = StocktakeStatus.IN_PROGRESS,
    )

    private val mockProduct = Product(
        id = "prod-001",
        name = "Espresso Beans",
        barcode = "1234567890123",
        sku = "SKU-001",
        categoryId = "cat-001",
        unitId = "unit-001",
        price = 1500.0,
        stockQty = 50.0,
        createdAt = Instant.fromEpochMilliseconds(1_700_000_000_000L),
        updatedAt = Instant.fromEpochMilliseconds(1_700_000_000_000L),
    )

    private val mockCount = StocktakeCount(
        productId = "prod-001",
        barcode = "1234567890123",
        productName = "Espresso Beans",
        systemQty = 50,
        countedQty = 1,
        scannedAt = 1_700_000_000_000L,
    )

    private val sessionFlow = MutableStateFlow<User?>(adminUser)
    private var startSessionResult: Result<StocktakeSession> = Result.Success(mockSession)
    private var scanItemResult: Result<StocktakeCount> = Result.Success(mockCount)
    private var completeResult: Result<Map<String, Int>> = Result.Success(mapOf("prod-001" to 5))

    private val fakeAuthRepo = object : AuthRepository {
        override suspend fun login(email: String, password: String): Result<User> = Result.Success(adminUser)
        override suspend fun logout() {}
        override fun getSession(): Flow<User?> = sessionFlow
        override suspend fun refreshToken(): Result<Unit> = Result.Success(Unit)
        override suspend fun updatePin(userId: String, pin: String): Result<Unit> = Result.Success(Unit)
        override suspend fun validatePin(userId: String, pin: String): Result<Boolean> = Result.Success(true)
    }

    private val fakeStocktakeRepo = object : StocktakeRepository {
        override suspend fun startSession(userId: String): Result<StocktakeSession> = startSessionResult
        override suspend fun updateCount(sessionId: String, barcode: String, qty: Int): Result<Unit> = Result.Success(Unit)
        override suspend fun getSession(id: String): Result<StocktakeSession> = Result.Success(mockSession)
        override suspend fun getCountsForSession(sessionId: String): Result<List<StocktakeCount>> =
            Result.Success(emptyList())
        override suspend fun complete(sessionId: String): Result<Map<String, Int>> = completeResult
        override suspend fun cancel(sessionId: String): Result<Unit> = Result.Success(Unit)
    }

    private val fakeProductRepo = object : ProductRepository {
        override fun getAll(): Flow<List<Product>> = MutableStateFlow(listOf(mockProduct))
        override suspend fun getById(id: String): Result<Product> = Result.Success(mockProduct)
        override fun search(query: String, categoryId: String?): Flow<List<Product>> =
            MutableStateFlow(emptyList())
        override suspend fun getByBarcode(barcode: String): Result<Product> = Result.Success(mockProduct)
        override suspend fun insert(product: Product): Result<Unit> = Result.Success(Unit)
        override suspend fun update(product: Product): Result<Unit> = Result.Success(Unit)
        override suspend fun delete(id: String): Result<Unit> = Result.Success(Unit)
        override suspend fun getCount(): Int = 1
    }

    private val fakeStockRepo = object : StockRepository {
        override suspend fun adjustStock(adjustment: StockAdjustment): Result<Unit> = Result.Success(Unit)
        override fun getMovements(productId: String): Flow<List<StockAdjustment>> =
            MutableStateFlow(emptyList())
        override fun getAlerts(threshold: Double?): Flow<List<Product>> = MutableStateFlow(emptyList())
    }

    private val fakeBarcodeScanner = object : BarcodeScanner {
        override val scanEvents: Flow<ScanResult> = MutableStateFlow(ScanResult.Barcode("1234567890123", com.zyntasolutions.zyntapos.hal.scanner.BarcodeFormat.EAN_13))
        override suspend fun startListening(): kotlin.Result<Unit> = kotlin.Result.success(Unit)
        override suspend fun stopListening() {}
    }

    private lateinit var viewModel: StocktakeViewModel

    @BeforeTest
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        val checkPermission = CheckPermissionUseCase(sessionFlow)
        checkPermission.updateSession(adminUser)

        viewModel = StocktakeViewModel(
            startStocktakeUseCase = StartStocktakeUseCase(fakeStocktakeRepo, checkPermission),
            scanStocktakeItemUseCase = ScanStocktakeItemUseCase(fakeProductRepo, fakeStocktakeRepo),
            completeStocktakeUseCase = CompleteStocktakeUseCase(fakeStocktakeRepo, fakeStockRepo, checkPermission),
            stocktakeRepository = fakeStocktakeRepo,
            authRepository = fakeAuthRepo,
            barcodeScanner = fakeBarcodeScanner,
        )
    }

    @AfterTest
    fun teardown() {
        Dispatchers.resetMain()
    }

    // ── Initial state ──────────────────────────────────────────────────────────

    @Test
    fun `initial state is empty`() {
        val state = viewModel.currentState
        assertNull(state.session)
        assertTrue(state.counts.isEmpty())
        assertFalse(state.isScanning)
        assertFalse(state.isStarting)
        assertFalse(state.isCompleting)
        assertNull(state.error)
    }

    // ── StartSession ───────────────────────────────────────────────────────────

    @Test
    fun `StartSession success sets session in state`() = runTest {
        testDispatcher.scheduler.advanceUntilIdle() // let init coroutine set currentUserId

        viewModel.handleIntentForTest(StocktakeIntent.StartSession)
        testDispatcher.scheduler.advanceUntilIdle()

        assertNotNull(viewModel.currentState.session)
        assertEquals("session-001", viewModel.currentState.session!!.id)
    }

    @Test
    fun `StartSession success clears isStarting`() = runTest {
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.handleIntentForTest(StocktakeIntent.StartSession)
        testDispatcher.scheduler.advanceUntilIdle()

        assertFalse(viewModel.currentState.isStarting)
    }

    @Test
    fun `StartSession failure sets error in state`() = runTest {
        testDispatcher.scheduler.advanceUntilIdle()
        startSessionResult = Result.Error(Exception("Session already in progress"))

        viewModel.handleIntentForTest(StocktakeIntent.StartSession)
        testDispatcher.scheduler.advanceUntilIdle()

        assertNotNull(viewModel.currentState.error)
        assertNull(viewModel.currentState.session)
    }

    // ── ScanItem ───────────────────────────────────────────────────────────────

    @Test
    fun `ScanItem with active session emits ScanSuccess effect`() = runTest {
        testDispatcher.scheduler.advanceUntilIdle()
        viewModel.handleIntentForTest(StocktakeIntent.StartSession)
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.effects.test {
            viewModel.handleIntentForTest(StocktakeIntent.ScanItem("1234567890123"))
            testDispatcher.scheduler.advanceUntilIdle()
            val effect = awaitItem()
            assertTrue(effect is StocktakeEffect.ScanSuccess)
            assertTrue((effect as StocktakeEffect.ScanSuccess).productName.isNotBlank())
        }
    }

    @Test
    fun `ScanItem with active session adds count to state`() = runTest {
        testDispatcher.scheduler.advanceUntilIdle()
        viewModel.handleIntentForTest(StocktakeIntent.StartSession)
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.handleIntentForTest(StocktakeIntent.ScanItem("1234567890123"))
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(1, viewModel.currentState.counts.size)
    }

    @Test
    fun `ScanItem without session is a no-op`() = runTest {
        testDispatcher.scheduler.advanceUntilIdle()
        // No StartSession called

        viewModel.effects.test {
            viewModel.handleIntentForTest(StocktakeIntent.ScanItem("1234567890123"))
            testDispatcher.scheduler.advanceUntilIdle()
            expectNoEvents()
        }
    }

    @Test
    fun `ScanItem failure emits ScanNotFound effect`() = runTest {
        testDispatcher.scheduler.advanceUntilIdle()
        scanItemResult = Result.Error(Exception("Product not found"))
        viewModel.handleIntentForTest(StocktakeIntent.StartSession)
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.effects.test {
            viewModel.handleIntentForTest(StocktakeIntent.ScanItem("unknown-barcode"))
            testDispatcher.scheduler.advanceUntilIdle()
            val effect = awaitItem()
            assertTrue(effect is StocktakeEffect.ScanNotFound)
        }
    }

    // ── RemoveCount ────────────────────────────────────────────────────────────

    @Test
    fun `RemoveCount removes item from counts`() = runTest {
        testDispatcher.scheduler.advanceUntilIdle()
        viewModel.handleIntentForTest(StocktakeIntent.StartSession)
        testDispatcher.scheduler.advanceUntilIdle()
        viewModel.handleIntentForTest(StocktakeIntent.ScanItem("1234567890123"))
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals(1, viewModel.currentState.counts.size)

        viewModel.handleIntentForTest(StocktakeIntent.RemoveCount("prod-001"))
        testDispatcher.scheduler.advanceUntilIdle()
        assertTrue(viewModel.currentState.counts.isEmpty())
    }

    // ── CancelStocktake ────────────────────────────────────────────────────────

    @Test
    fun `CancelStocktake emits SessionCancelled effect`() = runTest {
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.effects.test {
            viewModel.handleIntentForTest(StocktakeIntent.CancelStocktake)
            testDispatcher.scheduler.advanceUntilIdle()
            val effect = awaitItem()
            assertTrue(effect is StocktakeEffect.SessionCancelled)
        }
    }

    @Test
    fun `CancelStocktake clears session and counts`() = runTest {
        testDispatcher.scheduler.advanceUntilIdle()
        viewModel.handleIntentForTest(StocktakeIntent.StartSession)
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.handleIntentForTest(StocktakeIntent.CancelStocktake)
        testDispatcher.scheduler.advanceUntilIdle()

        assertNull(viewModel.currentState.session)
        assertTrue(viewModel.currentState.counts.isEmpty())
    }

    // ── CompleteStocktake ──────────────────────────────────────────────────────

    @Test
    fun `CompleteStocktake success emits StocktakeCompleted effect`() = runTest {
        testDispatcher.scheduler.advanceUntilIdle()
        completeResult = Result.Success(mapOf("prod-001" to 5, "prod-002" to 0)) // 1 non-zero variance

        viewModel.handleIntentForTest(StocktakeIntent.StartSession)
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.effects.test {
            viewModel.handleIntentForTest(StocktakeIntent.CompleteStocktake)
            testDispatcher.scheduler.advanceUntilIdle()
            val effect = awaitItem()
            assertTrue(effect is StocktakeEffect.StocktakeCompleted)
            assertEquals(1, (effect as StocktakeEffect.StocktakeCompleted).varianceCount)
        }
    }

    @Test
    fun `CompleteStocktake success clears session and counts`() = runTest {
        testDispatcher.scheduler.advanceUntilIdle()
        viewModel.handleIntentForTest(StocktakeIntent.StartSession)
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.handleIntentForTest(StocktakeIntent.CompleteStocktake)
        testDispatcher.scheduler.advanceUntilIdle()

        assertNull(viewModel.currentState.session)
        assertTrue(viewModel.currentState.counts.isEmpty())
    }

    @Test
    fun `CompleteStocktake failure sets error`() = runTest {
        testDispatcher.scheduler.advanceUntilIdle()
        completeResult = Result.Error(Exception("Session not found"))

        viewModel.handleIntentForTest(StocktakeIntent.StartSession)
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.handleIntentForTest(StocktakeIntent.CompleteStocktake)
        testDispatcher.scheduler.advanceUntilIdle()

        assertNotNull(viewModel.currentState.error)
    }

    // ── DismissError ───────────────────────────────────────────────────────────

    @Test
    fun `DismissError clears error in state`() = runTest {
        testDispatcher.scheduler.advanceUntilIdle()
        startSessionResult = Result.Error(Exception("error"))

        viewModel.handleIntentForTest(StocktakeIntent.StartSession)
        testDispatcher.scheduler.advanceUntilIdle()
        assertNotNull(viewModel.currentState.error)

        viewModel.handleIntentForTest(StocktakeIntent.DismissError)
        testDispatcher.scheduler.advanceUntilIdle()
        assertNull(viewModel.currentState.error)
    }
}

// ─── Extension to expose handleIntent for testing ────────────────────────────

private suspend fun StocktakeViewModel.handleIntentForTest(intent: StocktakeIntent) =
    handleIntent(intent)
