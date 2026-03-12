package com.zyntasolutions.zyntapos.api.repository

import com.zyntasolutions.zyntapos.api.models.ProductDto
import com.zyntasolutions.zyntapos.api.service.Products
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.SqlExpressionBuilder.greaterEq
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneOffset

/**
 * Exposed-backed implementation of [ProductRepository] (S3-15).
 */
class ProductRepositoryImpl : ProductRepository {

    override suspend fun list(storeId: String, page: Int, size: Int, updatedSince: Long?): ProductPageResult =
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
                        id = row[Products.id],
                        name = row[Products.name],
                        sku = row[Products.sku],
                        barcode = row[Products.barcode],
                        price = row[Products.price].toDouble(),
                        costPrice = row[Products.costPrice].toDouble(),
                        stockQty = row[Products.stockQty].toDouble(),
                        categoryId = row[Products.categoryId],
                        unitId = row[Products.unitId],
                        taxGroupId = row[Products.taxGroupId],
                        minStockQty = row[Products.minStockQty]?.toDouble() ?: 0.0,
                        imageUrl = row[Products.imageUrl],
                        description = row[Products.description],
                        isActive = row[Products.isActive],
                        createdAt = row[Products.createdAt]?.toInstant()?.toEpochMilli() ?: 0L,
                        updatedAt = row[Products.updatedAt].toInstant().toEpochMilli(),
                        syncStatus = "SYNCED",
                    )
                }

            ProductPageResult(items = items, total = total)
        }
}
