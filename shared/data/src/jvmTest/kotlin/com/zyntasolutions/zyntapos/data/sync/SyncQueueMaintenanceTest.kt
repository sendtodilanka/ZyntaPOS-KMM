package com.zyntasolutions.zyntapos.data.sync

import com.zyntasolutions.zyntapos.data.createTestDatabase
import com.zyntasolutions.zyntapos.db.ZyntaDatabase
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Clock

/**
 * Tests for [SyncQueueMaintenance] — queue pruning and deduplication.
 */
class SyncQueueMaintenanceTest {

    private lateinit var db: ZyntaDatabase
    private lateinit var maintenance: SyncQueueMaintenance

    private val now get() = Clock.System.now().toEpochMilliseconds()

    @BeforeTest
    fun setup() {
        db = createTestDatabase()
        maintenance = SyncQueueMaintenance(db)
    }

    private fun enqueue(id: String, entityType: String = "product", entityId: String = "e-1", createdAt: Long = now) {
        db.sync_queueQueries.enqueueOperation(
            id = id, entity_type = entityType, entity_id = entityId,
            operation = "UPDATE", payload = """{"id":"$entityId"}""", created_at = createdAt, store_id = "",
        )
    }

    // ── Prune SYNCED ──────────────────────────────────────────────────

    @Test
    fun run_prunesSyncedOpsOlderThanRetentionDays() {
        val eightDaysAgo = now - (8 * 86_400_000L)
        enqueue("old-synced", createdAt = eightDaysAgo)
        db.sync_queueQueries.markSynced("old-synced")

        enqueue("recent-synced", createdAt = now)
        db.sync_queueQueries.markSynced("recent-synced")

        maintenance.run()

        // Old SYNCED should be pruned; recent SYNCED should remain
        val remaining = db.sync_queueQueries.getByEntityId("old-synced").executeAsOneOrNull()
        assertEquals(null, remaining, "8-day-old SYNCED op should be pruned")

        val kept = db.sync_queueQueries.getByEntityId("recent-synced").executeAsOneOrNull()
        assertEquals("recent-synced", kept?.id, "Recent SYNCED op should be kept")
    }

    // ── Prune FAILED ──────────────────────────────────────────────────

    @Test
    fun run_prunesFailedOpsOlderThanFailedRetentionDays() {
        val thirtyOneDaysAgo = now - (31 * 86_400_000L)
        enqueue("old-failed", createdAt = thirtyOneDaysAgo)
        db.sync_queueQueries.markPermanentlyFailed("old-failed")

        val twentyNineDaysAgo = now - (29 * 86_400_000L)
        enqueue("recent-failed", createdAt = twentyNineDaysAgo)
        db.sync_queueQueries.markPermanentlyFailed("recent-failed")

        maintenance.run()

        val pruned = db.sync_queueQueries.getByEntityId("old-failed").executeAsOneOrNull()
        assertEquals(null, pruned, "31-day-old FAILED op should be pruned")

        val kept = db.sync_queueQueries.getByEntityId("recent-failed").executeAsOneOrNull()
        assertEquals("recent-failed", kept?.id, "29-day-old FAILED op should be kept")
    }

    // ── Deduplicate PENDING ───────────────────────────────────────────

    @Test
    fun run_deduplicatesPendingKeepsLatestOnly() {
        // 3 PENDING ops for the same entity — only the latest should survive
        enqueue("op-old", entityId = "p-1", createdAt = now - 3000)
        enqueue("op-mid", entityId = "p-1", createdAt = now - 2000)
        enqueue("op-new", entityId = "p-1", createdAt = now - 1000)

        maintenance.run()

        val eligible = db.sync_queueQueries.getEligibleOperations(store_id = "", batch_size = 10).executeAsList()
        assertEquals(1, eligible.size, "Only the latest PENDING op per entity should survive")
        assertEquals("op-new", eligible.first().id)
    }

    // ── PENDING ops not pruned ────────────────────────────────────────

    @Test
    fun run_doesNotPrunePendingOps() {
        val oldPending = now - (100 * 86_400_000L) // 100 days ago
        enqueue("ancient-pending", createdAt = oldPending)

        maintenance.run()

        val kept = db.sync_queueQueries.getByEntityId("ancient-pending").executeAsOneOrNull()
        assertEquals("ancient-pending", kept?.id, "PENDING ops must never be pruned regardless of age")
    }
}
