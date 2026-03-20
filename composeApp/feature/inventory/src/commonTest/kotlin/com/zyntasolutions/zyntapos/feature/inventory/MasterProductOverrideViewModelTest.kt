package com.zyntasolutions.zyntapos.feature.inventory

import app.cash.turbine.test
import com.zyntasolutions.zyntapos.core.result.Result
import com.zyntasolutions.zyntapos.domain.model.MasterProduct
import com.zyntasolutions.zyntapos.domain.model.StoreProductOverride
import com.zyntasolutions.zyntapos.domain.repository.MasterProductRepository
import com.zyntasolutions.zyntapos.domain.repository.StoreProductOverrideRepository
import com.zyntasolutions.zyntapos.feature.inventory.masterproduct.MasterProductOverrideEffect
import com.zyntasolutions.zyntapos.feature.inventory.masterproduct.MasterProductOverrideIntent
import com.zyntasolutions.zyntapos.feature.inventory.masterproduct.MasterProductOverrideViewModel
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
 * Unit tests for [MasterProductOverrideViewModel].
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MasterProductOverrideViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    private val mockMasterProduct = MasterProduct(
        id = "mp-001",
        sku = "SKU-001",
        barcode = "1234567890123",
        name = "Espresso Blend",
        basePrice = 1200.0,
        costPrice = 800.0,
        createdAt = Instant.fromEpochMilliseconds(1_700_000_000_000L),
        updatedAt = Instant.fromEpochMilliseconds(1_700_000_000_000L),
    )

    private val mockOverride = StoreProductOverride(
        id = "override-001",
        masterProductId = "mp-001",
        storeId = "store-001",
        localPrice = 1250.0,
        localStockQty = 30.0,
        createdAt = Instant.fromEpochMilliseconds(1_700_000_000_000L),
        updatedAt = Instant.fromEpochMilliseconds(1_700_000_000_000L),
    )

    // Fake repository state
    private var masterProductResult: Result<MasterProduct> = Result.Success(mockMasterProduct)
    private var overrideResult: Result<StoreProductOverride> = Result.Success(mockOverride)
    private var updatePriceResult: Result<Unit> = Result.Success(Unit)
    private var updateStockResult: Result<Unit> = Result.Success(Unit)

    private val fakeMasterProductRepo = object : MasterProductRepository {
        override fun getAll(): Flow<List<MasterProduct>> = MutableStateFlow(listOf(mockMasterProduct))
        override suspend fun getById(id: String): Result<MasterProduct> = masterProductResult
        override suspend fun getByBarcode(barcode: String): Result<MasterProduct> = masterProductResult
        override fun search(query: String): Flow<List<MasterProduct>> = MutableStateFlow(emptyList())
        override suspend fun upsertFromSync(masterProduct: MasterProduct): Result<Unit> = Result.Success(Unit)
        override suspend fun getCount(): Int = 1
    }

    private val fakeOverrideRepo = object : StoreProductOverrideRepository {
        override fun getByStore(storeId: String): Flow<List<StoreProductOverride>> =
            MutableStateFlow(listOf(mockOverride))
        override suspend fun getOverride(masterProductId: String, storeId: String): Result<StoreProductOverride> =
            overrideResult
        override suspend fun upsertFromSync(override: StoreProductOverride): Result<Unit> = Result.Success(Unit)
        override suspend fun updateLocalPrice(masterProductId: String, storeId: String, price: Double?): Result<Unit> =
            updatePriceResult
        override suspend fun updateLocalStock(masterProductId: String, storeId: String, qty: Double): Result<Unit> =
            updateStockResult
    }

    private lateinit var viewModel: MasterProductOverrideViewModel

    @BeforeTest
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        viewModel = MasterProductOverrideViewModel(
            storeId = "store-001",
            masterProductRepository = fakeMasterProductRepo,
            storeProductOverrideRepository = fakeOverrideRepo,
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
        assertNull(state.masterProduct)
        assertFalse(state.isLoading)
        assertFalse(state.isSaving)
        assertNull(state.error)
        assertEquals("", state.localPriceInput)
    }

    // ── Load ───────────────────────────────────────────────────────────────────

    @Test
    fun `Load sets masterProduct in state`() = runTest {
        viewModel.handleIntentForTest(MasterProductOverrideIntent.Load("mp-001"))
        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.currentState
        assertNotNull(state.masterProduct)
        assertEquals("Espresso Blend", state.masterProduct!!.name)
    }

    @Test
    fun `Load sets effectivePrice from override localPrice`() = runTest {
        viewModel.handleIntentForTest(MasterProductOverrideIntent.Load("mp-001"))
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(1250.0, viewModel.currentState.effectivePrice)
    }

    @Test
    fun `Load uses basePrice when no override exists`() = runTest {
        overrideResult = Result.Error(Exception("Not found"))
        viewModel.handleIntentForTest(MasterProductOverrideIntent.Load("mp-001"))
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(1200.0, viewModel.currentState.effectivePrice)
    }

    @Test
    fun `Load clears isLoading after completion`() = runTest {
        viewModel.handleIntentForTest(MasterProductOverrideIntent.Load("mp-001"))
        testDispatcher.scheduler.advanceUntilIdle()

        assertFalse(viewModel.currentState.isLoading)
    }

    @Test
    fun `Load failure sets error and emits ShowError effect`() = runTest {
        masterProductResult = Result.Error(Exception("Product not found"))

        viewModel.effects.test {
            viewModel.handleIntentForTest(MasterProductOverrideIntent.Load("mp-999"))
            testDispatcher.scheduler.advanceUntilIdle()
            val effect = awaitItem()
            assertTrue(effect is MasterProductOverrideEffect.ShowError)
        }

        assertNotNull(viewModel.currentState.error)
    }

    // ── UpdateLocalPrice ───────────────────────────────────────────────────────

    @Test
    fun `UpdateLocalPrice updates localPriceInput`() = runTest {
        viewModel.state.test {
            awaitItem()
            viewModel.handleIntentForTest(MasterProductOverrideIntent.UpdateLocalPrice("1500.00"))
            val updated = awaitItem()
            assertEquals("1500.00", updated.localPriceInput)
        }
    }

    // ── UpdateLocalStock ───────────────────────────────────────────────────────

    @Test
    fun `UpdateLocalStock updates localStockInput`() = runTest {
        viewModel.state.test {
            awaitItem()
            viewModel.handleIntentForTest(MasterProductOverrideIntent.UpdateLocalStock("50.0"))
            val updated = awaitItem()
            assertEquals("50.0", updated.localStockInput)
        }
    }

    // ── Save ───────────────────────────────────────────────────────────────────

    @Test
    fun `Save emits SaveSuccess effect on success`() = runTest {
        updatePriceResult = Result.Success(Unit)
        updateStockResult = Result.Success(Unit)
        viewModel.handleIntentForTest(MasterProductOverrideIntent.Load("mp-001"))
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.effects.test {
            viewModel.handleIntentForTest(MasterProductOverrideIntent.Save)
            testDispatcher.scheduler.advanceUntilIdle()
            val effect = awaitItem()
            assertTrue(effect is MasterProductOverrideEffect.SaveSuccess)
        }
    }

    @Test
    fun `Save emits ShowError when price update fails`() = runTest {
        updatePriceResult = Result.Error(Exception("Price update failed"))
        updateStockResult = Result.Success(Unit)
        viewModel.handleIntentForTest(MasterProductOverrideIntent.Load("mp-001"))
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.effects.test {
            viewModel.handleIntentForTest(MasterProductOverrideIntent.Save)
            testDispatcher.scheduler.advanceUntilIdle()
            val effect = awaitItem()
            assertTrue(effect is MasterProductOverrideEffect.ShowError)
        }
    }

    @Test
    fun `Save without loaded product is a no-op`() = runTest {
        // No Load called — masterProduct is null
        viewModel.effects.test {
            viewModel.handleIntentForTest(MasterProductOverrideIntent.Save)
            testDispatcher.scheduler.advanceUntilIdle()
            expectNoEvents()
        }
    }

    // ── NavigateBack ───────────────────────────────────────────────────────────

    @Test
    fun `NavigateBack emits NavigateBack effect`() = runTest {
        viewModel.effects.test {
            viewModel.handleIntentForTest(MasterProductOverrideIntent.NavigateBack)
            testDispatcher.scheduler.advanceUntilIdle()
            val effect = awaitItem()
            assertTrue(effect is MasterProductOverrideEffect.NavigateBack)
        }
    }
}

// ─── Extension to expose handleIntent for testing ────────────────────────────

private suspend fun MasterProductOverrideViewModel.handleIntentForTest(intent: MasterProductOverrideIntent) =
    handleIntent(intent)
