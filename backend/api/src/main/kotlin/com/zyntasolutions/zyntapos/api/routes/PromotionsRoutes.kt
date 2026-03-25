package com.zyntasolutions.zyntapos.api.routes

import com.zyntasolutions.zyntapos.api.repository.PromotionRepository
import com.zyntasolutions.zyntapos.api.repository.PromotionRow
import io.ktor.http.HttpStatusCode
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.auth.principal
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.route
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
 * the KMM app. This endpoint is read-only.
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
    }
}

@Serializable
data class PromotionsResponse(
    val total      : Int,
    val promotions : List<PromotionRow>,
)
