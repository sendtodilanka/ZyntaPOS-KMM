package com.zyntasolutions.zyntapos.api.repository

import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import java.time.OffsetDateTime
import java.time.ZoneOffset

open class SyncCursorRepository {

    open suspend fun upsert(storeId: String, deviceId: String, lastSeq: Long): Unit = newSuspendedTransaction {
        val now = OffsetDateTime.now(ZoneOffset.UTC)
        SyncCursors.upsert(SyncCursors.storeId, SyncCursors.deviceId) {
            it[SyncCursors.storeId]    = storeId
            it[SyncCursors.deviceId]   = deviceId
            it[SyncCursors.lastSeq]    = lastSeq
            it[SyncCursors.lastPullAt] = now
        }
    }

    suspend fun getLastSeq(storeId: String, deviceId: String): Long = newSuspendedTransaction {
        SyncCursors.selectAll()
            .where { (SyncCursors.storeId eq storeId) and (SyncCursors.deviceId eq deviceId) }
            .singleOrNull()
            ?.get(SyncCursors.lastSeq)
            ?: 0L
    }
}
