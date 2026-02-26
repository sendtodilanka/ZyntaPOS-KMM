package com.zyntasolutions.zyntapos.feature.coupons

import app.cash.turbine.test
import com.zyntasolutions.zyntapos.core.result.DatabaseException
import com.zyntasolutions.zyntapos.core.result.Result
import com.zyntasolutions.zyntapos.domain.model.Coupon
import com.zyntasolutions.zyntapos.domain.model.CouponUsage
import com.zyntasolutions.zyntapos.domain.model.DiscountType
import com.zyntasolutions.zyntapos.domain.model.Promotion
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
import kotlin.test.assertTrue

// ─────────────────────────────────────────────────────────────────────────────
// CouponViewModelTest
// Tests CouponViewModel MVI state transitions using hand-rolled fakes.
// ─────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalCoroutinesApi::class)
class CouponViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

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

        override suspend fun getPromotionById(id: String): Result<Promotion> =
            Result.Error(DatabaseException("Not found"))

        override suspend fun insertPromotion(promotion: Promotion): Result<Unit> =
            Result.Success(Unit)

        override suspend fun updatePromotion(promotion: Promotion): Result<Unit> =
            Result.Success(Unit)

        override suspend fun deletePromotion(id: String): Result<Unit> =
            Result.Success(Unit)
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
}
