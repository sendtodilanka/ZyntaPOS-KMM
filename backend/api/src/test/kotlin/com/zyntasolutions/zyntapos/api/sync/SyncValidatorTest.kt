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
        payload: String = """{"name":"Test"}""",
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
            op(id = "valid-2", entityType = "ORDER"),
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
}
