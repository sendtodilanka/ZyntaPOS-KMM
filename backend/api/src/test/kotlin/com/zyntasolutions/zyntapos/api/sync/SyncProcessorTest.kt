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
        redisPool = null, // no Redis in tests
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

    // ── Conflict detection and resolution ───────────────────────────────

    @Test
    fun `conflict detected when existing op from different device is newer`() = runTest {
        val now = System.currentTimeMillis()
        val op = validOp(createdAt = now - 5000) // incoming is older
        val request = PushRequest(deviceId = "dev-1", operations = listOf(op))

        val existingSnapshot = SyncOperationSnapshot(
            opId = "existing-op",
            deviceId = "dev-2", // different device
            clientTimestamp = now - 1000, // newer than incoming
            payload = """{"name":"Existing","price":20.0}""",
            serverSeq = 10L,
            status = "ACCEPTED",
        )

        coEvery { syncOpRepo.findExistingIds(any()) } returns emptySet()
        coEvery { syncOpRepo.findLatestForEntity("store-1", "PRODUCT", "entity-1") } returns existingSnapshot
        coEvery { syncOpRepo.getLatestSeq(any()) } returns 11L
        coEvery { conflictResolver.resolve(any(), any(), any(), any()) } returns
            ServerConflictResolver.ConflictResolution(
                conflictId = "conflict-1",
                winnerPayload = """{"name":"Existing","price":20.0}""",
                incomingWins = false,
                strategy = ServerConflictResolver.ResolutionStrategy.LWW_TIMESTAMP,
            )

        val result = processor().processPush("store-1", request)

        assertEquals(1, result.conflicts.size)
        assertEquals("op-1", result.conflicts[0])
        assertTrue(result.accepted.isEmpty())
        coVerify { conflictResolver.resolve("store-1", op, existingSnapshot, "dev-1") }
        coVerify { syncOpRepo.insertWithConflict(any(), any(), any(), eq("conflict-1"), any()) }
    }

    @Test
    fun `no conflict when existing op from same device`() = runTest {
        val now = System.currentTimeMillis()
        val op = validOp(createdAt = now - 1000)
        val request = PushRequest(deviceId = "dev-1", operations = listOf(op))

        val existingSnapshot = SyncOperationSnapshot(
            opId = "existing-op",
            deviceId = "dev-1", // same device — no conflict
            clientTimestamp = now - 5000,
            payload = """{"name":"Old","price":5.0}""",
            serverSeq = 10L,
            status = "ACCEPTED",
        )

        coEvery { syncOpRepo.findExistingIds(any()) } returns emptySet()
        coEvery { syncOpRepo.findLatestForEntity("store-1", "PRODUCT", "entity-1") } returns existingSnapshot
        coEvery { syncOpRepo.getLatestSeq(any()) } returns 11L

        val result = processor().processPush("store-1", request)

        assertEquals(listOf("op-1"), result.accepted)
        assertTrue(result.conflicts.isEmpty())
        coVerify(exactly = 0) { conflictResolver.resolve(any(), any(), any(), any()) }
    }

    @Test
    fun `incoming wins when newer than existing from different device`() = runTest {
        val now = System.currentTimeMillis()
        val op = validOp(createdAt = now - 1000) // incoming is newer
        val request = PushRequest(deviceId = "dev-1", operations = listOf(op))

        val existingSnapshot = SyncOperationSnapshot(
            opId = "existing-op",
            deviceId = "dev-2", // different device
            clientTimestamp = now - 5000, // older than incoming
            payload = """{"name":"Old","price":5.0}""",
            serverSeq = 10L,
            status = "ACCEPTED",
        )

        coEvery { syncOpRepo.findExistingIds(any()) } returns emptySet()
        coEvery { syncOpRepo.findLatestForEntity("store-1", "PRODUCT", "entity-1") } returns existingSnapshot
        coEvery { syncOpRepo.getLatestSeq(any()) } returns 11L

        val result = processor().processPush("store-1", request)

        // Incoming wins — accepted normally, no conflict
        assertEquals(listOf("op-1"), result.accepted)
        assertTrue(result.conflicts.isEmpty())
        coVerify(exactly = 0) { conflictResolver.resolve(any(), any(), any(), any()) }
        coVerify { syncOpRepo.insert("store-1", "dev-1", op) }
    }

    // ── Deduplication edge cases ────────────────────────────────────────

    @Test
    fun `batch with all duplicates returns all as accepted`() = runTest {
        val ops = (1..5).map { validOp(id = "op-$it", entityId = "e-$it") }
        val request = PushRequest(deviceId = "dev-1", operations = ops)

        coEvery { syncOpRepo.findExistingIds(any()) } returns setOf("op-1", "op-2", "op-3", "op-4", "op-5")
        coEvery { syncOpRepo.getLatestSeq(any()) } returns 100L

        val result = processor().processPush("store-1", request)

        assertEquals(5, result.accepted.size)
        assertTrue(result.rejected.isEmpty())
        coVerify(exactly = 0) { syncOpRepo.insert(any(), any(), any()) }
    }

    @Test
    fun `batch with mix of duplicates and new ops processes correctly`() = runTest {
        val ops = (1..4).map { validOp(id = "op-$it", entityId = "e-$it") }
        val request = PushRequest(deviceId = "dev-1", operations = ops)

        coEvery { syncOpRepo.findExistingIds(any()) } returns setOf("op-1", "op-3") // 2 dupes
        coEvery { syncOpRepo.findLatestForEntity(any(), any(), any()) } returns null
        coEvery { syncOpRepo.getLatestSeq(any()) } returns 100L

        val result = processor().processPush("store-1", request)

        assertEquals(4, result.accepted.size) // 2 new + 2 already-accepted
        assertTrue(result.rejected.isEmpty())
    }

    // ── Entity applier failure handling ──────────────────────────────────

    @Test
    fun `entity applier exception causes op to be rejected`() = runTest {
        val op = validOp()
        val request = PushRequest(deviceId = "dev-1", operations = listOf(op))

        coEvery { syncOpRepo.findExistingIds(any()) } returns emptySet()
        coEvery { syncOpRepo.findLatestForEntity(any(), any(), any()) } returns null
        coEvery { syncOpRepo.getLatestSeq(any()) } returns 1L
        // EntityApplier throws
        every { entityApplier.applyInTransaction(any(), any()) } throws RuntimeException("DB error")

        val result = processor().processPush("store-1", request)

        assertEquals(1, result.rejected.size)
        assertEquals("op-1", result.rejected[0])
        assertTrue(result.accepted.isEmpty())
    }

    // ── Dead letter queue ───────────────────────────────────────────────

    @Test
    fun `invalid ops are sent to dead letter queue`() = runTest {
        val invalidOp = validOp(entityType = "NONEXISTENT_TYPE")
        val request = PushRequest(deviceId = "dev-1", operations = listOf(invalidOp))

        coEvery { syncOpRepo.findExistingIds(any()) } returns emptySet()
        coEvery { syncOpRepo.getLatestSeq(any()) } returns 0L

        processor().processPush("store-1", request)

        coVerify { deadLetterRepo.insert("store-1", "dev-1", invalidOp, any()) }
    }

    @Test
    fun `dead letter insert failure does not crash processing`() = runTest {
        val invalidOp = validOp(entityType = "NONEXISTENT_TYPE")
        val request = PushRequest(deviceId = "dev-1", operations = listOf(invalidOp))

        coEvery { syncOpRepo.findExistingIds(any()) } returns emptySet()
        coEvery { syncOpRepo.getLatestSeq(any()) } returns 0L
        coEvery { deadLetterRepo.insert(any(), any(), any(), any()) } throws RuntimeException("DL write failed")

        // Should not throw — dead letter failures are logged and swallowed
        val result = processor().processPush("store-1", request)
        assertEquals(1, result.rejected.size)
    }

    // ── Multiple entity types in single batch ───────────────────────────

    @Test
    fun `batch with multiple entity types all accepted`() = runTest {
        val ops = listOf(
            validOp(id = "op-1", entityType = "PRODUCT", entityId = "p-1"),
            validOp(id = "op-2", entityType = "CATEGORY", entityId = "c-1", payload = """{"name":"Cat"}"""),
            validOp(id = "op-3", entityType = "CUSTOMER", entityId = "cu-1", payload = """{"name":"John"}"""),
            validOp(id = "op-4", entityType = "ORDER", entityId = "o-1", payload = """{"total":50.0}"""),
        )
        val request = PushRequest(deviceId = "dev-1", operations = ops)

        coEvery { syncOpRepo.findExistingIds(any()) } returns emptySet()
        coEvery { syncOpRepo.findLatestForEntity(any(), any(), any()) } returns null
        coEvery { syncOpRepo.getLatestSeq(any()) } returns 4L

        val result = processor().processPush("store-1", request)

        assertEquals(4, result.accepted.size)
        assertTrue(result.rejected.isEmpty())
    }

    // ── Server timestamp in response ────────────────────────────────────

    @Test
    fun `response contains correct server timestamp from repo`() = runTest {
        val request = PushRequest(deviceId = "dev-1", operations = emptyList())

        coEvery { syncOpRepo.findExistingIds(any()) } returns emptySet()
        coEvery { syncOpRepo.getLatestSeq("store-1") } returns 42L

        val result = processor().processPush("store-1", request)

        assertEquals(42L, result.serverTimestamp)
    }

    // ── Metrics detail ──────────────────────────────────────────────────

    @Test
    fun `rejected ops increment rejection counter`() = runTest {
        val invalidOp = validOp(entityType = "BOGUS_TYPE")
        val request = PushRequest(deviceId = "dev-1", operations = listOf(invalidOp))

        coEvery { syncOpRepo.findExistingIds(any()) } returns emptySet()
        coEvery { syncOpRepo.getLatestSeq(any()) } returns 0L

        val initialDeadLetters = metrics.deadLettersTotal.get()
        processor().processPush("store-1", request)

        assertTrue(metrics.deadLettersTotal.get() > initialDeadLetters)
    }

    @Test
    fun `conflict increments conflict counter`() = runTest {
        val now = System.currentTimeMillis()
        val op = validOp(createdAt = now - 5000)
        val request = PushRequest(deviceId = "dev-1", operations = listOf(op))

        val existingSnapshot = SyncOperationSnapshot(
            opId = "existing-op",
            deviceId = "dev-2",
            clientTimestamp = now - 1000,
            payload = """{"name":"Existing","price":20.0}""",
            serverSeq = 10L,
            status = "ACCEPTED",
        )

        coEvery { syncOpRepo.findExistingIds(any()) } returns emptySet()
        coEvery { syncOpRepo.findLatestForEntity(any(), any(), any()) } returns existingSnapshot
        coEvery { syncOpRepo.getLatestSeq(any()) } returns 11L
        coEvery { conflictResolver.resolve(any(), any(), any(), any()) } returns
            ServerConflictResolver.ConflictResolution("c-1", "{}", false, ServerConflictResolver.ResolutionStrategy.LWW_TIMESTAMP)

        val initialConflicts = metrics.conflictsTotal.get()
        processor().processPush("store-1", request)

        assertTrue(metrics.conflictsTotal.get() > initialConflicts)
    }

    // ── SyncNotification entityTypes ─────────────────────────────────────

    @Test
    fun `SyncNotification includes entityTypes field`() {
        val notification = SyncProcessor.SyncNotification(
            storeId = "store-1",
            senderDeviceId = "dev-1",
            operationCount = 3,
            latestSeq = 42L,
            entityTypes = listOf("PRODUCT", "CATEGORY", "ORDER"),
        )
        assertEquals(3, notification.entityTypes.size)
        assertTrue("PRODUCT" in notification.entityTypes)
        assertTrue("ORDER" in notification.entityTypes)
    }

    @Test
    fun `SyncNotification entityTypes defaults to empty list`() {
        val notification = SyncProcessor.SyncNotification(
            storeId = "store-1",
            senderDeviceId = "dev-1",
            operationCount = 0,
            latestSeq = 0L,
        )
        assertTrue(notification.entityTypes.isEmpty())
    }
}
