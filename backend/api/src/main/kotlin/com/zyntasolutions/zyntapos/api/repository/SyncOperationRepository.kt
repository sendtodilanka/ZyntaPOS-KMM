package com.zyntasolutions.zyntapos.api.repository

import com.zyntasolutions.zyntapos.api.models.SyncOperation
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.greater
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import java.time.OffsetDateTime
import java.time.ZoneOffset

data class SyncOperationSnapshot(
    val opId: String,
    val deviceId: String,
    val clientTimestamp: Long,
    val payload: String,
    val serverSeq: Long,
    val status: String,
)

open class SyncOperationRepository {

    suspend fun insert(storeId: String, deviceId: String, op: SyncOperation, status: String = "ACCEPTED"): Long =
        newSuspendedTransaction {
            SyncOperations.insert {
                it[SyncOperations.id]              = op.id
                it[SyncOperations.storeId]         = storeId
                it[SyncOperations.deviceId]        = deviceId
                it[SyncOperations.entityType]      = op.entityType
                it[SyncOperations.entityId]        = op.entityId
                it[SyncOperations.operation]       = if (op.operation == "CREATE") "INSERT" else op.operation
                it[SyncOperations.payload]         = op.payload
                it[SyncOperations.clientTimestamp] = op.createdAt
                it[SyncOperations.serverTimestamp] = OffsetDateTime.now(ZoneOffset.UTC)
                it[SyncOperations.vectorClock]     = op.retryCount.toLong()
                it[SyncOperations.status]          = status
            }[SyncOperations.serverSeq]
        }

    suspend fun insertWithConflict(
        storeId: String,
        deviceId: String,
        op: SyncOperation,
        conflictId: String,
        resolvedPayload: String,
    ): Long = newSuspendedTransaction {
        SyncOperations.insert {
            it[SyncOperations.id]              = op.id
            it[SyncOperations.storeId]         = storeId
            it[SyncOperations.deviceId]        = deviceId
            it[SyncOperations.entityType]      = op.entityType
            it[SyncOperations.entityId]        = op.entityId
            it[SyncOperations.operation]       = op.operation
            it[SyncOperations.payload]         = resolvedPayload
            it[SyncOperations.clientTimestamp] = op.createdAt
            it[SyncOperations.serverTimestamp] = OffsetDateTime.now(ZoneOffset.UTC)
            it[SyncOperations.vectorClock]     = op.retryCount.toLong()
            it[SyncOperations.status]          = "CONFLICT_RESOLVED"
            it[SyncOperations.conflictId]      = conflictId
        }[SyncOperations.serverSeq]
    }

    suspend fun findExistingIds(ids: List<String>): Set<String> = newSuspendedTransaction {
        if (ids.isEmpty()) return@newSuspendedTransaction emptySet()
        SyncOperations.select(SyncOperations.id)
            .where { SyncOperations.id inList ids }
            .map { it[SyncOperations.id] }
            .toSet()
    }

    suspend fun findLatestForEntity(storeId: String, entityType: String, entityId: String): SyncOperationSnapshot? =
        newSuspendedTransaction {
            SyncOperations.selectAll()
                .where {
                    (SyncOperations.storeId eq storeId) and
                    (SyncOperations.entityType eq entityType) and
                    (SyncOperations.entityId eq entityId) and
                    (SyncOperations.status neq "REJECTED")
                }
                .orderBy(SyncOperations.serverSeq, SortOrder.DESC)
                .limit(1)
                .singleOrNull()
                ?.let { row ->
                    SyncOperationSnapshot(
                        opId            = row[SyncOperations.id],
                        deviceId        = row[SyncOperations.deviceId],
                        clientTimestamp = row[SyncOperations.clientTimestamp],
                        payload         = row[SyncOperations.payload],
                        serverSeq       = row[SyncOperations.serverSeq],
                        status          = row[SyncOperations.status],
                    )
                }
        }

    /**
     * Batch version of [findLatestForEntity] — resolves all entity refs in a single
     * WHERE (entity_type, entity_id) IN (...) query, avoiding the N+1 pattern.
     *
     * Returns a map keyed by Pair(entityType, entityId) → most recent snapshot.
     */
    suspend fun findLatestForEntities(
        storeId: String,
        entityRefs: List<Pair<String, String>>,
    ): Map<Pair<String, String>, SyncOperationSnapshot> = newSuspendedTransaction {
        if (entityRefs.isEmpty()) return@newSuspendedTransaction emptyMap()

        // Fetch all matching rows, then pick the latest serverSeq per (entityType, entityId)
        SyncOperations.selectAll()
            .where {
                (SyncOperations.storeId eq storeId) and
                (SyncOperations.status neq "REJECTED") and
                (SyncOperations.entityType inList entityRefs.map { it.first }) and
                (SyncOperations.entityId inList entityRefs.map { it.second })
            }
            .orderBy(SyncOperations.serverSeq, SortOrder.DESC)
            .mapNotNull { row ->
                val key = row[SyncOperations.entityType] to row[SyncOperations.entityId]
                if (key in entityRefs) {
                    key to SyncOperationSnapshot(
                        opId            = row[SyncOperations.id],
                        deviceId        = row[SyncOperations.deviceId],
                        clientTimestamp = row[SyncOperations.clientTimestamp],
                        payload         = row[SyncOperations.payload],
                        serverSeq       = row[SyncOperations.serverSeq],
                        status          = row[SyncOperations.status],
                    )
                } else null
            }
            .distinctBy { it.first }   // keep only the first (highest serverSeq) per key
            .toMap()
    }

    open suspend fun findAfterSeq(storeId: String, afterSeq: Long, limit: Int): List<SyncOperation> =
        newSuspendedTransaction {
            SyncOperations.selectAll()
                .where {
                    (SyncOperations.storeId eq storeId) and
                    (SyncOperations.serverSeq greater afterSeq) and
                    (SyncOperations.status neq "REJECTED")
                }
                .orderBy(SyncOperations.serverSeq, SortOrder.ASC)
                .limit(limit)
                .map { row ->
                    SyncOperation(
                        id        = row[SyncOperations.id],
                        entityType = row[SyncOperations.entityType],
                        entityId  = row[SyncOperations.entityId],
                        operation = row[SyncOperations.operation],
                        payload   = row[SyncOperations.payload],
                        createdAt = row[SyncOperations.clientTimestamp],
                        retryCount = row[SyncOperations.vectorClock].toInt(),
                        serverSeq = row[SyncOperations.serverSeq],
                    )
                }
        }

    open suspend fun getLatestSeq(storeId: String): Long = newSuspendedTransaction {
        SyncOperations.select(SyncOperations.serverSeq)
            .where { SyncOperations.storeId eq storeId }
            .orderBy(SyncOperations.serverSeq, SortOrder.DESC)
            .limit(1)
            .singleOrNull()
            ?.get(SyncOperations.serverSeq)
            ?: 0L
    }

    suspend fun getServerTimestamp(): Long = java.time.Instant.now().toEpochMilli()

    suspend fun countPending(storeId: String): Long = newSuspendedTransaction {
        SyncOperations.selectAll()
            .where {
                (SyncOperations.storeId eq storeId) and
                (SyncOperations.status eq "ACCEPTED")
            }
            .count()
    }
}
