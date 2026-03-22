package com.zyntasolutions.zyntapos.api.routes

import com.zyntasolutions.zyntapos.api.auth.AdminPermissions
import com.zyntasolutions.zyntapos.api.repository.ReplenishmentRepository
import com.zyntasolutions.zyntapos.api.service.AdminAuthService
import com.zyntasolutions.zyntapos.common.ErrorResponse
import io.ktor.http.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.koin.ktor.ext.inject

/**
 * Admin panel routes for replenishment monitoring (C1.5).
 *
 * Per ADR-009: Admin panel provides READ-ONLY monitoring of replenishment rules and suggestions.
 * Write operations (create, update, delete rules) are exclusively available via
 * POS JWT-authenticated endpoints at /v1/replenishment (ReplenishmentRoutes.kt).
 *
 * Endpoints:
 *   GET /admin/replenishment/rules       — list all rules (filter: warehouseId)
 *   GET /admin/replenishment/suggestions  — products at or below reorder point
 */
fun Route.adminReplenishmentRoutes() {
    val repo: ReplenishmentRepository by inject()
    val authService: AdminAuthService by inject()

    route("/admin/replenishment") {

        /**
         * GET /admin/replenishment/rules
         *
         * Returns all replenishment rules (read-only monitoring).
         * Optional query params: warehouseId (filter to a single warehouse).
         * Roles: ADMIN, OPERATOR, FINANCE (inventory:read)
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
         * GET /admin/replenishment/suggestions
         *
         * Returns products whose current warehouse stock is at or below the
         * configured reorder point for their active replenishment rule (read-only monitoring).
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
