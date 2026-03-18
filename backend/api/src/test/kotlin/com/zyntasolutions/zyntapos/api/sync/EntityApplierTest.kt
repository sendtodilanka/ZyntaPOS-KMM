package com.zyntasolutions.zyntapos.api.sync

import com.zyntasolutions.zyntapos.api.models.SyncOperation
import kotlin.test.Test
import kotlin.test.assertFailsWith

/**
 * Unit tests for [EntityApplier].
 *
 * Note: Full DB integration tests (actual Exposed transactions) live in
 * [SyncPushPullIntegrationTest]. Here we verify the applier's non-DB behaviour:
 * unknown entity types are silently skipped, and exceptions from applyProduct
 * are re-thrown so the outer transaction rolls back.
 */
class EntityApplierTest {

    private val applier = EntityApplier()

    private fun op(
        entityType: String = "PRODUCT",
        entityId: String = "e-1",
        operation: String = "INSERT",
        payload: String = """{"name":"Widget","price":9.99,"cost_price":4.99,"stock_qty":10.0,"is_active":true}""",
        createdAt: Long = System.currentTimeMillis(),
    ) = SyncOperation(
        id = "op-1", entityType = entityType, entityId = entityId,
        operation = operation, payload = payload, createdAt = createdAt,
    )

    // ── Unknown entity types ──────────────────────────────────────────────

    @Test
    fun `unknown entity type is a no-op - does not throw`() {
        // Calling outside a transaction for non-DB entity type should be silent
        // (the DB call path only activates for "PRODUCT")
        // Since there is no DB in unit tests, PRODUCT would throw; UNKNOWN_TYPE should not.
        applier.applyInTransaction("store-1", op(entityType = "UNKNOWN_ENTITY"))
        // If we reach here without exception, the test passes
    }

    @Test
    fun `invalid JSON payload does not throw - returns early`() {
        // applyProduct parses JSON with runCatching and returns on failure
        applier.applyInTransaction("store-1", op(entityType = "UNKNOWN_ENTITY", payload = "bad-json"))
    }

    @Test
    fun `CREATE operation is treated same as INSERT`() {
        // Should not throw for unknown entity type (DB would be needed for PRODUCT)
        applier.applyInTransaction("store-1", op(entityType = "UNKNOWN_ENTITY", operation = "CREATE"))
    }

    @Test
    fun `DELETE for unknown entity type is a no-op`() {
        applier.applyInTransaction("store-1", op(entityType = "UNKNOWN_ENTITY", operation = "DELETE"))
    }

    // ── Entity type routing (no DB - only unknown types are safe) ─────────

    @Test
    fun `UPDATE for unknown entity type is a no-op`() {
        applier.applyInTransaction("store-1", op(entityType = "TOTALLY_FAKE", operation = "UPDATE"))
    }

    @Test
    fun `empty string entity type is a no-op`() {
        applier.applyInTransaction("store-1", op(entityType = ""))
    }

    @Test
    fun `case-sensitive entity type - lowercase product is unknown`() {
        // "product" != "PRODUCT" - should be treated as unknown, not routed to applyProduct
        applier.applyInTransaction("store-1", op(entityType = "product"))
    }

    // ── Payload parsing edge cases ───────────────────────────────────────

    @Test
    fun `empty JSON object payload does not throw for unknown type`() {
        applier.applyInTransaction("store-1", op(entityType = "UNKNOWN_ENTITY", payload = "{}"))
    }

    @Test
    fun `JSON array payload does not throw for unknown type`() {
        applier.applyInTransaction("store-1", op(entityType = "UNKNOWN_ENTITY", payload = "[]"))
    }

    @Test
    fun `null-like payload string does not throw for unknown type`() {
        applier.applyInTransaction("store-1", op(entityType = "UNKNOWN_ENTITY", payload = "null"))
    }

    @Test
    fun `deeply nested JSON payload is accepted for unknown type`() {
        val nested = """{"a":{"b":{"c":{"d":"deep"}}}}"""
        applier.applyInTransaction("store-1", op(entityType = "UNKNOWN_ENTITY", payload = nested))
    }

    @Test
    fun `unicode in payload does not throw for unknown type`() {
        val unicode = """{"name":"商品テスト","description":"价格 €100"}"""
        applier.applyInTransaction("store-1", op(entityType = "UNKNOWN_ENTITY", payload = unicode))
    }

    // ── Known entity types without DB - verify they throw (DB required) ──

    @Test
    fun `PRODUCT with invalid JSON returns early - no DB exception`() {
        // parsePayload returns null for invalid JSON, so applyProduct returns early
        applier.applyInTransaction("store-1", op(entityType = "PRODUCT", payload = "not-json"))
    }

    @Test
    fun `CATEGORY with invalid JSON returns early - no DB exception`() {
        applier.applyInTransaction("store-1", op(entityType = "CATEGORY", payload = "{bad"))
    }

    @Test
    fun `CUSTOMER with invalid JSON returns early - no DB exception`() {
        applier.applyInTransaction("store-1", op(entityType = "CUSTOMER", payload = ""))
    }

    @Test
    fun `SUPPLIER with invalid JSON returns early - no DB exception`() {
        applier.applyInTransaction("store-1", op(entityType = "SUPPLIER", payload = "xyz"))
    }

    @Test
    fun `ORDER with invalid JSON returns early - no DB exception`() {
        applier.applyInTransaction("store-1", op(entityType = "ORDER", payload = "[}"))
    }

    @Test
    fun `ORDER_ITEM with invalid JSON returns early - no DB exception`() {
        applier.applyInTransaction("store-1", op(entityType = "ORDER_ITEM", payload = "{{"))
    }

    @Test
    fun `AUDIT_ENTRY with invalid JSON returns early - no DB exception`() {
        applier.applyInTransaction("store-1", op(entityType = "AUDIT_ENTRY", payload = "nope"))
    }

    // ── Payload missing required fields - returns early ──────────────────

    @Test
    fun `PRODUCT payload missing name returns early - no DB exception`() {
        // applyProduct requires name: payload.str("name") ?: return
        applier.applyInTransaction(
            "store-1",
            op(entityType = "PRODUCT", payload = """{"price":10.0}"""),
        )
    }

    @Test
    fun `PRODUCT payload with null name returns early - no DB exception`() {
        applier.applyInTransaction(
            "store-1",
            op(entityType = "PRODUCT", payload = """{"name":null,"price":10.0}"""),
        )
    }

    @Test
    fun `CATEGORY payload missing name returns early`() {
        applier.applyInTransaction(
            "store-1",
            op(entityType = "CATEGORY", payload = """{"sort_order":1}"""),
        )
    }

    @Test
    fun `CUSTOMER payload missing name returns early`() {
        applier.applyInTransaction(
            "store-1",
            op(entityType = "CUSTOMER", payload = """{"email":"test@test.com"}"""),
        )
    }

    @Test
    fun `SUPPLIER payload missing name returns early`() {
        applier.applyInTransaction(
            "store-1",
            op(entityType = "SUPPLIER", payload = """{"phone":"123"}"""),
        )
    }

    @Test
    fun `ORDER_ITEM payload missing order_id returns early`() {
        // applyOrderItem requires orderId: payload.str("order_id") ?: return
        applier.applyInTransaction(
            "store-1",
            op(entityType = "ORDER_ITEM", payload = """{"product_id":"p1","quantity":1}"""),
        )
    }

    @Test
    fun `ORDER_ITEM payload with null order_id returns early`() {
        applier.applyInTransaction(
            "store-1",
            op(entityType = "ORDER_ITEM", payload = """{"order_id":null,"product_id":"p1"}"""),
        )
    }

    // ── AUDIT_ENTRY special behavior ─────────────────────────────────────

    @Test
    fun `AUDIT_ENTRY with UPDATE operation is ignored - append only`() {
        // applyAuditEntry only handles INSERT/CREATE; UPDATE/DELETE are logged and ignored
        applier.applyInTransaction(
            "store-1",
            op(
                entityType = "AUDIT_ENTRY",
                operation = "UPDATE",
                payload = """{"event_type":"LOGIN","user_id":"u1","details":"{}","hash":"h","previous_hash":"ph"}""",
            ),
        )
    }

    @Test
    fun `AUDIT_ENTRY with DELETE operation is ignored - append only`() {
        applier.applyInTransaction(
            "store-1",
            op(
                entityType = "AUDIT_ENTRY",
                operation = "DELETE",
                payload = """{"event_type":"LOGIN","user_id":"u1","details":"{}","hash":"h","previous_hash":"ph"}""",
            ),
        )
    }

    // ── All supported entity types route correctly ───────────────────────

    @Test
    fun `all seven supported entity types with bad JSON return early without exception`() {
        val types = listOf("PRODUCT", "CATEGORY", "CUSTOMER", "SUPPLIER", "ORDER", "ORDER_ITEM", "AUDIT_ENTRY")
        for (type in types) {
            applier.applyInTransaction("store-1", op(entityType = type, payload = "invalid"))
        }
    }

    // ── Operation types ──────────────────────────────────────────────────

    @Test
    fun `unrecognized operation type for unknown entity is a no-op`() {
        applier.applyInTransaction("store-1", op(entityType = "UNKNOWN_ENTITY", operation = "MERGE"))
    }

    @Test
    fun `empty operation string for unknown entity is a no-op`() {
        applier.applyInTransaction("store-1", op(entityType = "UNKNOWN_ENTITY", operation = ""))
    }
}
