package com.zyntasolutions.zyntapos.api.sync

import com.zyntasolutions.zyntapos.api.models.SyncOperation
import com.zyntasolutions.zyntapos.api.repository.SyncCursorRepository
import com.zyntasolutions.zyntapos.api.repository.SyncOperationRepository
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DeltaEngineTest {

    /** Stub repo that returns a fixed list of operations. */
    private class StubSyncOpRepo(private val rows: List<SyncOperation>) : SyncOperationRepository() {
        override suspend fun findAfterSeq(storeId: String, afterSeq: Long, limit: Int): List<SyncOperation> =
            rows.filter { it.serverSeq > afterSeq }.take(limit)

        override suspend fun getLatestSeq(storeId: String): Long =
            rows.maxOfOrNull { it.serverSeq } ?: 0L
    }

    /** No-op cursor repo. */
    private class StubCursorRepo : SyncCursorRepository() {
        var lastUpserted: Long = -1L
        override suspend fun upsert(storeId: String, deviceId: String, seq: Long) { lastUpserted = seq }
    }

    private fun op(seq: Long) = SyncOperation(
        id = "op-$seq", entityType = "PRODUCT", entityId = "e-$seq",
        operation = "UPDATE", payload = "{}", createdAt = System.currentTimeMillis(),
        serverSeq = seq,
    )

    private fun engine(rows: List<SyncOperation>, cursorRepo: StubCursorRepo = StubCursorRepo()) =
        DeltaEngine(StubSyncOpRepo(rows), cursorRepo, SyncMetrics())

    @Test
    fun `empty store returns empty operations`() = runBlocking {
        val result = engine(emptyList()).computeDelta("store-1", "dev-1", 0)
        assertTrue(result.operations.isEmpty())
        assertFalse(result.hasMore)
        assertEquals(0L, result.serverVectorClock)
    }

    @Test
    fun `returns operations after cursor`() = runBlocking {
        val rows = (1L..5L).map { op(it) }
        val result = engine(rows).computeDelta("store-1", "dev-1", since = 2L)
        assertEquals(3, result.operations.size)
        assertEquals(3L, result.operations.first().serverSeq)
    }

    @Test
    fun `hasMore is true when more pages exist`() = runBlocking {
        val rows = (1L..10L).map { op(it) }
        val result = engine(rows).computeDelta("store-1", "dev-1", since = 0L, limit = 5)
        assertTrue(result.hasMore)
        assertEquals(5, result.operations.size)
        assertEquals(5L, result.serverVectorClock)
    }

    @Test
    fun `hasMore is false on last page`() = runBlocking {
        val rows = (1L..3L).map { op(it) }
        val result = engine(rows).computeDelta("store-1", "dev-1", since = 0L, limit = 5)
        assertFalse(result.hasMore)
        assertEquals(3, result.operations.size)
    }

    @Test
    fun `cursor is persisted after successful pull`() = runBlocking {
        val cursorRepo = StubCursorRepo()
        val rows = (1L..3L).map { op(it) }
        engine(rows, cursorRepo).computeDelta("store-1", "dev-1", since = 0L)
        assertEquals(3L, cursorRepo.lastUpserted)
    }

    @Test
    fun `limit is clamped to MAX_LIMIT`() = runBlocking {
        val rows = (1L..300L).map { op(it) }
        val result = engine(rows).computeDelta("store-1", "dev-1", since = 0L, limit = 9999)
        assertEquals(DeltaEngine.MAX_LIMIT, result.operations.size)
        assertTrue(result.hasMore)
    }
}
