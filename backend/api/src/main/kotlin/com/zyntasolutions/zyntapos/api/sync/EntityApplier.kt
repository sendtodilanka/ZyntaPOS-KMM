package com.zyntasolutions.zyntapos.api.sync

import com.zyntasolutions.zyntapos.api.models.SyncOperation
import com.zyntasolutions.zyntapos.api.service.Products
import kotlinx.serialization.json.*
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.slf4j.LoggerFactory
import java.time.OffsetDateTime
import java.time.ZoneOffset

/**
 * Applies accepted sync operations to the normalized entity tables
 * (products, etc.) so that server-side queries see up-to-date data.
 *
 * Entity snapshots are maintained by the PostgreSQL trigger defined in V4
 * migration (trg_sync_op_snapshot), so this class only needs to handle the
 * normalized tables that the API uses for direct queries.
 *
 * [applyInTransaction] is called from within an existing [newSuspendedTransaction]
 * so that the insert + apply are atomic — a failure rolls back both.
 */
class EntityApplier {
    private val logger = LoggerFactory.getLogger(EntityApplier::class.java)
    private val json = Json { ignoreUnknownKeys = true }

    /**
     * Apply a single operation inside an already-open transaction.
     * Must be called within a [newSuspendedTransaction] block.
     * Re-throws on failure so the surrounding transaction is rolled back.
     */
    fun applyInTransaction(storeId: String, op: SyncOperation) {
        try {
            when (op.entityType) {
                "PRODUCT" -> applyProduct(storeId, op)
                else -> { /* entity_snapshots trigger handles the rest */ }
            }
        } catch (e: Exception) {
            logger.warn("EntityApplier: failed to apply ${op.entityType} op ${op.id}: ${e.message}")
            throw e  // re-throw to roll back the surrounding transaction
        }
    }

    private fun applyProduct(storeId: String, op: SyncOperation) {
        val payload = runCatching {
            json.parseToJsonElement(op.payload).jsonObject
        }.getOrNull() ?: return

        when (op.operation) {
            "INSERT", "CREATE", "UPDATE" -> {
                val productName = payload.str("name") ?: return
                Products.upsert(Products.id) {
                    it[Products.id]          = op.entityId
                    it[Products.storeId]     = storeId
                    it[Products.name]        = productName
                    it[Products.sku]         = payload.str("sku")
                    it[Products.barcode]     = payload.str("barcode")
                    it[Products.price]       = (payload.dbl("price")).toBigDecimal()
                    it[Products.costPrice]   = (payload.dbl("cost_price")).toBigDecimal()
                    it[Products.stockQty]    = payload.dbl("stock_qty").toBigDecimal()
                    it[Products.categoryId]  = payload.str("category_id")
                    it[Products.unitId]      = payload.str("unit_id")
                    it[Products.taxGroupId]  = payload.str("tax_group_id")
                    it[Products.minStockQty] = payload.dbl("min_stock_qty").toBigDecimal()
                    it[Products.imageUrl]    = payload.str("image_url")
                    it[Products.description] = payload.str("description")
                    it[Products.isActive]    = payload.bool("is_active")
                    it[Products.syncVersion] = op.createdAt
                    it[Products.updatedAt]   = OffsetDateTime.now(ZoneOffset.UTC)
                }
            }
            "DELETE" -> {
                Products.update({ Products.id eq op.entityId }) {
                    it[Products.isActive]    = false
                    it[Products.syncVersion] = op.createdAt
                    it[Products.updatedAt]   = OffsetDateTime.now(ZoneOffset.UTC)
                }
            }
        }
    }

    private fun JsonObject.str(key: String): String? =
        this[key]?.let { if (it is JsonNull) null else it.jsonPrimitive.content }

    private fun JsonObject.dbl(key: String): Double =
        this[key]?.jsonPrimitive?.doubleOrNull ?: 0.0

    private fun JsonObject.bool(key: String, default: Boolean = true): Boolean =
        this[key]?.jsonPrimitive?.booleanOrNull ?: default
}
