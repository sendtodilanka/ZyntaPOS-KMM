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

            // Validate user has been granted access to this store (multi-store access control)
            // Skip check if user_store_access table has no grant for this user (backwards compatible:
            // single-store deployments where no grants are configured still work)
            if (userId.isNotBlank() && !userStoreAccessRepo.hasAccess(userId, storeId)) {
                // Fall through if no grants exist at all for this user (single-store deployment)
                val hasAnyGrants = userStoreAccessRepo.getAccessibleStores(userId).isNotEmpty()
                if (hasAnyGrants) {
                    call.respond(HttpStatusCode.Forbidden, ErrorResponse("STORE_ACCESS_DENIED", "User does not have access to this store"))
                    return@post
                }
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

            // Validate user has been granted access to this store (multi-store access control)
            if (userId.isNotBlank() && !userStoreAccessRepo.hasAccess(userId, storeId)) {
                val hasAnyGrants = userStoreAccessRepo.getAccessibleStores(userId).isNotEmpty()
                if (hasAnyGrants) {
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
