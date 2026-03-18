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

    // ── CASH_REGISTER is a valid entity type ─────────────────────────────

    @Test
    fun `CASH_REGISTER is a valid entity type`() {
        val result = validator.validateBatch(listOf(
            op(entityType = "CASH_REGISTER", payload = """{"name":"Register 1"}""")
        ))
        assertEquals(1, result.valid.size)
        assertTrue(result.invalid.isEmpty())
    }

    // ── Field-level validation for new entity types ─────────────────────

    @Test
    fun `SUPPLIER blank name is rejected`() {
        val result = validator.validateBatch(listOf(
            op(entityType = "SUPPLIER", payload = """{"name":""}""")
        ))
        assertEquals(1, result.invalid.size)
        assertTrue(result.invalid.first().reason.contains("SUPPLIER.name"))
    }

    @Test
    fun `TAX_GROUP blank name is rejected`() {
        val result = validator.validateBatch(listOf(
            op(entityType = "TAX_GROUP", payload = """{"name":"","rate":10.0}""")
        ))
        assertEquals(1, result.invalid.size)
        assertTrue(result.invalid.first().reason.contains("TAX_GROUP.name"))
    }

    @Test
    fun `TAX_GROUP rate above 100 is rejected`() {
        val result = validator.validateBatch(listOf(
            op(entityType = "TAX_GROUP", payload = """{"name":"VAT","rate":150.0}""")
        ))
        assertEquals(1, result.invalid.size)
        assertTrue(result.invalid.first().reason.contains("TAX_GROUP.rate"))
    }

    @Test
    fun `TAX_GROUP negative rate is rejected`() {
        val result = validator.validateBatch(listOf(
            op(entityType = "TAX_GROUP", payload = """{"name":"VAT","rate":-5.0}""")
        ))
        assertEquals(1, result.invalid.size)
        assertTrue(result.invalid.first().reason.contains("TAX_GROUP.rate"))
    }

    @Test
    fun `TAX_GROUP valid rate passes`() {
        val result = validator.validateBatch(listOf(
            op(entityType = "TAX_GROUP", payload = """{"name":"VAT","rate":15.0}""")
        ))
        assertEquals(1, result.valid.size)
    }

    @Test
    fun `UNIT_OF_MEASURE blank name is rejected`() {
        val result = validator.validateBatch(listOf(
            op(entityType = "UNIT_OF_MEASURE", payload = """{"name":""}""")
        ))
        assertEquals(1, result.invalid.size)
        assertTrue(result.invalid.first().reason.contains("UNIT_OF_MEASURE.name"))
    }

    @Test
    fun `STOCK_ADJUSTMENT blank product_id is rejected`() {
        val result = validator.validateBatch(listOf(
            op(entityType = "STOCK_ADJUSTMENT", payload = """{"product_id":"","type":"INCREASE","quantity":5}""")
        ))
        assertEquals(1, result.invalid.size)
        assertTrue(result.invalid.first().reason.contains("STOCK_ADJUSTMENT.product_id"))
    }

    @Test
    fun `STOCK_ADJUSTMENT invalid type is rejected`() {
        val result = validator.validateBatch(listOf(
            op(entityType = "STOCK_ADJUSTMENT", payload = """{"product_id":"p-1","type":"INVALID","quantity":5}""")
        ))
        assertEquals(1, result.invalid.size)
        assertTrue(result.invalid.first().reason.contains("STOCK_ADJUSTMENT.type"))
    }

    @Test
    fun `STOCK_ADJUSTMENT negative quantity is rejected`() {
        val result = validator.validateBatch(listOf(
            op(entityType = "STOCK_ADJUSTMENT", payload = """{"product_id":"p-1","type":"INCREASE","quantity":-5}""")
        ))
        assertEquals(1, result.invalid.size)
        assertTrue(result.invalid.first().reason.contains("STOCK_ADJUSTMENT.quantity"))
    }

    @Test
    fun `STOCK_ADJUSTMENT valid payload passes`() {
        val result = validator.validateBatch(listOf(
            op(entityType = "STOCK_ADJUSTMENT", payload = """{"product_id":"p-1","type":"INCREASE","quantity":10}""")
        ))
        assertEquals(1, result.valid.size)
    }

    @Test
    fun `CASH_REGISTER blank name is rejected`() {
        val result = validator.validateBatch(listOf(
            op(entityType = "CASH_REGISTER", payload = """{"name":""}""")
        ))
        assertEquals(1, result.invalid.size)
        assertTrue(result.invalid.first().reason.contains("CASH_REGISTER.name"))
    }

    @Test
    fun `REGISTER_SESSION blank register_id is rejected`() {
        val result = validator.validateBatch(listOf(
            op(entityType = "REGISTER_SESSION", payload = """{"register_id":"","opened_by":"user-1"}""")
        ))
        assertEquals(1, result.invalid.size)
        assertTrue(result.invalid.first().reason.contains("REGISTER_SESSION.register_id"))
    }

    @Test
    fun `REGISTER_SESSION blank opened_by is rejected`() {
        val result = validator.validateBatch(listOf(
            op(entityType = "REGISTER_SESSION", payload = """{"register_id":"reg-1","opened_by":""}""")
        ))
        assertEquals(1, result.invalid.size)
        assertTrue(result.invalid.first().reason.contains("REGISTER_SESSION.opened_by"))
    }

    @Test
    fun `CASH_MOVEMENT blank session_id is rejected`() {
        val result = validator.validateBatch(listOf(
            op(entityType = "CASH_MOVEMENT", payload = """{"session_id":"","type":"IN","amount":100}""")
        ))
        assertEquals(1, result.invalid.size)
        assertTrue(result.invalid.first().reason.contains("CASH_MOVEMENT.session_id"))
    }

    @Test
    fun `CASH_MOVEMENT negative amount is rejected`() {
        val result = validator.validateBatch(listOf(
            op(entityType = "CASH_MOVEMENT", payload = """{"session_id":"s-1","type":"IN","amount":-50}""")
        ))
        assertEquals(1, result.invalid.size)
        assertTrue(result.invalid.first().reason.contains("CASH_MOVEMENT.amount"))
    }

    @Test
    fun `CASH_MOVEMENT invalid type is rejected`() {
        val result = validator.validateBatch(listOf(
            op(entityType = "CASH_MOVEMENT", payload = """{"session_id":"s-1","type":"BOTH","amount":100}""")
        ))
        assertEquals(1, result.invalid.size)
        assertTrue(result.invalid.first().reason.contains("CASH_MOVEMENT.type"))
    }

    @Test
    fun `COUPON blank code is rejected`() {
        val result = validator.validateBatch(listOf(
            op(entityType = "COUPON", payload = """{"code":"","name":"Sale","discount_value":10}""")
        ))
        assertEquals(1, result.invalid.size)
        assertTrue(result.invalid.first().reason.contains("COUPON.code"))
    }

    @Test
    fun `COUPON blank name is rejected`() {
        val result = validator.validateBatch(listOf(
            op(entityType = "COUPON", payload = """{"code":"SALE10","name":"","discount_value":10}""")
        ))
        assertEquals(1, result.invalid.size)
        assertTrue(result.invalid.first().reason.contains("COUPON.name"))
    }

    @Test
    fun `COUPON negative discount_value is rejected`() {
        val result = validator.validateBatch(listOf(
            op(entityType = "COUPON", payload = """{"code":"SALE","name":"Sale","discount_value":-10}""")
        ))
        assertEquals(1, result.invalid.size)
        assertTrue(result.invalid.first().reason.contains("COUPON.discount_value"))
    }

    @Test
    fun `EXPENSE negative amount is rejected`() {
        val result = validator.validateBatch(listOf(
            op(entityType = "EXPENSE", payload = """{"amount":-100}""")
        ))
        assertEquals(1, result.invalid.size)
        assertTrue(result.invalid.first().reason.contains("EXPENSE.amount"))
    }

    @Test
    fun `PAYMENT_SPLIT blank order_id is rejected`() {
        val result = validator.validateBatch(listOf(
            op(entityType = "PAYMENT_SPLIT", payload = """{"order_id":"","method":"CASH","amount":50}""")
        ))
        assertEquals(1, result.invalid.size)
        assertTrue(result.invalid.first().reason.contains("PAYMENT_SPLIT.order_id"))
    }

    @Test
    fun `PAYMENT_SPLIT negative amount is rejected`() {
        val result = validator.validateBatch(listOf(
            op(entityType = "PAYMENT_SPLIT", payload = """{"order_id":"o-1","method":"CASH","amount":-50}""")
        ))
        assertEquals(1, result.invalid.size)
        assertTrue(result.invalid.first().reason.contains("PAYMENT_SPLIT.amount"))
    }

    @Test
    fun `SETTINGS blank key is rejected`() {
        val result = validator.validateBatch(listOf(
            op(entityType = "SETTINGS", payload = """{"key":"","value":"test"}""")
        ))
        assertEquals(1, result.invalid.size)
        assertTrue(result.invalid.first().reason.contains("SETTINGS.key"))
    }

    @Test
    fun `SETTINGS valid payload passes`() {
        val result = validator.validateBatch(listOf(
            op(entityType = "SETTINGS", payload = """{"key":"store_name","value":"My Store"}""")
        ))
        assertEquals(1, result.valid.size)
    }

    @Test
    fun `DELETE operations skip field validation`() {
        // DELETE should not trigger field-level validation
        val result = validator.validateBatch(listOf(
            op(entityType = "STOCK_ADJUSTMENT", operation = "DELETE", payload = """{}""")
        ))
        assertEquals(1, result.valid.size)
    }

    @Test
    fun `all new entity types with valid minimal payload pass`() {
        val validPayloads = mapOf(
            "CASH_REGISTER" to """{"name":"Register 1"}""",
            "REGISTER_SESSION" to """{"register_id":"r-1","opened_by":"u-1"}""",
            "CASH_MOVEMENT" to """{"session_id":"s-1","type":"IN","amount":100}""",
            "TAX_GROUP" to """{"name":"VAT","rate":15}""",
            "UNIT_OF_MEASURE" to """{"name":"Kilogram"}""",
            "STOCK_ADJUSTMENT" to """{"product_id":"p-1","type":"INCREASE","quantity":5}""",
            "COUPON" to """{"code":"SALE","name":"Sale","discount_value":10}""",
            "EXPENSE" to """{"amount":50}""",
            "PAYMENT_SPLIT" to """{"order_id":"o-1","method":"CASH","amount":50}""",
            "SETTINGS" to """{"key":"theme","value":"dark"}""",
        )
        validPayloads.forEach { (entityType, payload) ->
            val result = validator.validateBatch(listOf(op(id = "op-$entityType", entityType = entityType, payload = payload)))
            assertEquals(1, result.valid.size, "Expected $entityType with payload $payload to pass validation")
        }
    }
}
