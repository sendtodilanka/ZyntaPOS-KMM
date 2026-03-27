package com.zyntasolutions.zyntapos.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Sync payload DTO for a `STOCK_ADJUSTMENT` delta received from the server.
 *
 * The server sends one of these per adjustment record when a client pulls deltas.
 * [StockRepositoryImpl.upsertFromSync] decodes this to persist the server-authoritative
 * adjustment log row. Stock quantity reconciliation is handled separately by
 * [StockRepositoryImpl.recomputeStockQty] (G-Counter pattern).
 */
@Serializable
internal data class StockAdjustmentSyncPayload(
    @SerialName("id")           val id: String,
    @SerialName("product_id")   val productId: String,
    @SerialName("type")         val type: String,
    @SerialName("quantity")     val quantity: Double,
    @SerialName("reason")       val reason: String? = null,
    @SerialName("adjusted_by")  val adjustedBy: String? = null,
    @SerialName("reference_id") val referenceId: String? = null,
    @SerialName("timestamp")    val timestamp: Long,
)
