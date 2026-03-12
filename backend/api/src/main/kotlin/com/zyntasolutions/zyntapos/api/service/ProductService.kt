package com.zyntasolutions.zyntapos.api.service

import com.zyntasolutions.zyntapos.api.models.PagedResponse
import com.zyntasolutions.zyntapos.api.models.ProductDto
import com.zyntasolutions.zyntapos.api.repository.ProductRepository
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.greaterEq
import org.jetbrains.exposed.sql.javatime.timestampWithTimeZone
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import org.slf4j.LoggerFactory
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneOffset

// ── Exposed table (mirrors products table + V9 additional columns) ────────────
// Retained here for backward compatibility — used by ProductRepositoryImpl

object Products : Table("products") {
    val id          = text("id")
    val storeId     = text("store_id")
    val name        = text("name")
    val sku         = text("sku").nullable()
    val barcode     = text("barcode").nullable()
    val price       = decimal("price", 12, 4)
    val costPrice   = decimal("cost_price", 12, 4)
    val stockQty    = decimal("stock_qty", 12, 4)
    val categoryId  = text("category_id").nullable()
    val unitId      = text("unit_id").nullable()
    val taxGroupId  = text("tax_group_id").nullable()
    val minStockQty = decimal("min_stock_qty", 12, 4).nullable()
    val imageUrl    = text("image_url").nullable()
    val description = text("description").nullable()
    val isActive    = bool("is_active")
    val syncVersion = long("sync_version")
    val createdAt   = timestampWithTimeZone("created_at").nullable()
    val updatedAt   = timestampWithTimeZone("updated_at")
    override val primaryKey = PrimaryKey(id)
}

// ── Service ───────────────────────────────────────────────────────────────────

class ProductService(
    private val productRepo: ProductRepository,
) {
    private val logger = LoggerFactory.getLogger(ProductService::class.java)

    suspend fun list(storeId: String, page: Int, size: Int, updatedSince: Long?): PagedResponse<ProductDto> {
        val result = productRepo.list(storeId, page, size, updatedSince)
        logger.info("Products list: storeId=$storeId page=$page size=$size updatedSince=$updatedSince total=${result.total}")
        return PagedResponse(
            data    = result.items,
            page    = page,
            size    = size,
            total   = result.total,
            hasMore = (page.toLong() * size + size) < result.total
        )
    }
}
