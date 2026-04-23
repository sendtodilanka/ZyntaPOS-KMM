package com.zyntasolutions.zyntapos.api.routes

import com.zyntasolutions.zyntapos.api.auth.AdminPermissions
import com.zyntasolutions.zyntapos.api.models.*
import com.zyntasolutions.zyntapos.common.validation.validateOr422
import com.zyntasolutions.zyntapos.api.service.AdminAuthService
import com.zyntasolutions.zyntapos.api.service.AdminConfigService
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.koin.ktor.ext.inject
import java.util.UUID

fun Route.adminConfigRoutes() {
    val configService: AdminConfigService by inject()
    val authService: AdminAuthService by inject()

    route("/admin/config") {

        // ── Feature Flags ──────────────────────────────────────────────────────

        get("/feature-flags") {
            resolveAdminUser(call, authService) ?: return@get
            call.respond(HttpStatusCode.OK, configService.listFeatureFlags())
        }

        patch("/feature-flags/{key}") {
            val admin = resolveAdminUser(call, authService) ?: return@patch
            val key = call.parameters["key"] ?: return@patch call.respond(
                HttpStatusCode.BadRequest, ErrorResponse("MISSING_KEY", "Flag key required")
            )
            val body = call.receive<ToggleFeatureFlagRequest>()
            val updated = configService.toggleFeatureFlag(key, body.enabled, admin.email)
                ?: return@patch call.respond(HttpStatusCode.NotFound, ErrorResponse("NOT_FOUND", "Feature flag not found"))
            call.respond(HttpStatusCode.OK, updated)
        }

        // ── Tax Rates (G-004) ──────────────────────────────────────────────────
        // Read: ADMIN, FINANCE, AUDITOR. Write: ADMIN, FINANCE.
        // Previously guarded only by `Authenticated` which allowed every role
        // (including HELPDESK) to mutate tax rate records — now explicit.

        get("/tax-rates") {
            val admin = resolveAdminUser(call, authService) ?: return@get
            AdminPermissions.requirePermission(admin.role, "config:tax_rates:read")
            call.respond(HttpStatusCode.OK, configService.listTaxRates())
        }

        post("/tax-rates") {
            val admin = resolveAdminUser(call, authService) ?: return@post
            AdminPermissions.requirePermission(admin.role, "config:tax_rates:write")
            val body = call.receive<TaxRateCreateRequest>()

            if (!call.validateOr422 {
                requireNotBlank("name", body.name)
                requireMaxLength("name", body.name, 100)
                requireInRange("rate", body.rate, 0.0, 100.0)
            }) return@post

            val created = configService.createTaxRate(body)
            call.respond(HttpStatusCode.Created, created)
        }

        put("/tax-rates/{id}") {
            val admin = resolveAdminUser(call, authService) ?: return@put
            AdminPermissions.requirePermission(admin.role, "config:tax_rates:write")
            val id = call.parameters["id"]?.let { runCatching { UUID.fromString(it) }.getOrNull() }
                ?: return@put call.respond(HttpStatusCode.BadRequest, ErrorResponse("INVALID_ID", "Invalid tax rate ID"))

            val body = call.receive<TaxRateUpdateRequest>()
            body.rate?.let {
                if (it < 0.0 || it > 100.0) {
                    call.respond(HttpStatusCode.UnprocessableEntity,
                        ErrorResponse("INVALID_RATE", "Rate must be between 0 and 100"))
                    return@put
                }
            }

            val updated = configService.updateTaxRate(id, body)
                ?: return@put call.respond(HttpStatusCode.NotFound, ErrorResponse("NOT_FOUND", "Tax rate not found"))
            call.respond(HttpStatusCode.OK, updated)
        }

        delete("/tax-rates/{id}") {
            val admin = resolveAdminUser(call, authService) ?: return@delete
            AdminPermissions.requirePermission(admin.role, "config:tax_rates:write")
            val id = call.parameters["id"]?.let { runCatching { UUID.fromString(it) }.getOrNull() }
                ?: return@delete call.respond(HttpStatusCode.BadRequest, ErrorResponse("INVALID_ID", "Invalid tax rate ID"))

            val deleted = configService.deleteTaxRate(id)
            if (!deleted) {
                call.respond(HttpStatusCode.NotFound, ErrorResponse("NOT_FOUND", "Tax rate not found"))
            } else {
                call.respond(HttpStatusCode.NoContent)
            }
        }

        // ── System Config ──────────────────────────────────────────────────────

        get("/system") {
            resolveAdminUser(call, authService) ?: return@get
            call.respond(HttpStatusCode.OK, configService.listSystemConfig())
        }

        patch("/system/{key}") {
            val admin = resolveAdminUser(call, authService) ?: return@patch
            val key = call.parameters["key"] ?: return@patch call.respond(
                HttpStatusCode.BadRequest, ErrorResponse("MISSING_KEY", "Config key required")
            )
            val body = call.receive<UpdateSystemConfigRequest>()
            if (!call.validateOr422 { requireMaxLength("value", body.value, 1024) }) return@patch

            val updated = configService.updateSystemConfig(key, body.value, admin.email)
                ?: return@patch call.respond(HttpStatusCode.NotFound, ErrorResponse("NOT_FOUND", "Config key not found"))
            call.respond(HttpStatusCode.OK, updated)
        }
    }
}
