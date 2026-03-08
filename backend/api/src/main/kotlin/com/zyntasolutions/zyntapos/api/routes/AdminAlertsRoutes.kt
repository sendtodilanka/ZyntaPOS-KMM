package com.zyntasolutions.zyntapos.api.routes

import com.zyntasolutions.zyntapos.api.models.ErrorResponse
import com.zyntasolutions.zyntapos.api.models.SilenceRequest
import com.zyntasolutions.zyntapos.api.models.ToggleAlertRuleRequest
import com.zyntasolutions.zyntapos.api.service.AdminAlertsService
import com.zyntasolutions.zyntapos.api.service.AdminAuthService
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.koin.ktor.ext.inject
import java.util.UUID

fun Route.adminAlertsRoutes() {
    val alertsService: AdminAlertsService by inject()
    val authService: AdminAuthService by inject()

    route("/admin/alerts") {

        get {
            resolveAdminUser(call, authService) ?: return@get
            val page     = call.request.queryParameters["page"]?.toIntOrNull()?.coerceAtLeast(0) ?: 0
            val pageSize = call.request.queryParameters["pageSize"]?.toIntOrNull()?.coerceIn(1, 100) ?: 20
            val status   = call.request.queryParameters["status"]
            val severity = call.request.queryParameters["severity"]
            val category = call.request.queryParameters["category"]
            val storeId  = call.request.queryParameters["storeId"]
            call.respond(HttpStatusCode.OK, alertsService.listAlerts(page, pageSize, status, severity, category, storeId))
        }

        get("/counts") {
            resolveAdminUser(call, authService) ?: return@get
            call.respond(HttpStatusCode.OK, alertsService.getCounts())
        }

        get("/rules") {
            resolveAdminUser(call, authService) ?: return@get
            call.respond(HttpStatusCode.OK, alertsService.listRules())
        }

        post("/{id}/acknowledge") {
            val admin = resolveAdminUser(call, authService) ?: return@post
            val alertId = call.parameters["id"]?.let { runCatching { UUID.fromString(it) }.getOrNull() }
                ?: return@post call.respond(HttpStatusCode.BadRequest, ErrorResponse("INVALID_ID", "Invalid alert ID"))

            val updated = alertsService.acknowledgeAlert(alertId, admin.id)
                ?: return@post call.respond(HttpStatusCode.NotFound, ErrorResponse("NOT_FOUND", "Alert not found"))
            call.respond(HttpStatusCode.OK, updated)
        }

        post("/{id}/resolve") {
            resolveAdminUser(call, authService) ?: return@post
            val alertId = call.parameters["id"]?.let { runCatching { UUID.fromString(it) }.getOrNull() }
                ?: return@post call.respond(HttpStatusCode.BadRequest, ErrorResponse("INVALID_ID", "Invalid alert ID"))

            val updated = alertsService.resolveAlert(alertId)
                ?: return@post call.respond(HttpStatusCode.NotFound, ErrorResponse("NOT_FOUND", "Alert not found"))
            call.respond(HttpStatusCode.OK, updated)
        }

        post("/{id}/silence") {
            resolveAdminUser(call, authService) ?: return@post
            val alertId = call.parameters["id"]?.let { runCatching { UUID.fromString(it) }.getOrNull() }
                ?: return@post call.respond(HttpStatusCode.BadRequest, ErrorResponse("INVALID_ID", "Invalid alert ID"))

            val body = call.receive<SilenceRequest>()
            if (body.durationMinutes < 1 || body.durationMinutes > 10_080) {
                call.respond(HttpStatusCode.UnprocessableEntity,
                    ErrorResponse("INVALID_DURATION", "durationMinutes must be between 1 and 10080"))
                return@post
            }

            val updated = alertsService.silenceAlert(alertId, body.durationMinutes)
                ?: return@post call.respond(HttpStatusCode.NotFound, ErrorResponse("NOT_FOUND", "Alert not found"))
            call.respond(HttpStatusCode.OK, updated)
        }

        patch("/rules/{id}") {
            resolveAdminUser(call, authService) ?: return@patch
            val ruleId = call.parameters["id"]?.let { runCatching { UUID.fromString(it) }.getOrNull() }
                ?: return@patch call.respond(HttpStatusCode.BadRequest, ErrorResponse("INVALID_ID", "Invalid rule ID"))

            val body = call.receive<ToggleAlertRuleRequest>()
            val updated = alertsService.toggleRule(ruleId, body.enabled)
                ?: return@patch call.respond(HttpStatusCode.NotFound, ErrorResponse("NOT_FOUND", "Rule not found"))
            call.respond(HttpStatusCode.OK, updated)
        }
    }
}
