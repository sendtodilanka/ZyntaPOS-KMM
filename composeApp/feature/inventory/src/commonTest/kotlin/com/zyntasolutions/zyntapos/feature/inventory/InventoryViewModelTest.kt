package com.zyntasolutions.zyntapos.feature.inventory

import app.cash.turbine.test
import com.zyntasolutions.zyntapos.core.result.DatabaseException
import com.zyntasolutions.zyntapos.core.result.Result
import com.zyntasolutions.zyntapos.domain.model.Category
import com.zyntasolutions.zyntapos.domain.model.Product
import com.zyntasolutions.zyntapos.domain.model.StockAdjustment
import com.zyntasolutions.zyntapos.domain.model.SyncStatus
import com.zyntasolutions.zyntapos.domain.repository.CategoryRepository
import com.zyntasolutions.zyntapos.domain.repository.ProductRepository
import com.zyntasolutions.zyntapos.domain.repository.StockRepository
import com.zyntasolutions.zyntapos.domain.usecase.inventory.AdjustStockUseCase
import com.zyntasolutions.zyntapos.domain.usecase.inventory.CreateProductUseCase
import com.zyntasolutions.zyntapos.domain.usecase.inventory.SearchProductsUseCase
import com.zyntasolutions.zyntapos.domain.usecase.inventory.UpdateProductUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.time.Clock
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import com.zyntasolutions.zyntapos.domain.repository.AuthRepository
import com.zyntasolutions.zyntapos.domain.model.User
import com.zyntasolutions.zyntapos.domain.model.Role

// ─────────────────────────────────────────────────────────────────────────────
// InventoryViewModelTest
// Tests InventoryViewModel MVI state transitions using hand-rolled fakes.
// ─────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalCoroutinesApi::class)
class InventoryViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private val currentUserId = "user-001"

    private val fakeAuthRepository = object : AuthRepository {
        private val _session = MutableStateFlow<User?>(
            User(
                id = "user-001", name = "Test User", email = "test@zynta.com",
                role = Role.CASHIER, storeId = "store-001", isActive = true,
                pinHash = null, createdAt = Clock.System.now(), updatedAt = Clock.System.now(),
            )
        )
        override fun getSession(): Flow<User?> = _session
        override suspend fun login(email: String, password: String): Result<User> =
            Result.Success(_session.value!!)
        override suspend fun logout() { _session.value = null }
        override suspend fun refreshToken(): Result<Unit> = Result.Success(Unit)
        override suspend fun updatePin(userId: String, pin: String): Result<Unit> =
            Result.Success(Unit)
    }

    // ── Fake backing state ────────────────────────────────────────────────────

    private val productsFlow = MutableStateFlow<List<Product>>(emptyList())
    private val categoriesFlow = MutableStateFlow<List<Category>>(emptyList())
    private val adjustmentsFlow = MutableStateFlow<List<StockAdjustment>>(emptyList())
    private var shouldFailInsert = false
    private var shouldFailDelete = false
    private var shouldFailAdjustStock = false

    private val now = Clock.System.now()

    private val testProduct = Product(
        id = "prod-001",
        name = "Test Widget",
        categoryId = "cat-001",
        unitId = "unit-001",
        price = 9.99,
        costPrice = 5.00,
        stockQty = 100.0,
        minStockQty = 10.0,
        createdAt = now,
        updatedAt = now,
    )

    private val testCategory = Category(
        id = "cat-001",
        name = "Electronics",
        displayOrder = 0,
    )

    // ── Fake ProductRepository ────────────────────────────────────────────────

    private val fakeProductRepository = object : ProductRepository {
        override fun getAll(): Flow<List<Product>> = productsFlow

        override suspend fun getById(id: String): Result<Product> {
            val p = productsFlow.value.firstOrNull { it.id == id }
                ?: return Result.Error(DatabaseException("Product '$id' not found"))
            return Result.Success(p)
        }

        override fun search(query: String, categoryId: String?): Flow<List<Product>> =
            productsFlow.map { list ->
                list.filter { p ->
                    (query.isBlank() || p.name.contains(query, ignoreCase = true)) &&
                        (categoryId == null || p.categoryId == categoryId)
                }
            }

        override suspend fun getByBarcode(barcode: String): Result<Product> {
            val p = productsFlow.value.firstOrNull { it.barcode == barcode }
                ?: return Result.Error(DatabaseException("Barcode not found"))
            return Result.Success(p)
        }

        override suspend fun insert(product: Product): Result<Unit> {
            if (shouldFailInsert) return Result.Error(DatabaseException("Insert failed"))
            productsFlow.value = productsFlow.value + product
            return Result.Success(Unit)
        }

        override suspend fun update(product: Product): Result<Unit> {
            val idx = productsFlow.value.indexOfFirst { it.id == product.id }
            if (idx == -1) return Result.Error(DatabaseException("Not found"))
            val updated = productsFlow.value.toMutableList().also { it[idx] = product }
            productsFlow.value = updated
            return Result.Success(Unit)
        }

        override suspend fun delete(id: String): Result<Unit> {
            if (shouldFailDelete) return Result.Error(DatabaseException("Delete failed"))
            productsFlow.value = productsFlow.value.filter { it.id != id }
            return Result.Success(Unit)
        }

        override suspend fun getCount(): Int = productsFlow.value.size
    }

    // ── Fake CategoryRepository ───────────────────────────────────────────────

    private val fakeCategoryRepository = object : CategoryRepository {
        override fun getAll(): Flow<List<Category>> = categoriesFlow

        override suspend fun getById(id: String): Result<Category> {
            val cat = categoriesFlow.value.firstOrNull { it.id == id }
                ?: return Result.Error(DatabaseException("Category '$id' not found"))
            return Result.Success(cat)
        }

        override suspend fun insert(category: Category): Result<Unit> {
            categoriesFlow.value = categoriesFlow.value + category
            return Result.Success(Unit)
        }

        override suspend fun update(category: Category): Result<Unit> {
            val idx = categoriesFlow.value.indexOfFirst { it.id == category.id }
            if (idx == -1) return Result.Error(DatabaseException("Not found"))
            val updated = categoriesFlow.value.toMutableList().also { it[idx] = category }
            categoriesFlow.value = updated
            return Result.Success(Unit)
        }

        override suspend fun delete(id: String): Result<Unit> {
            categoriesFlow.value = categoriesFlow.value.filter { it.id != id }
            return Result.Success(Unit)
        }

        override fun getTree(): Flow<List<Category>> = categoriesFlow
    }

    // ── Fake StockRepository ──────────────────────────────────────────────────

    private val fakeStockRepository = object : StockRepository {
        override suspend fun adjustStock(adjustment: StockAdjustment): Result<Unit> {
            if (shouldFailAdjustStock) return Result.Error(DatabaseException("Adjust stock failed"))
            adjustmentsFlow.value = adjustmentsFlow.value + adjustment
            return Result.Success(Unit)
        }

        override fun getMovements(productId: String): Flow<List<StockAdjustment>> =
            adjustmentsFlow.map { list -> list.filter { it.productId == productId } }

        override fun getAlerts(threshold: Double?): Flow<List<Product>> =
            productsFlow.map { list ->
                list.filter { p ->
                    val limit = threshold ?: p.minStockQty
                    p.stockQty < limit
                }
            }
    }

    // ── Use cases wired to fakes ──────────────────────────────────────────────

    private val searchProductsUseCase = SearchProductsUseCase(fakeProductRepository)
    private val createProductUseCase = CreateProductUseCase(fakeProductRepository)
    private val updateProductUseCase = UpdateProductUseCase(fakeProductRepository)
    private val adjustStockUseCase = AdjustStockUseCase(fakeStockRepository)

    private lateinit var viewModel: InventoryViewModel

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        productsFlow.value = emptyList()
        categoriesFlow.value = emptyList()
        adjustmentsFlow.value = emptyList()
        shouldFailInsert = false
        shouldFailDelete = false
        shouldFailAdjustStock = false

        viewModel = InventoryViewModel(
            productRepository = fakeProductRepository,
            categoryRepository = fakeCategoryRepository,
            searchProductsUseCase = searchProductsUseCase,
            createProductUseCase = createProductUseCase,
            updateProductUseCase = updateProductUseCase,
            adjustStockUseCase = adjustStockUseCase,
            authRepository = fakeAuthRepository,
        )
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // ── Initial state ─────────────────────────────────────────────────────────

    @Test
    fun `initial state has empty products list and not loading`() = runTest {
        testDispatcher.scheduler.advanceUntilIdle()
        val state = viewModel.state.value
        assertTrue(state.products.isEmpty())
        assertFalse(state.isLoading)
        assertNull(state.error)
        assertEquals(ViewMode.LIST, state.viewMode)
        assertEquals(StockFilter.ALL, state.stockFilter)
    }

    // ── Reactive product list ─────────────────────────────────────────────────

    @Test
    fun `products from repository are reflected in state reactively`() = runTest {
        productsFlow.value = listOf(testProduct)
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(1, viewModel.state.value.products.size)
        assertEquals("Test Widget", viewModel.state.value.products.first().name)
    }

    @Test
    fun `categories from repository are reflected in state reactively`() = runTest {
        categoriesFlow.value = listOf(testCategory)
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(1, viewModel.state.value.categories.size)
        assertEquals("Electronics", viewModel.state.value.categories.first().name)
    }

    // ── Search ────────────────────────────────────────────────────────────────

    @Test
    fun `SearchQueryChanged updates searchQuery in state`() = runTest {
        viewModel.dispatch(InventoryIntent.SearchQueryChanged("Widget"))
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals("Widget", viewModel.state.value.searchQuery)
    }

    @Test
    fun `SelectCategory updates selectedCategoryId in state`() = runTest {
        viewModel.dispatch(InventoryIntent.SelectCategory("cat-001"))
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals("cat-001", viewModel.state.value.selectedCategoryId)
    }

    // ── View mode and sort ────────────────────────────────────────────────────

    @Test
    fun `ToggleViewMode switches between LIST and GRID`() = runTest {
        assertEquals(ViewMode.LIST, viewModel.state.value.viewMode)

        viewModel.dispatch(InventoryIntent.ToggleViewMode)
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals(ViewMode.GRID, viewModel.state.value.viewMode)

        viewModel.dispatch(InventoryIntent.ToggleViewMode)
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals(ViewMode.LIST, viewModel.state.value.viewMode)
    }

    @Test
    fun `SortByColumn sets sortColumn and toggles direction on repeated call for same column`() = runTest {
        viewModel.dispatch(InventoryIntent.SortByColumn("price"))
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals("price", viewModel.state.value.sortColumn)
        assertEquals(SortDir.ASC, viewModel.state.value.sortDirection)

        viewModel.dispatch(InventoryIntent.SortByColumn("price"))
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals("price", viewModel.state.value.sortColumn)
        assertEquals(SortDir.DESC, viewModel.state.value.sortDirection)
    }

    // ── Product create ────────────────────────────────────────────────────────

    @Test
    fun `SaveProduct with valid form creates new product and emits ShowSuccess then NavigateToList`() = runTest {
        viewModel.dispatch(InventoryIntent.UpdateFormField("name", "New Product"))
        viewModel.dispatch(InventoryIntent.UpdateFormField("categoryId", "cat-001"))
        viewModel.dispatch(InventoryIntent.UpdateFormField("unitId", "unit-001"))
        viewModel.dispatch(InventoryIntent.UpdateFormField("price", "19.99"))
        viewModel.dispatch(InventoryIntent.UpdateFormField("costPrice", "10.00"))
        viewModel.dispatch(InventoryIntent.UpdateFormField("stockQty", "50"))
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.effects.test {
            viewModel.dispatch(InventoryIntent.SaveProduct)
            testDispatcher.scheduler.advanceUntilIdle()

            val effectSuccess = awaitItem()
            assertTrue(effectSuccess is InventoryEffect.ShowSuccess)
            val effectNav = awaitItem()
            assertTrue(effectNav is InventoryEffect.NavigateToList)
            cancelAndIgnoreRemainingEvents()
        }

        assertEquals(1, productsFlow.value.size)
        assertEquals("New Product", productsFlow.value.first().name)
    }

    @Test
    fun `SaveProduct with blank name sets validation error and does not persist`() = runTest {
        // Leave name blank but set other required fields
        viewModel.dispatch(InventoryIntent.UpdateFormField("categoryId", "cat-001"))
        viewModel.dispatch(InventoryIntent.UpdateFormField("unitId", "unit-001"))
        viewModel.dispatch(InventoryIntent.UpdateFormField("price", "10.00"))
        viewModel.dispatch(InventoryIntent.SaveProduct)
        testDispatcher.scheduler.advanceUntilIdle()

        assertNotNull(viewModel.state.value.editFormState.validationErrors["name"])
        assertTrue(productsFlow.value.isEmpty())
    }

    // ── Product delete ────────────────────────────────────────────────────────

    @Test
    fun `DeleteProduct emits ShowSuccess and removes product`() = runTest {
        productsFlow.value = listOf(testProduct)
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.effects.test {
            viewModel.dispatch(InventoryIntent.DeleteProduct(testProduct.id))
            testDispatcher.scheduler.advanceUntilIdle()

            val effect = awaitItem()
            assertTrue(effect is InventoryEffect.ShowSuccess)
            cancelAndIgnoreRemainingEvents()
        }

        assertTrue(productsFlow.value.isEmpty())
    }

    @Test
    fun `DeleteProduct on failure emits ShowError`() = runTest {
        shouldFailDelete = true
        productsFlow.value = listOf(testProduct)
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.effects.test {
            viewModel.dispatch(InventoryIntent.DeleteProduct(testProduct.id))
            testDispatcher.scheduler.advanceUntilIdle()

            val effect = awaitItem()
            assertTrue(effect is InventoryEffect.ShowError)
            cancelAndIgnoreRemainingEvents()
        }
    }

    // ── Stock Adjustment ──────────────────────────────────────────────────────

    @Test
    fun `OpenStockAdjustment sets stockAdjustmentTarget in state`() = runTest {
        productsFlow.value = listOf(testProduct)
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.dispatch(InventoryIntent.OpenStockAdjustment(testProduct))
        testDispatcher.scheduler.advanceUntilIdle()

        assertNotNull(viewModel.state.value.stockAdjustmentTarget)
        assertEquals(testProduct.id, viewModel.state.value.stockAdjustmentTarget?.id)
    }

    @Test
    fun `DismissStockAdjustment clears stockAdjustmentTarget`() = runTest {
        productsFlow.value = listOf(testProduct)
        testDispatcher.scheduler.advanceUntilIdle()
        viewModel.dispatch(InventoryIntent.OpenStockAdjustment(testProduct))
        testDispatcher.scheduler.advanceUntilIdle()
        assertNotNull(viewModel.state.value.stockAdjustmentTarget)

        viewModel.dispatch(InventoryIntent.DismissStockAdjustment)
        testDispatcher.scheduler.advanceUntilIdle()

        assertNull(viewModel.state.value.stockAdjustmentTarget)
    }

    @Test
    fun `SubmitStockAdjustment on success emits ShowSuccess and clears target`() = runTest {
        productsFlow.value = listOf(testProduct)
        testDispatcher.scheduler.advanceUntilIdle()
        viewModel.dispatch(InventoryIntent.OpenStockAdjustment(testProduct))
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.effects.test {
            viewModel.dispatch(
                InventoryIntent.SubmitStockAdjustment(
                    type = StockAdjustment.Type.INCREASE,
                    quantity = 20.0,
                    reason = "New stock received",
                )
            )
            testDispatcher.scheduler.advanceUntilIdle()

            val effect = awaitItem()
            assertTrue(effect is InventoryEffect.ShowSuccess)
            cancelAndIgnoreRemainingEvents()
        }

        assertNull(viewModel.state.value.stockAdjustmentTarget)
        assertEquals(1, adjustmentsFlow.value.size)
        assertEquals(20.0, adjustmentsFlow.value.first().quantity)
    }

    @Test
    fun `SubmitStockAdjustment on failure emits ShowError`() = runTest {
        shouldFailAdjustStock = true
        productsFlow.value = listOf(testProduct)
        testDispatcher.scheduler.advanceUntilIdle()
        viewModel.dispatch(InventoryIntent.OpenStockAdjustment(testProduct))
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.effects.test {
            viewModel.dispatch(
                InventoryIntent.SubmitStockAdjustment(
                    type = StockAdjustment.Type.DECREASE,
                    quantity = 5.0,
                    reason = "Damaged goods",
                )
            )
            testDispatcher.scheduler.advanceUntilIdle()

            val effect = awaitItem()
            assertTrue(effect is InventoryEffect.ShowError)
            cancelAndIgnoreRemainingEvents()
        }
    }

    // ── Barcode Generator dialog ───────────────────────────────────────────────

    @Test
    fun `OpenBarcodeGenerator sets barcodeGeneratorTarget and DismissBarcodeGenerator clears it`() = runTest {
        viewModel.dispatch(InventoryIntent.OpenBarcodeGenerator(testProduct))
        testDispatcher.scheduler.advanceUntilIdle()
        assertNotNull(viewModel.state.value.barcodeGeneratorTarget)
        assertEquals(testProduct.id, viewModel.state.value.barcodeGeneratorTarget?.id)

        viewModel.dispatch(InventoryIntent.DismissBarcodeGenerator)
        testDispatcher.scheduler.advanceUntilIdle()
        assertNull(viewModel.state.value.barcodeGeneratorTarget)
    }

    // ── SelectProduct / form load ─────────────────────────────────────────────

    @Test
    fun `SelectProduct with null ID opens new-product form and emits NavigateToDetail`() = runTest {
        viewModel.effects.test {
            viewModel.dispatch(InventoryIntent.SelectProduct(null))
            testDispatcher.scheduler.advanceUntilIdle()

            val effect = awaitItem()
            assertTrue(effect is InventoryEffect.NavigateToDetail)
            assertNull((effect as InventoryEffect.NavigateToDetail).productId)
            cancelAndIgnoreRemainingEvents()
        }

        assertNull(viewModel.state.value.selectedProduct)
        assertFalse(viewModel.state.value.editFormState.isEditing)
    }

    @Test
    fun `SelectProduct with valid ID loads product into form and emits NavigateToDetail`() = runTest {
        productsFlow.value = listOf(testProduct)
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.effects.test {
            viewModel.dispatch(InventoryIntent.SelectProduct(testProduct.id))
            testDispatcher.scheduler.advanceUntilIdle()

            val effect = awaitItem()
            assertTrue(effect is InventoryEffect.NavigateToDetail)
            assertEquals(testProduct.id, (effect as InventoryEffect.NavigateToDetail).productId)
            cancelAndIgnoreRemainingEvents()
        }

        val state = viewModel.state.value
        assertNotNull(state.selectedProduct)
        assertEquals("Test Widget", state.editFormState.name)
        assertTrue(state.editFormState.isEditing)
    }

    // ── UI Feedback ───────────────────────────────────────────────────────────

    @Test
    fun `ClearForm resets edit form state to defaults`() = runTest {
        viewModel.dispatch(InventoryIntent.UpdateFormField("name", "Temp Product"))
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals("Temp Product", viewModel.state.value.editFormState.name)

        viewModel.dispatch(InventoryIntent.ClearForm)
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals("", viewModel.state.value.editFormState.name)
    }

    @Test
    fun `DismissError clears error in state`() = runTest {
        viewModel.dispatch(InventoryIntent.DismissError)
        testDispatcher.scheduler.advanceUntilIdle()
        assertNull(viewModel.state.value.error)
    }

    @Test
    fun `DismissSuccess clears successMessage in state`() = runTest {
        viewModel.dispatch(InventoryIntent.DismissSuccess)
        testDispatcher.scheduler.advanceUntilIdle()
        assertNull(viewModel.state.value.successMessage)
    }
}
