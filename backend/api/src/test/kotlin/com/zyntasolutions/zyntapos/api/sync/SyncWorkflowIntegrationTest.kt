package com.zyntasolutions.zyntapos.api.sync

import com.zyntasolutions.zyntapos.api.db.ExposedTransactionRunner
import com.zyntasolutions.zyntapos.api.models.PushRequest
import com.zyntasolutions.zyntapos.api.models.SyncOperation
import com.zyntasolutions.zyntapos.api.repository.ConflictLogRepository
import com.zyntasolutions.zyntapos.api.repository.DeadLetterRepository
import com.zyntasolutions.zyntapos.api.repository.SyncConflictLog
import com.zyntasolutions.zyntapos.api.repository.SyncCursorRepository
import com.zyntasolutions.zyntapos.api.repository.SyncCursors
import com.zyntasolutions.zyntapos.api.repository.SyncDeadLetters
import com.zyntasolutions.zyntapos.api.repository.SyncOperationRepository
import com.zyntasolutions.zyntapos.api.repository.SyncOperations
import com.zyntasolutions.zyntapos.api.service.Products
import com.zyntasolutions.zyntapos.api.test.TestFixtures
import kotlinx.coroutines.test.runTest
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * C5 — Comprehensive sync workflow integration tests.
 *
 * These tests exercise the full push→pull roundtrip, multi-store isolation,
 * version vector advancement, large batch handling, idempotent re-push,
 * and error handling with a real PostgreSQL container (via AbstractSyncIntegrationTest).
 *
 * Existing test coverage (not duplicated here):
 * - SyncProcessorIntegrationTest: basic push acceptance, idempotent op ID, conflict detection
 * - EntityApplierIntegrationTest: per-entity-type CREATE/UPDATE/DELETE
 * - DeltaEngineTest: cursor logic with stub repos (no DB)
 * - SyncValidatorTest / SyncProcessorTest: validation + dedup with mocks
 */
class SyncWorkflowIntegrationTest : AbstractSyncIntegrationTest() {

    private val storeA = "store-workflow-A"
    private val storeB = "store-workflow-B"

    private fun makeProcessor() = SyncProcessor(
        syncOpRepo      = SyncOperationRepository(),
        conflictResolver = ServerConflictResolver(ConflictLogRepository()),
        validator        = SyncValidator(),
        entityApplier    = EntityApplier(DeadLetterRepository()),
        deadLetterRepo   = DeadLetterRepository(),
        metrics          = SyncMetrics(),
        redisPool        = null,
        txRunner         = ExposedTransactionRunner(),
    )

    private fun makeDeltaEngine() = DeltaEngine(
        syncOpRepo  = SyncOperationRepository(),
        cursorRepo  = SyncCursorRepository(),
        metrics     = SyncMetrics(),
    )

    private fun productOp(
        id: String = "op-${UUID.randomUUID()}",
        entityId: String = "prod-${UUID.randomUUID().toString().take(8)}",
        operation: String = "CREATE",
        name: String = "Widget",
        price: Double = 9.99,
        createdAt: Long = System.currentTimeMillis(),
    ) = SyncOperation(
        id = id,
        entityType = "PRODUCT",
        entityId = entityId,
        operation = operation,
        payload = """{"name":"$name","price":$price,"cost_price":4.00,"stock_qty":10.0,"is_active":true}""",
        createdAt = createdAt,
    )

    private fun categoryOp(
        id: String = "op-${UUID.randomUUID()}",
        entityId: String = "cat-${UUID.randomUUID().toString().take(8)}",
        operation: String = "CREATE",
        name: String = "Category",
        createdAt: Long = System.currentTimeMillis(),
    ) = SyncOperation(
        id = id,
        entityType = "CATEGORY",
        entityId = entityId,
        operation = operation,
        payload = """{"name":"$name","sort_order":0,"is_active":true}""",
        createdAt = createdAt,
    )

    private fun customerOp(
        id: String = "op-${UUID.randomUUID()}",
        entityId: String = "cust-${UUID.randomUUID().toString().take(8)}",
        operation: String = "CREATE",
        name: String = "Customer",
        createdAt: Long = System.currentTimeMillis(),
    ) = SyncOperation(
        id = id,
        entityType = "CUSTOMER",
        entityId = entityId,
        operation = operation,
        payload = """{"name":"$name","email":"$name@test.com","phone":"0771234567","loyalty_points":0,"is_active":true}""",
        createdAt = createdAt,
    )

    @BeforeEach
    fun seedStores() {
        TestFixtures.insertStore(id = storeA, name = "Store A")
        TestFixtures.insertStore(id = storeB, name = "Store B")
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // C5.1 — Full push→pull roundtrip
    // ═══════════════════════════════════════════════════════════════════════════

    @Test
    fun `push products then pull via DeltaEngine returns same operations`() = runTest {
        val processor = makeProcessor()
        val deltaEngine = makeDeltaEngine()

        val op1 = productOp(name = "RoundTrip A", price = 10.0)
        val op2 = productOp(name = "RoundTrip B", price = 20.0)
        val request = PushRequest(deviceId = "device-1", operations = listOf(op1, op2))

        val pushResp = processor.processPush(storeA, request)
        assertEquals(2, pushResp.accepted.size)

        // Pull from a different device
        val pullResp = deltaEngine.computeDelta(storeA, "device-2", since = 0L)

        assertEquals(2, pullResp.operations.size)
        val pulledIds = pullResp.operations.map { it.id }.toSet()
        assertTrue(op1.id in pulledIds, "op1 should appear in pull")
        assertTrue(op2.id in pulledIds, "op2 should appear in pull")
        assertFalse(pullResp.hasMore)
    }

    @Test
    fun `push mixed entity types then pull returns all types`() = runTest {
        val processor = makeProcessor()
        val deltaEngine = makeDeltaEngine()

        val prodOp = productOp(name = "MixedProduct")
        val catOp = categoryOp(name = "MixedCategory")
        val custOp = customerOp(name = "MixedCustomer")
        val request = PushRequest(deviceId = "device-1", operations = listOf(prodOp, catOp, custOp))

        val pushResp = processor.processPush(storeA, request)
        assertEquals(3, pushResp.accepted.size)

        val pullResp = deltaEngine.computeDelta(storeA, "device-2", since = 0L)
        assertEquals(3, pullResp.operations.size)

        val entityTypes = pullResp.operations.map { it.entityType }.toSet()
        assertTrue("PRODUCT" in entityTypes)
        assertTrue("CATEGORY" in entityTypes)
        assertTrue("CUSTOMER" in entityTypes)
    }

    @Test
    fun `push then pull with UPDATE reflects updated payload`() = runTest {
        val processor = makeProcessor()
        val deltaEngine = makeDeltaEngine()

        val entityId = "prod-roundtrip-upd"
        val createOp = productOp(entityId = entityId, name = "Original", price = 5.0)
        processor.processPush(storeA, PushRequest("device-1", listOf(createOp)))

        // Grab cursor after first push
        val cursor1 = deltaEngine.computeDelta(storeA, "device-2", since = 0L).serverVectorClock

        // Push update
        val updateOp = productOp(
            entityId = entityId,
            operation = "UPDATE",
            name = "Updated",
            price = 15.0,
        )
        processor.processPush(storeA, PushRequest("device-1", listOf(updateOp)))

        // Pull only new changes
        val pullResp = deltaEngine.computeDelta(storeA, "device-2", since = cursor1)
        assertEquals(1, pullResp.operations.size)
        assertTrue(pullResp.operations[0].payload.contains("Updated"))
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // C5.2 — Full pull workflow: filter by store
    // ═══════════════════════════════════════════════════════════════════════════

    @Test
    fun `pull filters operations by storeId`() = runTest {
        val processor = makeProcessor()
        val deltaEngine = makeDeltaEngine()

        // Push to store A
        val opA = productOp(name = "StoreA Product")
        processor.processPush(storeA, PushRequest("device-1", listOf(opA)))

        // Push to store B
        val opB = productOp(name = "StoreB Product")
        processor.processPush(storeB, PushRequest("device-1", listOf(opB)))

        // Pull from store A — should only get store A's data
        val pullA = deltaEngine.computeDelta(storeA, "device-2", since = 0L)
        assertEquals(1, pullA.operations.size)
        assertEquals(opA.id, pullA.operations[0].id)

        // Pull from store B — should only get store B's data
        val pullB = deltaEngine.computeDelta(storeB, "device-2", since = 0L)
        assertEquals(1, pullB.operations.size)
        assertEquals(opB.id, pullB.operations[0].id)
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // C5.3 — Conflict resolution: client update vs server update (LWW wins)
    // ═══════════════════════════════════════════════════════════════════════════

    @Test
    fun `LWW conflict resolution - newer device wins, conflict logged`() = runTest {
        val processor = makeProcessor()
        val entityId = "prod-lww-${UUID.randomUUID().toString().take(8)}"
        val now = System.currentTimeMillis()

        // Device-1 pushes first (newer timestamp wins)
        val op1 = productOp(entityId = entityId, name = "Device1Latest", price = 50.0, createdAt = now)
        processor.processPush(storeA, PushRequest("device-1", listOf(op1)))

        // Device-2 pushes same entity with older timestamp — existing (device-1) wins
        val op2 = productOp(entityId = entityId, name = "Device2Older", price = 30.0, createdAt = now - 10_000)
        val resp2 = processor.processPush(storeA, PushRequest("device-2", listOf(op2)))

        // op2 should be in conflicts list (not accepted)
        assertTrue(op2.id in resp2.conflicts, "Older device-2 op should be recorded as conflict")

        // Verify conflict log has entry
        val conflictCount = newSuspendedTransaction(db = database) {
            SyncConflictLog.selectAll().where {
                SyncConflictLog.entityId eq entityId
            }.count()
        }
        assertTrue(conflictCount >= 1L, "Conflict log must have at least one entry")

        // Verify the product table still has device-1's data (winner)
        val row = newSuspendedTransaction(db = database) {
            Products.selectAll().where { Products.id eq entityId }.singleOrNull()
        }
        assertNotNull(row)
        // The product should reflect the CREATE from device-1
        assertEquals("Device1Latest", row[Products.name])
    }

    @Test
    fun `LWW conflict resolution - incoming newer wins over existing older`() = runTest {
        val processor = makeProcessor()
        val entityId = "prod-lww-new-${UUID.randomUUID().toString().take(8)}"
        val now = System.currentTimeMillis()

        // Device-1 pushes first with older timestamp
        val op1 = productOp(entityId = entityId, name = "Device1Older", price = 10.0, createdAt = now - 10_000)
        processor.processPush(storeA, PushRequest("device-1", listOf(op1)))

        // Device-2 pushes same entity with newer timestamp — incoming wins
        val op2 = productOp(entityId = entityId, name = "Device2Newer", price = 99.0, createdAt = now)
        val resp2 = processor.processPush(storeA, PushRequest("device-2", listOf(op2)))

        // op2 wins — should be in accepted, not conflicts
        assertTrue(op2.id in resp2.accepted, "Newer incoming op should be accepted")
        assertTrue(resp2.conflicts.isEmpty(), "No conflicts expected when incoming wins")
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // C5.4 — Multi-store isolation
    // ═══════════════════════════════════════════════════════════════════════════

    @Test
    fun `operations from store A do not leak to store B pull`() = runTest {
        val processor = makeProcessor()
        val deltaEngine = makeDeltaEngine()

        // Push 5 products to store A
        val opsA = (1..5).map { productOp(name = "StoreA-Product-$it") }
        processor.processPush(storeA, PushRequest("device-A", opsA))

        // Push 3 products to store B
        val opsB = (1..3).map { productOp(name = "StoreB-Product-$it") }
        processor.processPush(storeB, PushRequest("device-B", opsB))

        // Pull store A — must get exactly 5
        val pullA = deltaEngine.computeDelta(storeA, "device-X", since = 0L)
        assertEquals(5, pullA.operations.size)
        assertTrue(pullA.operations.all { it.payload.contains("StoreA-Product") })

        // Pull store B — must get exactly 3
        val pullB = deltaEngine.computeDelta(storeB, "device-X", since = 0L)
        assertEquals(3, pullB.operations.size)
        assertTrue(pullB.operations.all { it.payload.contains("StoreB-Product") })
    }

    @Test
    fun `store A products table does not contain store B products`() = runTest {
        val processor = makeProcessor()

        val entityA = "prod-iso-A"
        val entityB = "prod-iso-B"

        processor.processPush(storeA, PushRequest("dev-A", listOf(
            productOp(entityId = entityA, name = "ProductA")
        )))
        processor.processPush(storeB, PushRequest("dev-B", listOf(
            productOp(entityId = entityB, name = "ProductB")
        )))

        // Verify products table has both but with correct store_id
        val rowA = newSuspendedTransaction(db = database) {
            Products.selectAll().where { Products.id eq entityA }.singleOrNull()
        }
        val rowB = newSuspendedTransaction(db = database) {
            Products.selectAll().where { Products.id eq entityB }.singleOrNull()
        }

        assertNotNull(rowA)
        assertNotNull(rowB)
        assertEquals(storeA, rowA[Products.storeId])
        assertEquals(storeB, rowB[Products.storeId])
    }

    @Test
    fun `conflict detection is scoped per store`() = runTest {
        val processor = makeProcessor()
        val now = System.currentTimeMillis()
        val entityId = "prod-scope-conflict"

        // Push to store A from device-1
        val opA = productOp(entityId = entityId, name = "StoreA", createdAt = now)
        processor.processPush(storeA, PushRequest("device-1", listOf(opA)))

        // Push same entityId to store B from device-2 — should NOT conflict
        // because conflict detection is scoped to storeId
        val opB = productOp(entityId = entityId, name = "StoreB", createdAt = now - 5000)
        val respB = processor.processPush(storeB, PushRequest("device-2", listOf(opB)))

        assertTrue(opB.id in respB.accepted, "Same entityId in different store should not conflict")
        assertTrue(respB.conflicts.isEmpty())
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // C5.5 — Version vector (cursor) advancement after sync
    // ═══════════════════════════════════════════════════════════════════════════

    @Test
    fun `cursor advances after each push and pull`() = runTest {
        val processor = makeProcessor()
        val deltaEngine = makeDeltaEngine()

        // Push first batch
        val ops1 = (1..3).map { productOp(name = "Batch1-$it") }
        processor.processPush(storeA, PushRequest("device-1", ops1))

        // Pull — cursor should advance to cover 3 ops
        val pull1 = deltaEngine.computeDelta(storeA, "device-2", since = 0L)
        assertEquals(3, pull1.operations.size)
        val cursor1 = pull1.serverVectorClock
        assertTrue(cursor1 > 0L, "Cursor must advance past 0")

        // Push second batch
        val ops2 = (1..2).map { productOp(name = "Batch2-$it") }
        processor.processPush(storeA, PushRequest("device-1", ops2))

        // Pull from cursor1 — should get only the 2 new ops
        val pull2 = deltaEngine.computeDelta(storeA, "device-2", since = cursor1)
        assertEquals(2, pull2.operations.size)
        val cursor2 = pull2.serverVectorClock
        assertTrue(cursor2 > cursor1, "Cursor must advance further")

        // Pull from cursor2 — should get nothing
        val pull3 = deltaEngine.computeDelta(storeA, "device-2", since = cursor2)
        assertTrue(pull3.operations.isEmpty())
        assertFalse(pull3.hasMore)
    }

    @Test
    fun `cursor is persisted in sync_cursors table`() = runTest {
        val processor = makeProcessor()
        val deltaEngine = makeDeltaEngine()

        val ops = (1..5).map { productOp(name = "Cursor-$it") }
        processor.processPush(storeA, PushRequest("device-1", ops))

        deltaEngine.computeDelta(storeA, "device-pull", since = 0L)

        val cursorRow = newSuspendedTransaction(db = database) {
            SyncCursors.selectAll().where {
                (SyncCursors.storeId eq storeA) and (SyncCursors.deviceId eq "device-pull")
            }.singleOrNull()
        }
        assertNotNull(cursorRow, "Cursor must be persisted in sync_cursors")
        assertTrue(cursorRow[SyncCursors.lastSeq] > 0L)
    }

    @Test
    fun `different devices have independent cursors`() = runTest {
        val processor = makeProcessor()
        val deltaEngine = makeDeltaEngine()

        val ops = (1..5).map { productOp(name = "MultiCursor-$it") }
        processor.processPush(storeA, PushRequest("device-push", ops))

        // Device-A pulls with limit 2
        val pullA = deltaEngine.computeDelta(storeA, "device-A", since = 0L, limit = 2)
        assertEquals(2, pullA.operations.size)
        assertTrue(pullA.hasMore)

        // Device-B pulls all 5
        val pullB = deltaEngine.computeDelta(storeA, "device-B", since = 0L, limit = 10)
        assertEquals(5, pullB.operations.size)
        assertFalse(pullB.hasMore)

        // Verify separate cursor rows
        val cursorA = newSuspendedTransaction(db = database) {
            SyncCursors.selectAll().where {
                (SyncCursors.storeId eq storeA) and (SyncCursors.deviceId eq "device-A")
            }.single()[SyncCursors.lastSeq]
        }
        val cursorB = newSuspendedTransaction(db = database) {
            SyncCursors.selectAll().where {
                (SyncCursors.storeId eq storeA) and (SyncCursors.deviceId eq "device-B")
            }.single()[SyncCursors.lastSeq]
        }

        assertTrue(cursorB > cursorA, "Device-B pulled more, so its cursor should be ahead")
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // C5.6 — Error handling: malformed operations, missing entity references
    // ═══════════════════════════════════════════════════════════════════════════

    @Test
    fun `malformed operation in batch is rejected while valid ops succeed`() = runTest {
        val processor = makeProcessor()

        val validOp = productOp(name = "Valid")
        val malformedOp = SyncOperation(
            id = "op-malformed-${UUID.randomUUID().toString().take(8)}",
            entityType = "PRODUCT",
            entityId = "prod-bad",
            operation = "CREATE",
            payload = """{"name":"","price":-5.0}""",  // fails field validation
            createdAt = System.currentTimeMillis(),
        )

        val request = PushRequest("device-1", listOf(validOp, malformedOp))
        val resp = processor.processPush(storeA, request)

        assertEquals(1, resp.accepted.size)
        assertEquals(validOp.id, resp.accepted[0])
        assertEquals(1, resp.rejected.size)
        assertEquals(malformedOp.id, resp.rejected[0])
    }

    @Test
    fun `malformed operation is sent to dead letter table`() = runTest {
        val processor = makeProcessor()

        val badOp = SyncOperation(
            id = "op-dl-${UUID.randomUUID().toString().take(8)}",
            entityType = "NONEXISTENT_TYPE",
            entityId = "e-1",
            operation = "CREATE",
            payload = """{"name":"Test"}""",
            createdAt = System.currentTimeMillis(),
        )

        processor.processPush(storeA, PushRequest("device-1", listOf(badOp)))

        val dlCount = newSuspendedTransaction(db = database) {
            SyncDeadLetters.selectAll().where {
                SyncDeadLetters.storeId eq storeA
            }.count()
        }
        assertTrue(dlCount >= 1L, "Dead letter table should have at least 1 entry")
    }

    @Test
    fun `operation with non-JSON payload is rejected`() = runTest {
        val processor = makeProcessor()

        val op = SyncOperation(
            id = "op-nonjson-${UUID.randomUUID().toString().take(8)}",
            entityType = "PRODUCT",
            entityId = "prod-nonjson",
            operation = "CREATE",
            payload = "this is not json at all",
            createdAt = System.currentTimeMillis(),
        )

        val resp = processor.processPush(storeA, PushRequest("device-1", listOf(op)))
        assertEquals(1, resp.rejected.size)
        assertTrue(resp.accepted.isEmpty())
    }

    @Test
    fun `operation with invalid operation type is rejected`() = runTest {
        val processor = makeProcessor()

        val op = SyncOperation(
            id = "op-badtype-${UUID.randomUUID().toString().take(8)}",
            entityType = "PRODUCT",
            entityId = "prod-badtype",
            operation = "MERGE",
            payload = """{"name":"Test","price":10.0}""",
            createdAt = System.currentTimeMillis(),
        )

        val resp = processor.processPush(storeA, PushRequest("device-1", listOf(op)))
        assertEquals(1, resp.rejected.size)
    }

    @Test
    fun `operation with future timestamp beyond tolerance is rejected`() = runTest {
        val processor = makeProcessor()

        val op = productOp(
            name = "FutureProduct",
            createdAt = System.currentTimeMillis() + 120_000,
        )

        val resp = processor.processPush(storeA, PushRequest("device-1", listOf(op)))
        assertEquals(1, resp.rejected.size)
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // C5.7 — Large batch handling: 50 operations in single push (max per batch)
    // ═══════════════════════════════════════════════════════════════════════════

    @Test
    fun `push batch of 50 operations all accepted`() = runTest {
        val processor = makeProcessor()

        val ops = (1..50).map { productOp(name = "BatchProd-$it") }
        val request = PushRequest("device-1", ops)

        val resp = processor.processPush(storeA, request)

        assertEquals(50, resp.accepted.size)
        assertTrue(resp.rejected.isEmpty())
    }

    @Test
    fun `push batch of 50 then pull all back`() = runTest {
        val processor = makeProcessor()
        val deltaEngine = makeDeltaEngine()

        val ops = (1..50).map { productOp(name = "PullBatch-$it") }
        processor.processPush(storeA, PushRequest("device-1", ops))

        val pullResp = deltaEngine.computeDelta(storeA, "device-2", since = 0L, limit = 200)
        assertEquals(50, pullResp.operations.size)
        assertFalse(pullResp.hasMore)
    }

    @Test
    fun `push multiple batches totaling 100+ operations`() = runTest {
        val processor = makeProcessor()
        val deltaEngine = makeDeltaEngine()

        // Push 3 batches of 40 = 120 total
        for (batch in 1..3) {
            val ops = (1..40).map { productOp(name = "Multi-B${batch}-P$it") }
            val resp = processor.processPush(storeA, PushRequest("device-1", ops))
            assertEquals(40, resp.accepted.size, "Batch $batch should have 40 accepted")
        }

        // Pull all — paginate through
        var totalPulled = 0
        var cursor = 0L
        var pages = 0
        do {
            val pull = deltaEngine.computeDelta(storeA, "device-2", since = cursor, limit = 50)
            totalPulled += pull.operations.size
            cursor = pull.serverVectorClock
            pages++
        } while (pull.hasMore)

        assertEquals(120, totalPulled, "Should pull all 120 operations across pages")
        assertTrue(pages >= 3, "Should require at least 3 pages to pull 120 ops at limit=50")
    }

    @Test
    fun `large batch operations are persisted to products table`() = runTest {
        val processor = makeProcessor()

        val entityIds = (1..30).map { "prod-lg-$it" }
        val ops = entityIds.map { id -> productOp(entityId = id, name = "LargeProduct-$id") }
        processor.processPush(storeA, PushRequest("device-1", ops))

        val count = newSuspendedTransaction(db = database) {
            Products.selectAll().where { Products.storeId eq storeA }.count()
        }
        assertEquals(30L, count, "All 30 products should be persisted")
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // C5.8 — Idempotent re-push: same operations pushed twice don't duplicate
    // ═══════════════════════════════════════════════════════════════════════════

    @Test
    fun `re-pushing entire batch is idempotent`() = runTest {
        val processor = makeProcessor()

        val ops = (1..10).map {
            productOp(id = "idem-op-$it", entityId = "idem-prod-$it", name = "Idem-$it")
        }
        val request = PushRequest("device-1", ops)

        // First push
        val resp1 = processor.processPush(storeA, request)
        assertEquals(10, resp1.accepted.size)

        // Second push with identical operations
        val resp2 = processor.processPush(storeA, request)
        assertEquals(10, resp2.accepted.size)
        assertTrue(resp2.rejected.isEmpty())
        assertTrue(resp2.conflicts.isEmpty())

        // Verify no duplicates in sync_operations table
        val opCount = newSuspendedTransaction(db = database) {
            SyncOperations.selectAll().where {
                SyncOperations.storeId eq storeA
            }.count()
        }
        assertEquals(10L, opCount, "Idempotent re-push must not create duplicate rows")
    }

    @Test
    fun `partial re-push with mix of new and duplicate ops`() = runTest {
        val processor = makeProcessor()

        // Push first batch
        val ops1 = (1..5).map {
            productOp(id = "partial-op-$it", entityId = "partial-prod-$it", name = "Partial-$it")
        }
        processor.processPush(storeA, PushRequest("device-1", ops1))

        // Push overlapping batch: ops 3-7 (3-5 are duplicates, 6-7 are new)
        val ops2 = (3..7).map {
            productOp(id = "partial-op-$it", entityId = "partial-prod-$it", name = "Partial-$it")
        }
        val resp2 = processor.processPush(storeA, PushRequest("device-1", ops2))

        assertEquals(5, resp2.accepted.size, "All 5 ops should be in accepted (3 deduped + 2 new)")
        assertTrue(resp2.rejected.isEmpty())

        // Verify total rows = 7 (not 10)
        val opCount = newSuspendedTransaction(db = database) {
            SyncOperations.selectAll().where {
                SyncOperations.storeId eq storeA
            }.count()
        }
        assertEquals(7L, opCount)
    }

    @Test
    fun `re-pushed ops do not generate duplicate products`() = runTest {
        val processor = makeProcessor()

        val entityId = "prod-idem-entity"
        val op = productOp(id = "idem-single", entityId = entityId, name = "IdempotentProd")
        val request = PushRequest("device-1", listOf(op))

        processor.processPush(storeA, request)
        processor.processPush(storeA, request)
        processor.processPush(storeA, request)

        val productCount = newSuspendedTransaction(db = database) {
            Products.selectAll().where { Products.id eq entityId }.count()
        }
        assertEquals(1L, productCount, "Product row must not be duplicated by re-push")
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Additional edge cases
    // ═══════════════════════════════════════════════════════════════════════════

    @Test
    fun `push response serverTimestamp reflects latest seq`() = runTest {
        val processor = makeProcessor()

        val ops = (1..3).map { productOp(name = "SeqTest-$it") }
        val resp = processor.processPush(storeA, PushRequest("device-1", ops))

        assertTrue(resp.serverTimestamp > 0L, "Server timestamp must be > 0 after push")

        // Push more
        val resp2 = processor.processPush(storeA, PushRequest("device-1", listOf(productOp(name = "SeqTest-4"))))
        assertTrue(
            resp2.serverTimestamp > resp.serverTimestamp,
            "Server timestamp must advance after additional push"
        )
    }

    @Test
    fun `empty push returns zero server timestamp when store has no ops`() = runTest {
        val processor = makeProcessor()

        val resp = processor.processPush(storeA, PushRequest("device-1", emptyList()))
        assertEquals(0L, resp.serverTimestamp)
        assertTrue(resp.accepted.isEmpty())
    }

    @Test
    fun `pull with limit 1 paginates correctly through all operations`() = runTest {
        val processor = makeProcessor()
        val deltaEngine = makeDeltaEngine()

        val ops = (1..5).map { productOp(name = "Paginate-$it") }
        processor.processPush(storeA, PushRequest("device-1", ops))

        var cursor = 0L
        val allIds = mutableListOf<String>()

        for (i in 1..5) {
            val pull = deltaEngine.computeDelta(storeA, "device-2", since = cursor, limit = 1)
            assertEquals(1, pull.operations.size, "Page $i should have exactly 1 op")
            allIds.add(pull.operations[0].id)
            cursor = pull.serverVectorClock

            if (i < 5) {
                assertTrue(pull.hasMore, "Page $i should have more")
            } else {
                assertFalse(pull.hasMore, "Last page should not have more")
            }
        }

        assertEquals(5, allIds.distinct().size, "All 5 operations should be distinct")
    }

    @Test
    fun `DELETE operation is persisted and appears in pull`() = runTest {
        val processor = makeProcessor()
        val deltaEngine = makeDeltaEngine()
        val entityId = "prod-del-workflow"

        // Create
        val createOp = productOp(entityId = entityId, name = "ToDelete")
        processor.processPush(storeA, PushRequest("device-1", listOf(createOp)))

        val cursor = deltaEngine.computeDelta(storeA, "device-2", since = 0L).serverVectorClock

        // Delete
        val deleteOp = SyncOperation(
            id = "op-del-${UUID.randomUUID().toString().take(8)}",
            entityType = "PRODUCT",
            entityId = entityId,
            operation = "DELETE",
            payload = """{}""",
            createdAt = System.currentTimeMillis(),
        )
        processor.processPush(storeA, PushRequest("device-1", listOf(deleteOp)))

        // Pull should include the DELETE op
        val pull = deltaEngine.computeDelta(storeA, "device-2", since = cursor)
        assertEquals(1, pull.operations.size)
        // EntityApplier normalizes CREATE → INSERT, so DELETE stays as-is or is normalized
        // The operation stored in sync_operations preserves the original operation type

        // Verify product is soft-deleted
        val row = newSuspendedTransaction(db = database) {
            Products.selectAll().where { Products.id eq entityId }.singleOrNull()
        }
        assertNotNull(row, "Product row should still exist (soft delete)")
        assertFalse(row[Products.isActive], "Product should be soft-deleted (is_active=false)")
    }
}
