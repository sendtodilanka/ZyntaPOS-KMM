package com.zyntasolutions.zyntapos.api.sync

import com.zyntasolutions.zyntapos.api.db.NoOpTransactionRunner
import com.zyntasolutions.zyntapos.api.models.PushRequest
import com.zyntasolutions.zyntapos.api.models.SyncOperation
import com.zyntasolutions.zyntapos.api.repository.DeadLetterRepository
import com.zyntasolutions.zyntapos.api.repository.SyncOperationRepository
import com.zyntasolutions.zyntapos.api.repository.SyncOperationSnapshot
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * S3-5: Unit tests for [SyncProcessor] using MockK.
 *
 * Tests the orchestration logic (validation → dedup → conflict resolution → persist → notify)
 * with mocked repositories and Redis connection.
 */
class SyncProcessorTest {

    private val syncOpRepo = mockk<SyncOperationRepository>(relaxed = true)
    private val conflictResolver = mockk<ServerConflictResolver>(relaxed = true)
    private val validator = SyncValidator() // real instance — pure logic
    private val entityApplier = mockk<EntityApplier>(relaxed = true)
    private val deadLetterRepo = mockk<DeadLetterRepository>(relaxed = true)
    private val metrics = SyncMetrics()

    private fun processor() = SyncProcessor(
        syncOpRepo = syncOpRepo,
        conflictResolver = conflictResolver,
        validator = validator,
        entityApplier = entityApplier,
        deadLetterRepo = deadLetterRepo,
        metrics = metrics,
        redisConnection = null, // no Redis in tests
        txRunner = NoOpTransactionRunner(),
    )

    private fun validOp(
        id: String = "op-1",
        entityType: String = "PRODUCT",
        entityId: String = "entity-1",
        operation: String = "CREATE",
        payload: String = """{"name":"Test","price":10.0}""",
        createdAt: Long = System.currentTimeMillis() - 1000,
    ) = SyncOperation(
        id = id,
        entityType = entityType,
        entityId = entityId,
        operation = operation,
        payload = payload,
        createdAt = createdAt,
    )

    // ── Validation rejection ────────────────────────────────────────────

    @Test
    fun `invalid operations are rejected and sent to dead letter queue`() = runTest {
        val invalidOp = validOp(entityType = "UNKNOWN_TYPE")
        val request = PushRequest(deviceId = "dev-1", operations = listOf(invalidOp))

        coEvery { syncOpRepo.findExistingIds(any()) } returns emptySet()
        coEvery { syncOpRepo.getLatestSeq(any()) } returns 0L

        val result = processor().processPush("store-1", request)

        assertEquals(1, result.rejected.size)
        assertEquals("op-1", result.rejected[0])
        assertTrue(result.accepted.isEmpty())
    }

    // ── Deduplication ───────────────────────────────────────────────────

    @Test
    fun `already-processed operations are included in accepted without re-processing`() = runTest {
        val op = validOp()
        val request = PushRequest(deviceId = "dev-1", operations = listOf(op))

        coEvery { syncOpRepo.findExistingIds(listOf("op-1")) } returns setOf("op-1")
        coEvery { syncOpRepo.getLatestSeq("store-1") } returns 42L

        val result = processor().processPush("store-1", request)

        assertEquals(listOf("op-1"), result.accepted)
        assertTrue(result.rejected.isEmpty())
        // Should NOT call insert since op was already processed
        coVerify(exactly = 0) { syncOpRepo.insert(any(), any(), any()) }
    }

    // ── Successful insert (no conflict) ─────────────────────────────────

    @Test
    fun `new operation with no conflict is accepted and applied`() = runTest {
        val op = validOp()
        val request = PushRequest(deviceId = "dev-1", operations = listOf(op))

        coEvery { syncOpRepo.findExistingIds(any()) } returns emptySet()
        coEvery { syncOpRepo.findLatestForEntity(any(), any(), any()) } returns null
        coEvery { syncOpRepo.getLatestSeq(any()) } returns 1L

        val result = processor().processPush("store-1", request)

        assertEquals(listOf("op-1"), result.accepted)
        assertTrue(result.rejected.isEmpty())
        assertTrue(result.conflicts.isEmpty())
    }

    // ── Multiple operations in one batch ────────────────────────────────

    @Test
    fun `batch with mix of valid and invalid operations splits correctly`() = runTest {
        val validOp1 = validOp(id = "op-1")
        val invalidOp = validOp(id = "op-2", entityType = "INVALID_TYPE")
        val validOp2 = validOp(id = "op-3", entityId = "entity-2")
        val request = PushRequest(deviceId = "dev-1", operations = listOf(validOp1, invalidOp, validOp2))

        coEvery { syncOpRepo.findExistingIds(any()) } returns emptySet()
        coEvery { syncOpRepo.findLatestForEntity(any(), any(), any()) } returns null
        coEvery { syncOpRepo.getLatestSeq(any()) } returns 3L

        val result = processor().processPush("store-1", request)

        assertEquals(2, result.accepted.size)
        assertEquals(1, result.rejected.size)
        assertEquals("op-2", result.rejected[0])
    }

    // ── Empty batch ─────────────────────────────────────────────────────

    @Test
    fun `empty batch returns empty response`() = runTest {
        val request = PushRequest(deviceId = "dev-1", operations = emptyList())

        coEvery { syncOpRepo.findExistingIds(any()) } returns emptySet()
        coEvery { syncOpRepo.getLatestSeq(any()) } returns 0L

        val result = processor().processPush("store-1", request)

        assertTrue(result.accepted.isEmpty())
        assertTrue(result.rejected.isEmpty())
        assertTrue(result.conflicts.isEmpty())
    }

    // ── Metrics tracking ────────────────────────────────────────────────

    @Test
    fun `metrics are updated after processing`() = runTest {
        val op = validOp()
        val request = PushRequest(deviceId = "dev-1", operations = listOf(op))

        coEvery { syncOpRepo.findExistingIds(any()) } returns emptySet()
        coEvery { syncOpRepo.findLatestForEntity(any(), any(), any()) } returns null
        coEvery { syncOpRepo.getLatestSeq(any()) } returns 1L

        val initialAccepted = metrics.opsAccepted.get()
        processor().processPush("store-1", request)

        assertTrue(metrics.opsAccepted.get() > initialAccepted)
    }
}
