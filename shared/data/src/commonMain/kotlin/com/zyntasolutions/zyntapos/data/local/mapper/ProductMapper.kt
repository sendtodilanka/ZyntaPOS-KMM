package com.zyntasolutions.zyntapos.data.local.mapper

import com.zyntasolutions.zyntapos.db.Products
import com.zyntasolutions.zyntapos.domain.model.Product
import com.zyntasolutions.zyntapos.domain.model.SyncStatus
import kotlinx.datetime.Instant

/**
 * Maps between the SQLDelight-generated [Products] entity and the domain [Product] model.
 *
 * Epoch-millis (INTEGER) ↔ [Instant] conversions use [Instant.fromEpochMilliseconds].
 * The `sync_status` TEXT column is mapped to [SyncStatus.State] by name.
 */
object ProductMapper {

    /** Converts a SQLDelight [Products] row to a domain [Product]. */
    fun toDomain(row: Products): Product = Product(
        id           = row.id,
        name         = row.name,
        barcode      = row.barcode,
        sku          = row.sku,
        categoryId   = row.category_id ?: "",
        unitId       = row.unit_id,
        price        = row.price,
        costPrice    = row.cost_price,
        taxGroupId   = row.tax_group_id,
        stockQty     = row.stock_qty,
        minStockQty  = row.min_stock_qty,
        imageUrl     = row.image_url,
        description  = row.description,
        isActive     = row.is_active == 1L,
        createdAt    = Instant.fromEpochMilliseconds(row.created_at),
        updatedAt    = Instant.fromEpochMilliseconds(row.updated_at),
    )

    /** Returns all parameters needed for `insertProduct` / `updateProduct` in order. */
    fun toInsertParams(p: Product, syncStatus: String = "PENDING") = InsertParams(
        id          = p.id,
        name        = p.name,
        barcode     = p.barcode,
        sku         = p.sku,
        categoryId  = p.categoryId.ifBlank { null },
        unitId      = p.unitId,
        price       = p.price,
        costPrice   = p.costPrice,
        taxGroupId  = p.taxGroupId,
        stockQty    = p.stockQty,
        minStockQty = p.minStockQty,
        imageUrl    = p.imageUrl,
        description = p.description,
        isActive    = if (p.isActive) 1L else 0L,
        createdAt   = p.createdAt.toEpochMilliseconds(),
        updatedAt   = p.updatedAt.toEpochMilliseconds(),
        syncStatus  = syncStatus,
    )

    data class InsertParams(
        val id: String,
        val name: String,
        val barcode: String?,
        val sku: String?,
        val categoryId: String?,
        val unitId: String,
        val price: Double,
        val costPrice: Double,
        val taxGroupId: String?,
        val stockQty: Double,
        val minStockQty: Double,
        val imageUrl: String?,
        val description: String?,
        val isActive: Long,
        val createdAt: Long,
        val updatedAt: Long,
        val syncStatus: String,
    )
}
