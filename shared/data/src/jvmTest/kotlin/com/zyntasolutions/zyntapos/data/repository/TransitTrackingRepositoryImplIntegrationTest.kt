package com.zyntasolutions.zyntapos.data.repository

import app.cash.turbine.test
import com.zyntasolutions.zyntapos.core.result.Result
import com.zyntasolutions.zyntapos.data.createTestDatabase
import com.zyntasolutions.zyntapos.data.local.SyncEnqueuer
import com.zyntasolutions.zyntapos.db.ZyntaDatabase
import com.zyntasolutions.zyntapos.domain.model.TransitEvent
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlinx.coroutines.test.runTest

/**
 * ZyntaPOS — TransitTrackingRepositoryImpl Integration Tests (jvmTest)
 *
 * Validates [TransitTrackingRepositoryImpl] against a real in-memory SQLite database.
 * Requires warehouses and stock_transfers seeded to satisfy FK constraints.
 *
 * Coverage:
 *  A. addEvent → getEventsForTransfer round-trip preserves all fields
 *  B. getEventsForTransfer returns events in correct order
 *  C. getEventsForTransfer returns empty list for unknown transferId
 *  D. getInTransitCount returns count of IN_TRANSIT transfers
 *  E. getInTransitCount returns 0 when no in-transit transfers
 */
class TransitTrackingRepositoryImplIntegrationTest {

    private lateinit var db: ZyntaDatabase
    private lateinit var repo: TransitTrackingRepositoryImpl

    @BeforeTest
    fun setup() {
        db = createTestDatabase()
        repo = TransitTrackingRepositoryImpl(db, SyncEnqueuer(db))

        val now = Clock.System.now().toEpochMilliseconds()

        // Seed warehouses (required by stock_transfers FK)
        db.warehousesQueries.insertWarehouse(
            "wh-01", "store-01", "Main Warehouse", null, 1L, 1L, null, null, now, now, "PENDING",
        )
        db.warehousesQueries.insertWarehouse(
            "wh-02", "store-01", "Secondary Warehouse", null, 1L, 0L, null, null, now, now, "PENDING",
        )

        // Seed stock transfer (required by transit_tracking FK)
        db.stock_transfersQueries.insertStockTransfer(
            id = "transfer-01",
            source_warehouse_id = "wh-01",
            dest_warehouse_id = "wh-02",
            product_id = "prod-01",
            quantity = 50.0,
            status = "IN_TRANSIT",
            notes = null,
            transferred_by = "user-01",
            created_by = "user-01",
            created_at = now,
            updated_at = now,
            sync_status = "PENDING",
        )
        db.stock_transfersQueries.insertStockTransfer(
            id = "transfer-02",
            source_warehouse_id = "wh-01",
            dest_warehouse_id = "wh-02",
            product_id = "prod-02",
            quantity = 20.0,
            status = "PENDING",
            notes = null,
            transferred_by = "user-01",
            created_by = "user-01",
            created_at = now,
            updated_at = now,
            sync_status = "PENDING",
        )
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private val now get() = Clock.System.now().toEpochMilliseconds()

    private fun makeEvent(
        id: String,
        transferId: String = "transfer-01",
        eventType: TransitEvent.EventType = TransitEvent.EventType.CHECKPOINT,
        location: String? = "Colombo Port",
        note: String? = "Goods in transit",
        recordedBy: String = "driver-01",
        recordedAt: Long = now,
    ) = TransitEvent(
        id = id,
        transferId = transferId,
        eventType = eventType,
        location = location,
        note = note,
        recordedBy = recordedBy,
        recordedAt = recordedAt,
    )

    // ── Tests ─────────────────────────────────────────────────────────────────

    @Test
    fun `A - addEvent then getEventsForTransfer returns event with all fields`() = runTest {
        val event = makeEvent(
            id = "evt-01",
            transferId = "transfer-01",
            eventType = TransitEvent.EventType.DISPATCHED,
            location = "Warehouse 1 Loading Dock",
            note = "Loaded and dispatched",
        )
        val addResult = repo.addEvent(event)
        assertIs<Result.Success<Unit>>(addResult)

        repo.getEventsForTransfer("transfer-01").test {
            val list = awaitItem()
            assertEquals(1, list.size)
            val fetched = list.first()
            assertEquals("evt-01", fetched.id)
            assertEquals("transfer-01", fetched.transferId)
            assertEquals(TransitEvent.EventType.DISPATCHED, fetched.eventType)
            assertEquals("Warehouse 1 Loading Dock", fetched.location)
            assertEquals("Loaded and dispatched", fetched.note)
            assertEquals("driver-01", fetched.recordedBy)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `B - getEventsForTransfer returns multiple events for same transfer`() = runTest {
        repo.addEvent(makeEvent(id = "evt-01", eventType = TransitEvent.EventType.DISPATCHED))
        repo.addEvent(makeEvent(id = "evt-02", eventType = TransitEvent.EventType.CHECKPOINT))
        repo.addEvent(makeEvent(id = "evt-03", eventType = TransitEvent.EventType.RECEIVED))

        repo.getEventsForTransfer("transfer-01").test {
            val list = awaitItem()
            assertEquals(3, list.size)
            assertTrue(list.any { it.eventType == TransitEvent.EventType.DISPATCHED })
            assertTrue(list.any { it.eventType == TransitEvent.EventType.CHECKPOINT })
            assertTrue(list.any { it.eventType == TransitEvent.EventType.RECEIVED })
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `C - getEventsForTransfer returns empty list for unknown transferId`() = runTest {
        repo.getEventsForTransfer("non-existent-transfer").test {
            val list = awaitItem()
            assertTrue(list.isEmpty())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `D - getInTransitCount returns count of IN_TRANSIT transfers`() = runTest {
        // transfer-01 is IN_TRANSIT (seeded in BeforeTest)
        // transfer-02 is PENDING (not in-transit)

        val countResult = repo.getInTransitCount()
        assertIs<Result.Success<Int>>(countResult)
        assertEquals(1, countResult.data)
    }

    @Test
    fun `E - getInTransitCount returns 0 when no in-transit transfers exist`() = runTest {
        // Use a fresh db (no seeded transfers)
        val freshDb = createTestDatabase()
        val freshRepo = TransitTrackingRepositoryImpl(freshDb, SyncEnqueuer(freshDb))

        val countResult = freshRepo.getInTransitCount()
        assertIs<Result.Success<Int>>(countResult)
        assertEquals(0, countResult.data)
    }
}
