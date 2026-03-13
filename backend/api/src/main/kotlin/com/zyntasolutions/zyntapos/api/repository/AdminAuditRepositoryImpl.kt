package com.zyntasolutions.zyntapos.api.repository

import com.zyntasolutions.zyntapos.api.db.AdminAuditLog
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.greaterEq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.lessEq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.like
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import java.time.OffsetDateTime
import java.util.UUID
import kotlin.math.ceil

class AdminAuditRepositoryImpl : AdminAuditRepository {

    override suspend fun insertEntry(entry: AuditEntryInput) = newSuspendedTransaction {
        AdminAuditLog.insert {
            it[id]             = entry.id
            it[eventType]      = entry.eventType
            it[category]       = entry.category
            it[adminId]        = entry.adminId
            it[adminName]      = entry.adminName
            it[storeId]        = entry.storeId
            it[storeName]      = entry.storeName
            it[entityType]     = entry.entityType
            it[entityId]       = entry.entityId
            it[previousValues] = entry.previousValues
            it[newValues]      = entry.newValues
            it[ipAddress]      = entry.ipAddress
            it[userAgent]      = entry.userAgent
            it[success]        = entry.success
            it[errorMessage]   = entry.errorMessage
            it[hashChain]      = entry.hashChain
            it[createdAt]      = entry.createdAt
        }
        Unit
    }

    override suspend fun findLatestHash(): String = newSuspendedTransaction {
        AdminAuditLog.selectAll()
            .orderBy(AdminAuditLog.createdAt, SortOrder.DESC)
            .limit(1)
            .singleOrNull()?.get(AdminAuditLog.hashChain) ?: ""
    }

    override suspend fun listEntries(filter: AuditFilter, page: Int, size: Int): AuditPage =
        newSuspendedTransaction {
            var query = AdminAuditLog.selectAll()
            query = query.applyFilter(filter)

            val total = query.count()
            val items = query
                .orderBy(AdminAuditLog.createdAt, SortOrder.DESC)
                .limit(size).offset((page * size).toLong())
                .map { it.toRow() }

            AuditPage(
                data       = items,
                page       = page,
                size       = size,
                total      = total.toInt(),
                totalPages = ceil(total.toDouble() / size).toInt(),
            )
        }

    override suspend fun exportEntries(filter: AuditFilter, limit: Int): List<AuditEntryRow> =
        newSuspendedTransaction {
            AdminAuditLog.selectAll()
                .applyFilter(filter)
                .orderBy(AdminAuditLog.createdAt, SortOrder.DESC)
                .limit(limit)
                .map { it.toRow() }
        }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun Query.applyFilter(f: AuditFilter): Query {
        var q = this
        if (!f.category.isNullOrBlank())
            q = q.adjustWhere { AdminAuditLog.category eq f.category.uppercase() }
        if (!f.eventType.isNullOrBlank())
            q = q.adjustWhere { AdminAuditLog.eventType eq f.eventType }
        if (!f.adminId.isNullOrBlank())
            runCatching { UUID.fromString(f.adminId) }.getOrNull()?.let { uid ->
                q = q.adjustWhere { AdminAuditLog.adminId eq uid }
            }
        if (!f.from.isNullOrBlank())
            runCatching { OffsetDateTime.parse(f.from) }.getOrNull()?.let { ts ->
                q = q.adjustWhere { AdminAuditLog.createdAt greaterEq ts }
            }
        if (!f.to.isNullOrBlank())
            runCatching { OffsetDateTime.parse(f.to) }.getOrNull()?.let { ts ->
                q = q.adjustWhere { AdminAuditLog.createdAt lessEq ts }
            }
        if (!f.search.isNullOrBlank()) {
            val term = "%${f.search.lowercase()}%"
            q = q.adjustWhere {
                (AdminAuditLog.eventType.lowerCase() like term) or
                (AdminAuditLog.entityId.lowerCase() like term) or
                (AdminAuditLog.adminName.lowerCase() like term)
            }
        }
        return q
    }

    private fun ResultRow.toRow() = AuditEntryRow(
        id             = this[AdminAuditLog.id].toString(),
        eventType      = this[AdminAuditLog.eventType],
        category       = this[AdminAuditLog.category],
        userId         = this[AdminAuditLog.adminId]?.toString(),
        userName       = this[AdminAuditLog.adminName],
        storeId        = this[AdminAuditLog.storeId],
        storeName      = this[AdminAuditLog.storeName],
        entityType     = this[AdminAuditLog.entityType],
        entityId       = this[AdminAuditLog.entityId],
        previousValues = this[AdminAuditLog.previousValues],
        newValues      = this[AdminAuditLog.newValues],
        ipAddress      = this[AdminAuditLog.ipAddress],
        userAgent      = this[AdminAuditLog.userAgent],
        success        = this[AdminAuditLog.success],
        errorMessage   = this[AdminAuditLog.errorMessage],
        hashChain      = this[AdminAuditLog.hashChain],
        createdAt      = this[AdminAuditLog.createdAt].toInstant().toString(),
    )
}
