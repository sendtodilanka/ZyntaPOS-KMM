package com.zyntasolutions.zyntapos.api.sync

import com.zyntasolutions.zyntapos.api.models.SyncOperation
import com.zyntasolutions.zyntapos.api.service.Products
import kotlinx.serialization.json.*
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
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
 */
class EntityApplier {
    private val logger = LoggerFactory.getLogger(EntityApplier::class.java)
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun applyBatch(storeId: String, ops: List<SyncOperation>) = newSuspendedTransaction {
        for (op in ops) {
            try {
                when (op.entityType) {
                    "PRODUCT" -> applyProduct(storeId, op)
                    // Future entity types can be added here as Phase 2 progresses
                    else -> { /* entity_snapshots trigger handles the rest */ }
                }
            } catch (e: Exception) {
                logger.warn("EntityApplier: failed to apply ${op.entityType} op ${op.id}: ${e.message}")
            }
        }
    }

    private fun applyProduct(storeId: String, op: SyncOperation) {
        val payload = runCatching {
            json.parseToJsonElement(op.payload).jsonObject
        }.getOrNull() ?: return

        when (op.operation) {
            "INSERT", "UPDATE" -> {
                val productName = payload.str("name") ?: return
                Products.upsert(Products.id) {
                    it[Products.id]          = op.entityId
                    it[Products.storeId]     = storeId
                    it[Products.name]        = productName
                    it[Products.sku]         = payload.str("sku")
                    it[Products.barcode]     = payload.str("barcode")
                    it[Products.price]       = (payload.dbl("price")).toBigDecimal()
                    it[Products.costPrice]   = (payload.dbl("costPrice")).toBigDecimal()
                    it[Products.stockQty]    = payload.int("stockQuantity")
                    it[Products.categoryId]  = payload.str("categoryId")
                    it[Products.isActive]    = payload.bool("isActive")
                    it[Products.syncVersion] = op.vectorClock
                    it[Products.updatedAt]   = OffsetDateTime.now(ZoneOffset.UTC)
                }
            }
            "DELETE" -> {
                Products.update({ Products.id eq op.entityId }) {
                    it[Products.isActive]    = false
                    it[Products.syncVersion] = op.vectorClock
                    it[Products.updatedAt]   = OffsetDateTime.now(ZoneOffset.UTC)
                }
            }
        }
    }

    private fun JsonObject.str(key: String): String? =
        this[key]?.let { if (it is JsonNull) null else it.jsonPrimitive.content }

    private fun JsonObject.dbl(key: String): Double =
        this[key]?.jsonPrimitive?.doubleOrNull ?: 0.0

    private fun JsonObject.int(key: String): Int =
        this[key]?.jsonPrimitive?.intOrNull ?: 0

    private fun JsonObject.bool(key: String, default: Boolean = true): Boolean =
        this[key]?.jsonPrimitive?.booleanOrNull ?: default
}
