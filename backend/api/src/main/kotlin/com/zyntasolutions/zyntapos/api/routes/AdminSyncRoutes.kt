package com.zyntasolutions.zyntapos.api.routes

import com.zyntasolutions.zyntapos.api.models.*
import com.zyntasolutions.zyntapos.api.plugins.TokenRevocationCache
import com.zyntasolutions.zyntapos.api.repository.ConflictLogRepository
import com.zyntasolutions.zyntapos.api.repository.DeadLetterRepository
import com.zyntasolutions.zyntapos.api.service.AdminAuditService
import com.zyntasolutions.zyntapos.api.service.AdminAuthService
import com.zyntasolutions.zyntapos.api.service.AdminStoresService
import com.zyntasolutions.zyntapos.api.service.ForceSyncNotifier
import com.zyntasolutions.zyntapos.api.auth.AdminPermissions
import com.zyntasolutions.zyntapos.api.db.RevokedTokens
import com.zyntasolutions.zyntapos.api.db.Stores
import com.zyntasolutions.zyntapos.api.db.SyncQueue
import com.zyntasolutions.zyntapos.common.validation.validateOr422
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.lettuce.core.api.StatefulRedisConnection
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import org.apache.commons.pool2.impl.GenericObjectPool
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.javatime.timestampWithTimeZone
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import org.koin.ktor.ext.inject
import org.slf4j.LoggerFactory
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
    val conflictLogRepo: ConflictLogRepository by inject()
    val deadLetterRepo: DeadLetterRepository by inject()

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

        // ── Conflict log endpoints ──────────────────────────────────────────

        get("/conflicts") {
            resolveAdminUser(call, authService) ?: return@get
            val conflicts = conflictLogRepo.findAll(limit = 200)
            call.respond(HttpStatusCode.OK, conflicts)
        }

        get("/{storeId}/conflicts") {
            resolveAdminUser(call, authService) ?: return@get
            val storeId = call.parameters["storeId"] ?: return@get call.respond(
                HttpStatusCode.BadRequest, ErrorResponse("MISSING_ID", "Store ID required")
            )
            val conflicts = conflictLogRepo.findRecent(storeId, limit = 50)
            call.respond(HttpStatusCode.OK, conflicts)
        }

        // ── Dead letter endpoints ────────────────────────────────────────────

        get("/dead-letters") {
            resolveAdminUser(call, authService) ?: return@get
            val letters = deadLetterRepo.findAllPending(limit = 200)
            call.respond(HttpStatusCode.OK, letters)
        }

        get("/{storeId}/dead-letters") {
            resolveAdminUser(call, authService) ?: return@get
            val storeId = call.parameters["storeId"] ?: return@get call.respond(
                HttpStatusCode.BadRequest, ErrorResponse("MISSING_ID", "Store ID required")
            )
            val letters = deadLetterRepo.findPending(storeId, limit = 100)
            call.respond(HttpStatusCode.OK, letters)
        }

        post("/dead-letters/{id}/retry") {
            resolveAdminUser(call, authService) ?: return@post
            val id = call.parameters["id"] ?: return@post call.respond(
                HttpStatusCode.BadRequest, ErrorResponse("MISSING_ID", "Dead letter ID required")
            )
            deadLetterRepo.findById(id)
                ?: return@post call.respond(HttpStatusCode.NotFound, ErrorResponse("NOT_FOUND", "Dead letter not found"))

            deadLetterRepo.incrementRetry(id)
            call.respond(HttpStatusCode.OK, mapOf("id" to id, "status" to "retried"))
        }

        delete("/dead-letters/{id}") {
            val admin = resolveAdminUser(call, authService) ?: return@delete
            val id = call.parameters["id"] ?: return@delete call.respond(
                HttpStatusCode.BadRequest, ErrorResponse("MISSING_ID", "Dead letter ID required")
            )
            deadLetterRepo.markReviewed(id, admin.email)
            deadLetterRepo.delete(id)
            call.respond(HttpStatusCode.OK, mapOf("id" to id, "status" to "discarded"))
        }

        // ── POS Token Revocation ──────────────────────────────────────────────
        tokenRevocationRoutes(authService)
    }
}

@Serializable
private data class RevokeTokenRequest(
    val jti: String,
    val reason: String? = null,
)

@Serializable
private data class RevokeTokenResponse(
    val jti: String,
    val status: String,
)

private val tokenRevokeLogger = LoggerFactory.getLogger("TokenRevocation")

/**
 * Admin endpoint to revoke POS JWT access tokens.
 * Inserts the JTI into `revoked_tokens` table and publishes to Redis `revoked_jtis` set
 * so the sync service can also reject the token without DB access.
 */
private fun Route.tokenRevocationRoutes(authService: AdminAuthService) {
    val redisPool: GenericObjectPool<StatefulRedisConnection<String, String>>? by inject()
    val auditService: AdminAuditService by inject()

    route("/tokens") {
        // POST /admin/sync/tokens/revoke — revoke a specific POS JWT by JTI
        post("/revoke") {
            val admin = resolveAdminUser(call, authService) ?: return@post
            AdminPermissions.requirePermission(admin.role, "users:sessions:revoke")

            val body = call.receive<RevokeTokenRequest>()
            if (!call.validateOr422 {
                requireNotBlank("jti", body.jti)
                requireMaxLength("jti", body.jti, 256)
            }) return@post

            // Insert into revoked_tokens table (idempotent — ignore if already exists)
            val inserted = newSuspendedTransaction {
                val existing = RevokedTokens.selectAll()
                    .where { RevokedTokens.jti eq body.jti }
                    .count()
                if (existing > 0L) return@newSuspendedTransaction false

                RevokedTokens.insert {
                    it[id] = UUID.randomUUID()
                    it[jti] = body.jti
                    it[reason] = body.reason
                    it[revokedAt] = OffsetDateTime.now(ZoneOffset.UTC)
                }
                true
            }

            // Update in-memory cache immediately
            TokenRevocationCache.markRevoked(body.jti)

            // Publish to Redis set so sync service can check revocation
            publishRevocationToRedis(redisPool, body.jti)

            // Audit trail
            auditService.log(
                adminId    = admin.id,
                adminName  = admin.name,
                eventType  = "POS_TOKEN_REVOKED",
                category   = "AUTH",
                entityType = "pos_token",
                entityId   = body.jti,
                ipAddress  = call.request.headers["X-Forwarded-For"]?.split(",")?.firstOrNull()?.trim()
                              ?: call.request.local.remoteHost,
                success    = true,
                newValues  = mapOf("reason" to (body.reason ?: "admin_revocation")),
            )

            tokenRevokeLogger.info("POS token revoked: jti=${body.jti} by=${admin.email} reason=${body.reason}")

            call.respond(
                if (inserted) HttpStatusCode.Created else HttpStatusCode.OK,
                RevokeTokenResponse(jti = body.jti, status = if (inserted) "revoked" else "already_revoked")
            )
        }
    }
}

private const val REVOKED_JTIS_REDIS_KEY = "revoked_jtis"
private const val REVOKED_JTI_TTL_SECONDS = 86400L // 24 hours — tokens expire before this

private fun publishRevocationToRedis(
    redisPool: GenericObjectPool<StatefulRedisConnection<String, String>>?,
    jti: String,
) {
    if (redisPool == null) return
    try {
        val conn = redisPool.borrowObject()
        try {
            // Add to Redis set with TTL so it self-cleans
            conn.sync().sadd(REVOKED_JTIS_REDIS_KEY, jti)
            // Refresh TTL on the set (covers all entries)
            conn.sync().expire(REVOKED_JTIS_REDIS_KEY, REVOKED_JTI_TTL_SECONDS)
        } finally {
            redisPool.returnObject(conn)
        }
    } catch (e: Exception) {
        tokenRevokeLogger.warn("Failed to publish token revocation to Redis: ${e.message}")
    }
}
