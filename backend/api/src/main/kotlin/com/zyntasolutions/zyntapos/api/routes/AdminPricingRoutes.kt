package com.zyntasolutions.zyntapos.api.routes

import com.zyntasolutions.zyntapos.api.auth.AdminPermissions
import com.zyntasolutions.zyntapos.api.repository.PricingRuleRepository
import com.zyntasolutions.zyntapos.api.repository.PricingRuleRow
import com.zyntasolutions.zyntapos.api.service.AdminAuthService
import com.zyntasolutions.zyntapos.common.ErrorResponse
import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable
import org.koin.ktor.ext.inject
import java.time.OffsetDateTime
import java.time.format.DateTimeParseException

/**
 * Admin panel routes for pricing rule management (C2.1 Region-Based Pricing).
 *
 * Endpoints:
 *   GET    /admin/pricing/rules          — list rules (filter: productId, storeId)
 *   POST   /admin/pricing/rules          — create or update a pricing rule
 *   DELETE /admin/pricing/rules/{id}     — delete a pricing rule
 */
fun Route.adminPricingRoutes() {
    val repo: PricingRuleRepository by inject()
    val authService: AdminAuthService by inject()

    route("/admin/pricing") {

        /**
         * GET /admin/pricing/rules?productId=X&storeId=Y
         *
         * Returns pricing rules, optionally filtered by product and/or store.
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

        /**
         * POST /admin/pricing/rules
         *
         * Creates or updates a pricing rule.
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
            val body = call.receive<UpsertPricingRuleRequest>()

            if (body.productId.isBlank()) {
                return@post call.respond(
                    HttpStatusCode.BadRequest,
                    ErrorResponse("VALIDATION_ERROR", "productId is required"),
                )
            }
            if (body.price < 0) {
                return@post call.respond(
                    HttpStatusCode.BadRequest,
                    ErrorResponse("VALIDATION_ERROR", "price must be non-negative"),
                )
            }

            val validFrom = body.validFrom?.let {
                try { OffsetDateTime.parse(it) } catch (_: DateTimeParseException) {
                    return@post call.respond(
                        HttpStatusCode.BadRequest,
                        ErrorResponse("VALIDATION_ERROR", "validFrom must be ISO-8601 format"),
                    )
                }
            }
            val validTo = body.validTo?.let {
                try { OffsetDateTime.parse(it) } catch (_: DateTimeParseException) {
                    return@post call.respond(
                        HttpStatusCode.BadRequest,
                        ErrorResponse("VALIDATION_ERROR", "validTo must be ISO-8601 format"),
                    )
                }
            }

            val rule = repo.upsertRule(
                id = body.id,
                productId = body.productId,
                storeId = body.storeId,
                price = body.price,
                costPrice = body.costPrice,
                priority = body.priority,
                validFrom = validFrom,
                validTo = validTo,
                isActive = body.isActive,
                description = body.description,
            )
            call.respond(HttpStatusCode.OK, rule)
        }

        /**
         * DELETE /admin/pricing/rules/{id}
         *
         * Deletes a pricing rule by ID.
         * Roles: ADMIN, OPERATOR (inventory:write)
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
                HttpStatusCode.BadRequest,
                ErrorResponse("VALIDATION_ERROR", "id path parameter required"),
            )
            val deleted = repo.deleteRule(id)
            if (deleted) {
                call.respond(HttpStatusCode.NoContent)
            } else {
                call.respond(
                    HttpStatusCode.NotFound,
                    ErrorResponse("NOT_FOUND", "Pricing rule not found: $id"),
                )
            }
        }
    }
}

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
