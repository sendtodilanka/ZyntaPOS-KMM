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
            op(id = "valid-2", entityType = "ORDER", payload = """{"grand_total":10.0}"""),
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
    fun `ORDER with negative grand_total is rejected`() {
        val result = validator.validateBatch(listOf(
            op(entityType = "ORDER", payload = """{"grand_total":-10.0}""")
        ))
        assertEquals(1, result.invalid.size)
        assertTrue(result.invalid.first().reason.contains("ORDER.grand_total"))
    }

    @Test
    fun `ORDER with zero grand_total is accepted`() {
        val result = validator.validateBatch(listOf(
            op(entityType = "ORDER", payload = """{"grand_total":0.0}""")
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
                "CUSTOMER", "CATEGORY", "SUPPLIER", "CASH_REGISTER", "UNIT_OF_MEASURE" -> """{"name":"N"}"""
                "ORDER" -> """{"grand_total":0}"""
                "TAX_GROUP" -> """{"name":"T","rate":10}"""
                "STOCK_ADJUSTMENT" -> """{"product_id":"p-1","type":"INCREASE","quantity":5}"""
                "REGISTER_SESSION" -> """{"register_id":"r-1","opened_by":"u-1"}"""
                "CASH_MOVEMENT" -> """{"session_id":"s-1","type":"IN","amount":100}"""
                "COUPON" -> """{"code":"C","name":"N","discount_value":10}"""
                "PAYMENT_SPLIT" -> """{"order_id":"o-1","amount":50}"""
                "SETTINGS" -> """{"key":"k","value":"v"}"""
                "REPLENISHMENT_RULE" -> """{"product_id":"p-1","warehouse_id":"w-1","reorder_point":10,"reorder_qty":50}"""
                "PURCHASE_ORDER" -> """{"supplier_id":"s-1","order_number":"PO-1","total_amount":100}"""
                "TRANSIT_EVENT" -> """{"transfer_id":"t-1","event_type":"DISPATCHED"}"""
                "WAREHOUSE_STOCK" -> """{"warehouse_id":"w-1","product_id":"p-1","quantity":100}"""
                else -> """{"id":"1"}"""
            }
            val result = validator.validateBatch(listOf(op(entityType = entityType, payload = payload)))
            assertTrue(result.valid.isNotEmpty(), "Expected $entityType to be valid but got: ${result.invalid.firstOrNull()?.reason}")
        }
    }

    @Test
    fun `lowercase entity types are normalized to uppercase and accepted`() {
        val lowercasePayloads = mapOf(
            "product" to """{"name":"P","price":1.0}""",
            "category" to """{"name":"C"}""",
            "customer" to """{"name":"N"}""",
            "order" to """{"grand_total":0}""",
            "settings" to """{"key":"k","value":"v"}""",
        )
        for ((entityType, payload) in lowercasePayloads) {
            val result = validator.validateBatch(listOf(op(entityType = entityType, payload = payload)))
            assertTrue(result.valid.isNotEmpty(), "Expected lowercase '$entityType' to be accepted")
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
    fun `negative timestamp is rejected - must be non-negative`() {
        val result = validator.validateBatch(listOf(op(createdAt = -1000L)))
        assertEquals(0, result.valid.size)
        assertEquals(1, result.invalid.size)
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
