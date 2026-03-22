package com.zyntasolutions.zyntapos.api.routes

import com.zyntasolutions.zyntapos.api.repository.ReplenishmentRepository
import com.zyntasolutions.zyntapos.api.repository.ReplenishmentRuleRow
import com.zyntasolutions.zyntapos.common.ErrorResponse
import io.ktor.http.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable
import org.koin.ktor.ext.inject

/**
 * POS-authenticated routes for replenishment rule management (C1.5).
 *
 * Uses RS256 JWT auth with storeId claim for store isolation.
 * Roles: ADMIN, MANAGER can create/update/delete rules.
 *        CASHIER can view suggestions.
 *
 * Per ADR-009: All write operations for replenishment rules are exclusively here
 * (not in admin panel routes). Admin panel has read-only monitoring at /admin/replenishment.
 */
fun Route.replenishmentRoutes() {
    val repo: ReplenishmentRepository by inject()

    route("/replenishment") {

        get("/rules") {
            call.principal<JWTPrincipal>()!!
            val warehouseId = call.request.queryParameters["warehouseId"]
            val rules = repo.getRules(warehouseId)
            call.respond(HttpStatusCode.OK, ReplenishmentRulesResponse(
                total = rules.size,
                rules = rules.map { it.toDto() },
            ))
        }

        post("/rules") {
            val principal = call.principal<JWTPrincipal>()!!
            val role = principal.payload.getClaim("role").asString()
            if (role != "ADMIN" && role != "MANAGER") {
                return@post call.respond(
                    HttpStatusCode.Forbidden,
                    ErrorResponse("FORBIDDEN", "Only ADMIN or MANAGER can manage replenishment rules")
                )
            }
            val userId = principal.payload.subject
            val body = call.receive<UpsertReplenishmentRuleRequest>()
            if (body.id.isBlank()) {
                return@post call.respond(HttpStatusCode.BadRequest, ErrorResponse("VALIDATION_ERROR", "id is required"))
            }
            if (body.productId.isBlank() || body.warehouseId.isBlank() || body.supplierId.isBlank()) {
                return@post call.respond(HttpStatusCode.BadRequest, ErrorResponse("VALIDATION_ERROR", "productId, warehouseId and supplierId are required"))
            }
            if (body.reorderPoint < 0) {
                return@post call.respond(HttpStatusCode.BadRequest, ErrorResponse("VALIDATION_ERROR", "reorderPoint must be >= 0"))
            }
            if (body.reorderQty <= 0) {
                return@post call.respond(HttpStatusCode.BadRequest, ErrorResponse("VALIDATION_ERROR", "reorderQty must be > 0"))
            }
            repo.upsertRule(ReplenishmentRuleRow(
                id = body.id, productId = body.productId, warehouseId = body.warehouseId,
                supplierId = body.supplierId, reorderPoint = body.reorderPoint,
                reorderQty = body.reorderQty, autoApprove = body.autoApprove,
                isActive = body.isActive, createdBy = userId, updatedAt = System.currentTimeMillis(),
            ))
            call.respond(HttpStatusCode.OK, mapOf("status" to "ok"))
        }

        delete("/rules/{id}") {
            val principal = call.principal<JWTPrincipal>()!!
            val role = principal.payload.getClaim("role").asString()
            if (role != "ADMIN" && role != "MANAGER") {
                return@delete call.respond(
                    HttpStatusCode.Forbidden,
                    ErrorResponse("FORBIDDEN", "Only ADMIN or MANAGER can delete replenishment rules")
                )
            }
            val id = call.parameters["id"] ?: return@delete call.respond(
                HttpStatusCode.BadRequest, ErrorResponse("BAD_REQUEST", "Missing id")
            )
            val deleted = repo.deleteRule(id)
            if (deleted == 0) {
                call.respond(HttpStatusCode.NotFound, ErrorResponse("NOT_FOUND", "Rule not found: $id"))
            } else {
                call.respond(HttpStatusCode.OK, mapOf("status" to "deleted"))
            }
        }

        get("/suggestions") {
            call.principal<JWTPrincipal>()!!
            val warehouseId = call.request.queryParameters["warehouseId"]
            val suggestions = repo.getSuggestions(warehouseId)
            call.respond(HttpStatusCode.OK, ReplenishmentSuggestionsResponse(
                total = suggestions.size,
                suggestions = suggestions.map { row ->
                    ReplenishmentSuggestionDto(
                        ruleId = row.ruleId, productId = row.productId,
                        warehouseId = row.warehouseId, supplierId = row.supplierId,
                        currentStock = row.currentStock, reorderPoint = row.reorderPoint,
                        reorderQty = row.reorderQty, autoApprove = row.autoApprove,
                    )
                },
            ))
        }
    }
}

// ── Request / Response DTOs ───────────────────────────────────────────────────

@Serializable
data class UpsertReplenishmentRuleRequest(
    val id: String,
    val productId: String,
    val warehouseId: String,
    val supplierId: String,
    val reorderPoint: Double,
    val reorderQty: Double,
    val autoApprove: Boolean = false,
    val isActive: Boolean = true,
)

@Serializable
data class ReplenishmentRulesResponse(
    val total: Int,
    val rules: List<ReplenishmentRuleDto>,
)

@Serializable
data class ReplenishmentRuleDto(
    val id: String,
    val productId: String,
    val warehouseId: String,
    val supplierId: String,
    val reorderPoint: Double,
    val reorderQty: Double,
    val autoApprove: Boolean,
    val isActive: Boolean,
    val updatedAt: Long,
)

@Serializable
data class ReplenishmentSuggestionsResponse(
    val total: Int,
    val suggestions: List<ReplenishmentSuggestionDto>,
)

@Serializable
data class ReplenishmentSuggestionDto(
    val ruleId: String,
    val productId: String,
    val warehouseId: String,
    val supplierId: String,
    val currentStock: Double,
    val reorderPoint: Double,
    val reorderQty: Double,
    val autoApprove: Boolean,
)

fun ReplenishmentRuleRow.toDto() = ReplenishmentRuleDto(
    id           = id,
    productId    = productId,
    warehouseId  = warehouseId,
    supplierId   = supplierId,
    reorderPoint = reorderPoint,
    reorderQty   = reorderQty,
    autoApprove  = autoApprove,
    isActive     = isActive,
    updatedAt    = updatedAt,
)
