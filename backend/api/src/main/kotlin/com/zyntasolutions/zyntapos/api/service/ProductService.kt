package com.zyntasolutions.zyntapos.api.service

import com.zyntasolutions.zyntapos.api.models.PagedResponse
import com.zyntasolutions.zyntapos.api.models.ProductDto
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.javatime.timestampWithTimeZone
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import org.slf4j.LoggerFactory
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneOffset

// ── Exposed table (mirrors V1__initial_schema.sql products table) ─────────────

object Products : Table("products") {
    val id          = text("id")
    val storeId     = text("store_id")
    val name        = text("name")
    val sku         = text("sku").nullable()
    val barcode     = text("barcode").nullable()
    val price       = decimal("price", 12, 4)
    val costPrice   = decimal("cost_price", 12, 4)
    val stockQty    = integer("stock_qty")
    val categoryId  = text("category_id").nullable()
    val isActive    = bool("is_active")
    val syncVersion = long("sync_version")
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
                query = query.adjustWhere { Products.updatedAt greater sinceTs }
            }

            val total = query.count()
            val items = query
                .orderBy(Products.updatedAt, SortOrder.ASC)
                .limit(size)
                .offset((page * size).toLong())
                .map { row ->
                    ProductDto(
                        id            = row[Products.id],
                        name          = row[Products.name],
                        sku           = row[Products.sku],
                        barcode       = row[Products.barcode],
                        price         = row[Products.price].toDouble(),
                        costPrice     = row[Products.costPrice].toDouble(),
                        stockQuantity = row[Products.stockQty],
                        categoryId    = row[Products.categoryId],
                        updatedAt     = row[Products.updatedAt].toInstant().toEpochMilli(),
                        isActive      = row[Products.isActive],
                        syncVersion   = row[Products.syncVersion]
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
