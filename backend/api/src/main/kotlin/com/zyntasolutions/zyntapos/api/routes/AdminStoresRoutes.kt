package com.zyntasolutions.zyntapos.api.routes

import com.zyntasolutions.zyntapos.api.models.AdminUpdateStoreConfigRequest
import com.zyntasolutions.zyntapos.api.models.ErrorResponse
import com.zyntasolutions.zyntapos.api.service.AdminAuthService
import com.zyntasolutions.zyntapos.api.service.AdminStoresService
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.koin.ktor.ext.inject

fun Route.adminStoresRoutes() {
    val storesService: AdminStoresService by inject()
    val authService: AdminAuthService by inject()

    route("/admin/stores") {

        get {
            resolveAdminUser(call, authService) ?: return@get
            val page   = call.request.queryParameters["page"]?.toIntOrNull()?.coerceAtLeast(0) ?: 0
            val size   = call.request.queryParameters["size"]?.toIntOrNull()?.coerceIn(1, 100) ?: 20
            val search = call.request.queryParameters["search"]
            val status = call.request.queryParameters["status"]
            call.respond(HttpStatusCode.OK, storesService.listStores(page, size, search, status))
        }

        get("/{storeId}") {
            resolveAdminUser(call, authService) ?: return@get
            val storeId = call.parameters["storeId"] ?: return@get call.respond(
                HttpStatusCode.BadRequest, ErrorResponse("MISSING_ID", "Store ID required")
            )
            val store = storesService.getStore(storeId)
                ?: return@get call.respond(HttpStatusCode.NotFound, ErrorResponse("NOT_FOUND", "Store not found"))
            call.respond(HttpStatusCode.OK, store)
        }

        get("/{storeId}/health") {
            resolveAdminUser(call, authService) ?: return@get
            val storeId = call.parameters["storeId"] ?: return@get call.respond(
                HttpStatusCode.BadRequest, ErrorResponse("MISSING_ID", "Store ID required")
            )
            val health = storesService.getStoreHealth(storeId)
                ?: return@get call.respond(HttpStatusCode.NotFound, ErrorResponse("NOT_FOUND", "Store not found"))
            call.respond(HttpStatusCode.OK, health)
        }

        put("/{storeId}/config") {
            resolveAdminUser(call, authService) ?: return@put
            val storeId = call.parameters["storeId"] ?: return@put call.respond(
                HttpStatusCode.BadRequest, ErrorResponse("MISSING_ID", "Store ID required")
            )
            val body = call.receive<AdminUpdateStoreConfigRequest>()
            val updated = storesService.updateStoreConfig(storeId, body)
                ?: return@put call.respond(HttpStatusCode.NotFound, ErrorResponse("NOT_FOUND", "Store not found"))
            call.respond(HttpStatusCode.OK, updated)
        }
    }
}
