package com.zyntasolutions.zyntapos.api.routes

import com.zyntasolutions.zyntapos.api.service.AdminAuthService
import com.zyntasolutions.zyntapos.api.service.AdminTransferService
import com.zyntasolutions.zyntapos.api.service.ApproveTransferRequest
import com.zyntasolutions.zyntapos.api.service.CreateTransferRequest
import com.zyntasolutions.zyntapos.api.service.DispatchTransferRequest
import com.zyntasolutions.zyntapos.api.service.ReceiveTransferRequest
import com.zyntasolutions.zyntapos.common.ErrorResponse
import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.koin.ktor.ext.inject

/**
 * Admin panel routes for inter-store stock transfer (IST) management (C1.3).
 *
 * Endpoints:
 *   GET    /admin/transfers                    — list all transfers (filterable by storeId, status)
 *   POST   /admin/transfers                    — create a new transfer in PENDING status
 *   GET    /admin/transfers/{id}               — get transfer detail
 *   PUT    /admin/transfers/{id}/approve       — approve a PENDING transfer (PENDING → APPROVED)
 *   PUT    /admin/transfers/{id}/dispatch      — dispatch an APPROVED transfer (APPROVED → IN_TRANSIT)
 *   PUT    /admin/transfers/{id}/receive       — receive an IN_TRANSIT transfer (IN_TRANSIT → RECEIVED)
 *   PUT    /admin/transfers/{id}/cancel        — cancel a PENDING or APPROVED transfer
 */
fun Route.adminTransferRoutes() {
    val service: AdminTransferService by inject()
    val authService: AdminAuthService by inject()

    route("/admin/transfers") {

        // GET /admin/transfers — paginated list with optional storeId and status filters
        get {
            resolveAdminUser(call, authService) ?: return@get
            val storeId = call.request.queryParameters["storeId"]
            val status  = call.request.queryParameters["status"]
            val page    = call.request.queryParameters["page"]?.toIntOrNull()?.coerceAtLeast(0) ?: 0
            val size    = call.request.queryParameters["size"]?.toIntOrNull()?.coerceIn(1, 100) ?: 20
            call.respond(HttpStatusCode.OK, service.listTransfers(storeId, status, page, size))
        }

        // POST /admin/transfers — create a new transfer (PENDING)
        post {
            val admin = resolveAdminUser(call, authService) ?: return@post
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
            val created = service.create(body, admin.email)
            call.respond(HttpStatusCode.Created, created)
        }

        // GET /admin/transfers/{id} — get single transfer
        get("/{id}") {
            resolveAdminUser(call, authService) ?: return@get
            val id = call.parameters["id"] ?: return@get call.respond(
                HttpStatusCode.BadRequest, ErrorResponse("MISSING_ID", "Transfer ID required")
            )
            val transfer = service.getById(id)
                ?: return@get call.respond(HttpStatusCode.NotFound, ErrorResponse("NOT_FOUND", "Transfer not found"))
            call.respond(HttpStatusCode.OK, transfer)
        }

        // PUT /admin/transfers/{id}/approve — PENDING → APPROVED
        put("/{id}/approve") {
            val admin = resolveAdminUser(call, authService) ?: return@put
            val id = call.parameters["id"] ?: return@put call.respond(
                HttpStatusCode.BadRequest, ErrorResponse("MISSING_ID", "Transfer ID required")
            )
            val body = runCatching { call.receive<ApproveTransferRequest>() }.getOrNull()
            val approvedBy = body?.approvedBy?.takeIf { it.isNotBlank() } ?: admin.email
            val updated = service.approve(id, approvedBy)
                ?: return@put call.respond(
                    HttpStatusCode.Conflict,
                    ErrorResponse("INVALID_STATE", "Transfer not found or not in PENDING status")
                )
            call.respond(HttpStatusCode.OK, updated)
        }

        // PUT /admin/transfers/{id}/dispatch — APPROVED → IN_TRANSIT
        put("/{id}/dispatch") {
            val admin = resolveAdminUser(call, authService) ?: return@put
            val id = call.parameters["id"] ?: return@put call.respond(
                HttpStatusCode.BadRequest, ErrorResponse("MISSING_ID", "Transfer ID required")
            )
            val body = runCatching { call.receive<DispatchTransferRequest>() }.getOrNull()
            val dispatchedBy = body?.dispatchedBy?.takeIf { it.isNotBlank() } ?: admin.email
            val updated = service.dispatch(id, dispatchedBy)
                ?: return@put call.respond(
                    HttpStatusCode.Conflict,
                    ErrorResponse("INVALID_STATE", "Transfer not found or not in APPROVED status")
                )
            call.respond(HttpStatusCode.OK, updated)
        }

        // PUT /admin/transfers/{id}/receive — IN_TRANSIT → RECEIVED
        put("/{id}/receive") {
            val admin = resolveAdminUser(call, authService) ?: return@put
            val id = call.parameters["id"] ?: return@put call.respond(
                HttpStatusCode.BadRequest, ErrorResponse("MISSING_ID", "Transfer ID required")
            )
            val body = runCatching { call.receive<ReceiveTransferRequest>() }.getOrNull()
            val receivedBy = body?.receivedBy?.takeIf { it.isNotBlank() } ?: admin.email
            val updated = service.receive(id, receivedBy)
                ?: return@put call.respond(
                    HttpStatusCode.Conflict,
                    ErrorResponse("INVALID_STATE", "Transfer not found or not in IN_TRANSIT status")
                )
            call.respond(HttpStatusCode.OK, updated)
        }

        // PUT /admin/transfers/{id}/cancel — PENDING|APPROVED → CANCELLED
        put("/{id}/cancel") {
            val admin = resolveAdminUser(call, authService) ?: return@put
            val id = call.parameters["id"] ?: return@put call.respond(
                HttpStatusCode.BadRequest, ErrorResponse("MISSING_ID", "Transfer ID required")
            )
            val updated = service.cancel(id, admin.email)
                ?: return@put call.respond(
                    HttpStatusCode.Conflict,
                    ErrorResponse("INVALID_STATE", "Transfer not found or is not cancellable (must be PENDING or APPROVED)")
                )
            call.respond(HttpStatusCode.OK, updated)
        }
    }
}
