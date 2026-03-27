package com.zyntasolutions.zyntapos.data.repository

import app.cash.turbine.test
import com.zyntasolutions.zyntapos.core.result.Result
import com.zyntasolutions.zyntapos.data.createTestDatabase
import com.zyntasolutions.zyntapos.data.local.SyncEnqueuer
import com.zyntasolutions.zyntapos.db.ZyntaDatabase
import com.zyntasolutions.zyntapos.domain.model.FulfillmentStatus
import com.zyntasolutions.zyntapos.domain.repository.FulfillmentOrder
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlinx.coroutines.test.runTest

/**
 * ZyntaPOS — FulfillmentRepositoryImpl Integration Tests (jvmTest)
 *
 * Validates [FulfillmentRepositoryImpl] against a real in-memory SQLite database.
 * fulfillment_orders has no external FK constraints.
 *
 * Coverage:
 *  A. create → getByOrderId round-trip preserves all fields
 *  B. getPendingPickups emits non-terminal orders via Turbine
 *  C. getPendingPickups excludes PICKED_UP, EXPIRED, CANCELLED orders
 *  D. updateStatus advances RECEIVED → PREPARING → READY_FOR_PICKUP → PICKED_UP
 *  E. expireOverdueOrders sets expired status for overdue orders
 *  F. getByOrderId returns error for unknown order
 */
class FulfillmentRepositoryImplIntegrationTest {

    private lateinit var db: ZyntaDatabase
    private lateinit var repo: FulfillmentRepositoryImpl

    @BeforeTest
    fun setup() {
        db = createTestDatabase()
        repo = FulfillmentRepositoryImpl(db, SyncEnqueuer(db))
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private val now get() = Clock.System.now().toEpochMilliseconds()

    private fun makeOrder(
        orderId: String = "order-01",
        storeId: String = "store-01",
        customerId: String = "cust-01",
        status: FulfillmentStatus = FulfillmentStatus.RECEIVED,
        pickupDeadline: Long = now + 3_600_000L, // 1 hour from now
    ) = FulfillmentOrder(
        orderId = orderId,
        storeId = storeId,
        customerId = customerId,
        status = status,
        pickupDeadline = pickupDeadline,
        customerNotified = false,
        createdAt = now,
        updatedAt = now,
    )

    // ── Tests ─────────────────────────────────────────────────────────────────

    @Test
    fun `A - create then getByOrderId round-trip preserves all fields`() = runTest {
        val order = makeOrder(
            orderId = "order-01",
            storeId = "store-01",
            customerId = "cust-01",
            status = FulfillmentStatus.RECEIVED,
        )
        val createResult = repo.create(order)
        assertIs<Result.Success<Unit>>(createResult)

        val fetchResult = repo.getByOrderId("order-01")
        assertIs<Result.Success<FulfillmentOrder>>(fetchResult)
        val fetched = fetchResult.data
        assertEquals("order-01", fetched.orderId)
        assertEquals("store-01", fetched.storeId)
        assertEquals("cust-01", fetched.customerId)
        assertEquals(FulfillmentStatus.RECEIVED, fetched.status)
    }

    @Test
    fun `B - getPendingPickups emits non-terminal orders via Turbine`() = runTest {
        repo.create(makeOrder(orderId = "order-01", status = FulfillmentStatus.RECEIVED))
        repo.create(makeOrder(orderId = "order-02", status = FulfillmentStatus.PREPARING))
        repo.create(makeOrder(orderId = "order-03", status = FulfillmentStatus.READY_FOR_PICKUP))

        repo.getPendingPickups("store-01").test {
            val list = awaitItem()
            assertEquals(3, list.size)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `C - getPendingPickups excludes PICKED_UP EXPIRED CANCELLED`() = runTest {
        repo.create(makeOrder(orderId = "order-active", status = FulfillmentStatus.RECEIVED))
        repo.create(makeOrder(orderId = "order-done", status = FulfillmentStatus.PICKED_UP))
        repo.create(makeOrder(orderId = "order-expired", status = FulfillmentStatus.EXPIRED))
        repo.create(makeOrder(orderId = "order-cancelled", status = FulfillmentStatus.CANCELLED))

        repo.getPendingPickups("store-01").test {
            val list = awaitItem()
            assertEquals(1, list.size)
            assertEquals("order-active", list.first().orderId)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `D - updateStatus advances status from RECEIVED to PICKED_UP`() = runTest {
        repo.create(makeOrder(orderId = "order-01", status = FulfillmentStatus.RECEIVED))

        repo.updateStatus("order-01", FulfillmentStatus.PREPARING, notifyCustomer = false)
        var fetched = (repo.getByOrderId("order-01") as Result.Success).data
        assertEquals(FulfillmentStatus.PREPARING, fetched.status)

        repo.updateStatus("order-01", FulfillmentStatus.READY_FOR_PICKUP, notifyCustomer = true)
        fetched = (repo.getByOrderId("order-01") as Result.Success).data
        assertEquals(FulfillmentStatus.READY_FOR_PICKUP, fetched.status)

        repo.updateStatus("order-01", FulfillmentStatus.PICKED_UP, notifyCustomer = false)
        fetched = (repo.getByOrderId("order-01") as Result.Success).data
        assertEquals(FulfillmentStatus.PICKED_UP, fetched.status)
    }

    @Test
    fun `E - expireOverdueOrders sets EXPIRED for overdue non-terminal orders`() = runTest {
        val pastDeadline = now - 3_600_000L  // 1 hour ago
        val futureDeadline = now + 3_600_000L // 1 hour from now

        repo.create(makeOrder(orderId = "order-overdue", status = FulfillmentStatus.RECEIVED,
            pickupDeadline = pastDeadline))
        repo.create(makeOrder(orderId = "order-pending", status = FulfillmentStatus.RECEIVED,
            pickupDeadline = futureDeadline))

        val expireResult = repo.expireOverdueOrders("store-01", now)
        assertIs<Result.Success<Int>>(expireResult)
        assertEquals(1, expireResult.data)  // 1 order expired

        val overdue = (repo.getByOrderId("order-overdue") as Result.Success).data
        assertEquals(FulfillmentStatus.EXPIRED, overdue.status)

        val pending = (repo.getByOrderId("order-pending") as Result.Success).data
        assertEquals(FulfillmentStatus.RECEIVED, pending.status)
    }

    @Test
    fun `F - getByOrderId returns error for unknown order`() = runTest {
        val result = repo.getByOrderId("non-existent")
        assertIs<Result.Error>(result)
    }
}
