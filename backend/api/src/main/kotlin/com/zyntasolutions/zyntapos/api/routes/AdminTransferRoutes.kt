package com.zyntasolutions.zyntapos.api.routes

import com.zyntasolutions.zyntapos.api.service.AdminAuthService
import com.zyntasolutions.zyntapos.api.service.AdminTransferService
import com.zyntasolutions.zyntapos.common.ErrorResponse
import io.ktor.http.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.koin.ktor.ext.inject

/**
 * Admin panel routes for inter-store stock transfer monitoring (C1.3).
 *
 * Per ADR-009: Admin panel provides READ-ONLY monitoring of store transfers.
 * Write operations (create, approve, dispatch, receive, cancel) are exclusively
 * available via POS JWT-authenticated endpoints at /v1/transfers (TransferRoutes.kt).
 *
 * Endpoints:
 *   GET /admin/transfers      — list all transfers (filterable by storeId, status)
 *   GET /admin/transfers/{id} — get transfer detail
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
    }
}
