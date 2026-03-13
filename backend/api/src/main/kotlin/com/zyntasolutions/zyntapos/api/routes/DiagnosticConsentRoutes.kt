package com.zyntasolutions.zyntapos.api.routes

import com.zyntasolutions.zyntapos.api.db.Stores
import com.zyntasolutions.zyntapos.api.models.ErrorResponse
import com.zyntasolutions.zyntapos.api.service.AdminAuditService
import com.zyntasolutions.zyntapos.api.service.DiagnosticSessionService
import com.zyntasolutions.zyntapos.common.validation.validateOr422
import io.ktor.http.HttpStatusCode
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.auth.principal
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import kotlinx.serialization.Serializable
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.koin.ktor.ext.inject
import java.util.UUID

@Serializable
private data class ConsentGrantRequest(
    val sessionId: String,
    /** Sent as a string by the KMM client (epoch milliseconds). */
    val grantedAtMs: String,
)

@Serializable
private data class ConsentRevokeRequest(
    val sessionId: String,
)

/**
 * POS-side diagnostic consent endpoints (JWT RS256, role-gated to ADMIN).
 *
 * These routes close the consent handshake started by the admin panel
 * (POST /admin/diagnostic/sessions). A store ADMIN user grants or revokes
 * access for the active PENDING_CONSENT session that belongs to their store.
 *
 * Security properties:
 *  - Role check: only POS role=ADMIN may approve/revoke remote diagnostic access.
 *  - Store ownership: sessionId is cross-checked against the JWT storeId claim to
 *    prevent cross-store authorization bypass.
 *  - Store liveness: storeId is validated against the DB (S2-10 defense-in-depth).
 *  - Audit trail: every grant/revoke is recorded in the hash-chained audit log.
 */
fun Route.diagnosticConsentRoutes() {
    val diagnosticService: DiagnosticSessionService by inject()
    val auditService: AdminAuditService by inject()

    route("/diagnostic/consent") {

        // POST /v1/diagnostic/consent/grant
        // POS store ADMIN grants consent for a PENDING_CONSENT diagnostic session.
        post("/grant") {
            val principal = call.principal<JWTPrincipal>()!!
            val userId  = principal.payload.subject
            val storeId = principal.payload.getClaim("storeId")?.asString().orEmpty()
            val role    = principal.payload.getClaim("role")?.asString().orEmpty()

            if (role != "ADMIN") {
                call.respond(
                    HttpStatusCode.Forbidden,
                    ErrorResponse("FORBIDDEN", "Only store administrators may grant diagnostic consent"),
                )
                return@post
            }

            val storeUUID = runCatching { UUID.fromString(storeId) }.getOrNull()
                ?: return@post call.respond(
                    HttpStatusCode.Unauthorized,
                    ErrorResponse("INVALID_STORE", "Invalid storeId claim in token"),
                )

            // S2-10: Validate storeId exists and is active (defense-in-depth against tampered JWTs)
            if (!verifyPosStoreActive(storeId)) {
                call.respond(
                    HttpStatusCode.Forbidden,
                    ErrorResponse("INVALID_STORE", "Store not found or inactive"),
                )
                return@post
            }

            val body = call.receive<ConsentGrantRequest>()
            if (!call.validateOr422 {
                requireNotBlank("sessionId", body.sessionId)
                requireNotBlank("grantedAtMs", body.grantedAtMs)
            }) return@post

            val sessionId = runCatching { UUID.fromString(body.sessionId) }.getOrNull()
                ?: return@post call.respond(
                    HttpStatusCode.BadRequest,
                    ErrorResponse("INVALID_ID", "Invalid sessionId format"),
                )

            val grantedAtMs = body.grantedAtMs.toLongOrNull()
                ?: return@post call.respond(
                    HttpStatusCode.UnprocessableEntity,
                    ErrorResponse("INVALID_TIMESTAMP", "grantedAtMs must be epoch milliseconds"),
                )

            // Cross-check: the session must belong to the JWT's storeId to prevent
            // a POS device from granting consent for another store's diagnostic session.
            val existing = diagnosticService.getActiveSession(storeUUID)
            if (existing == null || existing.id != body.sessionId) {
                call.respond(
                    HttpStatusCode.NotFound,
                    ErrorResponse("SESSION_NOT_FOUND", "No pending diagnostic session found for this store"),
                )
                return@post
            }

            if (existing.status != "PENDING_CONSENT") {
                call.respond(
                    HttpStatusCode.Conflict,
                    ErrorResponse("INVALID_STATE", "Session is not awaiting consent (current status: ${existing.status})"),
                )
                return@post
            }

            val activated = diagnosticService.activateSession(sessionId, grantedAtMs)
            if (activated == null) {
                call.respond(
                    HttpStatusCode.Gone,
                    ErrorResponse("SESSION_EXPIRED", "Diagnostic session has expired and cannot be activated"),
                )
                return@post
            }

            val ip = call.request.headers["X-Forwarded-For"]?.split(",")?.firstOrNull()?.trim()
                ?: call.request.local.remoteHost
            auditService.log(
                adminId    = null,
                adminName  = "pos:$userId",
                eventType  = "DIAGNOSTIC_SESSION_CONSENT_GRANTED",
                category   = "SYSTEM",
                entityType = "diagnostic_session",
                entityId   = body.sessionId,
                newValues  = mapOf(
                    "grantedByUserId" to userId,
                    "storeId"         to storeId,
                    "status"          to "ACTIVE",
                ),
                ipAddress  = ip,
                success    = true,
            )
            call.respond(HttpStatusCode.OK, activated)
        }

        // POST /v1/diagnostic/consent/revoke
        // POS store ADMIN revokes an active or pending diagnostic session.
        post("/revoke") {
            val principal = call.principal<JWTPrincipal>()!!
            val userId  = principal.payload.subject
            val storeId = principal.payload.getClaim("storeId")?.asString().orEmpty()
            val role    = principal.payload.getClaim("role")?.asString().orEmpty()

            if (role != "ADMIN") {
                call.respond(
                    HttpStatusCode.Forbidden,
                    ErrorResponse("FORBIDDEN", "Only store administrators may revoke diagnostic consent"),
                )
                return@post
            }

            val storeUUID = runCatching { UUID.fromString(storeId) }.getOrNull()
                ?: return@post call.respond(
                    HttpStatusCode.Unauthorized,
                    ErrorResponse("INVALID_STORE", "Invalid storeId claim in token"),
                )

            if (!verifyPosStoreActive(storeId)) {
                call.respond(
                    HttpStatusCode.Forbidden,
                    ErrorResponse("INVALID_STORE", "Store not found or inactive"),
                )
                return@post
            }

            val body = call.receive<ConsentRevokeRequest>()
            if (!call.validateOr422 {
                requireNotBlank("sessionId", body.sessionId)
            }) return@post

            val sessionId = runCatching { UUID.fromString(body.sessionId) }.getOrNull()
                ?: return@post call.respond(
                    HttpStatusCode.BadRequest,
                    ErrorResponse("INVALID_ID", "Invalid sessionId format"),
                )

            // Cross-check: the session must belong to the JWT's storeId
            val existing = diagnosticService.getActiveSession(storeUUID)
            if (existing == null || existing.id != body.sessionId) {
                call.respond(
                    HttpStatusCode.NotFound,
                    ErrorResponse("SESSION_NOT_FOUND", "No active diagnostic session found for this store"),
                )
                return@post
            }

            val revokedByUUID = runCatching { UUID.fromString(userId) }.getOrNull()
                ?: return@post call.respond(
                    HttpStatusCode.Unauthorized,
                    ErrorResponse("INVALID_USER", "Invalid sub claim in token"),
                )

            val revoked = diagnosticService.revokeSession(sessionId, revokedByUUID)
            if (!revoked) {
                call.respond(
                    HttpStatusCode.NotFound,
                    ErrorResponse("NOT_FOUND", "Session not found or already in terminal state"),
                )
                return@post
            }

            val ip = call.request.headers["X-Forwarded-For"]?.split(",")?.firstOrNull()?.trim()
                ?: call.request.local.remoteHost
            auditService.log(
                adminId    = null,
                adminName  = "pos:$userId",
                eventType  = "DIAGNOSTIC_SESSION_REVOKED",
                category   = "SYSTEM",
                entityType = "diagnostic_session",
                entityId   = body.sessionId,
                newValues  = mapOf(
                    "revokedByUserId" to userId,
                    "storeId"         to storeId,
                    "status"          to "REVOKED",
                ),
                ipAddress  = ip,
                success    = true,
            )
            call.respond(HttpStatusCode.NoContent)
        }
    }
}

/** S2-10: Verifies the storeId from the JWT maps to an existing, active store. */
private fun verifyPosStoreActive(storeId: String): Boolean = transaction {
    Stores.selectAll()
        .where { (Stores.id eq storeId) and (Stores.isActive eq true) }
        .count() > 0
}
