package com.zyntasolutions.zyntapos.api.repository

import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID

data class ConflictLogEntry(
    val storeId: String,
    val entityType: String,
    val entityId: String,
    val localOpId: String,
    val serverOpId: String,
    val localDeviceId: String,
    val serverDeviceId: String,
    val localTimestamp: Long,
    val serverTs: Long,
    val resolution: String,
    val localPayload: String?,
    val serverPayload: String?,
    val mergedPayload: String?,
)

class ConflictLogRepository {

    suspend fun insert(entry: ConflictLogEntry): String = newSuspendedTransaction {
        val id = UUID.randomUUID().toString()
        SyncConflictLog.insert {
            it[SyncConflictLog.id]             = id
            it[SyncConflictLog.storeId]        = entry.storeId
            it[SyncConflictLog.entityType]     = entry.entityType
            it[SyncConflictLog.entityId]       = entry.entityId
            it[SyncConflictLog.localOpId]      = entry.localOpId
            it[SyncConflictLog.serverOpId]     = entry.serverOpId
            it[SyncConflictLog.localDeviceId]  = entry.localDeviceId
            it[SyncConflictLog.serverDeviceId] = entry.serverDeviceId
            it[SyncConflictLog.localTimestamp] = entry.localTimestamp
            it[SyncConflictLog.serverTs]       = entry.serverTs
            it[SyncConflictLog.resolution]     = entry.resolution
            it[SyncConflictLog.localPayload]   = entry.localPayload
            it[SyncConflictLog.serverPayload]  = entry.serverPayload
            it[SyncConflictLog.mergedPayload]  = entry.mergedPayload
            it[SyncConflictLog.createdAt]      = OffsetDateTime.now(ZoneOffset.UTC)
        }
        id
    }

    suspend fun findRecent(storeId: String, limit: Int = 50): List<Map<String, Any?>> =
        newSuspendedTransaction {
            SyncConflictLog.selectAll()
                .where { SyncConflictLog.storeId eq storeId }
                .orderBy(SyncConflictLog.createdAt, SortOrder.DESC)
                .limit(limit)
                .map { row ->
                    mapOf(
                        "id"             to row[SyncConflictLog.id],
                        "entityType"     to row[SyncConflictLog.entityType],
                        "entityId"       to row[SyncConflictLog.entityId],
                        "localDeviceId"  to row[SyncConflictLog.localDeviceId],
                        "serverDeviceId" to row[SyncConflictLog.serverDeviceId],
                        "resolution"     to row[SyncConflictLog.resolution],
                        "createdAt"      to row[SyncConflictLog.createdAt].toInstant().toString(),
                    )
                }
        }

    suspend fun findAll(limit: Int = 200): List<Map<String, Any?>> = newSuspendedTransaction {
        SyncConflictLog.selectAll()
            .orderBy(SyncConflictLog.createdAt, SortOrder.DESC)
            .limit(limit)
            .map { row ->
                mapOf(
                    "id"         to row[SyncConflictLog.id],
                    "storeId"    to row[SyncConflictLog.storeId],
                    "entityType" to row[SyncConflictLog.entityType],
                    "entityId"   to row[SyncConflictLog.entityId],
                    "resolution" to row[SyncConflictLog.resolution],
                    "createdAt"  to row[SyncConflictLog.createdAt].toInstant().toString(),
                )
            }
    }
}
