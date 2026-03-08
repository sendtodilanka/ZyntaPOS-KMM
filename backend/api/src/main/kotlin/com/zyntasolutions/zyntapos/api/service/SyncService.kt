package com.zyntasolutions.zyntapos.api.service

import com.zyntasolutions.zyntapos.api.models.*
import kotlinx.serialization.json.*
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import org.slf4j.LoggerFactory
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneOffset

class SyncService {
    private val logger = LoggerFactory.getLogger(SyncService::class.java)
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun push(storeId: String, request: PushRequest): PushResponse = newSuspendedTransaction {
        val now = OffsetDateTime.now(ZoneOffset.UTC)
        val serverClock = System.currentTimeMillis()
        var accepted = 0
        var rejected = 0

        for (op in request.operations) {
            try {
                // Upsert into sync_queue — idempotent if client retries (server_ts updated)
                SyncQueue.upsert(SyncQueue.id) {
                    it[SyncQueue.id]          = op.id
                    it[SyncQueue.storeId]     = storeId
                    it[SyncQueue.deviceId]    = request.deviceId
                    it[SyncQueue.entityType]  = op.entityType
                    it[SyncQueue.entityId]    = op.entityId
                    it[SyncQueue.operation]   = op.operation
                    it[SyncQueue.payload]     = op.payload
                    it[SyncQueue.vectorClock] = op.vectorClock
                    it[SyncQueue.clientTs]    = OffsetDateTime.ofInstant(
                        Instant.ofEpochMilli(op.clientTimestamp), ZoneOffset.UTC
                    )
                    it[SyncQueue.serverTs]    = now
                    it[SyncQueue.isProcessed] = false
                }

                // Phase 1 LWW: apply PRODUCT operations to the server-side products mirror
                if (op.entityType == "PRODUCT") {
                    applyProductOperation(storeId, op)
                }

                accepted++
            } catch (e: Exception) {
                logger.warn("Failed to store sync op ${op.id}: ${e.message}")
                rejected++
            }
        }

        logger.info("Sync push: storeId=$storeId device=${request.deviceId} accepted=$accepted rejected=$rejected")
        PushResponse(
            accepted          = accepted,
            rejected          = rejected,
            conflicts         = emptyList(), // LWW — no explicit conflicts in Phase 1
            serverVectorClock = serverClock
        )
    }

    suspend fun pull(storeId: String, since: Long, limit: Int): PullResponse = newSuspendedTransaction {
        // Fetch one extra to detect hasMore without a separate COUNT query
        val rows = SyncQueue.selectAll()
            .where {
                (SyncQueue.storeId eq storeId) and
                (SyncQueue.vectorClock greater since)
            }
            .orderBy(SyncQueue.vectorClock, SortOrder.ASC)
            .limit(limit + 1)
            .toList()

        val hasMore = rows.size > limit
        val page = rows.take(limit)

        val operations = page.map { row ->
            SyncOperation(
                id              = row[SyncQueue.id],
                entityType      = row[SyncQueue.entityType],
                entityId        = row[SyncQueue.entityId],
                operation       = row[SyncQueue.operation],
                payload         = row[SyncQueue.payload],
                vectorClock     = row[SyncQueue.vectorClock],
                clientTimestamp = row[SyncQueue.clientTs].toInstant().toEpochMilli()
            )
        }

        // Return the highest vectorClock seen so client can use it as the next `since`
        val serverClock = if (page.isNotEmpty())
            page.last()[SyncQueue.vectorClock]
        else
            System.currentTimeMillis()

        logger.info("Sync pull: storeId=$storeId since=$since returned=${operations.size} hasMore=$hasMore")
        PullResponse(
            operations        = operations,
            serverVectorClock = serverClock,
            hasMore           = hasMore
        )
    }

    // ── Phase 1: apply PRODUCT operations to the server-side products mirror ──

    private fun applyProductOperation(storeId: String, op: SyncOperation) {
        try {
            val payload = json.parseToJsonElement(op.payload).jsonObject
            when (op.operation) {
                "INSERT", "UPDATE" -> {
                    val productName = payload.str("name") ?: return
                    Products.upsert(Products.id) {
                        it[Products.id]          = op.entityId
                        it[Products.storeId]     = storeId
                        it[Products.name]        = productName
                        it[Products.sku]         = payload.str("sku")
                        it[Products.barcode]     = payload.str("barcode")
                        it[Products.price]       = payload.dbl("price").toBigDecimal()
                        it[Products.costPrice]   = payload.dbl("costPrice").toBigDecimal()
                        it[Products.stockQty]    = payload.int("stockQuantity")
                        it[Products.categoryId]  = payload.str("categoryId")
                        it[Products.isActive]    = payload.bool("isActive")
                        it[Products.syncVersion] = op.vectorClock
                        it[Products.updatedAt]   = OffsetDateTime.now(ZoneOffset.UTC)
                    }
                }
                "DELETE" -> Products.update({ Products.id eq op.entityId }) {
                    it[Products.isActive]    = false
                    it[Products.syncVersion] = op.vectorClock
                    it[Products.updatedAt]   = OffsetDateTime.now(ZoneOffset.UTC)
                }
            }
        } catch (e: Exception) {
            logger.warn("Failed to apply PRODUCT op ${op.id} to products table: ${e.message}")
        }
    }

    // ── JSON helpers ──────────────────────────────────────────────────────────

    private fun JsonObject.str(key: String): String? =
        this[key]?.let { if (it is JsonNull) null else it.jsonPrimitive.content }

    private fun JsonObject.dbl(key: String): Double =
        this[key]?.jsonPrimitive?.doubleOrNull ?: 0.0

    private fun JsonObject.int(key: String): Int =
        this[key]?.jsonPrimitive?.intOrNull ?: 0

    private fun JsonObject.bool(key: String, default: Boolean = true): Boolean =
        this[key]?.jsonPrimitive?.booleanOrNull ?: default
}
