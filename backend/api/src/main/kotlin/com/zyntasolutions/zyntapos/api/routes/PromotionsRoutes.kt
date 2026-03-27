package com.zyntasolutions.zyntapos.api.routes

import com.zyntasolutions.zyntapos.api.repository.PromotionRepository
import com.zyntasolutions.zyntapos.api.repository.PromotionRow
import com.zyntasolutions.zyntapos.common.ErrorResponse
import io.ktor.http.HttpStatusCode
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.auth.principal
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.koin.ktor.ext.inject

/**
 * POS-authenticated endpoint for fetching store promotions (C2.4).
 *
 * Used by the KMM app to bootstrap its local promotions database for
 * offline-first evaluation by [ApplyStorePromotionsUseCase].
 *
 * Store isolation: `storeId` is extracted from the RS256 JWT claim —
 * the client cannot request promotions for a store it doesn't belong to.
 *
 * Per ADR-009: Promotion management (write) is a store-level operation in
 * the KMM app. Read + write endpoints live here under /v1/promotions.
 */
fun Route.promotionsRoutes() {
    val repo: PromotionRepository by inject()

    route("/promotions") {

        /**
         * GET /v1/promotions
         *
         * Returns active promotions for the requesting store.
         * Filters by [storeId] JWT claim + optional [updatedSince] cursor for delta sync.
         *
         * Query params:
         * - `updatedSince` (optional, Long): epoch ms — return only promotions updated after this time
         */
        get {
            val principal = call.principal<JWTPrincipal>()!!
            val storeId = principal.payload.getClaim("storeId").asString()
            val nowMs = System.currentTimeMillis()
            val promotions = repo.getActiveForStore(storeId, nowMs)
            call.respond(HttpStatusCode.OK, PromotionsResponse(
                total      = promotions.size,
                promotions = promotions,
            ))
        }

        /**
         * POST /v1/promotions
         *
         * Creates or updates a promotion for the current store.
         * Roles: ADMIN, MANAGER only.
         *
         * Body: UpsertPromotionRequest
         * Response 200: PromotionRow
         */
        post {
            val principal = call.principal<JWTPrincipal>()!!
            val storeId = principal.payload.getClaim("storeId").asString()
            val role = principal.payload.getClaim("role").asString()

            if (role != "ADMIN" && role != "MANAGER") {
                return@post call.respond(
                    HttpStatusCode.Forbidden,
                    ErrorResponse("FORBIDDEN", "Only ADMIN or MANAGER can manage promotions")
                )
            }

            val body = runCatching { call.receive<UpsertPromotionRequest>() }.getOrElse {
                return@post call.respond(
                    HttpStatusCode.BadRequest,
                    ErrorResponse("BAD_REQUEST", "Invalid request body")
                )
            }

            if (body.name.isBlank()) {
                return@post call.respond(
                    HttpStatusCode.BadRequest,
                    ErrorResponse("VALIDATION_ERROR", "name is required")
                )
            }
            if (body.type.isBlank()) {
                return@post call.respond(
                    HttpStatusCode.BadRequest,
                    ErrorResponse("VALIDATION_ERROR", "type is required")
                )
            }

            val saved = repo.upsert(
                id        = body.id,
                storeId   = storeId,
                name      = body.name,
                type      = body.type,
                config    = body.config ?: "{}",
                validFrom = body.validFrom,
                validTo   = body.validTo,
                priority  = body.priority,
                isActive  = body.isActive,
                storeIds  = body.storeIds ?: "[]",
            )
            call.respond(HttpStatusCode.OK, saved)
        }

        /**
         * DELETE /v1/promotions/{id}
         *
         * Deletes a promotion by ID. Only removes promotions owned by the caller's store.
         * Roles: ADMIN, MANAGER only.
         */
        delete("/{id}") {
            val principal = call.principal<JWTPrincipal>()!!
            val storeId = principal.payload.getClaim("storeId").asString()
            val role = principal.payload.getClaim("role").asString()

            if (role != "ADMIN" && role != "MANAGER") {
                return@delete call.respond(
                    HttpStatusCode.Forbidden,
                    ErrorResponse("FORBIDDEN", "Only ADMIN or MANAGER can delete promotions")
                )
            }

            val id = call.parameters["id"] ?: return@delete call.respond(
                HttpStatusCode.BadRequest,
                ErrorResponse("VALIDATION_ERROR", "id path parameter required")
            )

            val deleted = repo.delete(id, storeId)
            if (deleted) {
                call.respond(HttpStatusCode.NoContent)
            } else {
                call.respond(
                    HttpStatusCode.NotFound,
                    ErrorResponse("NOT_FOUND", "Promotion not found: $id")
                )
            }
        }
    }
}

@Serializable
data class PromotionsResponse(
    val total      : Int,
    val promotions : List<PromotionRow>,
)

@Serializable
data class UpsertPromotionRequest(
    val id: String? = null,
    val name: String,
    /** Promotion type — must match KMM PromotionType enum: BUY_X_GET_Y, BUNDLE, FLASH_SALE, SCHEDULED */
    val type: String,
    /** Typed PromotionConfig JSON matching KMM sealed class variants */
    val config: String? = null,
    @SerialName("valid_from")  val validFrom: Long? = null,
    @SerialName("valid_to")    val validTo: Long? = null,
    val priority: Int = 0,
    @SerialName("is_active")   val isActive: Boolean = true,
    /** JSON array of targeted store IDs; null or "[]" = applies to all stores */
    @SerialName("store_ids")   val storeIds: String? = null,
)
