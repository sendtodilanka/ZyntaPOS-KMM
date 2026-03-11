package com.zyntasolutions.zyntapos.api.service

import com.zyntasolutions.zyntapos.api.models.PagedResponse
import com.zyntasolutions.zyntapos.api.models.ProductDto
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.greaterEq
import org.jetbrains.exposed.sql.javatime.timestampWithTimeZone
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import org.slf4j.LoggerFactory
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneOffset

// ── Exposed table (mirrors products table + V9 additional columns) ────────────

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

class ProductService {
    private val logger = LoggerFactory.getLogger(ProductService::class.java)

    suspend fun list(storeId: String, page: Int, size: Int, updatedSince: Long?): PagedResponse<ProductDto> =
        newSuspendedTransaction {
            var query = Products.selectAll().where { Products.storeId eq storeId }

            if (updatedSince != null) {
                val sinceTs = OffsetDateTime.ofInstant(Instant.ofEpochMilli(updatedSince), ZoneOffset.UTC)
                query = query.adjustWhere { Products.updatedAt greaterEq sinceTs }
            }

            val total = query.count()
            val items = query
                .orderBy(Products.updatedAt, SortOrder.ASC)
                .limit(size)
                .offset((page * size).toLong())
                .map { row ->
                    ProductDto(
                        id          = row[Products.id],
                        name        = row[Products.name],
                        sku         = row[Products.sku],
                        barcode     = row[Products.barcode],
                        price       = row[Products.price].toDouble(),
                        costPrice   = row[Products.costPrice].toDouble(),
                        stockQty    = row[Products.stockQty].toDouble(),
                        categoryId  = row[Products.categoryId],
                        unitId      = row[Products.unitId],
                        taxGroupId  = row[Products.taxGroupId],
                        minStockQty = row[Products.minStockQty]?.toDouble() ?: 0.0,
                        imageUrl    = row[Products.imageUrl],
                        description = row[Products.description],
                        isActive    = row[Products.isActive],
                        createdAt   = row[Products.createdAt]?.toInstant()?.toEpochMilli() ?: 0L,
                        updatedAt   = row[Products.updatedAt].toInstant().toEpochMilli(),
                        syncStatus  = "SYNCED",
                    )
                }

            logger.info("Products list: storeId=$storeId page=$page size=$size updatedSince=$updatedSince total=$total")
            PagedResponse(
                data    = items,
                page    = page,
                size    = size,
                total   = total,
                hasMore = (page.toLong() * size + size) < total
            )
        }
}
