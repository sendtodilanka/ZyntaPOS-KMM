package com.zyntasolutions.zyntapos.feature.coupons

import app.cash.turbine.test
import com.zyntasolutions.zyntapos.core.result.DatabaseException
import com.zyntasolutions.zyntapos.core.result.Result
import com.zyntasolutions.zyntapos.domain.model.Category
import com.zyntasolutions.zyntapos.domain.model.Coupon
import com.zyntasolutions.zyntapos.domain.model.CouponUsage
import com.zyntasolutions.zyntapos.domain.model.DiscountType
import com.zyntasolutions.zyntapos.domain.model.Promotion
import com.zyntasolutions.zyntapos.domain.repository.CategoryRepository
import com.zyntasolutions.zyntapos.domain.repository.CouponRepository
import com.zyntasolutions.zyntapos.domain.usecase.coupons.SaveCouponUseCase
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
import com.zyntasolutions.zyntapos.core.analytics.AnalyticsTracker
import kotlin.test.assertTrue

// ─────────────────────────────────────────────────────────────────────────────
// CouponViewModelTest
// Tests CouponViewModel MVI state transitions using hand-rolled fakes.
// ─────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalCoroutinesApi::class)
class CouponViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    private val noOpAnalytics = object : AnalyticsTracker {
        override fun logEvent(name: String, params: Map<String, String>) = Unit
        override fun logScreenView(screenName: String, screenClass: String) = Unit
        override fun setUserId(userId: String?) = Unit
        override fun setUserProperty(name: String, value: String) = Unit
    }

    // ── Fake CouponRepository ─────────────────────────────────────────────────

    private val couponsFlow = MutableStateFlow<List<Coupon>>(emptyList())
    private var shouldFailInsert = false
    private var shouldFailDelete = false

    private val fakeCouponRepository = object : CouponRepository {
        override fun getAll(): Flow<List<Coupon>> = couponsFlow

        override fun getActiveCoupons(nowEpochMillis: Long): Flow<List<Coupon>> =
            couponsFlow.map { list ->
                list.filter { it.isActive && it.validFrom <= nowEpochMillis && it.validTo >= nowEpochMillis }
            }

        override suspend fun getByCode(code: String): Result<Coupon> {
            val coupon = couponsFlow.value.firstOrNull { it.code == code }
                ?: return Result.Error(DatabaseException("Coupon '$code' not found"))
            return Result.Success(coupon)
        }

        override suspend fun getById(id: String): Result<Coupon> {
            val coupon = couponsFlow.value.firstOrNull { it.id == id }
                ?: return Result.Error(DatabaseException("Coupon '$id' not found"))
            return Result.Success(coupon)
        }

        override suspend fun insert(coupon: Coupon): Result<Unit> {
            if (shouldFailInsert) return Result.Error(DatabaseException("Insert failed"))
            couponsFlow.value = couponsFlow.value + coupon
            return Result.Success(Unit)
        }

        override suspend fun update(coupon: Coupon): Result<Unit> {
            val idx = couponsFlow.value.indexOfFirst { it.id == coupon.id }
            if (idx == -1) return Result.Error(DatabaseException("Not found"))
            val updated = couponsFlow.value.toMutableList().also { it[idx] = coupon }
            couponsFlow.value = updated
            return Result.Success(Unit)
        }

        override suspend fun toggleActive(id: String, isActive: Boolean): Result<Unit> {
            val idx = couponsFlow.value.indexOfFirst { it.id == id }
            if (idx == -1) return Result.Error(DatabaseException("Not found"))
            val updated = couponsFlow.value.toMutableList()
            updated[idx] = updated[idx].copy(isActive = isActive)
            couponsFlow.value = updated
            return Result.Success(Unit)
        }

        override suspend fun delete(id: String): Result<Unit> {
            if (shouldFailDelete) return Result.Error(DatabaseException("Delete failed"))
            couponsFlow.value = couponsFlow.value.filter { it.id != id }
            return Result.Success(Unit)
        }

        override suspend fun recordRedemption(usage: CouponUsage): Result<Unit> =
            Result.Success(Unit)

        override suspend fun getCustomerUsageCount(
            couponId: String,
            customerId: String,
        ): Result<Int> = Result.Success(0)

        override fun getUsageByCoupon(couponId: String): Flow<List<CouponUsage>> =
            MutableStateFlow(emptyList())

        override fun getAllPromotions(): Flow<List<Promotion>> =
            MutableStateFlow(emptyList())

        override fun getActivePromotions(nowEpochMillis: Long): Flow<List<Promotion>> =
            MutableStateFlow(emptyList())

        override fun getActiveCouponsForStore(nowEpochMillis: Long, storeId: String): Flow<List<Coupon>> =
            getActiveCoupons(nowEpochMillis)

        override fun getActivePromotionsForStore(nowEpochMillis: Long, storeId: String): Flow<List<Promotion>> =
            MutableStateFlow(emptyList())

        override suspend fun getPromotionById(id: String): Result<Promotion> =
            Result.Error(DatabaseException("Not found"))

        override suspend fun insertPromotion(promotion: Promotion): Result<Unit> =
            Result.Success(Unit)

        override suspend fun updatePromotion(promotion: Promotion): Result<Unit> =
            Result.Success(Unit)

        override suspend fun deletePromotion(id: String): Result<Unit> =
            Result.Success(Unit)

        override suspend fun upsertPromotionFromSync(payload: String) = Unit
    }

    // ── Fake CategoryRepository ──────────────────────────────────────────────

    private val categoriesFlow = MutableStateFlow(listOf(
        Category(id = "cat-1", name = "Beverages"),
        Category(id = "cat-2", name = "Electronics"),
        Category(id = "cat-3", name = "Food"),
    ))

    private val fakeCategoryRepository = object : CategoryRepository {
        override fun getAll(): Flow<List<Category>> = categoriesFlow
        override suspend fun getById(id: String): Result<Category> {
            val cat = categoriesFlow.value.firstOrNull { it.id == id }
                ?: return Result.Error(DatabaseException("Not found"))
            return Result.Success(cat)
        }
        override suspend fun insert(category: Category): Result<Unit> = Result.Success(Unit)
        override suspend fun update(category: Category): Result<Unit> = Result.Success(Unit)
        override suspend fun delete(id: String): Result<Unit> = Result.Success(Unit)
        override fun getTree(): Flow<List<Category>> = categoriesFlow
    }

    private val saveCouponUseCase = SaveCouponUseCase(fakeCouponRepository)

    private lateinit var viewModel: CouponViewModel

    // ── Test fixture ──────────────────────────────────────────────────────────

    private val now = System.currentTimeMillis()
    private val testCoupon = Coupon(
        id = "coupon-001",
        code = "SUMMER20",
        name = "Summer Sale 20%",
        discountType = DiscountType.PERCENT,
        discountValue = 20.0,
        minimumPurchase = 1000.0,
        validFrom = now - 86_400_000L,  // yesterday
        validTo = now + 86_400_000L,    // tomorrow
        isActive = true,
    )

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        couponsFlow.value = emptyList()
        shouldFailInsert = false
        shouldFailDelete = false
        viewModel = CouponViewModel(
            couponRepository = fakeCouponRepository,
            saveCouponUseCase = saveCouponUseCase,
            categoryRepository = fakeCategoryRepository,
            analytics = noOpAnalytics,
        )
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // ── Initial state ─────────────────────────────────────────────────────────

    @Test
    fun `initial state has empty coupon list and not loading`() = runTest {
        testDispatcher.scheduler.advanceUntilIdle()
        val state = viewModel.state.value
        assertTrue(state.coupons.isEmpty())
        assertFalse(state.isLoading)
        assertNull(state.error)
    }

    // ── Observable list ───────────────────────────────────────────────────────

    @Test
    fun `adding coupon to repository updates state automatically`() = runTest {
        couponsFlow.value = listOf(testCoupon)
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(1, viewModel.state.value.coupons.size)
        assertEquals(testCoupon.code, viewModel.state.value.coupons.first().code)
    }

    @Test
    fun `LoadCoupons intent sets isLoading true`() = runTest {
        viewModel.dispatch(CouponIntent.LoadCoupons)
        testDispatcher.scheduler.advanceUntilIdle()
        // LoadCoupons sets isLoading=true; it remains true until the coupon flow re-emits
        assertTrue(viewModel.state.value.isLoading)
    }

    // ── SaveCoupon — validation ────────────────────────────────────────────────

    @Test
    fun `SaveCoupon with blank code sets validation error and does not persist`() = runTest {
        viewModel.dispatch(CouponIntent.UpdateFormField("name", "Test Coupon"))
        viewModel.dispatch(CouponIntent.UpdateFormField("discountValue", "10"))
        viewModel.dispatch(CouponIntent.UpdateFormField("validFrom", now.toString()))
        viewModel.dispatch(CouponIntent.UpdateFormField("validTo", (now + 100000L).toString()))
        // code left blank
        viewModel.dispatch(CouponIntent.SaveCoupon)
        testDispatcher.scheduler.advanceUntilIdle()

        val formState = viewModel.state.value.formState
        assertNotNull(formState.validationErrors["code"])
        assertTrue(couponsFlow.value.isEmpty(), "No coupon should be persisted on validation failure")
    }

    @Test
    fun `SaveCoupon with blank name sets validation error`() = runTest {
        viewModel.dispatch(CouponIntent.UpdateFormField("code", "SALE10"))
        // name left blank
        viewModel.dispatch(CouponIntent.UpdateFormField("discountValue", "10"))
        viewModel.dispatch(CouponIntent.UpdateFormField("validFrom", now.toString()))
        viewModel.dispatch(CouponIntent.UpdateFormField("validTo", (now + 100000L).toString()))
        viewModel.dispatch(CouponIntent.SaveCoupon)
        testDispatcher.scheduler.advanceUntilIdle()

        assertNotNull(viewModel.state.value.formState.validationErrors["name"])
    }

    // ── SaveCoupon — success ──────────────────────────────────────────────────

    @Test
    fun `SaveCoupon with valid form emits ShowSuccess and NavigateToList effects`() = runTest {
        val validFrom = now - 1000L
        val validTo = now + 100_000L
        viewModel.dispatch(CouponIntent.UpdateFormField("code", "SALE10"))
        viewModel.dispatch(CouponIntent.UpdateFormField("name", "10% Sale"))
        viewModel.dispatch(CouponIntent.UpdateFormField("discountValue", "10"))
        viewModel.dispatch(CouponIntent.UpdateFormField("validFrom", validFrom.toString()))
        viewModel.dispatch(CouponIntent.UpdateFormField("validTo", validTo.toString()))

        viewModel.effects.test {
            viewModel.dispatch(CouponIntent.SaveCoupon)
            testDispatcher.scheduler.advanceUntilIdle()

            val effect1 = awaitItem()
            assertTrue(effect1 is CouponEffect.ShowSuccess)
            val effect2 = awaitItem()
            assertTrue(effect2 is CouponEffect.NavigateToList)
            cancelAndIgnoreRemainingEvents()
        }
    }

    // ── DeleteCoupon ──────────────────────────────────────────────────────────

    @Test
    fun `DeleteCoupon removes coupon and emits ShowSuccess`() = runTest {
        couponsFlow.value = listOf(testCoupon)
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.effects.test {
            viewModel.dispatch(CouponIntent.DeleteCoupon(testCoupon.id))
            testDispatcher.scheduler.advanceUntilIdle()

            val effect1 = awaitItem()
            assertTrue(effect1 is CouponEffect.ShowSuccess)
            cancelAndIgnoreRemainingEvents()
        }
        assertTrue(couponsFlow.value.isEmpty())
    }

    @Test
    fun `DeleteCoupon on repository error emits ShowError effect`() = runTest {
        shouldFailDelete = true
        couponsFlow.value = listOf(testCoupon)
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.effects.test {
            viewModel.dispatch(CouponIntent.DeleteCoupon(testCoupon.id))
            testDispatcher.scheduler.advanceUntilIdle()

            val effect = awaitItem()
            assertTrue(effect is CouponEffect.ShowError)
            cancelAndIgnoreRemainingEvents()
        }
    }

    // ── ToggleCouponActive ────────────────────────────────────────────────────

    @Test
    fun `ToggleCouponActive disabling a coupon emits ShowSuccess`() = runTest {
        couponsFlow.value = listOf(testCoupon)
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.effects.test {
            viewModel.dispatch(CouponIntent.ToggleCouponActive(testCoupon.id, false))
            testDispatcher.scheduler.advanceUntilIdle()

            val effect = awaitItem()
            assertTrue(effect is CouponEffect.ShowSuccess)
            cancelAndIgnoreRemainingEvents()
        }
    }

    // ── DismissMessage ────────────────────────────────────────────────────────

    @Test
    fun `DismissMessage clears error and successMessage`() = runTest {
        viewModel.dispatch(CouponIntent.DismissMessage)
        testDispatcher.scheduler.advanceUntilIdle()

        assertNull(viewModel.state.value.error)
        assertNull(viewModel.state.value.successMessage)
    }

    // ── ToggleActiveFilter ────────────────────────────────────────────────────

    @Test
    fun `ToggleActiveFilter updates showActiveOnly in state`() = runTest {
        assertFalse(viewModel.state.value.showActiveOnly)

        viewModel.dispatch(CouponIntent.ToggleActiveFilter(true))
        testDispatcher.scheduler.advanceUntilIdle()

        assertTrue(viewModel.state.value.showActiveOnly)
    }

    // ── G12: BOGO + Category Rules ─────────────────────────────────────────

    @Test
    fun `categories are loaded on init`() = runTest {
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals(3, viewModel.state.value.availableCategories.size)
        assertEquals("Beverages", viewModel.state.value.availableCategories.first().name)
    }

    @Test
    fun `GenerateCode produces 8-character alphanumeric code`() = runTest {
        viewModel.dispatch(CouponIntent.GenerateCode)
        testDispatcher.scheduler.advanceUntilIdle()

        val code = viewModel.state.value.formState.code
        assertEquals(8, code.length)
        assertTrue(code.all { it.isLetterOrDigit() })
    }

    @Test
    fun `UpdateScope changes scope and clears scopeIds`() = runTest {
        // First add some scope IDs
        viewModel.dispatch(CouponIntent.UpdateScope(Coupon.CouponScope.CATEGORY.name))
        viewModel.dispatch(CouponIntent.ToggleScopeId("cat-1"))
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals(1, viewModel.state.value.formState.scopeIds.size)

        // Change scope — scopeIds should be cleared
        viewModel.dispatch(CouponIntent.UpdateScope(Coupon.CouponScope.PRODUCT.name))
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals(Coupon.CouponScope.PRODUCT.name, viewModel.state.value.formState.scope)
        assertTrue(viewModel.state.value.formState.scopeIds.isEmpty())
    }

    @Test
    fun `ToggleScopeId adds and removes IDs`() = runTest {
        viewModel.dispatch(CouponIntent.UpdateScope(Coupon.CouponScope.CATEGORY.name))
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.dispatch(CouponIntent.ToggleScopeId("cat-1"))
        testDispatcher.scheduler.advanceUntilIdle()
        assertTrue("cat-1" in viewModel.state.value.formState.scopeIds)

        viewModel.dispatch(CouponIntent.ToggleScopeId("cat-2"))
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals(2, viewModel.state.value.formState.scopeIds.size)

        // Toggle off
        viewModel.dispatch(CouponIntent.ToggleScopeId("cat-1"))
        testDispatcher.scheduler.advanceUntilIdle()
        assertFalse("cat-1" in viewModel.state.value.formState.scopeIds)
        assertEquals(1, viewModel.state.value.formState.scopeIds.size)
    }

    @Test
    fun `SaveCoupon with CATEGORY scope but no scopeIds fails validation`() = runTest {
        viewModel.dispatch(CouponIntent.UpdateFormField("code", "CAT10"))
        viewModel.dispatch(CouponIntent.UpdateFormField("name", "Category Coupon"))
        viewModel.dispatch(CouponIntent.UpdateFormField("discountValue", "10"))
        viewModel.dispatch(CouponIntent.UpdateFormField("validFrom", now.toString()))
        viewModel.dispatch(CouponIntent.UpdateFormField("validTo", (now + 100000L).toString()))
        viewModel.dispatch(CouponIntent.UpdateScope(Coupon.CouponScope.CATEGORY.name))
        // No scopeIds selected
        viewModel.dispatch(CouponIntent.SaveCoupon)
        testDispatcher.scheduler.advanceUntilIdle()

        assertNotNull(viewModel.state.value.formState.validationErrors["scopeIds"])
    }

    @Test
    fun `SaveCoupon with BOGO type does not require discountValue`() = runTest {
        viewModel.dispatch(CouponIntent.UpdateFormField("code", "BOGO1"))
        viewModel.dispatch(CouponIntent.UpdateFormField("name", "Buy One Get One"))
        viewModel.dispatch(CouponIntent.UpdateFormField("discountType", DiscountType.BOGO.name))
        viewModel.dispatch(CouponIntent.UpdateFormField("validFrom", (now - 1000L).toString()))
        viewModel.dispatch(CouponIntent.UpdateFormField("validTo", (now + 100000L).toString()))
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.effects.test {
            viewModel.dispatch(CouponIntent.SaveCoupon)
            testDispatcher.scheduler.advanceUntilIdle()

            val effect1 = awaitItem()
            assertTrue(effect1 is CouponEffect.ShowSuccess)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `SaveCoupon with scope and scopeIds persists correctly`() = runTest {
        viewModel.dispatch(CouponIntent.UpdateFormField("code", "CATDEAL"))
        viewModel.dispatch(CouponIntent.UpdateFormField("name", "Category Deal"))
        viewModel.dispatch(CouponIntent.UpdateFormField("discountValue", "15"))
        viewModel.dispatch(CouponIntent.UpdateFormField("validFrom", (now - 1000L).toString()))
        viewModel.dispatch(CouponIntent.UpdateFormField("validTo", (now + 100000L).toString()))
        viewModel.dispatch(CouponIntent.UpdateScope(Coupon.CouponScope.CATEGORY.name))
        viewModel.dispatch(CouponIntent.ToggleScopeId("cat-1"))
        viewModel.dispatch(CouponIntent.ToggleScopeId("cat-2"))
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.dispatch(CouponIntent.SaveCoupon)
        testDispatcher.scheduler.advanceUntilIdle()

        val saved = couponsFlow.value.firstOrNull { it.code == "CATDEAL" }
        assertNotNull(saved)
        assertEquals(Coupon.CouponScope.CATEGORY, saved.scope)
        assertEquals(2, saved.scopeIds.size)
        assertTrue("cat-1" in saved.scopeIds)
        assertTrue("cat-2" in saved.scopeIds)
    }

    @Test
    fun `SelectCoupon loads scope and scopeIds into form state`() = runTest {
        val couponWithScope = testCoupon.copy(
            id = "coupon-scoped",
            scope = Coupon.CouponScope.CATEGORY,
            scopeIds = listOf("cat-1", "cat-3"),
        )
        couponsFlow.value = listOf(couponWithScope)
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.dispatch(CouponIntent.SelectCoupon("coupon-scoped"))
        testDispatcher.scheduler.advanceUntilIdle()

        val form = viewModel.state.value.formState
        assertEquals(Coupon.CouponScope.CATEGORY.name, form.scope)
        assertEquals(2, form.scopeIds.size)
        assertTrue("cat-1" in form.scopeIds)
        assertTrue("cat-3" in form.scopeIds)
    }
}
