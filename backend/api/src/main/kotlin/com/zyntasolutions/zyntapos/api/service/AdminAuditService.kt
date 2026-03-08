package com.zyntasolutions.zyntapos.api.service

import com.zyntasolutions.zyntapos.api.models.AdminAuditEntry
import com.zyntasolutions.zyntapos.api.models.AdminPagedResponse
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.encodeToJsonElement
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.javatime.timestampWithTimeZone
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import java.security.MessageDigest
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID
import kotlin.math.ceil

// ── Exposed table ────────────────────────────────────────────────────────────

object AdminAuditLog : Table("admin_audit_log") {
    val id             = uuid("id")
    val eventType      = text("event_type")
    val category       = text("category")
    val adminId        = uuid("admin_id").nullable()
    val adminName      = text("admin_name").nullable()
    val storeId        = text("store_id").nullable()
    val storeName      = text("store_name").nullable()
    val entityType     = text("entity_type").nullable()
    val entityId       = text("entity_id").nullable()
    val previousValues = text("previous_values").nullable()  // JSONB stored as text
    val newValues      = text("new_values").nullable()
    val ipAddress      = text("ip_address").nullable()
    val userAgent      = text("user_agent").nullable()
    val success        = bool("success")
    val errorMessage   = text("error_message").nullable()
    val hashChain      = text("hash_chain")
    val createdAt      = timestampWithTimeZone("created_at")
    override val primaryKey = PrimaryKey(id)
}

// ── Service ──────────────────────────────────────────────────────────────────

class AdminAuditService {

    /** Write a new audit entry — call after every admin mutation. */
    suspend fun log(
        adminId: UUID?,
        adminName: String?,
        eventType: String,
        category: String,
        entityType: String? = null,
        entityId: String? = null,
        previousValues: Map<String, String>? = null,
        newValues: Map<String, String>? = null,
        ipAddress: String? = null,
        userAgent: String? = null,
        success: Boolean = true,
        errorMessage: String? = null
    ) = newSuspendedTransaction {
        val now = OffsetDateTime.now(ZoneOffset.UTC)
        val newId = UUID.randomUUID()

        // Compute hash chain: SHA-256(previousHash + eventType + entityId + timestamp)
        val lastHash = AdminAuditLog.selectAll()
            .orderBy(AdminAuditLog.createdAt, SortOrder.DESC)
            .limit(1)
            .singleOrNull()?.get(AdminAuditLog.hashChain) ?: ""
        val chainInput = "$lastHash|$eventType|${entityId ?: ""}|${now.toInstant().toEpochMilli()}"
        val hashChain = sha256Hex(chainInput)

        AdminAuditLog.insert {
            it[id]                   = newId
            it[this.eventType]       = eventType
            it[this.category]        = category
            it[this.adminId]         = adminId
            it[this.adminName]       = adminName
            it[this.entityType]      = entityType
            it[this.entityId]        = entityId
            it[this.previousValues]  = previousValues?.let { m ->
                "{${m.entries.joinToString(",") { (k, v) -> "\"$k\":\"$v\"" }}}" }
            it[this.newValues]       = newValues?.let { m ->
                "{${m.entries.joinToString(",") { (k, v) -> "\"$k\":\"$v\"" }}}" }
            it[this.ipAddress]       = ipAddress
            it[this.userAgent]       = userAgent
            it[this.success]         = success
            it[this.errorMessage]    = errorMessage
            it[this.hashChain]       = hashChain
            it[createdAt]            = now
        }
    }

    suspend fun listEntries(
        page: Int,
        size: Int,
        category: String?,
        eventType: String?,
        adminId: String?,
        from: String?,
        to: String?,
        search: String?
    ): AdminPagedResponse<AdminAuditEntry> = newSuspendedTransaction {
        var query = AdminAuditLog.selectAll()

        if (!category.isNullOrBlank()) {
            query = query.adjustWhere { AdminAuditLog.category eq category.uppercase() }
        }
        if (!eventType.isNullOrBlank()) {
            query = query.adjustWhere { AdminAuditLog.eventType eq eventType }
        }
        if (!adminId.isNullOrBlank()) {
            runCatching { UUID.fromString(adminId) }.getOrNull()?.let { uid ->
                query = query.adjustWhere { AdminAuditLog.adminId eq uid }
            }
        }
        if (!from.isNullOrBlank()) {
            val fromTs = runCatching { OffsetDateTime.parse(from) }.getOrNull()
            if (fromTs != null) query = query.adjustWhere { AdminAuditLog.createdAt greaterEq fromTs }
        }
        if (!to.isNullOrBlank()) {
            val toTs = runCatching { OffsetDateTime.parse(to) }.getOrNull()
            if (toTs != null) query = query.adjustWhere { AdminAuditLog.createdAt lessEq toTs }
        }
        if (!search.isNullOrBlank()) {
            val term = "%${search.lowercase()}%"
            query = query.adjustWhere {
                (AdminAuditLog.eventType.lowerCase() like term) or
                (AdminAuditLog.entityId.lowerCase() like term) or
                (AdminAuditLog.adminName.lowerCase() like term)
            }
        }

        val total = query.count()
        val items = query
            .orderBy(AdminAuditLog.createdAt, SortOrder.DESC)
            .limit(size, offset = (page * size).toLong())
            .map { it.toEntry() }

        AdminPagedResponse(
            data = items,
            page = page,
            size = size,
            total = total.toInt(),
            totalPages = ceil(total.toDouble() / size).toInt()
        )
    }

    suspend fun exportEntries(
        category: String?,
        eventType: String?,
        from: String?,
        to: String?,
        limit: Int = 10_000
    ): List<AdminAuditEntry> = newSuspendedTransaction {
        var query = AdminAuditLog.selectAll()
        if (!category.isNullOrBlank()) query = query.adjustWhere { AdminAuditLog.category eq category.uppercase() }
        if (!eventType.isNullOrBlank()) query = query.adjustWhere { AdminAuditLog.eventType eq eventType }
        if (!from.isNullOrBlank()) {
            runCatching { OffsetDateTime.parse(from) }.getOrNull()?.let { ts ->
                query = query.adjustWhere { AdminAuditLog.createdAt greaterEq ts }
            }
        }
        if (!to.isNullOrBlank()) {
            runCatching { OffsetDateTime.parse(to) }.getOrNull()?.let { ts ->
                query = query.adjustWhere { AdminAuditLog.createdAt lessEq ts }
            }
        }
        query.orderBy(AdminAuditLog.createdAt, SortOrder.DESC).limit(limit).map { it.toEntry() }
    }

    private fun ResultRow.toEntry(): AdminAuditEntry {
        val pvJson = this[AdminAuditLog.previousValues]?.let {
            runCatching { Json.parseToJsonElement(it) }.getOrNull()
        }
        val nvJson = this[AdminAuditLog.newValues]?.let {
            runCatching { Json.parseToJsonElement(it) }.getOrNull()
        }
        return AdminAuditEntry(
            id             = this[AdminAuditLog.id].toString(),
            eventType      = this[AdminAuditLog.eventType],
            category       = this[AdminAuditLog.category],
            userId         = this[AdminAuditLog.adminId]?.toString(),
            userName       = this[AdminAuditLog.adminName],
            storeId        = this[AdminAuditLog.storeId],
            storeName      = this[AdminAuditLog.storeName],
            entityType     = this[AdminAuditLog.entityType],
            entityId       = this[AdminAuditLog.entityId],
            previousValues = pvJson,
            newValues      = nvJson,
            ipAddress      = this[AdminAuditLog.ipAddress],
            userAgent      = this[AdminAuditLog.userAgent],
            success        = this[AdminAuditLog.success],
            errorMessage   = this[AdminAuditLog.errorMessage],
            hashChain      = this[AdminAuditLog.hashChain],
            createdAt      = this[AdminAuditLog.createdAt].toInstant().toString()
        )
    }

    private fun sha256Hex(input: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        return digest.digest(input.toByteArray()).joinToString("") { "%02x".format(it) }
    }
}
