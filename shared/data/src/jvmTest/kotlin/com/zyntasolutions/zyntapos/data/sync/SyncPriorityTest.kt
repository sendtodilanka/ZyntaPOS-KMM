package com.zyntasolutions.zyntapos.data.sync

import com.zyntasolutions.zyntapos.data.createTestDatabase
import com.zyntasolutions.zyntapos.db.ZyntaDatabase
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Clock

/**
 * Tests for sync priority ordering — critical entity types pushed before low-priority ones.
 */
class SyncPriorityTest {

    private lateinit var db: ZyntaDatabase

    private val now get() = Clock.System.now().toEpochMilliseconds()

    @BeforeTest
    fun setup() {
        db = createTestDatabase()
    }

    private fun enqueue(id: String, entityType: String, createdAt: Long = now) {
        db.sync_queueQueries.enqueueOperation(
            id = id, entity_type = entityType, entity_id = "e-$id",
            operation = "UPDATE", payload = """{"id":"e-$id"}""", created_at = createdAt, store_id = "",
        )
    }

    // ── Priority ordering ─────────────────────────────────────────────

    @Test
    fun getEligibleOperations_returnsCriticalBeforeLow() {
        // Enqueue LOW first, then CRITICAL — should return CRITICAL first regardless of insertion order
        enqueue("settings-1", entityType = "settings", createdAt = now - 1000)
        enqueue("order-1", entityType = "order", createdAt = now)

        val ops = db.sync_queueQueries.getEligibleOperations(store_id = "", batch_size = 10).executeAsList()
        assertEquals(2, ops.size)
        assertEquals("order-1", ops[0].id, "CRITICAL (order) should come before LOW (settings)")
        assertEquals("settings-1", ops[1].id)
    }

    @Test
    fun getEligibleOperations_sameTierOrderedByCreatedAt() {
        // Two CRITICAL ops — should be ordered by created_at (FIFO)
        enqueue("order-old", entityType = "order", createdAt = now - 2000)
        enqueue("order-new", entityType = "order", createdAt = now - 1000)

        val ops = db.sync_queueQueries.getEligibleOperations(store_id = "", batch_size = 10).executeAsList()
        assertEquals("order-old", ops[0].id, "Same-tier ops should be FIFO by created_at")
        assertEquals("order-new", ops[1].id)
    }

    @Test
    fun getEligibleOperations_allFourTiersOrdered() {
        // One op per tier — should be ordered CRITICAL < HIGH < NORMAL < LOW
        enqueue("media-1", entityType = "media_file", createdAt = now - 4000)   // LOW (3)
        enqueue("category-1", entityType = "category", createdAt = now - 3000)  // NORMAL (2)
        enqueue("product-1", entityType = "product", createdAt = now - 2000)    // HIGH (1)
        enqueue("order-1", entityType = "order", createdAt = now - 1000)        // CRITICAL (0)

        val ops = db.sync_queueQueries.getEligibleOperations(store_id = "", batch_size = 10).executeAsList()
        assertEquals(4, ops.size)
        assertEquals("order-1", ops[0].id, "CRITICAL should be first")
        assertEquals("product-1", ops[1].id, "HIGH should be second")
        assertEquals("category-1", ops[2].id, "NORMAL should be third")
        assertEquals("media-1", ops[3].id, "LOW should be last")
    }

    @Test
    fun getEligibleOperations_criticalFilledFirstInBatch() {
        // 3 LOW + 1 CRITICAL with batch limit of 2 — CRITICAL must be in the batch
        enqueue("low-1", entityType = "settings", createdAt = now - 3000)
        enqueue("low-2", entityType = "settings", createdAt = now - 2000)
        enqueue("low-3", entityType = "settings", createdAt = now - 1000)
        enqueue("critical-1", entityType = "order", createdAt = now)

        val batch = db.sync_queueQueries.getEligibleOperations(store_id = "", batch_size = 2).executeAsList()
        assertEquals(2, batch.size)
        assertEquals("critical-1", batch[0].id, "CRITICAL op must be in the batch even though LOWs were enqueued first")
    }

    // ── SyncPriority object ───────────────────────────────────────────

    @Test
    fun tierFor_mapsEntityTypesCorrectly() {
        assertEquals(SyncPriority.CRITICAL, SyncPriority.tierFor("order"))
        assertEquals(SyncPriority.CRITICAL, SyncPriority.tierFor("cash_movement"))
        assertEquals(SyncPriority.HIGH, SyncPriority.tierFor("product"))
        assertEquals(SyncPriority.HIGH, SyncPriority.tierFor("stock_adjustment"))
        assertEquals(SyncPriority.NORMAL, SyncPriority.tierFor("category"))
        assertEquals(SyncPriority.LOW, SyncPriority.tierFor("media_file"))
        assertEquals(SyncPriority.LOW, SyncPriority.tierFor("unknown_type"))
    }
}
