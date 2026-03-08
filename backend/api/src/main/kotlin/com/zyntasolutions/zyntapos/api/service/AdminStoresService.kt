package com.zyntasolutions.zyntapos.api.service

import com.zyntasolutions.zyntapos.api.models.*
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.javatime.timestampWithTimeZone
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import java.time.OffsetDateTime
import java.time.ZoneOffset
import kotlin.math.ceil

// ── Exposed tables (reuse V1 schema) ─────────────────────────────────────────

object Stores : Table("stores") {
    val id             = text("id")
    val name           = text("name")
    val licenseKey     = text("license_key")
    val timezone       = text("timezone")
    val currency       = text("currency")
    val isActive       = bool("is_active")
    val createdAt      = timestampWithTimeZone("created_at")
    val updatedAt      = timestampWithTimeZone("updated_at")
    override val primaryKey = PrimaryKey(id)
}

object SyncQueue : Table("sync_queue") {
    val id          = text("id")
    val storeId     = text("store_id")
    val deviceId    = text("device_id")
    val entityType  = text("entity_type")
    val entityId    = text("entity_id")
    val operation   = text("operation")
    val payload     = text("payload")   // JSONB
    val vectorClock = long("vector_clock")
    val clientTs    = timestampWithTimeZone("client_ts")
    val serverTs    = timestampWithTimeZone("server_ts")
    val isProcessed = bool("is_processed")
    override val primaryKey = PrimaryKey(id)
}

// ── Service ──────────────────────────────────────────────────────────────────

class AdminStoresService {

    suspend fun listStores(
        page: Int,
        size: Int,
        search: String?,
        status: String?
    ): AdminPagedResponse<AdminStore> = newSuspendedTransaction {
        var query = Stores.selectAll()

        if (!search.isNullOrBlank()) {
            val term = "%${search.lowercase()}%"
            query = query.adjustWhere {
                (Stores.name.lowerCase() like term) or
                (Stores.id.lowerCase() like term)
            }
        }
        // status filter: compute from isActive + last heartbeat (simplified: use isActive)
        if (status == "OFFLINE") {
            query = query.adjustWhere { Stores.isActive eq false }
        } else if (!status.isNullOrBlank() && status != "OFFLINE") {
            query = query.adjustWhere { Stores.isActive eq true }
        }

        val total = query.count()
        val items = query
            .orderBy(Stores.createdAt, SortOrder.DESC)
            .limit(size, offset = (page * size).toLong())
            .map { it.toAdminStore() }

        AdminPagedResponse(
            data = items,
            page = page,
            size = size,
            total = total.toInt(),
            totalPages = ceil(total.toDouble() / size).toInt()
        )
    }

    suspend fun getStore(storeId: String): AdminStore? = newSuspendedTransaction {
        Stores.selectAll().where { Stores.id eq storeId }.singleOrNull()?.toAdminStore()
    }

    suspend fun getStoreHealth(storeId: String): AdminStoreHealth? = newSuspendedTransaction {
        val store = Stores.selectAll().where { Stores.id eq storeId }.singleOrNull()
            ?: return@newSuspendedTransaction null

        val pendingOps = SyncQueue.selectAll().where {
            (SyncQueue.storeId eq storeId) and (SyncQueue.isProcessed eq false)
        }.count().toInt()

        val status = when {
            !store[Stores.isActive] -> "OFFLINE"
            pendingOps > 50 -> "WARNING"
            else -> "HEALTHY"
        }

        AdminStoreHealth(
            storeId = storeId,
            status = status,
            healthScore = if (status == "HEALTHY") 100 else if (status == "WARNING") 60 else 0,
            dbSizeBytes = 0L,
            syncQueueDepth = pendingOps,
            errorCount24h = 0,
            uptimeHours = 0.0,
            lastHeartbeatAt = null,
            responseTimeMs = 0L,
            appVersion = "",
            osInfo = "Android"
        )
    }

    suspend fun updateStoreConfig(storeId: String, req: AdminUpdateStoreConfigRequest): AdminStore? =
        newSuspendedTransaction {
            val existing = Stores.selectAll().where { Stores.id eq storeId }.singleOrNull()
                ?: return@newSuspendedTransaction null

            val now = OffsetDateTime.now(ZoneOffset.UTC)
            Stores.update({ Stores.id eq storeId }) { stmt ->
                req.timezone?.let { stmt[timezone] = it }
                req.currency?.let { stmt[currency] = it }
                stmt[updatedAt] = now
            }

            Stores.selectAll().where { Stores.id eq storeId }.single().toAdminStore()
        }

    suspend fun getAllStoreHealthSummaries(): List<StoreHealthSummary> = newSuspendedTransaction {
        Stores.selectAll().map { row ->
            val storeId = row[Stores.id]
            val pendingOps = SyncQueue.selectAll().where {
                (SyncQueue.storeId eq storeId) and (SyncQueue.isProcessed eq false)
            }.count().toInt()

            val status = when {
                !row[Stores.isActive] -> "unknown"
                pendingOps > 50 -> "degraded"
                else -> "healthy"
            }

            StoreHealthSummary(
                storeId = storeId,
                storeName = row[Stores.name],
                status = status,
                lastSync = null,
                pendingOperations = pendingOps,
                appVersion = "",
                androidVersion = "",
                uptimePercent = if (row[Stores.isActive]) 99.0 else 0.0
            )
        }
    }

    suspend fun getStoreHealthDetail(storeId: String): StoreHealthDetail? = newSuspendedTransaction {
        val store = Stores.selectAll().where { Stores.id eq storeId }.singleOrNull()
            ?: return@newSuspendedTransaction null

        val pendingOps = SyncQueue.selectAll().where {
            (SyncQueue.storeId eq storeId) and (SyncQueue.isProcessed eq false)
        }.count().toInt()

        val status = when {
            !store[Stores.isActive] -> "unknown"
            pendingOps > 50 -> "degraded"
            else -> "healthy"
        }

        StoreHealthDetail(
            storeId = storeId,
            storeName = store[Stores.name],
            status = status,
            lastSync = null,
            pendingOperations = pendingOps,
            appVersion = "",
            androidVersion = "",
            uptimePercent = if (store[Stores.isActive]) 99.0 else 0.0
        )
    }

    private fun ResultRow.toAdminStore() = AdminStore(
        id             = this[Stores.id],
        name           = this[Stores.name],
        location       = this[Stores.timezone],
        licenseKey     = this[Stores.licenseKey],
        edition        = "STANDARD",
        status         = if (this[Stores.isActive]) "HEALTHY" else "OFFLINE",
        activeUsers    = 0,
        lastSyncAt     = null,
        lastHeartbeatAt = null,
        appVersion     = "",
        createdAt      = this[Stores.createdAt].toInstant().toString()
    )
}
