package com.zyntasolutions.zyntapos.api.routes

import com.zyntasolutions.zyntapos.api.auth.AdminPermissions
import com.zyntasolutions.zyntapos.api.models.ErrorResponse
import com.zyntasolutions.zyntapos.api.service.AdminAuditService
import com.zyntasolutions.zyntapos.api.service.AdminAuthService
import com.zyntasolutions.zyntapos.api.service.DiagnosticSessionService
import com.zyntasolutions.zyntapos.common.validation.validateOr422
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable
import org.koin.ktor.ext.inject
import java.util.UUID

@Serializable
private data class CreateDiagnosticSessionRequest(
    val storeId: String,
    val technicianId: String,
    val scope: String = "READ_ONLY_DIAGNOSTICS",
)

fun Route.adminDiagnosticRoutes() {
    val diagnosticService: DiagnosticSessionService by inject()
    val authService: AdminAuthService by inject()
    val auditService: AdminAuditService by inject()

    route("/admin/diagnostic/sessions") {

        // POST /admin/diagnostic/sessions — create JIT session token
        post {
            val admin = resolveAdminUser(call, authService) ?: return@post
            if (!AdminPermissions.check(admin.role, "diagnostics:access")) {
                call.respond(HttpStatusCode.Forbidden, ErrorResponse("FORBIDDEN", "Insufficient permissions"))
                return@post
            }

            val body = call.receive<CreateDiagnosticSessionRequest>()
            if (!call.validateOr422 {
                requireNotBlank("storeId", body.storeId)
                requireNotBlank("technicianId", body.technicianId)
            }) return@post

            val scope = body.scope.takeIf {
                it in setOf("READ_ONLY_DIAGNOSTICS", "FULL_READ_ONLY")
            } ?: return@post call.respond(
                HttpStatusCode.UnprocessableEntity,
                ErrorResponse("INVALID_SCOPE", "scope must be READ_ONLY_DIAGNOSTICS or FULL_READ_ONLY")
            )

            val storeId = runCatching { UUID.fromString(body.storeId) }.getOrNull()
                ?: return@post call.respond(HttpStatusCode.BadRequest, ErrorResponse("INVALID_ID", "Invalid storeId"))
            val technicianId = runCatching { UUID.fromString(body.technicianId) }.getOrNull()
                ?: return@post call.respond(HttpStatusCode.BadRequest, ErrorResponse("INVALID_ID", "Invalid technicianId"))

            val session = diagnosticService.createSession(storeId, technicianId, admin.id, scope)

            val ip = call.request.headers["X-Forwarded-For"]?.split(",")?.firstOrNull()?.trim()
                     ?: call.request.local.remoteHost
            auditService.log(
                adminId    = admin.id,
                adminName  = admin.name,
                eventType  = "DIAGNOSTIC_SESSION",
                category   = "SYSTEM",
                entityType = "diagnostic_session",
                entityId   = session.id,
                newValues  = mapOf("storeId" to body.storeId, "scope" to scope),
                ipAddress  = ip,
                success    = true,
            )
            call.respond(HttpStatusCode.Created, session)
        }

        // GET /admin/diagnostic/sessions/{storeId} — active session status for a store
        get("/{storeId}") {
            val admin = resolveAdminUser(call, authService) ?: return@get
            if (!AdminPermissions.check(admin.role, "diagnostics:read")) {
                call.respond(HttpStatusCode.Forbidden, ErrorResponse("FORBIDDEN", "Insufficient permissions"))
                return@get
            }

            val storeIdStr = call.parameters["storeId"] ?: return@get call.respond(
                HttpStatusCode.BadRequest, ErrorResponse("MISSING_ID", "storeId required"))
            val storeId = runCatching { UUID.fromString(storeIdStr) }.getOrNull()
                ?: return@get call.respond(HttpStatusCode.BadRequest, ErrorResponse("INVALID_ID", "Invalid storeId"))

            val session = diagnosticService.getActiveSession(storeId)
            if (session == null) {
                call.respond(HttpStatusCode.NotFound, ErrorResponse("NO_ACTIVE_SESSION", "No active diagnostic session for this store"))
            } else {
                call.respond(HttpStatusCode.OK, session)
            }
        }

        // DELETE /admin/diagnostic/sessions/{sessionId} — revoke session
        delete("/{sessionId}") {
            val admin = resolveAdminUser(call, authService) ?: return@delete
            if (!AdminPermissions.check(admin.role, "diagnostics:access")) {
                call.respond(HttpStatusCode.Forbidden, ErrorResponse("FORBIDDEN", "Insufficient permissions"))
                return@delete
            }

            val sessionIdStr = call.parameters["sessionId"] ?: return@delete call.respond(
                HttpStatusCode.BadRequest, ErrorResponse("MISSING_ID", "sessionId required"))
            val sessionId = runCatching { UUID.fromString(sessionIdStr) }.getOrNull()
                ?: return@delete call.respond(HttpStatusCode.BadRequest, ErrorResponse("INVALID_ID", "Invalid sessionId"))

            val revoked = diagnosticService.revokeSession(sessionId, admin.id)
            if (!revoked) {
                call.respond(HttpStatusCode.NotFound, ErrorResponse("NOT_FOUND", "Session not found or already terminal"))
                return@delete
            }

            val ip = call.request.headers["X-Forwarded-For"]?.split(",")?.firstOrNull()?.trim()
                     ?: call.request.local.remoteHost
            auditService.log(
                adminId    = admin.id,
                adminName  = admin.name,
                eventType  = "DIAGNOSTIC_SESSION_REVOKED",
                category   = "SYSTEM",
                entityType = "diagnostic_session",
                entityId   = sessionIdStr,
                ipAddress  = ip,
                success    = true,
            )
            call.respond(HttpStatusCode.NoContent)
        }
    }
}
