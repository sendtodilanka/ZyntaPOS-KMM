package com.zyntasolutions.zyntapos.api.repository

import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction

class EntitySnapshotRepository {

    suspend fun findLatest(storeId: String, entityType: String, entityId: String): Map<String, Any?>? =
        newSuspendedTransaction {
            EntitySnapshots.selectAll()
                .where {
                    (EntitySnapshots.storeId eq storeId) and
                    (EntitySnapshots.entityType eq entityType) and
                    (EntitySnapshots.entityId eq entityId)
                }
                .singleOrNull()
                ?.let { row ->
                    mapOf(
                        "storeId"      to row[EntitySnapshots.storeId],
                        "entityType"   to row[EntitySnapshots.entityType],
                        "entityId"     to row[EntitySnapshots.entityId],
                        "payload"      to row[EntitySnapshots.payload],
                        "lastOpId"     to row[EntitySnapshots.lastOpId],
                        "lastSeq"      to row[EntitySnapshots.lastSeq],
                        "lastDeviceId" to row[EntitySnapshots.lastDeviceId],
                        "isDeleted"    to row[EntitySnapshots.isDeleted],
                        "updatedAt"    to row[EntitySnapshots.updatedAt].toInstant().toString(),
                    )
                }
        }

    suspend fun findByType(storeId: String, entityType: String, afterSeq: Long = 0L): List<Map<String, Any?>> =
        newSuspendedTransaction {
            EntitySnapshots.selectAll()
                .where {
                    (EntitySnapshots.storeId eq storeId) and
                    (EntitySnapshots.entityType eq entityType) and
                    (EntitySnapshots.lastSeq greater afterSeq) and
                    (EntitySnapshots.isDeleted eq false)
                }
                .orderBy(EntitySnapshots.lastSeq, SortOrder.ASC)
                .map { row ->
                    mapOf(
                        "entityId"   to row[EntitySnapshots.entityId],
                        "payload"    to row[EntitySnapshots.payload],
                        "lastSeq"    to row[EntitySnapshots.lastSeq],
                        "updatedAt"  to row[EntitySnapshots.updatedAt].toInstant().toString(),
                    )
                }
        }
}
