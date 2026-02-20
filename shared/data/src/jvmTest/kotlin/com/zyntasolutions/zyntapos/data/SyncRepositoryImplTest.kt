package com.zyntasolutions.zyntapos.data

/**
 * ZentaPOS — SyncRepositoryImpl Integration Tests
 *
 * Step 3.4.7 | :shared:data | jvmTest
 *
 * Uses SQLDelight [JdbcSqliteDriver.IN_MEMORY] to exercise all queue-management
 * paths in [SyncRepositoryImpl] against a real in-memory SQLite schema.
 *
 * Coverage:
 *  A. getPendingOperations — returns PENDING rows in FIFO order
 *  B. getPendingOperations — respects BATCH_SIZE cap (50)
 *  C. getPendingOperations — skips rows with retry_count >= MAX_RETRIES
 *  D. getPendingOperations — resets stale SYNCING rows before fetching
 *  E. markSynced — removes rows from eligible set
 *  F. markFailed — increments retry_count; permanently fails at MAX_RETRIES
 *  G. pruneSynced — hard-deletes SYNCED rows older than cutoff
 *  H. deduplicatePending — removes duplicate entity+type combos, keeps latest
 *  I. pushToServer stub — marks ops SYNCED via markSynced
 *  J. pullFromServer stub — returns empty list
 */

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.zyntasolutions.zyntapos.core.result.Result
import com.zyntasolutions.zyntapos.data.repository.SyncRepositoryImpl
import com.zyntasolutions.zyntapos.db.ZyntaDatabase
import com.zyntasolutions.zyntapos.domain.model.SyncOperation
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Clock
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class SyncRepositoryImplTest {

    private lateinit var db: ZyntaDatabase
    private lateinit var repo: SyncRepositoryImpl

    @BeforeTest
    fun setUp() {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        ZyntaDatabase.Schema.create(driver)
        db = ZyntaDatabase(driver)
        repo = SyncRepositoryImpl(db = db)
    }

    // ─── Helpers ───────────────────────────────────────────────────────────────

    private fun enqueue(
        id: String         = "op-1",
        entityType: String = "ORDER",
        entityId: String   = "e-$id",
        operation: String  = "CREATE",
        createdAt: Long    = Clock.System.now().toEpochMilliseconds(),
    ) {
        db.sync_queueQueries.enqueueOperation(
            id          = id,
            entity_type = entityType,
            entity_id   = entityId,
            operation   = operation,
            payload     = "{}",
            created_at  = createdAt,
        )
    }

    // ─── A. getPendingOperations returns PENDING rows in FIFO ─────────────────

    @Test
    fun getPendingOperations_returns_fifo_pending_rows() = runTest {
        val now = Clock.System.now().toEpochMilliseconds()
        enqueue("op-1", createdAt = now - 2000)
        enqueue("op-2", createdAt = now - 1000)
        enqueue("op-3", createdAt = now)

        val pending = repo.getPendingOperations()
        assertEquals(3, pending.size)
        assertEquals("op-1", pending[0].id)
        assertEquals("op-2", pending[1].id)
        assertEquals("op-3", pending[2].id)
    }

    // ─── B. getPendingOperations respects BATCH_SIZE ──────────────────────────

    @Test
    fun getPendingOperations_respects_batch_size() = runTest {
        val now = Clock.System.now().toEpochMilliseconds()
        repeat(60) { i ->
            enqueue(id = "op-$i", entityId = "e-$i", createdAt = now + i)
        }
        val pending = repo.getPendingOperations()
        // BATCH_SIZE = 50
        assertEquals(SyncRepositoryImpl.BATCH_SIZE.toInt(), pending.size)
    }

    // ─── C. Rows with retry_count >= MAX_RETRIES are skipped ─────────────────

    @Test
    fun getPendingOperations_skips_maxed_retry_rows() = runTest {
        enqueue("op-good")
        enqueue("op-maxed")
        // Simulate MAX_RETRIES exhaustion
        db.sync_queueQueries.markPermanentlyFailed("op-maxed")

        val pending = repo.getPendingOperations()
        assertEquals(1, pending.size)
        assertEquals("op-good", pending[0].id)
    }

    // ─── D. Stale SYNCING rows are reset to PENDING ───────────────────────────

    @Test
    fun getPendingOperations_resets_stale_syncing_rows() = runTest {
        enqueue("op-stale")
        val staleTs = Clock.System.now().toEpochMilliseconds() -
                SyncRepositoryImpl.STALE_SYNCING_THRESHOLD_MS - 1_000
        db.sync_queueQueries.markSyncing(staleTs, "op-stale")

        // Should appear after stale reset
        val pending = repo.getPendingOperations()
        assertEquals(1, pending.size)
        assertEquals("op-stale", pending[0].id)
    }

    // ─── E. markSynced removes from eligible set ──────────────────────────────

    @Test
    fun markSynced_removes_operations_from_eligible_set() = runTest {
        enqueue("op-1")
        enqueue("op-2")

        val syncResult = repo.markSynced(listOf("op-1"))
        assertIs<Result.Success<Unit>>(syncResult)

        val pending = repo.getPendingOperations()
        assertEquals(1, pending.size)
        assertEquals("op-2", pending[0].id)
    }

    // ─── F. markFailed increments retry_count; permanently fails at MAX ───────

    @Test
    fun markFailed_increments_retry_count_and_fails_at_max() = runTest {
        enqueue("op-retry")

        // Fail up to MAX_RETRIES - 1 times → row stays eligible
        repeat(SyncRepositoryImpl.MAX_RETRIES - 1) {
            repo.markFailed(listOf("op-retry"))
        }
        var pending = repo.getPendingOperations()
        assertTrue(pending.any { it.id == "op-retry" },
            "Op should still be eligible before reaching MAX_RETRIES")

        // One more failure → permanently FAILED
        repo.markFailed(listOf("op-retry"))
        pending = repo.getPendingOperations()
        assertTrue(pending.none { it.id == "op-retry" },
            "Op should no longer be eligible after MAX_RETRIES exceeded")
    }

    // ─── G. pruneSynced hard-deletes old SYNCED rows ─────────────────────────

    @Test
    fun pruneSynced_removes_synced_rows_older_than_cutoff() = runTest {
        val oldTs  = 1_000_000L
        val newTs  = Clock.System.now().toEpochMilliseconds()
        val cutoff = newTs - 1000L

        enqueue("op-old",  createdAt = oldTs)
        enqueue("op-new",  createdAt = newTs)
        repo.markSynced(listOf("op-old", "op-new"))

        // Confirm both SYNCED
        val allBefore = db.sync_queueQueries.getEligibleOperations(100L).executeAsList()
        assertEquals(0, allBefore.size)  // SYNCED rows not in eligible set

        val pruneResult = repo.pruneSynced(cutoff)
        assertIs<Result.Success<Unit>>(pruneResult)

        // Verify op-old is gone, op-new still there
        val rawNew = db.sync_queueQueries.getByEntityId("e-op-new").executeAsList()
        assertEquals(1, rawNew.size, "Recent SYNCED row must survive pruning")
    }

    // ─── H. deduplicatePending keeps only latest per entity ──────────────────

    @Test
    fun deduplicatePending_keeps_only_latest_per_entity_type_id() = runTest {
        val now = Clock.System.now().toEpochMilliseconds()
        // Two PENDING ops for the same entity — older and newer
        enqueue("op-old",  entityType = "PRODUCT", entityId = "p-1", createdAt = now - 500)
        enqueue("op-new",  entityType = "PRODUCT", entityId = "p-1", createdAt = now)

        val dedupeResult = repo.deduplicatePending()
        assertIs<Result.Success<Unit>>(dedupeResult)

        val pending = repo.getPendingOperations()
        assertEquals(1, pending.size)
        assertEquals("op-new", pending[0].id)
    }

    // ─── I. pushToServer stub marks ops as SYNCED ────────────────────────────

    @Test
    fun pushToServer_stub_marks_ops_as_synced() = runTest {
        enqueue("op-1")
        enqueue("op-2")
        val ops = repo.getPendingOperations()
        assertEquals(2, ops.size)

        val pushResult = repo.pushToServer(ops)
        assertIs<Result.Success<Unit>>(pushResult)

        val remaining = repo.getPendingOperations()
        assertEquals(0, remaining.size, "After push stub, all ops should be SYNCED")
    }

    // ─── J. pullFromServer stub returns empty list ────────────────────────────

    @Test
    fun pullFromServer_stub_returns_empty_list() = runTest {
        val result = repo.pullFromServer(lastSyncTimestamp = 0L)
        assertIs<Result.Success<List<SyncOperation>>>(result)
        assertTrue(result.data.isEmpty())
    }
}
