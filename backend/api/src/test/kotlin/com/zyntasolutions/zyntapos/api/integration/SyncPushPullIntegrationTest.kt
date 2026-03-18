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
        payload: String = """{"id":"entity-$id","name":"Product $id"}""",
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
            makeOp("good-2", entityType = "ORDER"),
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
}
