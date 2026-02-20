package com.zyntasolutions.zyntapos.data.local.mapper

import com.zyntasolutions.zyntapos.db.Stock_adjustments
import com.zyntasolutions.zyntapos.domain.model.StockAdjustment
import com.zyntasolutions.zyntapos.domain.model.SyncStatus
import kotlinx.datetime.Instant

/**
 * Maps between the SQLDelight-generated [Stock_adjustments] entity and the
 * domain [StockAdjustment] model.
 */
object StockMapper {

    fun toDomain(row: Stock_adjustments): StockAdjustment = StockAdjustment(
        id          = row.id,
        productId   = row.product_id,
        type        = StockAdjustment.Type.valueOf(row.type),
        quantity    = row.quantity,
        reason      = row.reason,
        adjustedBy  = row.adjusted_by,
        timestamp   = Instant.fromEpochMilliseconds(row.timestamp_),
        syncStatus  = SyncStatus(
            state = SyncStatus.State.valueOf(row.sync_status.uppercase()),
        ),
    )

    fun toInsertParams(a: StockAdjustment, syncStatus: String = "PENDING") = InsertParams(
        id          = a.id,
        productId   = a.productId,
        type        = a.type.name,
        quantity    = a.quantity,
        reason      = a.reason,
        adjustedBy  = a.adjustedBy,
        referenceId = null,
        timestamp   = a.timestamp.toEpochMilliseconds(),
        syncStatus  = syncStatus,
    )

    data class InsertParams(
        val id: String,
        val productId: String,
        val type: String,
        val quantity: Double,
        val reason: String,
        val adjustedBy: String,
        val referenceId: String?,
        val timestamp: Long,
        val syncStatus: String,
    )
}
