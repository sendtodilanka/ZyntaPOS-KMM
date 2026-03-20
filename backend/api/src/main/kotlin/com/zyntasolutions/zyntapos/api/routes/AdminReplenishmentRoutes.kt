package com.zyntasolutions.zyntapos.api.routes

import com.zyntasolutions.zyntapos.api.auth.AdminPermissions
import com.zyntasolutions.zyntapos.api.repository.ReplenishmentRepository
import com.zyntasolutions.zyntapos.api.repository.ReplenishmentRuleRow
import com.zyntasolutions.zyntapos.api.service.AdminAuthService
import com.zyntasolutions.zyntapos.common.ErrorResponse
import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable
import org.koin.ktor.ext.inject

/**
 * Admin panel routes for warehouse-to-store replenishment management (C1.5).
 *
 * Endpoints:
 *   GET    /admin/replenishment/rules              — list all rules (filter: warehouseId)
 *   POST   /admin/replenishment/rules              — create or update a rule (upsert)
 *   DELETE /admin/replenishment/rules/{id}         — delete a rule
 *   GET    /admin/replenishment/suggestions        — products at or below reorder point
 */
fun Route.adminReplenishmentRoutes() {
    val repo: ReplenishmentRepository by inject()
    val authService: AdminAuthService by inject()

    route("/admin/replenishment") {

        /**
         * GET /admin/replenishment/rules
         *
         * Returns all replenishment rules.
         * Optional query params: warehouseId (filter to a single warehouse).
         * Roles: ADMIN, OPERATOR (inventory:read)
         */
        get("/rules") {
            val user = resolveAdminUser(call, authService) ?: return@get
            if (!AdminPermissions.check(user.role, "inventory:read")) {
                return@get call.respond(
                    HttpStatusCode.Forbidden,
                    ErrorResponse("FORBIDDEN", "inventory:read permission required"),
                )
            }
            val warehouseId = call.request.queryParameters["warehouseId"]
            val rules = repo.getRules(warehouseId)
            call.respond(HttpStatusCode.OK, ReplenishmentRulesResponse(
                total = rules.size,
                rules = rules.map { it.toDto() },
            ))
        }

        /**
         * POST /admin/replenishment/rules
         *
         * Upserts a replenishment rule.
         * Roles: ADMIN, OPERATOR (inventory:write)
         */
        post("/rules") {
            val user = resolveAdminUser(call, authService) ?: return@post
            if (!AdminPermissions.check(user.role, "inventory:write")) {
                return@post call.respond(
                    HttpStatusCode.Forbidden,
                    ErrorResponse("FORBIDDEN", "inventory:write permission required"),
                )
            }
            val body = call.receive<UpsertReplenishmentRuleRequest>()

            if (body.id.isBlank()) {
                return@post call.respond(
                    HttpStatusCode.BadRequest,
                    ErrorResponse("VALIDATION_ERROR", "id is required"),
                )
            }
            if (body.productId.isBlank() || body.warehouseId.isBlank() || body.supplierId.isBlank()) {
                return@post call.respond(
                    HttpStatusCode.BadRequest,
                    ErrorResponse("VALIDATION_ERROR", "productId, warehouseId and supplierId are required"),
                )
            }
            if (body.reorderPoint < 0) {
                return@post call.respond(
                    HttpStatusCode.BadRequest,
                    ErrorResponse("VALIDATION_ERROR", "reorderPoint must be >= 0"),
                )
            }
            if (body.reorderQty <= 0) {
                return@post call.respond(
                    HttpStatusCode.BadRequest,
                    ErrorResponse("VALIDATION_ERROR", "reorderQty must be > 0"),
                )
            }

            repo.upsertRule(
                ReplenishmentRuleRow(
                    id           = body.id,
                    productId    = body.productId,
                    warehouseId  = body.warehouseId,
                    supplierId   = body.supplierId,
                    reorderPoint = body.reorderPoint,
                    reorderQty   = body.reorderQty,
                    autoApprove  = body.autoApprove,
                    isActive     = body.isActive,
                    createdBy    = user.id,
                    updatedAt    = System.currentTimeMillis(),
                )
            )
            call.respond(HttpStatusCode.OK, mapOf("status" to "ok"))
        }

        /**
         * DELETE /admin/replenishment/rules/{id}
         *
         * Deletes a replenishment rule.
         * Roles: ADMIN (inventory:write)
         */
        delete("/rules/{id}") {
            val user = resolveAdminUser(call, authService) ?: return@delete
            if (!AdminPermissions.check(user.role, "inventory:write")) {
                return@delete call.respond(
                    HttpStatusCode.Forbidden,
                    ErrorResponse("FORBIDDEN", "inventory:write permission required"),
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

        /**
         * GET /admin/replenishment/suggestions
         *
         * Returns products whose current warehouse stock is at or below the
         * configured reorder point for their active replenishment rule.
         *
         * Optional query params: warehouseId (filter to a single warehouse).
         * Roles: ADMIN, OPERATOR, FINANCE (inventory:read)
         */
        get("/suggestions") {
            val user = resolveAdminUser(call, authService) ?: return@get
            if (!AdminPermissions.check(user.role, "inventory:read")) {
                return@get call.respond(
                    HttpStatusCode.Forbidden,
                    ErrorResponse("FORBIDDEN", "inventory:read permission required"),
                )
            }
            val warehouseId = call.request.queryParameters["warehouseId"]
            val suggestions = repo.getSuggestions(warehouseId)
            call.respond(HttpStatusCode.OK, ReplenishmentSuggestionsResponse(
                total       = suggestions.size,
                suggestions = suggestions.map { row ->
                    ReplenishmentSuggestionDto(
                        ruleId       = row.ruleId,
                        productId    = row.productId,
                        warehouseId  = row.warehouseId,
                        supplierId   = row.supplierId,
                        currentStock = row.currentStock,
                        reorderPoint = row.reorderPoint,
                        reorderQty   = row.reorderQty,
                        autoApprove  = row.autoApprove,
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

private fun ReplenishmentRuleRow.toDto() = ReplenishmentRuleDto(
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
