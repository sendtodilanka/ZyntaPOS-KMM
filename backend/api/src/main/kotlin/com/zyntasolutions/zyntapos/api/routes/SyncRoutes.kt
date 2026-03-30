package com.zyntasolutions.zyntapos.api.routes

import com.zyntasolutions.zyntapos.api.db.Stores
import com.zyntasolutions.zyntapos.api.models.ErrorResponse
import com.zyntasolutions.zyntapos.api.models.PushRequest
import com.zyntasolutions.zyntapos.api.repository.UserStoreAccessRepository
import com.zyntasolutions.zyntapos.api.sync.DeltaEngine
import com.zyntasolutions.zyntapos.api.sync.SyncProcessor
import com.zyntasolutions.zyntapos.common.validation.validateOr422
import io.ktor.http.HttpStatusCode
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.auth.principal
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.koin.ktor.ext.inject

/**
 * When SINGLE_STORE_COMPAT_MODE=true, the per-user store ownership check is skipped for
 * users who have no rows in user_store_access. This preserves behaviour for single-store
 * deployments that have not yet populated the grants table.
 *
 * SECURITY: This flag must be false (the default) for any multi-store deployment. With it
 * enabled, users with no grants bypass the access check entirely and can sync to any store
 * whose storeId appears in their JWT — which is still scoped by what the login flow embedded.
 * The risk is minimal in single-store mode (all terminals share the same store) but real in
 * a partially-migrated multi-store deployment where some users have grants and others do not.
 *
 * Transition path: populate all user_store_access rows, then set SINGLE_STORE_COMPAT_MODE=false.
 */
private val SINGLE_STORE_COMPAT_MODE: Boolean =
    System.getenv("SINGLE_STORE_COMPAT_MODE")?.lowercase() == "true"

fun Route.syncRoutes() {
    val syncProcessor: SyncProcessor by inject()
    val deltaEngine: DeltaEngine by inject()
    val userStoreAccessRepo: UserStoreAccessRepository by inject()

    route("/sync") {

        // POST /v1/sync/push — accepts a batch of sync operations from a POS terminal
        post("/push") {
            val principal = call.principal<JWTPrincipal>()!!
            val storeId   = principal.payload.getClaim("storeId").asString()
            val userId    = principal.subject ?: ""

            // S2-10: Validate storeId claim against DB — prevents JWT manipulation attacks
            if (storeId.isBlank() || !verifyStoreExists(storeId)) {
                call.respond(HttpStatusCode.Forbidden, ErrorResponse("INVALID_STORE", "storeId not found or inactive"))
                return@post
            }

            // Validate user has been granted access to this store (multi-store access control).
            // SECURITY FIX: the previous fallback silently bypassed the check when no grants
            // existed at all, creating a gap for partially-migrated multi-store deployments.
            // Now the fallback is gated on an explicit SINGLE_STORE_COMPAT_MODE flag (default false).
            if (userId.isNotBlank() && !userStoreAccessRepo.hasAccess(userId, storeId)) {
                if (!SINGLE_STORE_COMPAT_MODE) {
                    call.respond(HttpStatusCode.Forbidden, ErrorResponse("STORE_ACCESS_DENIED", "User does not have access to this store"))
                    return@post
                }
                // SINGLE_STORE_COMPAT_MODE=true: fall through for users with no grants (single-store deployment)
            }

            val request = call.receive<PushRequest>()

            if (!call.validateOr422 {
                requireNotBlank("deviceId", request.deviceId)
                requireMaxLength("deviceId", request.deviceId, 256)
                requireNotEmpty("operations", request.operations)
                requireMaxSize("operations", request.operations, 50)
            }) return@post

            val result = syncProcessor.processPush(storeId, request)
            call.respond(HttpStatusCode.OK, result)
        }

        // GET /v1/sync/pull?since=<cursor>&limit=50&deviceId=<device>
        get("/pull") {
            val principal = call.principal<JWTPrincipal>()!!
            val storeId   = principal.payload.getClaim("storeId").asString()
            val userId    = principal.subject ?: ""
            val deviceId  = call.request.queryParameters["deviceId"] ?: "unknown"
            val since     = call.request.queryParameters["since"]?.toLongOrNull() ?: 0L
            val limit     = call.request.queryParameters["limit"]?.toIntOrNull()?.coerceIn(1, 200) ?: 50

            // S2-10: Validate storeId claim against DB
            if (storeId.isBlank() || !verifyStoreExists(storeId)) {
                call.respond(HttpStatusCode.Forbidden, ErrorResponse("INVALID_STORE", "storeId not found or inactive"))
                return@get
            }

            // Same access check as /push — see SINGLE_STORE_COMPAT_MODE comment above.
            if (userId.isNotBlank() && !userStoreAccessRepo.hasAccess(userId, storeId)) {
                if (!SINGLE_STORE_COMPAT_MODE) {
                    call.respond(HttpStatusCode.Forbidden, ErrorResponse("STORE_ACCESS_DENIED", "User does not have access to this store"))
                    return@get
                }
            }

            val result = deltaEngine.computeDelta(
                storeId  = storeId,
                deviceId = deviceId,
                since    = since,
                limit    = limit,
            )
            call.respond(HttpStatusCode.OK, result)
        }
    }
}

/** S2-10: Checks that the storeId from the JWT claim maps to an active store. */
private fun verifyStoreExists(storeId: String): Boolean = transaction {
    Stores.selectAll()
        .where { (Stores.id eq storeId) and (Stores.isActive eq true) }
        .count() > 0
}
