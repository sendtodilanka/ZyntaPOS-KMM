package com.zyntasolutions.zyntapos.api.routes

import com.zyntasolutions.zyntapos.api.repository.UserStoreAccessRepository
import io.ktor.http.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable

/**
 * REST endpoints for multi-store user access management (C3.2).
 *
 * These are store-level operations managed by store ADMINs via the KMM app,
 * secured by POS RS256 JWT auth (ADR-009 compliant — under /v1/).
 */
fun Route.storeAccessRoutes(repo: UserStoreAccessRepository) {
    route("/store-access") {

            /**
             * GET /v1/store-access/my-stores
             * Returns all stores the current user has access to.
             */
            get("/my-stores") {
                val principal = call.principal<JWTPrincipal>()!!
                val userId = principal.subject!!
                val stores = repo.getAccessibleStores(userId)
                call.respond(HttpStatusCode.OK, stores.map { it.toResponse() })
            }

            /**
             * GET /v1/store-access/users?storeId={storeId}
             * Returns all users who have been granted access to a specific store.
             * Requires ADMIN or STORE_MANAGER role.
             */
            get("/users") {
                val principal = call.principal<JWTPrincipal>()!!
                val role = principal.payload.getClaim("role").asString()
                if (role !in listOf("ADMIN", "STORE_MANAGER")) {
                    call.respond(HttpStatusCode.Forbidden, mapOf("error" to "Insufficient permissions"))
                    return@get
                }

                val storeId = call.request.queryParameters["storeId"]
                    ?: return@get call.respond(HttpStatusCode.BadRequest, mapOf("error" to "storeId required"))

                val users = repo.getUsersForStore(storeId)
                call.respond(HttpStatusCode.OK, users.map { it.toResponse() })
            }

            /**
             * POST /v1/store-access/grant
             * Grants a user access to a store. Requires ADMIN role.
             */
            post("/grant") {
                val principal = call.principal<JWTPrincipal>()!!
                val role = principal.payload.getClaim("role").asString()
                if (role != "ADMIN") {
                    call.respond(HttpStatusCode.Forbidden, mapOf("error" to "Only ADMIN can grant store access"))
                    return@post
                }

                val request = call.receive<GrantAccessRequest>()
                if (request.userId.isBlank() || request.storeId.isBlank()) {
                    call.respond(HttpStatusCode.BadRequest, mapOf("error" to "userId and storeId are required"))
                    return@post
                }

                val grantedBy = principal.subject!!
                val result = repo.grantAccess(
                    userId = request.userId,
                    storeId = request.storeId,
                    roleAtStore = request.roleAtStore,
                    grantedBy = grantedBy,
                )
                call.respond(HttpStatusCode.Created, result.toResponse())
            }

            /**
             * POST /v1/store-access/revoke
             * Revokes a user's access to a store. Requires ADMIN role.
             */
            post("/revoke") {
                val principal = call.principal<JWTPrincipal>()!!
                val role = principal.payload.getClaim("role").asString()
                if (role != "ADMIN") {
                    call.respond(HttpStatusCode.Forbidden, mapOf("error" to "Only ADMIN can revoke store access"))
                    return@post
                }

                val request = call.receive<RevokeAccessRequest>()
                val revoked = repo.revokeAccess(request.userId, request.storeId)
                if (revoked) {
                    call.respond(HttpStatusCode.OK, mapOf("status" to "revoked"))
                } else {
                    call.respond(HttpStatusCode.NotFound, mapOf("error" to "Access grant not found"))
                }
            }

            /**
             * GET /v1/store-access/check?userId={userId}&storeId={storeId}
             * Checks if a user has access to a specific store.
             */
            get("/check") {
                val userId = call.request.queryParameters["userId"]
                    ?: return@get call.respond(HttpStatusCode.BadRequest, mapOf("error" to "userId required"))
                val storeId = call.request.queryParameters["storeId"]
                    ?: return@get call.respond(HttpStatusCode.BadRequest, mapOf("error" to "storeId required"))

                val hasAccess = repo.hasAccess(userId, storeId)
                call.respond(HttpStatusCode.OK, mapOf("hasAccess" to hasAccess))
            }
    }
}

@Serializable
data class GrantAccessRequest(
    val userId: String,
    val storeId: String,
    val roleAtStore: String? = null,
)

@Serializable
data class RevokeAccessRequest(
    val userId: String,
    val storeId: String,
)

@Serializable
data class StoreAccessResponse(
    val id: String,
    val userId: String,
    val storeId: String,
    val roleAtStore: String?,
    val isActive: Boolean,
    val grantedBy: String?,
    val createdAt: String,
    val updatedAt: String,
)

private fun UserStoreAccessRepository.UserStoreAccessRow.toResponse() = StoreAccessResponse(
    id = id.toString(),
    userId = userId,
    storeId = storeId,
    roleAtStore = roleAtStore,
    isActive = isActive,
    grantedBy = grantedBy,
    createdAt = createdAt.toString(),
    updatedAt = updatedAt.toString(),
)
