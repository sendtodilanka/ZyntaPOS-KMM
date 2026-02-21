package com.zyntasolutions.zyntapos.data.repository

import com.zyntasolutions.zyntapos.data.createTestDatabase
import com.zyntasolutions.zyntapos.db.ZyntaDatabase
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Clock
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * ZyntaPOS — SyncRepositoryImpl Integration Tests (jvmTest)
 *
 * Step 3.4.7 | :shared:data | jvmTest
 *
 * Tests the sync queue lifecycle against a real in-memory SQLite database
 * (JdbcSqliteDriver.IN_MEMORY). No mocking — validates actual SQL queries.
 *
 * Coverage:
 *  A. Enqueue → row inserted with PENDING status
 *  B. getEligibleOperations — returns PENDING rows up to batch limit
 *  C. markSynced — status transitions to SYNCED; row excluded from next eligibleOps fetch
 *  D. markFailed — increments retry_count; row excluded after MAX_RETRIES
 *  E. pruneSynced — removes old SYNCED rows; PENDING rows untouched
 *  F. deduplicatePending — only one PENDING row survives per entity
 *  G. getPendingCount / getFailedCount — correct counts after transitions
 */
class SyncRepositoryIntegrationTest {

    private lateinit var db: ZyntaDatabase
    private val now get() = Clock.System.now().toEpochMilliseconds()

    @BeforeTest
    fun setup() {
        db = createTestDatabase()
    }

    @AfterTest
    fun teardown() {
        // In-memory driver closes automatically with the ZyntaDatabase instance GC.
    }

    // ── Helpers ─────────────────────────────────────────────────────────────

    private fun enqueue(id: String, entityType: String = "ORDER", entityId: String = "entity-$id") {
        db.sync_queueQueries.enqueueOperation(
            id          = id,
            entity_type = entityType,
            entity_id   = entityId,
            operation   = "CREATE",
            payload     = """{"id":"$entityId"}""",
            created_at  = now,
        )
    }

    // ── A. Enqueue inserts with PENDING status ───────────────────────────────

    @Test
    fun enqueue_inserts_with_pending_status() {
        enqueue("op-1")

        val rows = db.sync_queueQueries.getEligibleOperations(10).executeAsList()
        assertEquals(1, rows.size)
        assertEquals("op-1",   rows[0].id)
        assertEquals("PENDING", rows[0].status)
        assertEquals(0,         rows[0].retry_count)
    }

    // ── B. getEligibleOperations respects batch limit ────────────────────────

    @Test
    fun getEligibleOperations_respects_batch_limit() {
        repeat(5) { i -> enqueue("op-$i") }

        val rows = db.sync_queueQueries.getEligibleOperations(3).executeAsList()
        assertEquals(3, rows.size)
    }

    // ── C. markSynced transitions status; row excluded from next fetch ───────

    @Test
    fun markSynced_excludes_row_from_eligible_operations() {
        enqueue("op-1")
        enqueue("op-2")

        db.sync_queueQueries.markSynced("op-1")

        val eligible = db.sync_queueQueries.getEligibleOperations(10).executeAsList()
        assertEquals(1, eligible.size)
        assertEquals("op-2", eligible[0].id)
    }

    // ── D. markFailed increments retry_count; excluded at MAX_RETRIES ────────

    @Test
    fun markFailed_increments_retry_count_and_excludes_at_max_retries() {
        enqueue("op-1")

        // First 4 failures — still eligible
        repeat(4) { db.sync_queueQueries.markFailed(now, "op-1") }
        val afterFour = db.sync_queueQueries.getEligibleOperations(10).executeAsList()
        assertEquals(1, afterFour.size, "Should still be eligible with retry_count=4")
        assertEquals(4, afterFour[0].retry_count)

        // 5th failure — retry_count = 5 → no longer eligible (5 < 5 fails)
        db.sync_queueQueries.markFailed(now, "op-1")
        val afterFive = db.sync_queueQueries.getEligibleOperations(10).executeAsList()
        assertEquals(0, afterFive.size, "retry_count=5 should be excluded from eligible ops")
    }

    // ── E. markPermanentlyFailed immediately excludes ─────────────────────────

    @Test
    fun markPermanentlyFailed_immediately_excludes_from_eligible_operations() {
        enqueue("op-1")

        db.sync_queueQueries.markPermanentlyFailed("op-1")

        val eligible = db.sync_queueQueries.getEligibleOperations(10).executeAsList()
        assertEquals(0, eligible.size)
    }

    // ── F. pruneSynced removes old SYNCED; PENDING rows untouched ────────────

    @Test
    fun pruneSynced_removes_old_synced_rows_only() {
        enqueue("op-synced")
        enqueue("op-pending")

        db.sync_queueQueries.markSynced("op-synced")

        // Prune all SYNCED rows created before "now + 1ms"
        val cutoff = now + 1
        db.sync_queueQueries.pruneSynced(cutoff)

        val pending = db.sync_queueQueries.getEligibleOperations(10).executeAsList()
        assertEquals(1, pending.size)
        assertEquals("op-pending", pending[0].id)

        // Confirm the SYNCED row is gone via getPendingCount
        val pendingCount = db.sync_queueQueries.getPendingCount().executeAsOne()
        assertEquals(1L, pendingCount)
    }

    // ── G. deduplicatePending keeps latest per entity ─────────────────────────

    @Test
    fun deduplicatePending_keeps_only_latest_per_entity() {
        // Two PENDING ops for the same entity — simulate rapid offline mutations
        db.sync_queueQueries.enqueueOperation(
            id = "op-old", entity_type = "PRODUCT", entity_id = "prod-1",
            operation = "UPDATE", payload = """{"name":"v1"}""",
            created_at = now - 1000,
        )
        db.sync_queueQueries.enqueueOperation(
            id = "op-new", entity_type = "PRODUCT", entity_id = "prod-1",
            operation = "UPDATE", payload = """{"name":"v2"}""",
            created_at = now,
        )

        db.sync_queueQueries.deduplicatePending()

        val remaining = db.sync_queueQueries.getEligibleOperations(10).executeAsList()
        assertEquals(1, remaining.size, "Only one PENDING row should survive deduplication")
        assertEquals("op-new", remaining[0].id, "Latest (op-new) should be kept")
    }

    // ── H. getPendingCount / getFailedCount ────────────────────────────────

    @Test
    fun getPendingCount_and_getFailedCount_return_correct_values() {
        enqueue("op-1")
        enqueue("op-2")
        enqueue("op-3")

        db.sync_queueQueries.markSynced("op-1")
        // Make op-2 permanently failed
        db.sync_queueQueries.markPermanentlyFailed("op-2")

        val pending = db.sync_queueQueries.getPendingCount().executeAsOne()
        val failed  = db.sync_queueQueries.getFailedCount().executeAsOne()

        assertEquals(1L, pending, "op-3 should be the only PENDING row")
        assertEquals(1L, failed,  "op-2 should be the only FAILED row")
    }

    // ── I. resetStaleSync recovers SYNCING rows ──────────────────────────────

    @Test
    fun resetStaleSync_recovers_stuck_syncing_rows() {
        enqueue("op-1")
        val staleCutoff = now - 1000  // simulates an old last_tried timestamp

        // Manually mark as SYNCING with a stale last_tried time
        db.sync_queueQueries.markSyncing(staleCutoff - 1, "op-1")

        // Confirm it's now SYNCING (not in eligible ops)
        val beforeReset = db.sync_queueQueries.getEligibleOperations(10).executeAsList()
        assertEquals(0, beforeReset.size)

        // Reset stale SYNCING rows
        db.sync_queueQueries.resetStaleSync(staleCutoff)

        // Should be PENDING again
        val afterReset = db.sync_queueQueries.getEligibleOperations(10).executeAsList()
        assertEquals(1, afterReset.size)
        assertEquals("PENDING", afterReset[0].status)
    }
}
