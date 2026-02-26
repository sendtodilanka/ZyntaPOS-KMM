package com.zyntasolutions.zyntapos.debug

import com.zyntasolutions.zyntapos.core.result.NetworkException
import com.zyntasolutions.zyntapos.core.result.Result
import com.zyntasolutions.zyntapos.debug.actions.NetworkActionHandlerImpl
import com.zyntasolutions.zyntapos.domain.model.SyncOperation
import com.zyntasolutions.zyntapos.domain.repository.SyncRepository
import kotlinx.coroutines.test.runTest
import kotlin.time.Clock
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * Unit tests for [NetworkActionHandlerImpl].
 *
 * All tests use a [StubSyncRepository] with configurable behaviour to avoid
 * real network or database access. Each test verifies a distinct behavioural
 * contract of [NetworkActionHandlerImpl]:
 *
 * - [NetworkActionHandlerImpl.getPendingOperations] wraps repository results.
 * - [NetworkActionHandlerImpl.clearSyncQueue] delegates to [SyncRepository.markSynced]
 *   with the correct IDs — or short-circuits when the queue is empty.
 * - [NetworkActionHandlerImpl.forceSyncNow] delegates to [SyncRepository.pushToServer]
 *   with the pending ops — or short-circuits when the queue is empty.
 */
class NetworkActionHandlerTest {

    private val now = Clock.System.now()

    // ── Test data ─────────────────────────────────────────────────────────────

    private fun makeSyncOp(id: String) = SyncOperation(
        id         = id,
        entityType = "product",
        entityId   = "entity-$id",
        operation  = SyncOperation.Operation.INSERT,
        payload    = """{"name":"Product $id"}""",
        createdAt  = now,
    )

    private val op1 = makeSyncOp("op-1")
    private val op2 = makeSyncOp("op-2")
    private val op3 = makeSyncOp("op-3")

    // ── Stub repository ────────────────────────────────────────────────────────

    private class StubSyncRepository(
        private val pendingOps: List<SyncOperation> = emptyList(),
        private val throwOnGetPending: Boolean = false,
        private var markSyncedResult: Result<Unit> = Result.Success(Unit),
        private var pushToServerResult: Result<Unit> = Result.Success(Unit),
        private val throwOnMarkSynced: Boolean = false,
        private val throwOnPushToServer: Boolean = false,
    ) : SyncRepository {
        val markedSyncedIds = mutableListOf<String>()
        val pushedOps = mutableListOf<SyncOperation>()
        var markSyncedCallCount = 0
        var pushToServerCallCount = 0

        override suspend fun getPendingOperations(): List<SyncOperation> {
            if (throwOnGetPending) throw RuntimeException("DB read failed")
            return pendingOps
        }

        override suspend fun markSynced(ids: List<String>): Result<Unit> {
            markSyncedCallCount++
            if (throwOnMarkSynced) throw RuntimeException("Cannot update sync status")
            markedSyncedIds += ids
            return markSyncedResult
        }

        override suspend fun pushToServer(ops: List<SyncOperation>): Result<Unit> {
            pushToServerCallCount++
            if (throwOnPushToServer) throw RuntimeException("Network unreachable")
            pushedOps += ops
            return pushToServerResult
        }

        override suspend fun pullFromServer(lastSyncTimestamp: Long): Result<List<SyncOperation>> =
            Result.Success(emptyList())
    }

    // ── getPendingOperations ───────────────────────────────────────────────────

    @Test
    fun `getPendingOperations returns Success with empty list when queue is empty`() = runTest {
        val handler = NetworkActionHandlerImpl(StubSyncRepository())

        val result = handler.getPendingOperations()

        assertIs<Result.Success<List<SyncOperation>>>(result)
        assertTrue(result.data.isEmpty())
    }

    @Test
    fun `getPendingOperations returns Success with all pending ops`() = runTest {
        val handler = NetworkActionHandlerImpl(StubSyncRepository(pendingOps = listOf(op1, op2, op3)))

        val result = handler.getPendingOperations()

        assertIs<Result.Success<List<SyncOperation>>>(result)
        assertEquals(3, result.data.size)
    }

    @Test
    fun `getPendingOperations preserves op IDs in returned list`() = runTest {
        val handler = NetworkActionHandlerImpl(StubSyncRepository(pendingOps = listOf(op1, op2)))

        val result = handler.getPendingOperations() as Result.Success
        val ids = result.data.map { it.id }

        assertTrue(ids.contains("op-1"))
        assertTrue(ids.contains("op-2"))
    }

    @Test
    fun `getPendingOperations returns Result Error when repository throws`() = runTest {
        val handler = NetworkActionHandlerImpl(StubSyncRepository(throwOnGetPending = true))

        val result = handler.getPendingOperations()

        assertIs<Result.Error>(result)
        assertIs<NetworkException>(result.exception)
    }

    @Test
    fun `getPendingOperations Error message contains Failed to load sync queue`() = runTest {
        val handler = NetworkActionHandlerImpl(StubSyncRepository(throwOnGetPending = true))

        val result = handler.getPendingOperations()

        assertIs<Result.Error>(result)
        assertTrue(result.exception.message.contains("Failed to load sync queue"))
    }

    // ── clearSyncQueue ─────────────────────────────────────────────────────────

    @Test
    fun `clearSyncQueue returns Success without calling markSynced when queue is empty`() = runTest {
        val stub = StubSyncRepository(pendingOps = emptyList())
        val handler = NetworkActionHandlerImpl(stub)

        val result = handler.clearSyncQueue()

        assertIs<Result.Success<Unit>>(result)
        assertEquals(0, stub.markSyncedCallCount,
            "markSynced must NOT be called when there are no pending ops")
    }

    @Test
    fun `clearSyncQueue calls markSynced with all pending op IDs`() = runTest {
        val stub = StubSyncRepository(pendingOps = listOf(op1, op2, op3))
        val handler = NetworkActionHandlerImpl(stub)

        handler.clearSyncQueue()

        assertTrue(stub.markedSyncedIds.containsAll(listOf("op-1", "op-2", "op-3")),
            "Expected all 3 op IDs to be marked synced, got: ${stub.markedSyncedIds}")
    }

    @Test
    fun `clearSyncQueue returns the Result from markSynced on success`() = runTest {
        val stub = StubSyncRepository(
            pendingOps       = listOf(op1),
            markSyncedResult = Result.Success(Unit),
        )
        val handler = NetworkActionHandlerImpl(stub)

        val result = handler.clearSyncQueue()

        assertIs<Result.Success<Unit>>(result)
    }

    @Test
    fun `clearSyncQueue returns Result Error when markSynced throws`() = runTest {
        val stub = StubSyncRepository(
            pendingOps      = listOf(op1),
            throwOnMarkSynced = true,
        )
        val handler = NetworkActionHandlerImpl(stub)

        val result = handler.clearSyncQueue()

        assertIs<Result.Error>(result)
        assertIs<NetworkException>(result.exception)
    }

    @Test
    fun `clearSyncQueue Error message contains Failed to clear sync queue`() = runTest {
        val stub = StubSyncRepository(
            pendingOps        = listOf(op1),
            throwOnMarkSynced = true,
        )
        val handler = NetworkActionHandlerImpl(stub)

        val result = handler.clearSyncQueue()

        assertIs<Result.Error>(result)
        assertTrue(result.exception.message.contains("Failed to clear sync queue"))
    }

    @Test
    fun `clearSyncQueue with single op passes exactly that id to markSynced`() = runTest {
        val stub = StubSyncRepository(pendingOps = listOf(op1))
        val handler = NetworkActionHandlerImpl(stub)

        handler.clearSyncQueue()

        assertEquals(listOf("op-1"), stub.markedSyncedIds)
    }

    @Test
    fun `clearSyncQueue returns Result Error when getPendingOperations throws`() = runTest {
        val stub = StubSyncRepository(throwOnGetPending = true)
        val handler = NetworkActionHandlerImpl(stub)

        val result = handler.clearSyncQueue()

        assertIs<Result.Error>(result)
        assertIs<NetworkException>(result.exception)
    }

    // ── forceSyncNow ───────────────────────────────────────────────────────────

    @Test
    fun `forceSyncNow returns Success without calling pushToServer when queue is empty`() = runTest {
        val stub = StubSyncRepository(pendingOps = emptyList())
        val handler = NetworkActionHandlerImpl(stub)

        val result = handler.forceSyncNow()

        assertIs<Result.Success<Unit>>(result)
        assertEquals(0, stub.pushToServerCallCount,
            "pushToServer must NOT be called when there are no pending ops")
    }

    @Test
    fun `forceSyncNow calls pushToServer with all pending ops`() = runTest {
        val stub = StubSyncRepository(pendingOps = listOf(op1, op2))
        val handler = NetworkActionHandlerImpl(stub)

        handler.forceSyncNow()

        assertEquals(1, stub.pushToServerCallCount)
        assertEquals(2, stub.pushedOps.size)
    }

    @Test
    fun `forceSyncNow pushes ops preserving their IDs`() = runTest {
        val stub = StubSyncRepository(pendingOps = listOf(op1, op2, op3))
        val handler = NetworkActionHandlerImpl(stub)

        handler.forceSyncNow()

        val pushedIds = stub.pushedOps.map { it.id }
        assertTrue(pushedIds.containsAll(listOf("op-1", "op-2", "op-3")))
    }

    @Test
    fun `forceSyncNow returns the Result from pushToServer on success`() = runTest {
        val stub = StubSyncRepository(
            pendingOps         = listOf(op1),
            pushToServerResult = Result.Success(Unit),
        )
        val handler = NetworkActionHandlerImpl(stub)

        val result = handler.forceSyncNow()

        assertIs<Result.Success<Unit>>(result)
    }

    @Test
    fun `forceSyncNow returns Result Error when pushToServer throws`() = runTest {
        val stub = StubSyncRepository(
            pendingOps           = listOf(op1),
            throwOnPushToServer  = true,
        )
        val handler = NetworkActionHandlerImpl(stub)

        val result = handler.forceSyncNow()

        assertIs<Result.Error>(result)
        assertIs<NetworkException>(result.exception)
    }

    @Test
    fun `forceSyncNow Error message contains Sync failed`() = runTest {
        val stub = StubSyncRepository(
            pendingOps          = listOf(op1),
            throwOnPushToServer = true,
        )
        val handler = NetworkActionHandlerImpl(stub)

        val result = handler.forceSyncNow()

        assertIs<Result.Error>(result)
        assertTrue(result.exception.message.contains("Sync failed"))
    }

    @Test
    fun `forceSyncNow returns Result Error when getPendingOperations throws`() = runTest {
        val stub = StubSyncRepository(throwOnGetPending = true)
        val handler = NetworkActionHandlerImpl(stub)

        val result = handler.forceSyncNow()

        assertIs<Result.Error>(result)
        assertIs<NetworkException>(result.exception)
    }

    @Test
    fun `forceSyncNow calls pushToServer exactly once with non-empty queue`() = runTest {
        val stub = StubSyncRepository(pendingOps = listOf(op1, op2, op3))
        val handler = NetworkActionHandlerImpl(stub)

        handler.forceSyncNow()

        assertEquals(1, stub.pushToServerCallCount,
            "pushToServer should be called exactly once for a batch")
    }

    @Test
    fun `clearSyncQueue and forceSyncNow are independent - both succeed when ops are present`() = runTest {
        val clearStub = StubSyncRepository(pendingOps = listOf(op1))
        val syncStub  = StubSyncRepository(pendingOps = listOf(op2))

        val clearResult = NetworkActionHandlerImpl(clearStub).clearSyncQueue()
        val syncResult  = NetworkActionHandlerImpl(syncStub).forceSyncNow()

        assertIs<Result.Success<Unit>>(clearResult)
        assertIs<Result.Success<Unit>>(syncResult)
    }
}
