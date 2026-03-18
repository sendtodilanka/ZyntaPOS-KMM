package com.zyntasolutions.zyntapos.api.sync

import com.zyntasolutions.zyntapos.api.models.SyncOperation
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SyncValidatorTest {

    private val validator = SyncValidator()

    private fun op(
        id: String = "op-1",
        entityType: String = "PRODUCT",
        entityId: String = "entity-1",
        operation: String = "INSERT",
        payload: String = """{"name":"Test","price":10.0}""",
        createdAt: Long = System.currentTimeMillis() - 1000,
        retryCount: Int = 0,
    ) = SyncOperation(id, entityType, entityId, operation, payload, createdAt, retryCount)

    @Test
    fun `valid single operation passes`() {
        val result = validator.validateBatch(listOf(op()))
        assertEquals(1, result.valid.size)
        assertTrue(result.invalid.isEmpty())
    }

    @Test
    fun `CREATE operation type is valid`() {
        val result = validator.validateBatch(listOf(op(operation = "CREATE")))
        assertEquals(1, result.valid.size)
        assertTrue(result.invalid.isEmpty())
    }

    @Test
    fun `batch exceeding max size rejects all`() {
        val ops = (1..51).map { op(id = "op-$it") }
        val result = validator.validateBatch(ops)
        assertTrue(result.valid.isEmpty())
        assertEquals(51, result.invalid.size)
        assertTrue(result.invalid.first().reason.contains("max size"))
    }

    @Test
    fun `invalid operation type is rejected`() {
        val result = validator.validateBatch(listOf(op(operation = "MERGE")))
        assertEquals(1, result.invalid.size)
        assertTrue(result.invalid.first().reason.contains("Invalid operation"))
    }

    @Test
    fun `unknown entity type is rejected`() {
        val result = validator.validateBatch(listOf(op(entityType = "UNKNOWN_ENTITY")))
        assertEquals(1, result.invalid.size)
        assertTrue(result.invalid.first().reason.contains("Unknown entity type"))
    }

    @Test
    fun `invalid JSON payload is rejected`() {
        val result = validator.validateBatch(listOf(op(payload = "not-valid-json")))
        assertEquals(1, result.invalid.size)
        assertTrue(result.invalid.first().reason.contains("valid JSON"))
    }

    @Test
    fun `future timestamp is rejected`() {
        val future = System.currentTimeMillis() + 120_000
        val result = validator.validateBatch(listOf(op(createdAt = future)))
        assertEquals(1, result.invalid.size)
        assertTrue(result.invalid.first().reason.contains("future"))
    }

    @Test
    fun `blank id is rejected`() {
        val result = validator.validateBatch(listOf(op(id = "")))
        assertEquals(1, result.invalid.size)
        assertTrue(result.invalid.first().reason.contains("ID"))
    }

    @Test
    fun `blank entity id is rejected`() {
        val result = validator.validateBatch(listOf(op(entityId = "")))
        assertEquals(1, result.invalid.size)
        assertTrue(result.invalid.first().reason.contains("Entity ID"))
    }

    @Test
    fun `mixed valid and invalid operations are split correctly`() {
        val ops = listOf(
            op(id = "valid-1"),
            op(id = "invalid-1", operation = "UPSERT"),
            op(id = "valid-2", entityType = "ORDER", payload = """{"total":10.0}"""),
        )
        val result = validator.validateBatch(ops)
        assertEquals(2, result.valid.size)
        assertEquals(1, result.invalid.size)
        assertEquals("invalid-1", result.invalid.first().id)
    }

    @Test
    fun `all valid entity types pass`() {
        SyncValidator.VALID_ENTITY_TYPES.take(5).forEachIndexed { i, entityType ->
            val result = validator.validateBatch(listOf(op(id = "op-$i", entityType = entityType)))
            assertTrue(result.valid.isNotEmpty(), "Expected $entityType to be valid")
        }
    }

    @Test
    fun `all valid operations pass`() {
        SyncValidator.VALID_OPERATIONS.forEachIndexed { i, operation ->
            val result = validator.validateBatch(listOf(op(id = "op-$i", operation = operation)))
            assertTrue(result.valid.isNotEmpty(), "Expected $operation to be valid")
        }
    }

    @Test
    fun `empty batch returns empty results`() {
        val result = validator.validateBatch(emptyList())
        assertTrue(result.valid.isEmpty())
        assertTrue(result.invalid.isEmpty())
    }

    // ── New test cases: field-level validation (S2-7) ───────────────────

    @Test
    fun `PRODUCT with blank name is rejected`() {
        val result = validator.validateBatch(listOf(
            op(payload = """{"name":"","price":10.0}""")
        ))
        assertEquals(1, result.invalid.size)
        assertTrue(result.invalid.first().reason.contains("PRODUCT.name"))
    }

    @Test
    fun `PRODUCT with missing name is rejected`() {
        val result = validator.validateBatch(listOf(
            op(payload = """{"price":10.0}""")
        ))
        assertEquals(1, result.invalid.size)
        assertTrue(result.invalid.first().reason.contains("PRODUCT.name"))
    }

    @Test
    fun `PRODUCT with negative price is rejected`() {
        val result = validator.validateBatch(listOf(
            op(payload = """{"name":"Widget","price":-5.0}""")
        ))
        assertEquals(1, result.invalid.size)
        assertTrue(result.invalid.first().reason.contains("PRODUCT.price"))
    }

    @Test
    fun `PRODUCT with zero price is accepted`() {
        val result = validator.validateBatch(listOf(
            op(payload = """{"name":"Free Item","price":0.0}""")
        ))
        assertEquals(1, result.valid.size)
    }

    @Test
    fun `PRODUCT with negative cost_price is rejected`() {
        val result = validator.validateBatch(listOf(
            op(payload = """{"name":"Widget","price":10.0,"cost_price":-1.0}""")
        ))
        assertEquals(1, result.invalid.size)
        assertTrue(result.invalid.first().reason.contains("cost_price"))
    }

    @Test
    fun `PRODUCT with negative stock_qty is rejected`() {
        val result = validator.validateBatch(listOf(
            op(payload = """{"name":"Widget","price":10.0,"stock_qty":-5}""")
        ))
        assertEquals(1, result.invalid.size)
        assertTrue(result.invalid.first().reason.contains("stock_qty"))
    }

    @Test
    fun `PRODUCT with all valid fields passes`() {
        val result = validator.validateBatch(listOf(
            op(payload = """{"name":"Widget","price":19.99,"cost_price":8.50,"stock_qty":100}""")
        ))
        assertEquals(1, result.valid.size)
    }

    @Test
    fun `CUSTOMER with blank name is rejected`() {
        val result = validator.validateBatch(listOf(
            op(entityType = "CUSTOMER", payload = """{"name":""}""")
        ))
        assertEquals(1, result.invalid.size)
        assertTrue(result.invalid.first().reason.contains("CUSTOMER.name"))
    }

    @Test
    fun `CUSTOMER with valid name passes`() {
        val result = validator.validateBatch(listOf(
            op(entityType = "CUSTOMER", payload = """{"name":"John Doe"}""")
        ))
        assertEquals(1, result.valid.size)
    }

    @Test
    fun `CATEGORY with blank name is rejected`() {
        val result = validator.validateBatch(listOf(
            op(entityType = "CATEGORY", payload = """{"name":""}""")
        ))
        assertEquals(1, result.invalid.size)
        assertTrue(result.invalid.first().reason.contains("CATEGORY.name"))
    }

    @Test
    fun `ORDER with negative total is rejected`() {
        val result = validator.validateBatch(listOf(
            op(entityType = "ORDER", payload = """{"total":-10.0}""")
        ))
        assertEquals(1, result.invalid.size)
        assertTrue(result.invalid.first().reason.contains("ORDER.total"))
    }

    @Test
    fun `ORDER with zero total is accepted`() {
        val result = validator.validateBatch(listOf(
            op(entityType = "ORDER", payload = """{"total":0.0}""")
        ))
        assertEquals(1, result.valid.size)
    }

    // ── Field validation only applies to CREATE/INSERT/UPDATE ───────────

    @Test
    fun `DELETE operation skips field validation`() {
        // DELETE with payload that would fail field validation if checked
        val result = validator.validateBatch(listOf(
            op(operation = "DELETE", payload = """{"name":"","price":-1}""")
        ))
        assertEquals(1, result.valid.size)
    }

    // ── Payload size limit ──────────────────────────────────────────────

    @Test
    fun `payload exceeding 1MB is rejected`() {
        val largePayload = """{"name":"Test","data":"${"x".repeat(1_048_576)}"}"""
        val result = validator.validateBatch(listOf(op(payload = largePayload)))
        assertEquals(1, result.invalid.size)
        assertTrue(result.invalid.first().reason.contains("1 MB"))
    }

    // ── Batch boundary ──────────────────────────────────────────────────

    @Test
    fun `batch of exactly 50 ops is accepted`() {
        val ops = (1..50).map { op(id = "op-$it", entityId = "e-$it") }
        val result = validator.validateBatch(ops)
        assertEquals(50, result.valid.size)
        assertTrue(result.invalid.isEmpty())
    }

    // ── All entity types exhaustive ─────────────────────────────────────

    @Test
    fun `all VALID_ENTITY_TYPES are accepted`() {
        for (entityType in SyncValidator.VALID_ENTITY_TYPES) {
            val payload = when (entityType) {
                "PRODUCT" -> """{"name":"P","price":1.0}"""
                "CUSTOMER", "CATEGORY" -> """{"name":"N"}"""
                "ORDER" -> """{"total":0}"""
                else -> """{"id":"1"}"""
            }
            val result = validator.validateBatch(listOf(op(entityType = entityType, payload = payload)))
            assertTrue(result.valid.isNotEmpty(), "Expected $entityType to be valid but got: ${result.invalid.firstOrNull()?.reason}")
        }
    }

    // ── Multiple validation errors in single op ─────────────────────────

    @Test
    fun `multiple errors collected with semicolon separator`() {
        val result = validator.validateBatch(listOf(
            op(id = "", entityId = "", entityType = "BOGUS", operation = "MERGE", payload = "bad")
        ))
        assertEquals(1, result.invalid.size)
        val reason = result.invalid.first().reason
        // Should contain multiple semicolon-separated errors
        assertTrue(reason.contains(";"), "Expected semicolons in: $reason")
    }

    // ── Timestamp edge cases ────────────────────────────────────────────

    @Test
    fun `timestamp exactly at 60s tolerance boundary passes`() {
        val boundary = System.currentTimeMillis() + 59_000 // 59s (under 60s threshold)
        val result = validator.validateBatch(listOf(op(createdAt = boundary)))
        assertEquals(1, result.valid.size)
    }

    @Test
    fun `timestamp at exactly 60s001 in future is rejected`() {
        val justOver = System.currentTimeMillis() + 61_000
        val result = validator.validateBatch(listOf(op(createdAt = justOver)))
        assertEquals(1, result.invalid.size)
    }

    @Test
    fun `very old timestamp is accepted - no minimum check`() {
        val result = validator.validateBatch(listOf(op(createdAt = 0L)))
        assertEquals(1, result.valid.size)
    }

    @Test
    fun `negative timestamp is accepted - no minimum check`() {
        val result = validator.validateBatch(listOf(op(createdAt = -1000L)))
        assertEquals(1, result.valid.size)
    }

    // ── JSON edge cases ─────────────────────────────────────────────────

    @Test
    fun `JSON array payload is valid JSON`() {
        val result = validator.validateBatch(listOf(
            op(operation = "DELETE", payload = """[{"id":"1"}]""")
        ))
        assertEquals(1, result.valid.size)
    }

    @Test
    fun `empty JSON object is valid`() {
        val result = validator.validateBatch(listOf(
            op(operation = "DELETE", payload = """{}""")
        ))
        assertEquals(1, result.valid.size)
    }

    @Test
    fun `whitespace-only payload is rejected`() {
        val result = validator.validateBatch(listOf(op(payload = "   ")))
        assertEquals(1, result.invalid.size)
    }

    @Test
    fun `unicode in JSON payload is accepted`() {
        val result = validator.validateBatch(listOf(
            op(payload = """{"name":"商品テスト","price":10.0}""")
        ))
        assertEquals(1, result.valid.size)
    }
}
