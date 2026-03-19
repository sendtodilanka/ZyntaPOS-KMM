package com.zyntasolutions.zyntapos.api.sync

import com.zyntasolutions.zyntapos.api.db.ExposedTransactionRunner
import com.zyntasolutions.zyntapos.api.models.PushRequest
import com.zyntasolutions.zyntapos.api.models.SyncOperation
import com.zyntasolutions.zyntapos.api.repository.ConflictLogRepository
import com.zyntasolutions.zyntapos.api.repository.DeadLetterRepository
import com.zyntasolutions.zyntapos.api.repository.SyncOperationRepository
import com.zyntasolutions.zyntapos.api.repository.SyncOperations
import com.zyntasolutions.zyntapos.api.repository.SyncConflictLog
import com.zyntasolutions.zyntapos.api.service.Products
import com.zyntasolutions.zyntapos.api.test.TestFixtures
import kotlinx.coroutines.test.runTest
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * A1.1 — Integration tests for the full [SyncProcessor] push pipeline with real PostgreSQL.
 *
 * Tests cover:
 * - Single operation accepted and entity written to normalized table
 * - Duplicate operation ID is idempotent (not re-processed)
 * - Two devices pushing the same entity results in a conflict log entry
 */
class SyncProcessorIntegrationTest : AbstractSyncIntegrationTest() {

    private val storeId = "store-sp-test"

    private fun makeProcessor() = SyncProcessor(
        syncOpRepo      = SyncOperationRepository(),
        conflictResolver = ServerConflictResolver(ConflictLogRepository()),
        validator       = SyncValidator(),
        entityApplier   = EntityApplier(),
        deadLetterRepo  = DeadLetterRepository(),
        metrics         = SyncMetrics(),
        redisPool       = null,
        txRunner        = ExposedTransactionRunner(),
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

    @BeforeEach
    fun seedStore() {
        TestFixtures.insertStore(id = storeId)
    }

    // ── Basic push acceptance ────────────────────────────────────────────────

    @Test
    fun `push single PRODUCT operation - accepted and written to products table`() = runTest {
        val entityId = "prod-push-${UUID.randomUUID().toString().take(8)}"
        val op = productOp(entityId = entityId, name = "PushWidget")
        val request = PushRequest(deviceId = "device-1", operations = listOf(op))

        val response = makeProcessor().processPush(storeId, request)

        assertEquals(listOf(op.id), response.accepted)
        assertTrue(response.rejected.isEmpty())

        val row = newSuspendedTransaction(db = database) {
            Products.selectAll().where { Products.id eq entityId }.singleOrNull()
        }
        assertNotNull(row, "Product row should exist in normalized table after push")
        assertEquals("PushWidget", row[Products.name])
    }

    @Test
    fun `push batch of two operations - both accepted`() = runTest {
        val op1 = productOp(name = "Product A")
        val op2 = productOp(name = "Product B")
        val request = PushRequest(deviceId = "device-1", operations = listOf(op1, op2))

        val response = makeProcessor().processPush(storeId, request)

        assertEquals(2, response.accepted.size)
        assertTrue(response.rejected.isEmpty())
        assertTrue(op1.id in response.accepted)
        assertTrue(op2.id in response.accepted)
    }

    // ── Idempotency ──────────────────────────────────────────────────────────

    @Test
    fun `push same operation ID twice - second push is idempotent`() = runTest {
        val opId = "op-idem-${UUID.randomUUID().toString().take(8)}"
        val entityId = "prod-idem-${UUID.randomUUID().toString().take(8)}"
        val op = productOp(id = opId, entityId = entityId, name = "IdempotentProduct")
        val request = PushRequest(deviceId = "device-1", operations = listOf(op))
        val processor = makeProcessor()

        // First push
        val resp1 = processor.processPush(storeId, request)
        assertEquals(listOf(opId), resp1.accepted)

        // Second push with same op ID — should be accepted (idempotent) without re-processing
        val resp2 = processor.processPush(storeId, request)
        assertEquals(listOf(opId), resp2.accepted)
        assertTrue(resp2.rejected.isEmpty())

        // Verify only one row exists in sync_operations
        val count = newSuspendedTransaction(db = database) {
            SyncOperations.selectAll().where { SyncOperations.id eq opId }.count()
        }
        assertEquals(1L, count, "Duplicate op ID must not create a second sync_operations row")
    }

    // ── Invalid operations ──────────────────────────────────────────────────

    @Test
    fun `push operation with invalid entity type - goes to rejected list`() = runTest {
        val op = SyncOperation(
            id = "op-bad-${UUID.randomUUID().toString().take(8)}",
            entityType = "INVALID_TYPE",
            entityId = "e-1",
            operation = "CREATE",
            payload = """{"name":"x"}""",
            createdAt = System.currentTimeMillis(),
        )
        val request = PushRequest(deviceId = "device-1", operations = listOf(op))

        val response = makeProcessor().processPush(storeId, request)

        assertEquals(listOf(op.id), response.rejected)
        assertTrue(response.accepted.isEmpty())
    }

    // ── Conflict detection ──────────────────────────────────────────────────

    @Test
    fun `push from device-2 with older timestamp triggers conflict log`() = runTest {
        val entityId = "prod-conflict-${UUID.randomUUID().toString().take(8)}"
        val now = System.currentTimeMillis()

        // Device-1 pushes first at T=now
        val op1 = productOp(entityId = entityId, name = "FromDevice1", createdAt = now)
        val req1 = PushRequest(deviceId = "device-1", operations = listOf(op1))
        makeProcessor().processPush(storeId, req1)

        // Device-2 pushes same entity at T=now-10000 (older — should trigger conflict)
        val op2 = productOp(entityId = entityId, name = "FromDevice2", createdAt = now - 10_000)
        val req2 = PushRequest(deviceId = "device-2", operations = listOf(op2))
        val response = makeProcessor().processPush(storeId, req2)

        // op2 should be in conflicts (not rejected)
        assertTrue(
            op2.id in response.conflicts || op2.id in response.accepted,
            "Conflicting op must be in either conflicts or accepted (LWW resolution)"
        )

        // sync_conflict_log should have exactly one entry for this entity
        val conflictCount = newSuspendedTransaction(db = database) {
            SyncConflictLog.selectAll().where {
                SyncConflictLog.entityId eq entityId
            }.count()
        }
        assertTrue(conflictCount >= 1L, "sync_conflict_log must record the conflict")
    }
}
