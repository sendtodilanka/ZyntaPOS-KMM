package com.zyntasolutions.zyntapos.api.repository

import com.zyntasolutions.zyntapos.api.models.SyncOperation
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import java.time.OffsetDateTime
import java.time.ZoneOffset

class DeadLetterRepository {

    suspend fun insert(storeId: String, deviceId: String, op: SyncOperation, reason: String) =
        newSuspendedTransaction {
            SyncDeadLetters.insert {
                it[SyncDeadLetters.id]              = op.id
                it[SyncDeadLetters.storeId]         = storeId
                it[SyncDeadLetters.deviceId]        = deviceId
                it[SyncDeadLetters.entityType]      = op.entityType
                it[SyncDeadLetters.entityId]        = op.entityId
                it[SyncDeadLetters.operation]       = op.operation
                it[SyncDeadLetters.payload]         = op.payload
                it[SyncDeadLetters.clientTimestamp] = op.createdAt
                it[SyncDeadLetters.errorReason]     = reason
                it[SyncDeadLetters.retryCount]      = 0
                it[SyncDeadLetters.createdAt]       = OffsetDateTime.now(ZoneOffset.UTC)
            }
        }

    suspend fun findPending(storeId: String, limit: Int = 100): List<Map<String, Any?>> =
        newSuspendedTransaction {
            SyncDeadLetters.selectAll()
                .where {
                    (SyncDeadLetters.storeId eq storeId) and
                    (SyncDeadLetters.reviewedAt.isNull())
                }
                .orderBy(SyncDeadLetters.createdAt, SortOrder.DESC)
                .limit(limit)
                .map { row ->
                    mapOf(
                        "id"              to row[SyncDeadLetters.id],
                        "storeId"         to row[SyncDeadLetters.storeId],
                        "deviceId"        to row[SyncDeadLetters.deviceId],
                        "entityType"      to row[SyncDeadLetters.entityType],
                        "entityId"        to row[SyncDeadLetters.entityId],
                        "operation"       to row[SyncDeadLetters.operation],
                        "errorReason"     to row[SyncDeadLetters.errorReason],
                        "retryCount"      to row[SyncDeadLetters.retryCount],
                        "clientTimestamp" to row[SyncDeadLetters.clientTimestamp],
                        "createdAt"       to row[SyncDeadLetters.createdAt].toInstant().toString(),
                    )
                }
        }

    suspend fun findAllPending(limit: Int = 200): List<Map<String, Any?>> = newSuspendedTransaction {
        SyncDeadLetters.selectAll()
            .where { SyncDeadLetters.reviewedAt.isNull() }
            .orderBy(SyncDeadLetters.createdAt, SortOrder.DESC)
            .limit(limit)
            .map { row ->
                mapOf(
                    "id"          to row[SyncDeadLetters.id],
                    "storeId"     to row[SyncDeadLetters.storeId],
                    "entityType"  to row[SyncDeadLetters.entityType],
                    "errorReason" to row[SyncDeadLetters.errorReason],
                    "retryCount"  to row[SyncDeadLetters.retryCount],
                    "createdAt"   to row[SyncDeadLetters.createdAt].toInstant().toString(),
                )
            }
    }

    suspend fun markReviewed(id: String, reviewedBy: String) = newSuspendedTransaction {
        SyncDeadLetters.update({ SyncDeadLetters.id eq id }) {
            it[reviewedAt] = OffsetDateTime.now(ZoneOffset.UTC)
            it[SyncDeadLetters.reviewedBy] = reviewedBy
        }
    }

    suspend fun incrementRetry(id: String) = newSuspendedTransaction {
        SyncDeadLetters.update({ SyncDeadLetters.id eq id }) {
            with(SqlExpressionBuilder) {
                it.update(retryCount, retryCount + 1)
            }
        }
    }

    suspend fun delete(id: String) = newSuspendedTransaction {
        SyncDeadLetters.deleteWhere { SyncDeadLetters.id eq id }
    }

    suspend fun findById(id: String): Map<String, Any?>? = newSuspendedTransaction {
        SyncDeadLetters.selectAll()
            .where { SyncDeadLetters.id eq id }
            .singleOrNull()
            ?.let { row ->
                mapOf(
                    "id"              to row[SyncDeadLetters.id],
                    "storeId"         to row[SyncDeadLetters.storeId],
                    "deviceId"        to row[SyncDeadLetters.deviceId],
                    "entityType"      to row[SyncDeadLetters.entityType],
                    "entityId"        to row[SyncDeadLetters.entityId],
                    "operation"       to row[SyncDeadLetters.operation],
                    "payload"         to row[SyncDeadLetters.payload],
                    "errorReason"     to row[SyncDeadLetters.errorReason],
                    "retryCount"      to row[SyncDeadLetters.retryCount],
                    "clientTimestamp" to row[SyncDeadLetters.clientTimestamp],
                    "createdAt"       to row[SyncDeadLetters.createdAt].toInstant().toString(),
                )
            }
    }
}
