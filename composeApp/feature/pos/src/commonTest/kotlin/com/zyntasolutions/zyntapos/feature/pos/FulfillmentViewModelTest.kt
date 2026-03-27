package com.zyntasolutions.zyntapos.feature.pos.fulfillment

import com.zyntasolutions.zyntapos.core.result.DatabaseException
import com.zyntasolutions.zyntapos.core.result.Result
import com.zyntasolutions.zyntapos.domain.model.FulfillmentStatus
import com.zyntasolutions.zyntapos.domain.repository.FulfillmentOrder
import com.zyntasolutions.zyntapos.domain.repository.FulfillmentRepository
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
class FulfillmentViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    // ── In-memory fake ─────────────────────────────────────────────────────────

    private class FakeFulfillmentRepository : FulfillmentRepository {

        val orders = mutableListOf<FulfillmentOrder>()
        private val _trigger = MutableStateFlow(0)
        var shouldFailUpdate: Boolean = false
        var shouldFailExpiry: Boolean = false
        var expiredCount: Int = 2
        var lastUpdatedOrderId: String? = null
        var lastUpdatedStatus: FulfillmentStatus? = null
        var lastNotifyCustomer: Boolean = false

        override fun getPendingPickups(storeId: String): Flow<List<FulfillmentOrder>> =
            _trigger.map { orders.filter { it.storeId == storeId } }

        override suspend fun getByOrderId(orderId: String): Result<FulfillmentOrder> {
            val o = orders.find { it.orderId == orderId }
                ?: return Result.Error(DatabaseException("Not found: $orderId"))
            return Result.Success(o)
        }

        override suspend fun create(fulfillment: FulfillmentOrder): Result<Unit> {
            orders.add(fulfillment)
            _trigger.value++
            return Result.Success(Unit)
        }

        override suspend fun updateStatus(
            orderId: String,
            newStatus: FulfillmentStatus,
            notifyCustomer: Boolean,
        ): Result<Unit> {
            lastUpdatedOrderId = orderId
            lastUpdatedStatus = newStatus
            lastNotifyCustomer = notifyCustomer
            if (shouldFailUpdate) {
                return Result.Error(DatabaseException("Update failed"))
            }
            val idx = orders.indexOfFirst { it.orderId == orderId }
            if (idx >= 0) {
                orders[idx] = orders[idx].copy(status = newStatus)
                _trigger.value++
            }
            return Result.Success(Unit)
        }

        override suspend fun expireOverdueOrders(storeId: String, timeoutEpochMillis: Long): Result<Int> {
            if (shouldFailExpiry) {
                return Result.Error(DatabaseException("Expiry failed"))
            }
            return Result.Success(expiredCount)
        }
    }

    // ── Fixture builder ────────────────────────────────────────────────────────

    private fun buildOrder(
        orderId: String = "order-01",
        storeId: String = "store-01",
        customerId: String = "cust-01",
        status: FulfillmentStatus = FulfillmentStatus.RECEIVED,
        pickupDeadline: Long = Long.MAX_VALUE,
    ) = FulfillmentOrder(
        orderId = orderId,
        storeId = storeId,
        customerId = customerId,
        status = status,
        pickupDeadline = pickupDeadline,
        createdAt = 1_000_000L,
        updatedAt = 1_000_000L,
    )

    // ── Subject ────────────────────────────────────────────────────────────────

    private lateinit var fakeRepo: FakeFulfillmentRepository
    private lateinit var viewModel: FulfillmentViewModel

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        fakeRepo = FakeFulfillmentRepository()
        viewModel = FulfillmentViewModel(fakeRepo, storeId = "store-01")
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // ── Initial state ──────────────────────────────────────────────────────────

    @Test
    fun `initial state has empty pickups and isLoading becomes false after first emission`() = runTest {
        testDispatcher.scheduler.advanceUntilIdle()

        val s = viewModel.state.value
        assertTrue(s.pickups.isEmpty())
        assertFalse(s.isLoading)
        assertNull(s.errorMessage)
        assertNull(s.updatingOrderId)
    }

    // ── Reactive pickup list ───────────────────────────────────────────────────

    @Test
    fun `pickups list reflects repository state on emission`() = runTest {
        fakeRepo.create(buildOrder(orderId = "order-01", storeId = "store-01"))
        fakeRepo.create(buildOrder(orderId = "order-02", storeId = "store-01"))
        testDispatcher.scheduler.advanceUntilIdle()

        val s = viewModel.state.value
        assertEquals(2, s.pickups.size)
    }

    @Test
    fun `pickups only shows orders for the correct store`() = runTest {
        fakeRepo.create(buildOrder(orderId = "order-01", storeId = "store-01"))
        fakeRepo.create(buildOrder(orderId = "order-02", storeId = "store-99"))
        testDispatcher.scheduler.advanceUntilIdle()

        val s = viewModel.state.value
        assertEquals(1, s.pickups.size)
        assertEquals("order-01", s.pickups.first().orderId)
    }

    // ── MarkPreparing ──────────────────────────────────────────────────────────

    @Test
    fun `MarkPreparing calls updateStatus with PREPARING`() = runTest {
        fakeRepo.create(buildOrder(orderId = "order-01"))
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.dispatch(FulfillmentIntent.MarkPreparing("order-01"))
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals("order-01", fakeRepo.lastUpdatedOrderId)
        assertEquals(FulfillmentStatus.PREPARING, fakeRepo.lastUpdatedStatus)
        assertNull(viewModel.state.value.updatingOrderId)
    }

    @Test
    fun `MarkPreparing shows error on repository failure`() = runTest {
        fakeRepo.shouldFailUpdate = true
        viewModel.dispatch(FulfillmentIntent.MarkPreparing("order-01"))
        testDispatcher.scheduler.advanceUntilIdle()

        assertNotNull(viewModel.state.value.errorMessage)
        assertNull(viewModel.state.value.updatingOrderId)
    }

    // ── MarkReady ──────────────────────────────────────────────────────────────

    @Test
    fun `MarkReady calls updateStatus with READY_FOR_PICKUP and notifyCustomer`() = runTest {
        fakeRepo.create(buildOrder(orderId = "order-01"))
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.dispatch(FulfillmentIntent.MarkReady("order-01", notifyCustomer = true))
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(FulfillmentStatus.READY_FOR_PICKUP, fakeRepo.lastUpdatedStatus)
        assertTrue(fakeRepo.lastNotifyCustomer)
    }

    @Test
    fun `MarkReady with notifyCustomer false does not notify`() = runTest {
        viewModel.dispatch(FulfillmentIntent.MarkReady("order-01", notifyCustomer = false))
        testDispatcher.scheduler.advanceUntilIdle()
        assertFalse(fakeRepo.lastNotifyCustomer)
    }

    // ── MarkPickedUp ───────────────────────────────────────────────────────────

    @Test
    fun `MarkPickedUp calls updateStatus with PICKED_UP`() = runTest {
        fakeRepo.create(buildOrder(orderId = "order-01"))
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.dispatch(FulfillmentIntent.MarkPickedUp("order-01"))
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(FulfillmentStatus.PICKED_UP, fakeRepo.lastUpdatedStatus)
    }

    // ── CancelOrder ────────────────────────────────────────────────────────────

    @Test
    fun `CancelOrder calls updateStatus with CANCELLED`() = runTest {
        fakeRepo.create(buildOrder(orderId = "order-01"))
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.dispatch(FulfillmentIntent.CancelOrder("order-01"))
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(FulfillmentStatus.CANCELLED, fakeRepo.lastUpdatedStatus)
    }

    // ── CheckExpiry ────────────────────────────────────────────────────────────

    @Test
    fun `CheckExpiry shows error message when expired orders found`() = runTest {
        fakeRepo.expiredCount = 3
        viewModel.dispatch(FulfillmentIntent.CheckExpiry)
        testDispatcher.scheduler.advanceUntilIdle()

        assertNotNull(viewModel.state.value.errorMessage)
        assertTrue(viewModel.state.value.errorMessage!!.contains("3"))
    }

    @Test
    fun `CheckExpiry with zero expired orders shows no message`() = runTest {
        fakeRepo.expiredCount = 0
        viewModel.dispatch(FulfillmentIntent.CheckExpiry)
        testDispatcher.scheduler.advanceUntilIdle()

        assertNull(viewModel.state.value.errorMessage)
    }

    @Test
    fun `CheckExpiry failure shows error message`() = runTest {
        fakeRepo.shouldFailExpiry = true
        viewModel.dispatch(FulfillmentIntent.CheckExpiry)
        testDispatcher.scheduler.advanceUntilIdle()

        assertNotNull(viewModel.state.value.errorMessage)
    }

    // ── DismissError ───────────────────────────────────────────────────────────

    @Test
    fun `DismissError clears errorMessage`() = runTest {
        fakeRepo.shouldFailUpdate = true
        viewModel.dispatch(FulfillmentIntent.MarkPreparing("order-01"))
        testDispatcher.scheduler.advanceUntilIdle()
        assertNotNull(viewModel.state.value.errorMessage)

        viewModel.dispatch(FulfillmentIntent.DismissError)
        testDispatcher.scheduler.advanceUntilIdle()
        assertNull(viewModel.state.value.errorMessage)
    }

    // ── LoadQueue re-subscribes ────────────────────────────────────────────────

    @Test
    fun `LoadQueue intent re-triggers observation`() = runTest {
        testDispatcher.scheduler.advanceUntilIdle()

        fakeRepo.create(buildOrder(orderId = "order-01", storeId = "store-01"))
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.dispatch(FulfillmentIntent.LoadQueue)
        testDispatcher.scheduler.advanceUntilIdle()

        // After LoadQueue, state should still reflect current repository contents
        assertEquals(1, viewModel.state.value.pickups.size)
    }

    // ── updatingOrderId lifecycle ──────────────────────────────────────────────

    @Test
    fun `updatingOrderId is cleared after successful status update`() = runTest {
        fakeRepo.create(buildOrder(orderId = "order-01"))
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.dispatch(FulfillmentIntent.MarkPickedUp("order-01"))
        testDispatcher.scheduler.advanceUntilIdle()

        assertNull(viewModel.state.value.updatingOrderId)
    }
}
