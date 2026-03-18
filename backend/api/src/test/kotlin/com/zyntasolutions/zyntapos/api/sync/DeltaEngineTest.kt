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
        var lastStoreId: String? = null
        var lastDeviceId: String? = null
        override suspend fun upsert(storeId: String, deviceId: String, lastSeq: Long) {
            lastUpserted = lastSeq
            lastStoreId = storeId
            lastDeviceId = deviceId
        }
    }

    private fun op(seq: Long) = SyncOperation(
        id = "op-$seq", entityType = "PRODUCT", entityId = "e-$seq",
        operation = "UPDATE", payload = "{}", createdAt = System.currentTimeMillis(),
        serverSeq = seq,
    )

    private fun engine(rows: List<SyncOperation>, cursorRepo: StubCursorRepo = StubCursorRepo()) =
        DeltaEngine(StubSyncOpRepo(rows), cursorRepo, SyncMetrics())

    @Test
    fun `empty store returns empty operations`() { runBlocking {
        val result = engine(emptyList()).computeDelta("store-1", "dev-1", 0)
        assertTrue(result.operations.isEmpty())
        assertFalse(result.hasMore)
        assertEquals(0L, result.serverVectorClock)
    } }

    @Test
    fun `returns operations after cursor`() { runBlocking {
        val rows = (1L..5L).map { op(it) }
        val result = engine(rows).computeDelta("store-1", "dev-1", since = 2L)
        assertEquals(3, result.operations.size)
        assertEquals(3L, result.operations.first().serverSeq)
    } }

    @Test
    fun `hasMore is true when more pages exist`() { runBlocking {
        val rows = (1L..10L).map { op(it) }
        val result = engine(rows).computeDelta("store-1", "dev-1", since = 0L, limit = 5)
        assertTrue(result.hasMore)
        assertEquals(5, result.operations.size)
        assertEquals(5L, result.serverVectorClock)
    } }

    @Test
    fun `hasMore is false on last page`() { runBlocking {
        val rows = (1L..3L).map { op(it) }
        val result = engine(rows).computeDelta("store-1", "dev-1", since = 0L, limit = 5)
        assertFalse(result.hasMore)
        assertEquals(3, result.operations.size)
    } }

    @Test
    fun `cursor is persisted after successful pull`() { runBlocking {
        val cursorRepo = StubCursorRepo()
        val rows = (1L..3L).map { op(it) }
        engine(rows, cursorRepo).computeDelta("store-1", "dev-1", since = 0L)
        assertEquals(3L, cursorRepo.lastUpserted)
    } }

    @Test
    fun `limit is clamped to MAX_LIMIT`() { runBlocking {
        val rows = (1L..300L).map { op(it) }
        val result = engine(rows).computeDelta("store-1", "dev-1", since = 0L, limit = 9999)
        assertEquals(DeltaEngine.MAX_LIMIT, result.operations.size)
        assertTrue(result.hasMore)
    } }

    // ── New test cases ──────────────────────────────────────────────────

    @Test
    fun `limit clamped to minimum of 1`() { runBlocking {
        val rows = (1L..5L).map { op(it) }
        val result = engine(rows).computeDelta("store-1", "dev-1", since = 0L, limit = 0)
        // limit = 0 coerced to 1
        assertEquals(1, result.operations.size)
        assertTrue(result.hasMore)
    } }

    @Test
    fun `negative limit clamped to 1`() { runBlocking {
        val rows = (1L..5L).map { op(it) }
        val result = engine(rows).computeDelta("store-1", "dev-1", since = 0L, limit = -10)
        assertEquals(1, result.operations.size)
        assertTrue(result.hasMore)
    } }

    @Test
    fun `cursor at head returns empty - no new data`() { runBlocking {
        val rows = (1L..5L).map { op(it) }
        val result = engine(rows).computeDelta("store-1", "dev-1", since = 5L)
        assertTrue(result.operations.isEmpty())
        assertFalse(result.hasMore)
        // cursor stays at 'since' value when no new data
        assertEquals(5L, result.serverVectorClock)
    } }

    @Test
    fun `cursor beyond max seq returns empty`() { runBlocking {
        val rows = (1L..5L).map { op(it) }
        val result = engine(rows).computeDelta("store-1", "dev-1", since = 999L)
        assertTrue(result.operations.isEmpty())
        assertFalse(result.hasMore)
    } }

    @Test
    fun `default limit is DEFAULT_LIMIT`() { runBlocking {
        val rows = (1L..100L).map { op(it) }
        val result = engine(rows).computeDelta("store-1", "dev-1", since = 0L)
        // Default limit = 50
        assertEquals(DeltaEngine.DEFAULT_LIMIT, result.operations.size)
        assertTrue(result.hasMore)
    } }

    @Test
    fun `cursor persisted with correct storeId and deviceId`() { runBlocking {
        val cursorRepo = StubCursorRepo()
        val rows = (1L..3L).map { op(it) }
        engine(rows, cursorRepo).computeDelta("store-abc", "device-xyz", since = 0L)
        assertEquals("store-abc", cursorRepo.lastStoreId)
        assertEquals("device-xyz", cursorRepo.lastDeviceId)
        assertEquals(3L, cursorRepo.lastUpserted)
    } }

    @Test
    fun `operations returned in ascending server_seq order`() { runBlocking {
        // The stub mirrors production behavior: filter by serverSeq > afterSeq, take(limit)
        // In production, the query orders by serverSeq ASC; stub preserves insertion order
        val rows = (1L..5L).map { op(it) }
        val result = engine(rows).computeDelta("store-1", "dev-1", since = 0L, limit = 10)
        val seqs = result.operations.map { it.serverSeq }
        assertEquals(listOf(1L, 2L, 3L, 4L, 5L), seqs)
    } }

    @Test
    fun `single row at exact limit boundary - hasMore is false`() { runBlocking {
        val rows = listOf(op(1))
        val result = engine(rows).computeDelta("store-1", "dev-1", since = 0L, limit = 1)
        assertEquals(1, result.operations.size)
        assertFalse(result.hasMore)
    } }

    @Test
    fun `two rows with limit 1 - hasMore is true`() { runBlocking {
        val rows = listOf(op(1), op(2))
        val result = engine(rows).computeDelta("store-1", "dev-1", since = 0L, limit = 1)
        assertEquals(1, result.operations.size)
        assertTrue(result.hasMore)
        assertEquals(1L, result.serverVectorClock)
    } }

    @Test
    fun `serverVectorClock is last serverSeq in page`() { runBlocking {
        val rows = (1L..10L).map { op(it) }
        val result = engine(rows).computeDelta("store-1", "dev-1", since = 0L, limit = 7)
        assertEquals(7L, result.serverVectorClock)
    } }

    @Test
    fun `cursor persisted even when empty result`() { runBlocking {
        val cursorRepo = StubCursorRepo()
        engine(emptyList(), cursorRepo).computeDelta("store-1", "dev-1", since = 0L)
        // Even with no results, cursor should be persisted (at the 'since' value)
        assertEquals(0L, cursorRepo.lastUpserted)
    } }

    @Test
    fun `multiple sequential pulls advance cursor correctly`() { runBlocking {
        val cursorRepo = StubCursorRepo()
        val rows = (1L..20L).map { op(it) }
        val eng = engine(rows, cursorRepo)

        // First pull: get ops 1-5
        val result1 = eng.computeDelta("store-1", "dev-1", since = 0L, limit = 5)
        assertEquals(5, result1.operations.size)
        assertTrue(result1.hasMore)
        assertEquals(5L, cursorRepo.lastUpserted)

        // Second pull: get ops 6-10
        val result2 = eng.computeDelta("store-1", "dev-1", since = 5L, limit = 5)
        assertEquals(5, result2.operations.size)
        assertTrue(result2.hasMore)
        assertEquals(10L, cursorRepo.lastUpserted)

        // Third pull: get ops 11-15
        val result3 = eng.computeDelta("store-1", "dev-1", since = 10L, limit = 5)
        assertEquals(5, result3.operations.size)
        assertTrue(result3.hasMore)
        assertEquals(15L, cursorRepo.lastUpserted)
    } }

    @Test
    fun `limit at exactly MAX_LIMIT returns correct count`() { runBlocking {
        val rows = (1L..250L).map { op(it) }
        val result = engine(rows).computeDelta("store-1", "dev-1", since = 0L, limit = DeltaEngine.MAX_LIMIT)
        assertEquals(DeltaEngine.MAX_LIMIT, result.operations.size)
        assertTrue(result.hasMore)
    } }
}
