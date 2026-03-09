package com.zyntasolutions.zyntapos.api.repository

import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.javatime.timestampWithTimeZone

/**
 * Exposed table definitions for all sync engine tables (V4 migration).
 */

object SyncOperations : Table("sync_operations") {
    val id              = text("id")
    val storeId         = text("store_id")
    val deviceId        = text("device_id")
    val entityType      = text("entity_type")
    val entityId        = text("entity_id")
    val operation       = text("operation")
    val payload         = text("payload")   // stored as JSONB, Exposed reads as text
    val clientTimestamp = long("client_timestamp")
    val serverSeq       = long("server_seq").autoIncrement()
    val serverTimestamp = timestampWithTimeZone("server_timestamp")
    val vectorClock     = long("vector_clock").default(0L)
    val status          = text("status").default("ACCEPTED")
    val conflictId      = text("conflict_id").nullable()

    override val primaryKey = PrimaryKey(id)
}

object SyncCursors : Table("sync_cursors") {
    val storeId     = text("store_id")
    val deviceId    = text("device_id")
    val lastSeq     = long("last_seq").default(0L)
    val lastPullAt  = timestampWithTimeZone("last_pull_at")

    override val primaryKey = PrimaryKey(storeId, deviceId)
}

object SyncConflictLog : Table("sync_conflict_log") {
    val id               = text("id")
    val storeId          = text("store_id")
    val entityType       = text("entity_type")
    val entityId         = text("entity_id")
    val localOpId        = text("local_op_id")
    val serverOpId       = text("server_op_id")
    val localDeviceId    = text("local_device_id")
    val serverDeviceId   = text("server_device_id")
    val localTimestamp   = long("local_timestamp")
    val serverTs         = long("server_ts")
    val resolution       = text("resolution")
    val localPayload     = text("local_payload").nullable()
    val serverPayload    = text("server_payload").nullable()
    val mergedPayload    = text("merged_payload").nullable()
    val createdAt        = timestampWithTimeZone("created_at")

    override val primaryKey = PrimaryKey(id)
}

object SyncDeadLetters : Table("sync_dead_letters") {
    val id               = text("id")
    val storeId          = text("store_id")
    val deviceId         = text("device_id")
    val entityType       = text("entity_type")
    val entityId         = text("entity_id")
    val operation        = text("operation")
    val payload          = text("payload")
    val clientTimestamp  = long("client_timestamp")
    val errorReason      = text("error_reason")
    val retryCount       = integer("retry_count").default(0)
    val createdAt        = timestampWithTimeZone("created_at")
    val reviewedAt       = timestampWithTimeZone("reviewed_at").nullable()
    val reviewedBy       = text("reviewed_by").nullable()

    override val primaryKey = PrimaryKey(id)
}

object EntitySnapshots : Table("entity_snapshots") {
    val storeId      = text("store_id")
    val entityType   = text("entity_type")
    val entityId     = text("entity_id")
    val payload      = text("payload")
    val lastOpId     = text("last_op_id")
    val lastSeq      = long("last_seq")
    val lastDeviceId = text("last_device_id")
    val updatedAt    = timestampWithTimeZone("updated_at")
    val isDeleted    = bool("is_deleted").default(false)

    override val primaryKey = PrimaryKey(storeId, entityType, entityId)
}
