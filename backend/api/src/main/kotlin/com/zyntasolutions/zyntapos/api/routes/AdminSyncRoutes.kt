package com.zyntasolutions.zyntapos.api.routes

import com.zyntasolutions.zyntapos.api.models.*
import com.zyntasolutions.zyntapos.api.service.AdminAuthService
import com.zyntasolutions.zyntapos.api.service.AdminStoresService
import com.zyntasolutions.zyntapos.api.service.ForceSyncNotifier
import com.zyntasolutions.zyntapos.api.service.Stores
import com.zyntasolutions.zyntapos.api.service.SyncQueue
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.javatime.timestampWithTimeZone
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import org.koin.ktor.ext.inject
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID

object StoreSyncFlags : Table("store_sync_flags") {
    val storeId              = text("store_id")
    val forceSyncRequested   = bool("force_sync_requested")
    val requestedAt          = timestampWithTimeZone("requested_at").nullable()
    val requestedBy          = uuid("requested_by").nullable()
    override val primaryKey  = PrimaryKey(storeId)
}

fun Route.adminSyncRoutes() {
    val authService: AdminAuthService by inject()
    val forceSyncNotifier: ForceSyncNotifier by inject()

    route("/admin/sync") {

        get("/status") {
            resolveAdminUser(call, authService) ?: return@get
            val statuses = newSuspendedTransaction {
                val storeMap = Stores.selectAll().associate { it[Stores.id] to it[Stores.name] }

                storeMap.map { (storeId, storeName) ->
                    val pending = SyncQueue.selectAll().where {
                        (SyncQueue.storeId eq storeId) and (SyncQueue.isProcessed eq false)
                    }.count().toInt()

                    val lastSync = SyncQueue.selectAll().where {
                        (SyncQueue.storeId eq storeId) and (SyncQueue.isProcessed eq true)
                    }.orderBy(SyncQueue.serverTs, SortOrder.DESC).limit(1)
                        .singleOrNull()?.get(SyncQueue.serverTs)?.toInstant()?.toString()

                    val status = when {
                        pending > 50 -> "FAILED"
                        pending > 10 -> "PENDING"
                        else         -> "SYNCED"
                    }

                    StoreSyncStatus(
                        storeId            = storeId,
                        storeName          = storeName,
                        status             = status,
                        queueDepth         = pending,
                        lastSyncAt         = lastSync,
                        lastSyncDurationMs = null,
                        errorCount         = 0,
                        pendingOperations  = pending
                    )
                }
            }
            call.respond(HttpStatusCode.OK, statuses)
        }

        get("/{storeId}") {
            resolveAdminUser(call, authService) ?: return@get
            val storeId = call.parameters["storeId"] ?: return@get call.respond(
                HttpStatusCode.BadRequest, ErrorResponse("MISSING_ID", "Store ID required")
            )
            val result = newSuspendedTransaction {
                val store = Stores.selectAll().where { Stores.id eq storeId }.singleOrNull()
                    ?: return@newSuspendedTransaction null

                val pending = SyncQueue.selectAll().where {
                    (SyncQueue.storeId eq storeId) and (SyncQueue.isProcessed eq false)
                }.count().toInt()

                val lastSync = SyncQueue.selectAll().where {
                    (SyncQueue.storeId eq storeId) and (SyncQueue.isProcessed eq true)
                }.orderBy(SyncQueue.serverTs, SortOrder.DESC).limit(1)
                    .singleOrNull()?.get(SyncQueue.serverTs)?.toInstant()?.toString()

                val status = when {
                    pending > 50 -> "FAILED"
                    pending > 10 -> "PENDING"
                    else         -> "SYNCED"
                }

                StoreSyncStatus(
                    storeId            = storeId,
                    storeName          = store[Stores.name],
                    status             = status,
                    queueDepth         = pending,
                    lastSyncAt         = lastSync,
                    lastSyncDurationMs = null,
                    errorCount         = 0,
                    pendingOperations  = pending
                )
            } ?: return@get call.respond(HttpStatusCode.NotFound, ErrorResponse("NOT_FOUND", "Store not found"))

            call.respond(HttpStatusCode.OK, result)
        }

        get("/{storeId}/queue") {
            resolveAdminUser(call, authService) ?: return@get
            val storeId = call.parameters["storeId"] ?: return@get call.respond(
                HttpStatusCode.BadRequest, ErrorResponse("MISSING_ID", "Store ID required")
            )
            val ops = newSuspendedTransaction {
                SyncQueue.selectAll().where {
                    (SyncQueue.storeId eq storeId) and (SyncQueue.isProcessed eq false)
                }.orderBy(SyncQueue.vectorClock, SortOrder.ASC)
                    .limit(100)
                    .map { row ->
                        val payloadJson = runCatching {
                            Json.parseToJsonElement(row[SyncQueue.payload])
                        }.getOrDefault(JsonNull)

                        AdminSyncQueueItem(
                            id                = row[SyncQueue.id],
                            storeId           = row[SyncQueue.storeId],
                            entityType        = row[SyncQueue.entityType],
                            entityId          = row[SyncQueue.entityId],
                            operationType     = row[SyncQueue.operation],
                            payload           = payloadJson,
                            clientTimestamp   = row[SyncQueue.clientTs].toInstant().toString(),
                            retryCount        = 0,
                            lastErrorMessage  = null,
                            createdAt         = row[SyncQueue.serverTs].toInstant().toString()
                        )
                    }
            }
            call.respond(HttpStatusCode.OK, ops)
        }

        post("/{storeId}/force") {
            val admin = resolveAdminUser(call, authService) ?: return@post
            val storeId = call.parameters["storeId"] ?: return@post call.respond(
                HttpStatusCode.BadRequest, ErrorResponse("MISSING_ID", "Store ID required")
            )

            val exists = newSuspendedTransaction {
                Stores.selectAll().where { Stores.id eq storeId }.any()
            }
            if (!exists) {
                call.respond(HttpStatusCode.NotFound, ErrorResponse("NOT_FOUND", "Store not found"))
                return@post
            }

            val now = OffsetDateTime.now(ZoneOffset.UTC)
            newSuspendedTransaction {
                StoreSyncFlags.upsert(StoreSyncFlags.storeId) {
                    it[StoreSyncFlags.storeId]            = storeId
                    it[forceSyncRequested]  = true
                    it[requestedAt]         = now
                    it[requestedBy]         = admin.id
                }
            }

            // Notify connected WebSocket devices via Redis pub/sub
            forceSyncNotifier.publish(storeId)

            call.respond(
                HttpStatusCode.OK,
                ForceSyncResult(
                    storeId          = storeId,
                    operationsQueued = 0,
                    triggeredAt      = now.toInstant().toString()
                )
            )
        }
    }
}
