package com.zyntasolutions.zyntapos.api.routes

import com.zyntasolutions.zyntapos.api.auth.AdminPermissions
import com.zyntasolutions.zyntapos.api.repository.PricingRuleRepository
import com.zyntasolutions.zyntapos.api.service.AdminAuthService
import com.zyntasolutions.zyntapos.common.ErrorResponse
import io.ktor.http.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.koin.ktor.ext.inject

/**
 * Admin panel routes for pricing rule monitoring (C2.1 Region-Based Pricing).
 *
 * Per ADR-009: Admin panel provides READ-ONLY monitoring of pricing rules.
 * Write operations (create, update, delete rules) are exclusively available via
 * POS JWT-authenticated endpoints at /v1/pricing (PricingRoutes.kt).
 *
 * Endpoints:
 *   GET /admin/pricing/rules — list rules (filter: productId, storeId)
 */
fun Route.adminPricingRoutes() {
    val repo: PricingRuleRepository by inject()
    val authService: AdminAuthService by inject()

    route("/admin/pricing") {

        /**
         * GET /admin/pricing/rules?productId=X&storeId=Y
         *
         * Returns pricing rules (read-only monitoring), optionally filtered by product and/or store.
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
            val productId = call.request.queryParameters["productId"]
            val storeId = call.request.queryParameters["storeId"]
            val rules = repo.getRules(productId, storeId)
            call.respond(HttpStatusCode.OK, PricingRulesResponse(
                total = rules.size,
                rules = rules,
            ))
        }
    }
}
