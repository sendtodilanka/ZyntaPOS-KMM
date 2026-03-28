package com.zyntasolutions.zyntapos.data.job

import com.zyntasolutions.zyntapos.core.result.DatabaseException
import com.zyntasolutions.zyntapos.core.result.Result
import com.zyntasolutions.zyntapos.domain.model.FulfillmentStatus
import com.zyntasolutions.zyntapos.domain.repository.FulfillmentOrder
import com.zyntasolutions.zyntapos.domain.repository.FulfillmentRepository
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest

/**
 * ZyntaPOS — FulfillmentExpiryJobTest Unit Tests (commonTest)
 *
 * Validates overdue Click & Collect order expiry logic in [FulfillmentExpiryJob.runExpiry].
 *
 * Coverage:
 *  A. runExpiry calls expireOverdueOrders with the correct storeId
 *  B. runExpiry passes a timestamp in the past (epoch ms > 0)
 *  C. exception swallowed without re-throwing (non-cancellation exception)
 *  D. expireOverdueOrders called exactly once per runExpiry invocation
 */
class FulfillmentExpiryJobTest {

    // ── Fake ──────────────────────────────────────────────────────────────────

    private class FakeFulfillmentRepository(
        private val shouldThrow: Boolean = false,
    ) : FulfillmentRepository {
        val expireCalls = mutableListOf<Pair<String, Long>>() // storeId to epochMs

        override fun getPendingPickups(storeId: String): Flow<List<FulfillmentOrder>> = flowOf(emptyList())
        override suspend fun getByOrderId(orderId: String): Result<FulfillmentOrder> = Result.Error(DatabaseException("not found"))
        override suspend fun create(fulfillment: FulfillmentOrder): Result<Unit> = Result.Success(Unit)
        override suspend fun updateStatus(
            orderId: String,
            newStatus: FulfillmentStatus,
            notifyCustomer: Boolean,
        ): Result<Unit> = Result.Success(Unit)
        override suspend fun expireOverdueOrders(storeId: String, timeoutEpochMillis: Long): Result<Int> {
            if (shouldThrow) throw RuntimeException("expiry failed")
            expireCalls.add(storeId to timeoutEpochMillis)
            return Result.Success(0)
        }
    }

    private fun makeJob(
        storeId: String = "store-01",
        repo: FakeFulfillmentRepository = FakeFulfillmentRepository(),
    ): Pair<FulfillmentExpiryJob, FakeFulfillmentRepository> {
        val scope = kotlinx.coroutines.MainScope()
        return FulfillmentExpiryJob(
            fulfillmentRepository = repo,
            storeId = storeId,
            scope = scope,
        ) to repo
    }

    // ── A — Correct storeId forwarded ────────────────────────────────────────

    @Test
    fun `A - runExpiry forwards the correct storeId to repository`() = runTest {
        val (job, repo) = makeJob(storeId = "store-42")

        job.runExpiry()

        assertEquals("store-42", repo.expireCalls.single().first)
    }

    // ── B — Timestamp is a valid epoch millis ─────────────────────────────────

    @Test
    fun `B - runExpiry passes a positive epoch timestamp to repository`() = runTest {
        val (job, repo) = makeJob()

        job.runExpiry()

        val passedEpochMs = repo.expireCalls.single().second
        assertTrue(passedEpochMs > 0L, "Epoch timestamp must be positive, got $passedEpochMs")
    }

    // ── C — Exception swallowed ───────────────────────────────────────────────

    @Test
    fun `C - runExpiry swallows non-cancellation exceptions without re-throwing`() = runTest {
        val (job, _) = makeJob(repo = FakeFulfillmentRepository(shouldThrow = true))

        // Must not throw
        job.runExpiry()
    }

    // ── D — Called exactly once per invocation ───────────────────────────────

    @Test
    fun `D - expireOverdueOrders called exactly once per runExpiry call`() = runTest {
        val (job, repo) = makeJob()

        job.runExpiry()
        job.runExpiry()

        assertEquals(2, repo.expireCalls.size, "Expected expireOverdueOrders to be called once per runExpiry")
    }
}
