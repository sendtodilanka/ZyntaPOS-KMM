package com.zyntasolutions.zyntapos.data.mapper

import com.zyntasolutions.zyntapos.data.local.mapper.SyncOperationMapper
import com.zyntasolutions.zyntapos.db.Pending_operations
import com.zyntasolutions.zyntapos.domain.model.SyncOperation
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * ZyntaPOS — SyncOperationMapper Unit Tests (commonTest)
 *
 * Coverage (toDomain):
 *  A. all required fields mapped correctly
 *  B. "CREATE" operation maps to INSERT
 *  C. "INSERT" operation maps to INSERT
 *  D. "DELETE" operation maps to DELETE
 *  E. any other operation string maps to UPDATE
 *  F. "SYNCED" status maps to SYNCED
 *  G. "FAILED" status maps to FAILED
 *  H. "SYNCING"/"IN_FLIGHT" status maps to IN_FLIGHT
 *  I. any other status string maps to PENDING
 *  J. retry_count Long converts to Int
 *  K. created_at Long converts to Instant
 *
 * Coverage (operationToSql):
 *  L. INSERT → "CREATE"
 *  M. UPDATE → "UPDATE"
 *  N. DELETE → "DELETE"
 */
class SyncOperationMapperTest {

    private fun buildRow(
        id: String = "op-1",
        entityType: String = "product",
        entityId: String = "prod-1",
        operation: String = "CREATE",
        payload: String = "{}",
        status: String = "PENDING",
        retryCount: Long = 0L,
        createdAt: Long = 1_000_000L,
        lastTried: Long = 0L,
        storeId: String = "store-1",
    ) = Pending_operations(
        id = id,
        entity_type = entityType,
        entity_id = entityId,
        operation = operation,
        payload = payload,
        status = status,
        retry_count = retryCount,
        created_at = createdAt,
        last_tried = lastTried,
        store_id = storeId,
    )

    // ── toDomain ──────────────────────────────────────────────────────────────

    @Test
    fun `A - toDomain maps all required fields correctly`() {
        val domain = SyncOperationMapper.toDomain(
            buildRow(id = "op-99", entityType = "order", entityId = "ord-1", payload = "{\"foo\":\"bar\"}")
        )
        assertEquals("op-99", domain.id)
        assertEquals("order", domain.entityType)
        assertEquals("ord-1", domain.entityId)
        assertEquals("{\"foo\":\"bar\"}", domain.payload)
    }

    @Test
    fun `B - CREATE operation maps to INSERT`() {
        assertEquals(SyncOperation.Operation.INSERT, SyncOperationMapper.toDomain(buildRow(operation = "CREATE")).operation)
    }

    @Test
    fun `C - INSERT operation maps to INSERT`() {
        assertEquals(SyncOperation.Operation.INSERT, SyncOperationMapper.toDomain(buildRow(operation = "INSERT")).operation)
    }

    @Test
    fun `D - DELETE operation maps to DELETE`() {
        assertEquals(SyncOperation.Operation.DELETE, SyncOperationMapper.toDomain(buildRow(operation = "DELETE")).operation)
    }

    @Test
    fun `E - unknown operation string maps to UPDATE`() {
        assertEquals(SyncOperation.Operation.UPDATE, SyncOperationMapper.toDomain(buildRow(operation = "UPDATE")).operation)
        assertEquals(SyncOperation.Operation.UPDATE, SyncOperationMapper.toDomain(buildRow(operation = "REPLACE")).operation)
    }

    @Test
    fun `F - SYNCED status maps to SYNCED`() {
        assertEquals(SyncOperation.Status.SYNCED, SyncOperationMapper.toDomain(buildRow(status = "SYNCED")).status)
    }

    @Test
    fun `G - FAILED status maps to FAILED`() {
        assertEquals(SyncOperation.Status.FAILED, SyncOperationMapper.toDomain(buildRow(status = "FAILED")).status)
    }

    @Test
    fun `H - SYNCING and IN_FLIGHT map to IN_FLIGHT`() {
        assertEquals(SyncOperation.Status.IN_FLIGHT, SyncOperationMapper.toDomain(buildRow(status = "SYNCING")).status)
        assertEquals(SyncOperation.Status.IN_FLIGHT, SyncOperationMapper.toDomain(buildRow(status = "IN_FLIGHT")).status)
    }

    @Test
    fun `I - unknown status maps to PENDING`() {
        assertEquals(SyncOperation.Status.PENDING, SyncOperationMapper.toDomain(buildRow(status = "PENDING")).status)
        assertEquals(SyncOperation.Status.PENDING, SyncOperationMapper.toDomain(buildRow(status = "UNKNOWN")).status)
    }

    @Test
    fun `J - retry_count Long converts to Int`() {
        assertEquals(3, SyncOperationMapper.toDomain(buildRow(retryCount = 3L)).retryCount)
    }

    @Test
    fun `K - created_at Long converts to Instant epoch millis`() {
        val domain = SyncOperationMapper.toDomain(buildRow(createdAt = 1_700_000_000_000L))
        assertEquals(1_700_000_000_000L, domain.createdAt.toEpochMilliseconds())
    }

    // ── operationToSql ────────────────────────────────────────────────────────

    @Test
    fun `L - operationToSql INSERT returns CREATE`() {
        assertEquals("CREATE", SyncOperationMapper.operationToSql(SyncOperation.Operation.INSERT))
    }

    @Test
    fun `M - operationToSql UPDATE returns UPDATE`() {
        assertEquals("UPDATE", SyncOperationMapper.operationToSql(SyncOperation.Operation.UPDATE))
    }

    @Test
    fun `N - operationToSql DELETE returns DELETE`() {
        assertEquals("DELETE", SyncOperationMapper.operationToSql(SyncOperation.Operation.DELETE))
    }
}
