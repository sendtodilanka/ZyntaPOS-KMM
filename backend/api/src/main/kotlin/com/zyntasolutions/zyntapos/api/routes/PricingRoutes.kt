package com.zyntasolutions.zyntapos.api.routes

import com.zyntasolutions.zyntapos.api.repository.PricingRuleRepository
import com.zyntasolutions.zyntapos.api.repository.PricingRuleRow
import com.zyntasolutions.zyntapos.common.ErrorResponse
import io.ktor.http.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable
import org.koin.ktor.ext.inject
import java.time.OffsetDateTime
import java.time.format.DateTimeParseException

/**
 * POS-authenticated routes for pricing rule management (C2.1).
 *
 * Uses RS256 JWT auth with storeId claim for store isolation.
 * Roles: ADMIN, MANAGER can create/update/delete rules.
 *        CASHIER, CUSTOMER_SERVICE, REPORTER can only read.
 *
 * Per ADR-009: All write operations for pricing rules are exclusively here
 * (not in admin panel routes). Admin panel has read-only monitoring at /admin/pricing.
 */
fun Route.pricingRoutes() {
    val repo: PricingRuleRepository by inject()

    route("/pricing") {

        get("/rules") {
            val principal = call.principal<JWTPrincipal>()!!
            val storeId = principal.payload.getClaim("storeId").asString()
            val productId = call.request.queryParameters["productId"]
            val rules = repo.getRules(productId, storeId)
            call.respond(HttpStatusCode.OK, PricingRulesResponse(
                total = rules.size,
                rules = rules,
            ))
        }

        post("/rules") {
            val principal = call.principal<JWTPrincipal>()!!
            val role = principal.payload.getClaim("role").asString()
            if (role != "ADMIN" && role != "MANAGER") {
                return@post call.respond(
                    HttpStatusCode.Forbidden,
                    ErrorResponse("FORBIDDEN", "Only ADMIN or MANAGER can manage pricing rules")
                )
            }
            val body = call.receive<UpsertPricingRuleRequest>()
            if (body.productId.isBlank()) {
                return@post call.respond(HttpStatusCode.BadRequest, ErrorResponse("VALIDATION_ERROR", "productId is required"))
            }
            if (body.price < 0) {
                return@post call.respond(HttpStatusCode.BadRequest, ErrorResponse("VALIDATION_ERROR", "price must be non-negative"))
            }
            val validFrom = body.validFrom?.let {
                try { OffsetDateTime.parse(it) } catch (_: DateTimeParseException) {
                    return@post call.respond(HttpStatusCode.BadRequest, ErrorResponse("VALIDATION_ERROR", "validFrom must be ISO-8601 format"))
                }
            }
            val validTo = body.validTo?.let {
                try { OffsetDateTime.parse(it) } catch (_: DateTimeParseException) {
                    return@post call.respond(HttpStatusCode.BadRequest, ErrorResponse("VALIDATION_ERROR", "validTo must be ISO-8601 format"))
                }
            }
            val rule = repo.upsertRule(
                id = body.id, productId = body.productId, storeId = body.storeId,
                price = body.price, costPrice = body.costPrice, priority = body.priority,
                validFrom = validFrom, validTo = validTo,
                isActive = body.isActive, description = body.description,
            )
            call.respond(HttpStatusCode.OK, rule)
        }

        delete("/rules/{id}") {
            val principal = call.principal<JWTPrincipal>()!!
            val role = principal.payload.getClaim("role").asString()
            if (role != "ADMIN" && role != "MANAGER") {
                return@delete call.respond(
                    HttpStatusCode.Forbidden,
                    ErrorResponse("FORBIDDEN", "Only ADMIN or MANAGER can delete pricing rules")
                )
            }
            val id = call.parameters["id"] ?: return@delete call.respond(
                HttpStatusCode.BadRequest, ErrorResponse("VALIDATION_ERROR", "id path parameter required")
            )
            val deleted = repo.deleteRule(id)
            if (deleted) {
                call.respond(HttpStatusCode.NoContent)
            } else {
                call.respond(HttpStatusCode.NotFound, ErrorResponse("NOT_FOUND", "Pricing rule not found: $id"))
            }
        }
    }
}

// ── Request / Response DTOs ───────────────────────────────────────────────────

@Serializable
data class PricingRulesResponse(
    val total: Int,
    val rules: List<PricingRuleRow>,
)

@Serializable
data class UpsertPricingRuleRequest(
    val id: String? = null,
    val productId: String,
    val storeId: String? = null,
    val price: Double,
    val costPrice: Double? = null,
    val priority: Int = 0,
    val validFrom: String? = null,
    val validTo: String? = null,
    val isActive: Boolean = true,
    val description: String = "",
)
