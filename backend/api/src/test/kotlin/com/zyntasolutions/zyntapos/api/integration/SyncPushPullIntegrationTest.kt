package com.zyntasolutions.zyntapos.api.integration

import com.zyntasolutions.zyntapos.api.sync.SyncValidator
import com.zyntasolutions.zyntapos.api.models.SyncOperation
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Integration-level tests for the sync engine pipeline.
 *
 * These tests use the pure in-memory components (SyncValidator) and verify
 * end-to-end validation + deduplication logic without requiring a database.
 *
 * Full DB-backed push→pull roundtrip tests require a live PostgreSQL instance
 * and are exercised in CI via the docker-compose test environment.
 */
class SyncPushPullIntegrationTest {

    private val validator = SyncValidator()

    private fun makeOp(
        id: String,
        entityType: String = "PRODUCT",
        entityId: String = "entity-$id",
        operation: String = "INSERT",
        payload: String = """{"id":"entity-$id","name":"Product $id","price":10.0}""",
        clientTimestamp: Long = System.currentTimeMillis() - 100,
    ) = SyncOperation(id, entityType, entityId, operation, payload, clientTimestamp)

    @Test
    fun `valid push batch of 50 ops all accepted`() {
        val ops = (1..50).map { makeOp("op-$it") }
        val result = validator.validateBatch(ops)
        assertEquals(50, result.valid.size)
        assertTrue(result.invalid.isEmpty())
    }

    @Test
    fun `push batch with 51 ops all rejected`() {
        val ops = (1..51).map { makeOp("op-$it") }
        val result = validator.validateBatch(ops)
        assertTrue(result.valid.isEmpty())
        assertEquals(51, result.invalid.size)
    }

    @Test
    fun `mixed batch separates valid and invalid correctly`() {
        val ops = listOf(
            makeOp("good-1"),
            makeOp("bad-1", operation = "PATCH"),
            makeOp("good-2", entityType = "ORDER", payload = """{"grand_total":10.0}"""),
            makeOp("bad-2", payload = "not-json"),
        )
        val result = validator.validateBatch(ops)
        assertEquals(2, result.valid.size)
        assertEquals(2, result.invalid.size)
        assertTrue(result.valid.any { it.id == "good-1" })
        assertTrue(result.valid.any { it.id == "good-2" })
        assertTrue(result.invalid.any { it.id == "bad-1" })
        assertTrue(result.invalid.any { it.id == "bad-2" })
    }

    /**
     * Provides a valid minimal payload for each entity type that passes
     * field-level validation. Types without specific validation rules
     * use the generic payload.
     */
    private fun validPayloadFor(entityType: String, id: String): String = when (entityType) {
        "STOCK_ADJUSTMENT" -> """{"product_id":"p-1","type":"INCREASE","quantity":5,"name":"adj-$id"}"""
        "REGISTER_SESSION" -> """{"register_id":"r-1","opened_by":"u-1","name":"session-$id"}"""
        "CASH_MOVEMENT" -> """{"session_id":"s-1","type":"IN","amount":100,"name":"mv-$id"}"""
        "COUPON" -> """{"code":"CODE-$id","name":"Coupon $id","discount_value":10}"""
        "PAYMENT_SPLIT" -> """{"order_id":"o-1","method":"CASH","amount":50,"name":"split-$id"}"""
        "SETTINGS" -> """{"key":"setting-$id","value":"val-$id","name":"setting-$id"}"""
        "REPLENISHMENT_RULE" -> """{"product_id":"p-1","warehouse_id":"w-1","reorder_point":10,"reorder_qty":50}"""
        "PURCHASE_ORDER" -> """{"supplier_id":"s-1","order_number":"PO-$id","total_amount":100}"""
        "TRANSIT_EVENT" -> """{"transfer_id":"t-1","event_type":"DISPATCHED"}"""
        "WAREHOUSE_STOCK" -> """{"warehouse_id":"w-1","product_id":"p-1","quantity":100}"""
        else -> """{"id":"entity-$id","name":"Product $id"}"""
    }

    @Test
    fun `all valid entity types accepted`() {
        val ops = SyncValidator.VALID_ENTITY_TYPES.mapIndexed { i, entityType ->
            makeOp("op-$i", entityType = entityType, payload = validPayloadFor(entityType, "op-$i"))
        }.toList()

        // Process in batches of 50
        ops.chunked(50).forEach { batch ->
            val result = validator.validateBatch(batch)
            assertTrue(result.invalid.isEmpty(), "Unexpected invalid ops: ${result.invalid}")
        }
    }

    @Test
    fun `DELETE operation is valid`() {
        val op = makeOp("del-1", operation = "DELETE", payload = """{"id":"entity-1"}""")
        val result = validator.validateBatch(listOf(op))
        assertEquals(1, result.valid.size)
    }

    @Test
    fun `future timestamp within 60s tolerance is accepted`() {
        val slightlyFuture = System.currentTimeMillis() + 30_000 // 30s ahead (within 60s tolerance)
        val op = makeOp("future-ok", clientTimestamp = slightlyFuture)
        val result = validator.validateBatch(listOf(op))
        assertEquals(1, result.valid.size)
    }

    @Test
    fun `timestamp more than 60s in future is rejected`() {
        val tooFarFuture = System.currentTimeMillis() + 90_000 // 90s ahead
        val op = makeOp("future-bad", clientTimestamp = tooFarFuture)
        val result = validator.validateBatch(listOf(op))
        assertEquals(1, result.invalid.size)
        assertTrue(result.invalid.first().reason.contains("future"))
    }

    // ── New integration test cases ──────────────────────────────────────

    @Test
    fun `batch of all DELETE ops is valid`() {
        val ops = (1..10).map { makeOp("del-$it", operation = "DELETE", payload = """{"id":"e-$it"}""") }
        val result = validator.validateBatch(ops)
        assertEquals(10, result.valid.size)
    }

    @Test
    fun `batch of all UPDATE ops with valid payloads passes`() {
        val ops = (1..10).map {
            makeOp("upd-$it", operation = "UPDATE", payload = """{"name":"Updated $it","price":${it * 10.0}}""")
        }
        val result = validator.validateBatch(ops)
        assertEquals(10, result.valid.size)
    }

    @Test
    fun `batch mixing CREATE UPDATE DELETE all accepted`() {
        val ops = listOf(
            makeOp("c1", operation = "CREATE", payload = """{"name":"New","price":5.0}"""),
            makeOp("u1", operation = "UPDATE", entityId = "e-1", payload = """{"name":"Updated","price":6.0}"""),
            makeOp("d1", operation = "DELETE", entityId = "e-2", payload = """{"id":"e-2"}"""),
        )
        val result = validator.validateBatch(ops)
        assertEquals(3, result.valid.size)
        assertTrue(result.invalid.isEmpty())
    }

    @Test
    fun `multiple entity types in single batch with proper payloads`() {
        val ops = listOf(
            makeOp("p1", entityType = "PRODUCT", payload = """{"name":"Widget","price":10.0}"""),
            makeOp("c1", entityType = "CATEGORY", payload = """{"name":"Electronics"}"""),
            makeOp("cu1", entityType = "CUSTOMER", payload = """{"name":"Jane Doe"}"""),
            makeOp("s1", entityType = "SUPPLIER", payload = """{"name":"Acme Corp"}"""),
            makeOp("o1", entityType = "ORDER", payload = """{"grand_total":100.0}"""),
            makeOp("oi1", entityType = "ORDER_ITEM", payload = """{"id":"oi1"}"""),
        )
        val result = validator.validateBatch(ops)
        assertEquals(6, result.valid.size)
        assertTrue(result.invalid.isEmpty())
    }

    @Test
    fun `field validation catches multiple invalid products in batch`() {
        val ops = listOf(
            makeOp("ok", payload = """{"name":"Good","price":10.0}"""),
            makeOp("bad-name", payload = """{"name":"","price":10.0}"""),
            makeOp("bad-price", payload = """{"name":"Widget","price":-5.0}"""),
            makeOp("bad-stock", payload = """{"name":"Widget","price":1.0,"stock_qty":-10}"""),
        )
        val result = validator.validateBatch(ops)
        assertEquals(1, result.valid.size)
        assertEquals("ok", result.valid.first().id)
        assertEquals(3, result.invalid.size)
    }

    @Test
    fun `exact batch size boundary - 49 ops accepted, 50 accepted, 51 rejected`() {
        val ops49 = (1..49).map { makeOp("op-$it") }
        assertEquals(49, validator.validateBatch(ops49).valid.size)

        val ops50 = (1..50).map { makeOp("op-$it") }
        assertEquals(50, validator.validateBatch(ops50).valid.size)

        val ops51 = (1..51).map { makeOp("op-$it") }
        assertTrue(validator.validateBatch(ops51).valid.isEmpty())
    }

    @Test
    fun `simultaneous invalid operation and entity type errors`() {
        val op = makeOp("bad", entityType = "FAKE", operation = "MERGE", payload = "not-json")
        val result = validator.validateBatch(listOf(op))
        assertEquals(1, result.invalid.size)
        // Should contain multiple errors separated by semicolons
        val reason = result.invalid.first().reason
        assertTrue(reason.contains(";"), "Expected multiple errors: $reason")
    }

    @Test
    fun `whitespace-only id is treated as blank`() {
        val op = SyncOperation("   ", "PRODUCT", "e-1", "INSERT", """{"name":"Test","price":1.0}""", System.currentTimeMillis() - 100)
        val result = validator.validateBatch(listOf(op))
        assertEquals(1, result.invalid.size)
    }
}
