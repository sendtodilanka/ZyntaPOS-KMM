package com.zyntasolutions.zyntapos.feature.inventory.pricing

import com.zyntasolutions.zyntapos.core.pagination.PageRequest
import com.zyntasolutions.zyntapos.core.pagination.PaginatedResult
import com.zyntasolutions.zyntapos.core.result.DatabaseException
import com.zyntasolutions.zyntapos.core.result.Result
import com.zyntasolutions.zyntapos.domain.model.PricingRule
import com.zyntasolutions.zyntapos.domain.model.Product
import com.zyntasolutions.zyntapos.domain.repository.PricingRuleRepository
import com.zyntasolutions.zyntapos.domain.repository.ProductRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class PricingRuleViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    // ── In-memory fakes ────────────────────────────────────────────────────────

    private class FakePricingRuleRepository : PricingRuleRepository {

        val rules = mutableListOf<PricingRule>()
        private val _allRules = MutableStateFlow<List<PricingRule>>(emptyList())
        var shouldFailUpsert: Boolean = false
        var shouldFailDelete: Boolean = false

        override fun getAllRules(): Flow<List<PricingRule>> = _allRules

        override fun getRulesForProduct(productId: String): Flow<List<PricingRule>> =
            _allRules.map { it.filter { r -> r.productId == productId } }

        override fun getActiveRulesForProduct(productId: String, storeId: String): Flow<List<PricingRule>> =
            _allRules.map { it.filter { r -> r.productId == productId && r.isActive } }

        override suspend fun getEffectiveRule(productId: String, storeId: String, nowEpochMs: Long): Result<PricingRule?> =
            Result.Success(rules.firstOrNull { it.productId == productId && it.isActive })

        override suspend fun upsert(rule: PricingRule): Result<Unit> {
            if (shouldFailUpsert) return Result.Error(DatabaseException("Upsert failed"))
            rules.removeAll { it.id == rule.id }
            rules.add(rule)
            _allRules.value = rules.toList()
            return Result.Success(Unit)
        }

        override suspend fun delete(ruleId: String): Result<Unit> {
            if (shouldFailDelete) return Result.Error(DatabaseException("Delete failed"))
            rules.removeAll { it.id == ruleId }
            _allRules.value = rules.toList()
            return Result.Success(Unit)
        }
    }

    private class FakeProductRepository : ProductRepository {

        val products = mutableListOf<Product>()
        private val _allProducts = MutableStateFlow<List<Product>>(emptyList())

        override fun getAll(): Flow<List<Product>> = _allProducts

        override suspend fun getById(id: String): Result<Product> {
            val p = products.find { it.id == id } ?: return Result.Error(DatabaseException("Not found"))
            return Result.Success(p)
        }

        override fun search(query: String, categoryId: String?): Flow<List<Product>> =
            _allProducts.map { it.filter { p -> p.name.contains(query, ignoreCase = true) } }

        override suspend fun insert(product: Product): Result<Unit> {
            products.add(product)
            _allProducts.value = products.toList()
            return Result.Success(Unit)
        }

        override suspend fun update(product: Product): Result<Unit> {
            val idx = products.indexOfFirst { it.id == product.id }
            if (idx >= 0) products[idx] = product
            _allProducts.value = products.toList()
            return Result.Success(Unit)
        }

        override suspend fun delete(id: String): Result<Unit> {
            products.removeAll { it.id == id }
            _allProducts.value = products.toList()
            return Result.Success(Unit)
        }

        override suspend fun getByBarcode(barcode: String): Result<Product> =
            Result.Error(DatabaseException("Not found"))

        override suspend fun getCount(): Int = products.size

        override suspend fun getPage(pageRequest: PageRequest, categoryId: String?, searchQuery: String?): PaginatedResult<Product> =
            PaginatedResult(items = products, totalCount = products.size.toLong(), hasMore = false)
    }

    // ── Fixture builders ───────────────────────────────────────────────────────

    private fun buildRule(
        id: String = "rule-01",
        productId: String = "prod-01",
        storeId: String? = null,
        price: Double = 99.9,
        isActive: Boolean = true,
    ) = PricingRule(
        id = id,
        productId = productId,
        storeId = storeId,
        price = price,
        isActive = isActive,
        description = "Test rule",
        createdAt = 0L,
        updatedAt = 0L,
    )

    // ── Subject ────────────────────────────────────────────────────────────────

    private lateinit var fakePricingRepo: FakePricingRuleRepository
    private lateinit var fakeProductRepo: FakeProductRepository
    private lateinit var viewModel: PricingRuleViewModel

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        fakePricingRepo = FakePricingRuleRepository()
        fakeProductRepo = FakeProductRepository()
        viewModel = PricingRuleViewModel(fakePricingRepo, fakeProductRepo)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // ── Initial state ──────────────────────────────────────────────────────────

    @Test
    fun `initial state has empty rules and no dialog`() = runTest {
        testDispatcher.scheduler.advanceUntilIdle()
        val s = viewModel.state.value
        assertTrue(s.rules.isEmpty())
        assertFalse(s.showDialog)
        assertNull(s.editingRule)
        assertNull(s.error)
        assertNull(s.successMessage)
        assertFalse(s.isSaving)
    }

    // ── Reactive rule list ─────────────────────────────────────────────────────

    @Test
    fun `rules list populated from repository on init`() = runTest {
        fakePricingRepo.upsert(buildRule())
        fakePricingRepo.upsert(buildRule(id = "rule-02"))
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(2, viewModel.state.value.rules.size)
    }

    // ── Filter ────────────────────────────────────────────────────────────────

    @Test
    fun `FilterByProduct updates filterProductId`() = runTest {
        viewModel.dispatch(PricingRuleIntent.FilterByProduct("prod-01"))
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals("prod-01", viewModel.state.value.filterProductId)
    }

    @Test
    fun `SetActiveOnlyFilter updates filterActiveOnly`() = runTest {
        viewModel.dispatch(PricingRuleIntent.SetActiveOnlyFilter(false))
        testDispatcher.scheduler.advanceUntilIdle()
        assertFalse(viewModel.state.value.filterActiveOnly)
    }

    // ── OpenDialog / DismissDialog ─────────────────────────────────────────────

    @Test
    fun `OpenDialog with null opens create dialog with blank form`() = runTest {
        viewModel.dispatch(PricingRuleIntent.OpenDialog(null))
        testDispatcher.scheduler.advanceUntilIdle()

        val s = viewModel.state.value
        assertTrue(s.showDialog)
        assertNull(s.editingRule)
        assertEquals("", s.formProductId)
        assertEquals("", s.formPrice)
        assertTrue(s.formIsActive)
    }

    @Test
    fun `OpenDialog with existing rule populates form`() = runTest {
        val rule = buildRule(productId = "prod-01", storeId = "store-01", price = 88.0)
        viewModel.dispatch(PricingRuleIntent.OpenDialog(rule))
        testDispatcher.scheduler.advanceUntilIdle()

        val s = viewModel.state.value
        assertTrue(s.showDialog)
        assertEquals(rule, s.editingRule)
        assertEquals("prod-01", s.formProductId)
        assertEquals("store-01", s.formStoreId)
        assertEquals("88.0", s.formPrice)
    }

    @Test
    fun `DismissDialog closes dialog and clears editingRule`() = runTest {
        val rule = buildRule()
        viewModel.dispatch(PricingRuleIntent.OpenDialog(rule))
        viewModel.dispatch(PricingRuleIntent.DismissDialog)
        testDispatcher.scheduler.advanceUntilIdle()

        val s = viewModel.state.value
        assertFalse(s.showDialog)
        assertNull(s.editingRule)
    }

    // ── UpdateField ────────────────────────────────────────────────────────────

    @Test
    fun `UpdateField PRODUCT_ID updates formProductId`() = runTest {
        viewModel.dispatch(PricingRuleIntent.UpdateField(PricingField.PRODUCT_ID, "prod-99"))
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals("prod-99", viewModel.state.value.formProductId)
    }

    @Test
    fun `UpdateField PRICE updates formPrice`() = runTest {
        viewModel.dispatch(PricingRuleIntent.UpdateField(PricingField.PRICE, "55.5"))
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals("55.5", viewModel.state.value.formPrice)
    }

    @Test
    fun `UpdateField STORE_ID updates formStoreId`() = runTest {
        viewModel.dispatch(PricingRuleIntent.UpdateField(PricingField.STORE_ID, "store-02"))
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals("store-02", viewModel.state.value.formStoreId)
    }

    @Test
    fun `UpdateField DESCRIPTION updates formDescription`() = runTest {
        viewModel.dispatch(PricingRuleIntent.UpdateField(PricingField.DESCRIPTION, "Holiday sale"))
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals("Holiday sale", viewModel.state.value.formDescription)
    }

    // ── SetActive ──────────────────────────────────────────────────────────────

    @Test
    fun `SetActive updates formIsActive`() = runTest {
        viewModel.dispatch(PricingRuleIntent.SetActive(false))
        testDispatcher.scheduler.advanceUntilIdle()
        assertFalse(viewModel.state.value.formIsActive)

        viewModel.dispatch(PricingRuleIntent.SetActive(true))
        testDispatcher.scheduler.advanceUntilIdle()
        assertTrue(viewModel.state.value.formIsActive)
    }

    // ── SaveRule validation ────────────────────────────────────────────────────

    @Test
    fun `SaveRule shows error when productId is blank`() = runTest {
        viewModel.dispatch(PricingRuleIntent.UpdateField(PricingField.PRICE, "50.0"))
        viewModel.dispatch(PricingRuleIntent.SaveRule)
        testDispatcher.scheduler.advanceUntilIdle()

        assertNotNull(viewModel.state.value.error)
        assertTrue(viewModel.state.value.error!!.contains("Product", ignoreCase = true))
    }

    @Test
    fun `SaveRule shows error when price is blank`() = runTest {
        viewModel.dispatch(PricingRuleIntent.UpdateField(PricingField.PRODUCT_ID, "prod-01"))
        viewModel.dispatch(PricingRuleIntent.SaveRule)
        testDispatcher.scheduler.advanceUntilIdle()

        assertNotNull(viewModel.state.value.error)
        assertTrue(viewModel.state.value.error!!.contains("Price", ignoreCase = true))
    }

    @Test
    fun `SaveRule shows error when price is negative`() = runTest {
        viewModel.dispatch(PricingRuleIntent.UpdateField(PricingField.PRODUCT_ID, "prod-01"))
        viewModel.dispatch(PricingRuleIntent.UpdateField(PricingField.PRICE, "-5.0"))
        viewModel.dispatch(PricingRuleIntent.SaveRule)
        testDispatcher.scheduler.advanceUntilIdle()

        assertNotNull(viewModel.state.value.error)
    }

    @Test
    fun `SaveRule creates new rule and shows success`() = runTest {
        viewModel.dispatch(PricingRuleIntent.UpdateField(PricingField.PRODUCT_ID, "prod-01"))
        viewModel.dispatch(PricingRuleIntent.UpdateField(PricingField.PRICE, "75.0"))
        viewModel.dispatch(PricingRuleIntent.UpdateField(PricingField.DESCRIPTION, "Test"))
        viewModel.dispatch(PricingRuleIntent.SaveRule)
        testDispatcher.scheduler.advanceUntilIdle()

        assertNull(viewModel.state.value.error)
        assertNotNull(viewModel.state.value.successMessage)
        assertTrue(viewModel.state.value.successMessage!!.contains("created", ignoreCase = true))
        assertFalse(viewModel.state.value.showDialog)
        assertEquals(1, fakePricingRepo.rules.size)
        assertEquals(75.0, fakePricingRepo.rules.first().price)
    }

    @Test
    fun `SaveRule updates existing rule and shows success`() = runTest {
        val existing = buildRule(id = "rule-01", productId = "prod-01", price = 50.0)
        fakePricingRepo.rules.add(existing)

        viewModel.dispatch(PricingRuleIntent.OpenDialog(existing))
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.dispatch(PricingRuleIntent.UpdateField(PricingField.PRICE, "120.0"))
        viewModel.dispatch(PricingRuleIntent.SaveRule)
        testDispatcher.scheduler.advanceUntilIdle()

        assertNotNull(viewModel.state.value.successMessage)
        assertTrue(viewModel.state.value.successMessage!!.contains("updated", ignoreCase = true))

        val updated = fakePricingRepo.rules.find { it.id == "rule-01" }
        assertNotNull(updated)
        assertEquals(120.0, updated.price)
    }

    @Test
    fun `SaveRule propagates repository failure to error state`() = runTest {
        fakePricingRepo.shouldFailUpsert = true
        viewModel.dispatch(PricingRuleIntent.UpdateField(PricingField.PRODUCT_ID, "prod-01"))
        viewModel.dispatch(PricingRuleIntent.UpdateField(PricingField.PRICE, "50.0"))
        viewModel.dispatch(PricingRuleIntent.SaveRule)
        testDispatcher.scheduler.advanceUntilIdle()

        assertNotNull(viewModel.state.value.error)
        assertFalse(viewModel.state.value.isSaving)
    }

    // ── Zero price accepted ────────────────────────────────────────────────────

    @Test
    fun `SaveRule accepts zero price`() = runTest {
        viewModel.dispatch(PricingRuleIntent.UpdateField(PricingField.PRODUCT_ID, "prod-01"))
        viewModel.dispatch(PricingRuleIntent.UpdateField(PricingField.PRICE, "0.0"))
        viewModel.dispatch(PricingRuleIntent.SaveRule)
        testDispatcher.scheduler.advanceUntilIdle()

        assertNull(viewModel.state.value.error)
        assertEquals(0.0, fakePricingRepo.rules.first().price)
    }

    // ── Delete ────────────────────────────────────────────────────────────────

    @Test
    fun `ConfirmDelete sets deleteTarget`() = runTest {
        val rule = buildRule()
        viewModel.dispatch(PricingRuleIntent.ConfirmDelete(rule))
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals(rule, viewModel.state.value.deleteTarget)
    }

    @Test
    fun `DismissDelete clears deleteTarget`() = runTest {
        val rule = buildRule()
        viewModel.dispatch(PricingRuleIntent.ConfirmDelete(rule))
        viewModel.dispatch(PricingRuleIntent.DismissDelete)
        testDispatcher.scheduler.advanceUntilIdle()
        assertNull(viewModel.state.value.deleteTarget)
    }

    @Test
    fun `ExecuteDelete removes rule from repository and shows success`() = runTest {
        val rule = buildRule(id = "rule-01")
        fakePricingRepo.rules.add(rule)

        viewModel.dispatch(PricingRuleIntent.ConfirmDelete(rule))
        viewModel.dispatch(PricingRuleIntent.ExecuteDelete)
        testDispatcher.scheduler.advanceUntilIdle()

        assertNull(viewModel.state.value.deleteTarget)
        assertNotNull(viewModel.state.value.successMessage)
        assertTrue(fakePricingRepo.rules.isEmpty())
    }

    @Test
    fun `ExecuteDelete does nothing when deleteTarget is null`() = runTest {
        fakePricingRepo.rules.add(buildRule())
        viewModel.dispatch(PricingRuleIntent.ExecuteDelete)
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(1, fakePricingRepo.rules.size)
    }

    @Test
    fun `ExecuteDelete propagates repository failure to error state`() = runTest {
        fakePricingRepo.shouldFailDelete = true
        val rule = buildRule()
        viewModel.dispatch(PricingRuleIntent.ConfirmDelete(rule))
        viewModel.dispatch(PricingRuleIntent.ExecuteDelete)
        testDispatcher.scheduler.advanceUntilIdle()

        assertNotNull(viewModel.state.value.error)
        assertNull(viewModel.state.value.deleteTarget)
    }

    // ── DismissError / DismissSuccess ──────────────────────────────────────────

    @Test
    fun `DismissError clears error`() = runTest {
        fakePricingRepo.shouldFailUpsert = true
        viewModel.dispatch(PricingRuleIntent.UpdateField(PricingField.PRODUCT_ID, "prod-01"))
        viewModel.dispatch(PricingRuleIntent.UpdateField(PricingField.PRICE, "50.0"))
        viewModel.dispatch(PricingRuleIntent.SaveRule)
        testDispatcher.scheduler.advanceUntilIdle()
        assertNotNull(viewModel.state.value.error)

        viewModel.dispatch(PricingRuleIntent.DismissError)
        testDispatcher.scheduler.advanceUntilIdle()
        assertNull(viewModel.state.value.error)
    }

    @Test
    fun `DismissSuccess clears successMessage`() = runTest {
        viewModel.dispatch(PricingRuleIntent.UpdateField(PricingField.PRODUCT_ID, "prod-01"))
        viewModel.dispatch(PricingRuleIntent.UpdateField(PricingField.PRICE, "50.0"))
        viewModel.dispatch(PricingRuleIntent.SaveRule)
        testDispatcher.scheduler.advanceUntilIdle()
        assertNotNull(viewModel.state.value.successMessage)

        viewModel.dispatch(PricingRuleIntent.DismissSuccess)
        testDispatcher.scheduler.advanceUntilIdle()
        assertNull(viewModel.state.value.successMessage)
    }
}
