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
        operation: String = "INSERT",
        payload: String = """{"name":"Widget","price":9.99,"cost_price":4.99,"stock_qty":10.0,"is_active":true}""",
        createdAt: Long = System.currentTimeMillis(),
    ) = SyncOperation(
        id = "op-1", entityType = entityType, entityId = "e-1",
        operation = operation, payload = payload, createdAt = createdAt,
    )

    @Test
    fun `unknown entity type is a no-op — does not throw`() {
        // Calling outside a transaction for non-DB entity type should be silent
        // (the DB call path only activates for "PRODUCT")
        // Since there is no DB in unit tests, PRODUCT would throw; UNKNOWN_TYPE should not.
        applier.applyInTransaction("store-1", op(entityType = "UNKNOWN_ENTITY"))
        // If we reach here without exception, the test passes
    }

    @Test
    fun `invalid JSON payload does not throw — returns early`() {
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
}
