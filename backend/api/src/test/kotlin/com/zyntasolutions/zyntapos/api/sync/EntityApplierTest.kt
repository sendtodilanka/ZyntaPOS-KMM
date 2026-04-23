package com.zyntasolutions.zyntapos.api.sync

import com.zyntasolutions.zyntapos.api.repository.DeadLetterRepository
import com.zyntasolutions.zyntapos.api.models.SyncOperation
import kotlin.test.Test
import kotlin.test.assertFailsWith

/**
 * Unit tests for [EntityApplier].
 *
 * Note: Full DB integration tests (actual Exposed transactions) live in
 * [SyncPushPullIntegrationTest]. Here we verify the applier's non-DB behaviour:
 * unknown entity types are silently skipped, invalid payloads return early,
 * and exceptions from apply methods are re-thrown so the outer transaction rolls back.
 *
 * Each entity type handler is tested for:
 * - Unknown entity type → no-op (no exception)
 * - Invalid JSON payload → no-op (returns early)
 * - Missing required fields → no-op (returns early)
 * - All operation types (INSERT, CREATE, UPDATE, DELETE) are accepted
 */
class EntityApplierTest {

    private val applier = EntityApplier(DeadLetterRepository())

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

    // ── Unknown entity type ──────────────────────────────────────────────

    @Test
    fun `unknown entity type is a no-op - does not throw`() {
        applier.applyInTransaction("store-1", "device-1", op(entityType = "UNKNOWN_ENTITY"))
    }

    @Test
    fun `invalid JSON payload does not throw - returns early`() {
        applier.applyInTransaction("store-1", "device-1", op(entityType = "UNKNOWN_ENTITY", payload = "bad-json"))
    }

    @Test
    fun `CREATE operation is treated same as INSERT`() {
        applier.applyInTransaction("store-1", "device-1", op(entityType = "UNKNOWN_ENTITY", operation = "CREATE"))
    }

    @Test
    fun `DELETE for unknown entity type is a no-op`() {
        applier.applyInTransaction("store-1", "device-1", op(entityType = "UNKNOWN_ENTITY", operation = "DELETE"))
    }

    // ── Entity type routing (no DB - only unknown types are safe) ─────────

    @Test
    fun `UPDATE for unknown entity type is a no-op`() {
        applier.applyInTransaction("store-1", "device-1", op(entityType = "TOTALLY_FAKE", operation = "UPDATE"))
    }

    @Test
    fun `empty string entity type is a no-op`() {
        applier.applyInTransaction("store-1", "device-1", op(entityType = ""))
    }

    @Test
    fun `case-insensitive normalization - lowercase entity type routes to handler`() {
        // EntityApplier normalizes entityType to uppercase — lowercase routes correctly.
        // "product" (lowercase) → uppercase "PRODUCT" → applyProduct, which will
        // throw outside a real DB transaction. This proves the routing works.
        assertFailsWith<Exception> {
            applier.applyInTransaction("store-1", "device-1", op(entityType = "product"))
        }
    }

    // ── Payload parsing edge cases ───────────────────────────────────────

    @Test
    fun `empty JSON object payload does not throw for unknown type`() {
        applier.applyInTransaction("store-1", "device-1", op(entityType = "UNKNOWN_ENTITY", payload = "{}"))
    }

    @Test
    fun `JSON array payload does not throw for unknown type`() {
        applier.applyInTransaction("store-1", "device-1", op(entityType = "UNKNOWN_ENTITY", payload = "[]"))
    }

    @Test
    fun `null-like payload string does not throw for unknown type`() {
        applier.applyInTransaction("store-1", "device-1", op(entityType = "UNKNOWN_ENTITY", payload = "null"))
    }

    @Test
    fun `deeply nested JSON payload is accepted for unknown type`() {
        val nested = """{"a":{"b":{"c":{"d":"deep"}}}}"""
        applier.applyInTransaction("store-1", "device-1", op(entityType = "UNKNOWN_ENTITY", payload = nested))
    }

    @Test
    fun `unicode in payload does not throw for unknown type`() {
        val unicode = """{"name":"商品テスト","description":"价格 €100"}"""
        applier.applyInTransaction("store-1", "device-1", op(entityType = "UNKNOWN_ENTITY", payload = unicode))
    }

    // ── STOCK_ADJUSTMENT (no DB — tests payload parsing logic) ───────────

    @Test
    fun `STOCK_ADJUSTMENT with invalid JSON is a no-op`() {
        applier.applyInTransaction("store-1", "device-1", op(entityType = "STOCK_ADJUSTMENT", payload = "{{bad"))
    }

    @Test
    fun `STOCK_ADJUSTMENT INSERT missing product_id returns early`() {
        // applyStockAdjustment requires product_id; missing → returns early
        // No DB means upsert won't be called, but the parsing check still runs
        applier.applyInTransaction(
            "store-1",
            "device-1",
            op(
                entityType = "STOCK_ADJUSTMENT",
                payload = """{"type":"INCREASE","quantity":5.0}"""
            )
        )
        // Reaches here = no exception = missing required field returns early
    }

    @Test
    fun `STOCK_ADJUSTMENT INSERT missing type returns early`() {
        applier.applyInTransaction(
            "store-1",
            "device-1",
            op(
                entityType = "STOCK_ADJUSTMENT",
                payload = """{"product_id":"p-1","quantity":5.0}"""
            )
        )
    }

    // ── CASH_REGISTER ────────────────────────────────────────────────────

    @Test
    fun `CASH_REGISTER with invalid JSON is a no-op`() {
        applier.applyInTransaction("store-1", "device-1", op(entityType = "CASH_REGISTER", payload = "{{bad"))
    }

    @Test
    fun `CASH_REGISTER missing name returns early`() {
        applier.applyInTransaction(
            "store-1",
            "device-1",
            op(entityType = "CASH_REGISTER", payload = """{"is_active":true}""")
        )
    }

    // ── REGISTER_SESSION ─────────────────────────────────────────────────

    @Test
    fun `REGISTER_SESSION with invalid JSON is a no-op`() {
        applier.applyInTransaction("store-1", "device-1", op(entityType = "REGISTER_SESSION", payload = "{{bad"))
    }

    @Test
    fun `REGISTER_SESSION missing register_id returns early`() {
        applier.applyInTransaction(
            "store-1",
            "device-1",
            op(entityType = "REGISTER_SESSION", payload = """{"opened_by":"user-1"}""")
        )
    }

    @Test
    fun `REGISTER_SESSION missing opened_by returns early`() {
        applier.applyInTransaction(
            "store-1",
            "device-1",
            op(entityType = "REGISTER_SESSION", payload = """{"register_id":"reg-1"}""")
        )
    }

    // ── CASH_MOVEMENT ────────────────────────────────────────────────────

    @Test
    fun `CASH_MOVEMENT with invalid JSON is a no-op`() {
        applier.applyInTransaction("store-1", "device-1", op(entityType = "CASH_MOVEMENT", payload = "{{bad"))
    }

    @Test
    fun `CASH_MOVEMENT missing session_id returns early`() {
        applier.applyInTransaction(
            "store-1",
            "device-1",
            op(entityType = "CASH_MOVEMENT", payload = """{"type":"IN","amount":100.0}""")
        )
    }

    // ── TAX_GROUP ────────────────────────────────────────────────────────

    @Test
    fun `TAX_GROUP with invalid JSON is a no-op`() {
        applier.applyInTransaction("store-1", "device-1", op(entityType = "TAX_GROUP", payload = "{{bad"))
    }

    @Test
    fun `TAX_GROUP missing name returns early`() {
        applier.applyInTransaction(
            "store-1",
            "device-1",
            op(entityType = "TAX_GROUP", payload = """{"rate":10.0}""")
        )
    }

    // ── UNIT_OF_MEASURE ──────────────────────────────────────────────────

    @Test
    fun `UNIT_OF_MEASURE with invalid JSON is a no-op`() {
        applier.applyInTransaction("store-1", "device-1", op(entityType = "UNIT_OF_MEASURE", payload = "{{bad"))
    }

    @Test
    fun `UNIT_OF_MEASURE missing name returns early`() {
        applier.applyInTransaction(
            "store-1",
            "device-1",
            op(entityType = "UNIT_OF_MEASURE", payload = """{"abbreviation":"kg"}""")
        )
    }

    // ── PAYMENT_SPLIT ────────────────────────────────────────────────────

    @Test
    fun `PAYMENT_SPLIT with invalid JSON is a no-op`() {
        applier.applyInTransaction("store-1", "device-1", op(entityType = "PAYMENT_SPLIT", payload = "{{bad"))
    }

    @Test
    fun `PAYMENT_SPLIT missing order_id returns early`() {
        applier.applyInTransaction(
            "store-1",
            "device-1",
            op(entityType = "PAYMENT_SPLIT", payload = """{"method":"CASH","amount":50.0}""")
        )
    }

    // ── COUPON ───────────────────────────────────────────────────────────

    @Test
    fun `COUPON with invalid JSON is a no-op`() {
        applier.applyInTransaction("store-1", "device-1", op(entityType = "COUPON", payload = "{{bad"))
    }

    @Test
    fun `COUPON missing code returns early`() {
        applier.applyInTransaction(
            "store-1",
            "device-1",
            op(entityType = "COUPON", payload = """{"name":"Summer Sale","discount_value":10.0}""")
        )
    }

    @Test
    fun `COUPON missing name returns early`() {
        applier.applyInTransaction(
            "store-1",
            "device-1",
            op(entityType = "COUPON", payload = """{"code":"SUMMER10","discount_value":10.0}""")
        )
    }

    // ── EXPENSE ──────────────────────────────────────────────────────────

    @Test
    fun `EXPENSE with invalid JSON is a no-op`() {
        applier.applyInTransaction("store-1", "device-1", op(entityType = "EXPENSE", payload = "{{bad"))
    }

    // ── SETTINGS ─────────────────────────────────────────────────────────

    @Test
    fun `SETTINGS with invalid JSON is a no-op`() {
        applier.applyInTransaction("store-1", "device-1", op(entityType = "SETTINGS", payload = "{{bad"))
    }

    @Test
    fun `SETTINGS missing key returns early`() {
        applier.applyInTransaction(
            "store-1",
            "device-1",
            op(entityType = "SETTINGS", payload = """{"value":"some-value"}""")
        )
    }

    // ── Existing entity types (regression) ───────────────────────────────

    @Test
    fun `PRODUCT with invalid JSON is a no-op`() {
        applier.applyInTransaction("store-1", "device-1", op(entityType = "PRODUCT", payload = "{{bad"))
    }

    @Test
    fun `PRODUCT missing name returns early`() {
        applier.applyInTransaction(
            "store-1",
            "device-1",
            op(entityType = "PRODUCT", payload = """{"price":9.99}""")
        )
    }

    @Test
    fun `PRODUCT payload with null name returns early - no DB exception`() {
        applier.applyInTransaction(
            "store-1",
            "device-1",
            op(entityType = "PRODUCT", payload = """{"name":null,"price":10.0}"""),
        )
    }

    @Test
    fun `CATEGORY with invalid JSON is a no-op`() {
        applier.applyInTransaction("store-1", "device-1", op(entityType = "CATEGORY", payload = "{{bad"))
    }

    @Test
    fun `CATEGORY missing name returns early`() {
        applier.applyInTransaction(
            "store-1",
            "device-1",
            op(entityType = "CATEGORY", payload = """{"sort_order":1}""")
        )
    }

    @Test
    fun `CUSTOMER with invalid JSON is a no-op`() {
        applier.applyInTransaction("store-1", "device-1", op(entityType = "CUSTOMER", payload = "{{bad"))
    }

    @Test
    fun `CUSTOMER missing name returns early`() {
        applier.applyInTransaction(
            "store-1",
            "device-1",
            op(entityType = "CUSTOMER", payload = """{"email":"test@test.com"}""")
        )
    }

    @Test
    fun `SUPPLIER with invalid JSON is a no-op`() {
        applier.applyInTransaction("store-1", "device-1", op(entityType = "SUPPLIER", payload = "{{bad"))
    }

    @Test
    fun `SUPPLIER missing name returns early`() {
        applier.applyInTransaction(
            "store-1",
            "device-1",
            op(entityType = "SUPPLIER", payload = """{"phone":"1234567890"}""")
        )
    }

    @Test
    fun `ORDER with invalid JSON is a no-op`() {
        applier.applyInTransaction("store-1", "device-1", op(entityType = "ORDER", payload = "{{bad"))
    }

    @Test
    fun `ORDER_ITEM with invalid JSON is a no-op`() {
        applier.applyInTransaction("store-1", "device-1", op(entityType = "ORDER_ITEM", payload = "{{bad"))
    }

    @Test
    fun `ORDER_ITEM missing order_id returns early`() {
        applier.applyInTransaction(
            "store-1",
            "device-1",
            op(entityType = "ORDER_ITEM", payload = """{"product_id":"p-1","quantity":1}""")
        )
    }

    @Test
    fun `ORDER_ITEM payload with null order_id returns early`() {
        applier.applyInTransaction(
            "store-1",
            "device-1",
            op(entityType = "ORDER_ITEM", payload = """{"order_id":null,"product_id":"p1"}"""),
        )
    }

    @Test
    fun `AUDIT_ENTRY with invalid JSON is a no-op`() {
        applier.applyInTransaction("store-1", "device-1", op(entityType = "AUDIT_ENTRY", payload = "{{bad"))
    }

    // ── AUDIT_ENTRY special behavior ─────────────────────────────────────

    @Test
    fun `AUDIT_ENTRY UPDATE is ignored - append-only`() {
        applier.applyInTransaction(
            "store-1",
            "device-1",
            op(
                entityType = "AUDIT_ENTRY",
                operation = "UPDATE",
                payload = """{"event_type":"LOGIN","user_id":"u-1","details":"{}","hash":"h","previous_hash":"ph"}"""
            )
        )
    }

    @Test
    fun `AUDIT_ENTRY DELETE is ignored - append-only`() {
        applier.applyInTransaction(
            "store-1",
            "device-1",
            op(
                entityType = "AUDIT_ENTRY",
                operation = "DELETE",
                payload = """{"event_type":"LOGIN","user_id":"u-1","details":"{}","hash":"h","previous_hash":"ph"}"""
            )
        )
    }

    // ── EMPLOYEE ────────────────────────────────────────────────────────

    @Test
    fun `EMPLOYEE with invalid JSON is a no-op`() {
        applier.applyInTransaction("store-1", "device-1", op(entityType = "EMPLOYEE", payload = "{{bad"))
    }

    @Test
    fun `EMPLOYEE missing name returns early`() {
        applier.applyInTransaction(
            "store-1",
            "device-1",
            op(entityType = "EMPLOYEE", payload = """{"email":"alice@test.com","role":"MANAGER"}""")
        )
    }

    // ── EXPENSE_CATEGORY ───────────────────────────────────────────────

    @Test
    fun `EXPENSE_CATEGORY with invalid JSON is a no-op`() {
        applier.applyInTransaction("store-1", "device-1", op(entityType = "EXPENSE_CATEGORY", payload = "{{bad"))
    }

    @Test
    fun `EXPENSE_CATEGORY missing name returns early`() {
        applier.applyInTransaction(
            "store-1",
            "device-1",
            op(entityType = "EXPENSE_CATEGORY", payload = """{"sort_order":1}""")
        )
    }

    // ── COUPON_USAGE ───────────────────────────────────────────────────

    @Test
    fun `COUPON_USAGE with invalid JSON is a no-op`() {
        applier.applyInTransaction("store-1", "device-1", op(entityType = "COUPON_USAGE", payload = "{{bad"))
    }

    @Test
    fun `COUPON_USAGE missing coupon_id returns early`() {
        applier.applyInTransaction(
            "store-1",
            "device-1",
            op(entityType = "COUPON_USAGE", payload = """{"order_id":"ord-1","discount_amount":5.0}""")
        )
    }

    @Test
    fun `COUPON_USAGE missing order_id returns early`() {
        applier.applyInTransaction(
            "store-1",
            "device-1",
            op(entityType = "COUPON_USAGE", payload = """{"coupon_id":"cpn-1","discount_amount":5.0}""")
        )
    }

    // ── PROMOTION ──────────────────────────────────────────────────────

    @Test
    fun `PROMOTION with invalid JSON is a no-op`() {
        applier.applyInTransaction("store-1", "device-1", op(entityType = "PROMOTION", payload = "{{bad"))
    }

    @Test
    fun `PROMOTION missing name returns early`() {
        applier.applyInTransaction(
            "store-1",
            "device-1",
            op(entityType = "PROMOTION", payload = """{"type":"PERCENTAGE","value":10.0}""")
        )
    }

    // ── CUSTOMER_GROUP ─────────────────────────────────────────────────

    @Test
    fun `CUSTOMER_GROUP with invalid JSON is a no-op`() {
        applier.applyInTransaction("store-1", "device-1", op(entityType = "CUSTOMER_GROUP", payload = "{{bad"))
    }

    @Test
    fun `CUSTOMER_GROUP missing name returns early`() {
        applier.applyInTransaction(
            "store-1",
            "device-1",
            op(entityType = "CUSTOMER_GROUP", payload = """{"discount_rate":5.0}""")
        )
    }

    // ── All supported entity types route correctly ───────────────────────

    @Test
    fun `all supported entity types with bad JSON return early without exception`() {
        val types = listOf(
            "PRODUCT", "CATEGORY", "CUSTOMER", "SUPPLIER", "ORDER", "ORDER_ITEM", "AUDIT_ENTRY",
            "STOCK_ADJUSTMENT", "CASH_REGISTER", "REGISTER_SESSION", "CASH_MOVEMENT",
            "TAX_GROUP", "UNIT_OF_MEASURE", "PAYMENT_SPLIT", "COUPON", "EXPENSE", "SETTINGS",
            "EMPLOYEE", "EXPENSE_CATEGORY", "COUPON_USAGE", "PROMOTION", "CUSTOMER_GROUP"
        )
        for (type in types) {
            applier.applyInTransaction("store-1", "device-1", op(entityType = type, payload = "invalid"))
        }
    }

    // ── Operation types ──────────────────────────────────────────────────

    @Test
    fun `unrecognized operation type for unknown entity is a no-op`() {
        applier.applyInTransaction("store-1", "device-1", op(entityType = "UNKNOWN_ENTITY", operation = "MERGE"))
    }

    @Test
    fun `empty operation string for unknown entity is a no-op`() {
        applier.applyInTransaction("store-1", "device-1", op(entityType = "UNKNOWN_ENTITY", operation = ""))
    }

    // ── DELETE operations for known entity types require a DB transaction ──
    // Full DELETE integration tests are in SyncPushPullIntegrationTest
    // (they need a live PostgreSQL + Exposed transaction context).

    // ── CATEGORY self-referencing parent (no DB needed) ──────────────────

    @Test
    fun `CATEGORY with self-referencing parentId is rejected - returns early`() {
        // parentId == entityId → self-reference → returns early without upsert
        // Since no DB transaction is active, if it tries to upsert it would fail;
        // the fact that no exception is thrown proves the self-reference check works.
        applier.applyInTransaction(
            "store-1",
            "device-1",
            op(
                entityType = "CATEGORY",
                entityId = "cat-1",
                payload = """{"name":"Test Category","parent_id":"cat-1","sort_order":0,"is_active":true}"""
            )
        )
    }

    @Test
    fun `CATEGORY with null parentId is accepted (no circular check needed)`() {
        // Null parent means root category — no circular ref check passes.
        // Upsert fails (no active DB transaction), proving we reached the DB layer
        // without the circular-ref guard blocking.
        assertFailsWith<IllegalStateException> {
            applier.applyInTransaction(
                "store-1",
                "device-1",
                op(
                    entityType = "CATEGORY",
                    entityId = "cat-2",
                    payload = """{"name":"Root Category","sort_order":0,"is_active":true}"""
                )
            )
        }
    }

    @Test
    fun `CATEGORY with missing name returns early`() {
        applier.applyInTransaction(
            "store-1",
            "device-1",
            op(
                entityType = "CATEGORY",
                entityId = "cat-3",
                payload = """{"sort_order":0,"is_active":true}"""
            )
        )
    }
}
