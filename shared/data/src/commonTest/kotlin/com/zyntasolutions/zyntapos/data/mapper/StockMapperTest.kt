package com.zyntasolutions.zyntapos.data.mapper

import com.zyntasolutions.zyntapos.data.local.mapper.StockMapper
import com.zyntasolutions.zyntapos.db.Stock_adjustments
import com.zyntasolutions.zyntapos.domain.model.StockAdjustment
import com.zyntasolutions.zyntapos.domain.model.SyncStatus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.datetime.Instant

/**
 * ZyntaPOS — StockMapper Unit Tests (commonTest)
 *
 * Coverage (toDomain):
 *  A. all required fields mapped correctly
 *  B. type string round-trips through StockAdjustment.Type.valueOf
 *  C. timestamp Long converts to Instant correctly
 *  D. sync_status string converts to SyncStatus.State by name
 *
 * Coverage (toInsertParams):
 *  E. all fields mapped correctly
 *  F. type enum serialised to its name string
 *  G. timestamp Instant converts to epoch millis
 *  H. referenceId is always null (not yet used in InsertParams)
 *  I. default syncStatus is PENDING
 *  J. custom syncStatus used when provided
 */
class StockMapperTest {

    private fun buildRow(
        id: String = "adj-1",
        productId: String = "prod-1",
        type: String = "INCREASE",
        quantity: Double = 10.0,
        reason: String = "Delivery received",
        adjustedBy: String = "user-1",
        referenceId: String? = null,
        timestamp: Long = 1_700_000_000_000L,
        syncStatus: String = "SYNCED",
    ) = Stock_adjustments(
        id = id,
        product_id = productId,
        type = type,
        quantity = quantity,
        reason = reason,
        adjusted_by = adjustedBy,
        reference_id = referenceId,
        timestamp = timestamp,
        sync_status = syncStatus,
    )

    private fun buildAdjustment(
        id: String = "adj-1",
        productId: String = "prod-1",
        type: StockAdjustment.Type = StockAdjustment.Type.INCREASE,
        quantity: Double = 10.0,
        reason: String = "Delivery received",
        adjustedBy: String = "user-1",
        timestamp: Instant = Instant.fromEpochMilliseconds(1_700_000_000_000L),
        syncStatus: SyncStatus = SyncStatus(SyncStatus.State.PENDING),
    ) = StockAdjustment(
        id = id,
        productId = productId,
        type = type,
        quantity = quantity,
        reason = reason,
        adjustedBy = adjustedBy,
        timestamp = timestamp,
        syncStatus = syncStatus,
    )

    // ── toDomain ──────────────────────────────────────────────────────────────

    @Test
    fun `A - toDomain maps all required fields correctly`() {
        val domain = StockMapper.toDomain(buildRow(id = "adj-99", productId = "prod-42"))
        assertEquals("adj-99", domain.id)
        assertEquals("prod-42", domain.productId)
        assertEquals(10.0, domain.quantity)
        assertEquals("Delivery received", domain.reason)
        assertEquals("user-1", domain.adjustedBy)
    }

    @Test
    fun `B - toDomain maps type string to StockAdjustment Type enum`() {
        assertEquals(StockAdjustment.Type.INCREASE, StockMapper.toDomain(buildRow(type = "INCREASE")).type)
        assertEquals(StockAdjustment.Type.DECREASE, StockMapper.toDomain(buildRow(type = "DECREASE")).type)
        assertEquals(StockAdjustment.Type.TRANSFER, StockMapper.toDomain(buildRow(type = "TRANSFER")).type)
    }

    @Test
    fun `C - toDomain converts timestamp Long to Instant`() {
        val domain = StockMapper.toDomain(buildRow(timestamp = 1_700_000_000_000L))
        assertEquals(Instant.fromEpochMilliseconds(1_700_000_000_000L), domain.timestamp)
    }

    @Test
    fun `D - toDomain converts sync_status to SyncStatus State`() {
        assertEquals(SyncStatus.State.SYNCED, StockMapper.toDomain(buildRow(syncStatus = "SYNCED")).syncStatus.state)
        assertEquals(SyncStatus.State.PENDING, StockMapper.toDomain(buildRow(syncStatus = "PENDING")).syncStatus.state)
    }

    // ── toInsertParams ────────────────────────────────────────────────────────

    @Test
    fun `E - toInsertParams maps all fields correctly`() {
        val adj = buildAdjustment(id = "adj-55", productId = "prod-7", quantity = 5.5)
        val params = StockMapper.toInsertParams(adj)
        assertEquals("adj-55", params.id)
        assertEquals("prod-7", params.productId)
        assertEquals(5.5, params.quantity)
        assertEquals("Delivery received", params.reason)
        assertEquals("user-1", params.adjustedBy)
    }

    @Test
    fun `F - toInsertParams serialises type enum to its name string`() {
        assertEquals("INCREASE", StockMapper.toInsertParams(buildAdjustment(type = StockAdjustment.Type.INCREASE)).type)
        assertEquals("DECREASE", StockMapper.toInsertParams(buildAdjustment(type = StockAdjustment.Type.DECREASE)).type)
        assertEquals("TRANSFER", StockMapper.toInsertParams(buildAdjustment(type = StockAdjustment.Type.TRANSFER)).type)
    }

    @Test
    fun `G - toInsertParams converts Instant timestamp to epoch millis`() {
        val ts = Instant.fromEpochMilliseconds(1_700_000_000_000L)
        assertEquals(1_700_000_000_000L, StockMapper.toInsertParams(buildAdjustment(timestamp = ts)).timestamp)
    }

    @Test
    fun `H - toInsertParams sets referenceId to null`() {
        val params = StockMapper.toInsertParams(buildAdjustment())
        assertEquals(null, params.referenceId)
    }

    @Test
    fun `I - toInsertParams uses PENDING as default syncStatus`() {
        assertEquals("PENDING", StockMapper.toInsertParams(buildAdjustment()).syncStatus)
    }

    @Test
    fun `J - toInsertParams uses provided syncStatus`() {
        assertEquals("SYNCED", StockMapper.toInsertParams(buildAdjustment(), syncStatus = "SYNCED").syncStatus)
    }
}
