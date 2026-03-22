package com.zyntasolutions.zyntapos.api.routes

import com.zyntasolutions.zyntapos.api.service.AdminTransferService
import com.zyntasolutions.zyntapos.api.service.CreateTransferRequest
import com.zyntasolutions.zyntapos.common.ErrorResponse
import io.ktor.http.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.koin.ktor.ext.inject

/**
 * POS-authenticated routes for inter-store stock transfer (IST) management (C1.3).
 *
 * Uses RS256 JWT auth with storeId claim for store isolation.
 * Roles: ADMIN, MANAGER can create/approve/dispatch/receive/cancel.
 *        CASHIER can only view transfers.
 */
fun Route.transferRoutes() {
    val service: AdminTransferService by inject()

    route("/transfers") {

        get {
            val principal = call.principal<JWTPrincipal>()!!
            val storeId = principal.payload.getClaim("storeId").asString()
            val status = call.request.queryParameters["status"]
            val page = call.request.queryParameters["page"]?.toIntOrNull()?.coerceAtLeast(0) ?: 0
            val size = call.request.queryParameters["size"]?.toIntOrNull()?.coerceIn(1, 100) ?: 20
            call.respond(HttpStatusCode.OK, service.listTransfers(storeId, status, page, size))
        }

        get("/{id}") {
            call.principal<JWTPrincipal>()!!
            val id = call.parameters["id"] ?: return@get call.respond(
                HttpStatusCode.BadRequest, ErrorResponse("MISSING_ID", "Transfer ID required")
            )
            val transfer = service.getById(id)
                ?: return@get call.respond(HttpStatusCode.NotFound, ErrorResponse("NOT_FOUND", "Transfer not found"))
            call.respond(HttpStatusCode.OK, transfer)
        }

        post {
            val principal = call.principal<JWTPrincipal>()!!
            val role = principal.payload.getClaim("role").asString()
            if (role != "ADMIN" && role != "MANAGER") {
                return@post call.respond(
                    HttpStatusCode.Forbidden,
                    ErrorResponse("FORBIDDEN", "Only ADMIN or MANAGER can create transfers")
                )
            }
            val userId = principal.payload.subject
            val body = call.receive<CreateTransferRequest>()
            if (body.sourceWarehouseId.isBlank() || body.destWarehouseId.isBlank()) {
                return@post call.respond(
                    HttpStatusCode.BadRequest,
                    ErrorResponse("VALIDATION_ERROR", "Source and destination warehouse IDs are required")
                )
            }
            if (body.productId.isBlank()) {
                return@post call.respond(
                    HttpStatusCode.BadRequest,
                    ErrorResponse("VALIDATION_ERROR", "Product ID is required")
                )
            }
            if (body.quantity <= 0.0) {
                return@post call.respond(
                    HttpStatusCode.BadRequest,
                    ErrorResponse("VALIDATION_ERROR", "Quantity must be positive")
                )
            }
            val created = service.create(body, userId)
            call.respond(HttpStatusCode.Created, created)
        }

        put("/{id}/approve") {
            val principal = call.principal<JWTPrincipal>()!!
            val role = principal.payload.getClaim("role").asString()
            if (role != "ADMIN" && role != "MANAGER") {
                return@put call.respond(
                    HttpStatusCode.Forbidden,
                    ErrorResponse("FORBIDDEN", "Only ADMIN or MANAGER can approve transfers")
                )
            }
            val userId = principal.payload.subject
            val id = call.parameters["id"] ?: return@put call.respond(
                HttpStatusCode.BadRequest, ErrorResponse("MISSING_ID", "Transfer ID required")
            )
            val updated = service.approve(id, userId)
                ?: return@put call.respond(
                    HttpStatusCode.Conflict,
                    ErrorResponse("INVALID_STATE", "Transfer not found or not in PENDING status")
                )
            call.respond(HttpStatusCode.OK, updated)
        }

        put("/{id}/dispatch") {
            val principal = call.principal<JWTPrincipal>()!!
            val role = principal.payload.getClaim("role").asString()
            if (role != "ADMIN" && role != "MANAGER") {
                return@put call.respond(
                    HttpStatusCode.Forbidden,
                    ErrorResponse("FORBIDDEN", "Only ADMIN or MANAGER can dispatch transfers")
                )
            }
            val userId = principal.payload.subject
            val id = call.parameters["id"] ?: return@put call.respond(
                HttpStatusCode.BadRequest, ErrorResponse("MISSING_ID", "Transfer ID required")
            )
            val updated = service.dispatch(id, userId)
                ?: return@put call.respond(
                    HttpStatusCode.Conflict,
                    ErrorResponse("INVALID_STATE", "Transfer not found or not in APPROVED status")
                )
            call.respond(HttpStatusCode.OK, updated)
        }

        put("/{id}/receive") {
            val principal = call.principal<JWTPrincipal>()!!
            val role = principal.payload.getClaim("role").asString()
            if (role != "ADMIN" && role != "MANAGER") {
                return@put call.respond(
                    HttpStatusCode.Forbidden,
                    ErrorResponse("FORBIDDEN", "Only ADMIN or MANAGER can receive transfers")
                )
            }
            val userId = principal.payload.subject
            val id = call.parameters["id"] ?: return@put call.respond(
                HttpStatusCode.BadRequest, ErrorResponse("MISSING_ID", "Transfer ID required")
            )
            val updated = service.receive(id, userId)
                ?: return@put call.respond(
                    HttpStatusCode.Conflict,
                    ErrorResponse("INVALID_STATE", "Transfer not found or not in IN_TRANSIT status")
                )
            call.respond(HttpStatusCode.OK, updated)
        }

        put("/{id}/cancel") {
            val principal = call.principal<JWTPrincipal>()!!
            val role = principal.payload.getClaim("role").asString()
            if (role != "ADMIN" && role != "MANAGER") {
                return@put call.respond(
                    HttpStatusCode.Forbidden,
                    ErrorResponse("FORBIDDEN", "Only ADMIN or MANAGER can cancel transfers")
                )
            }
            val userId = principal.payload.subject
            val id = call.parameters["id"] ?: return@put call.respond(
                HttpStatusCode.BadRequest, ErrorResponse("MISSING_ID", "Transfer ID required")
            )
            val updated = service.cancel(id, userId)
                ?: return@put call.respond(
                    HttpStatusCode.Conflict,
                    ErrorResponse("INVALID_STATE", "Transfer not found or is not cancellable")
                )
            call.respond(HttpStatusCode.OK, updated)
        }
    }
}
