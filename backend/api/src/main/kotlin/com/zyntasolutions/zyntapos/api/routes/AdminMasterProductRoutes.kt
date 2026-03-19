package com.zyntasolutions.zyntapos.api.routes

import com.zyntasolutions.zyntapos.api.service.AssignToStoreRequest
import com.zyntasolutions.zyntapos.api.service.BulkAssignRequest
import com.zyntasolutions.zyntapos.api.service.CreateMasterProductRequest
import com.zyntasolutions.zyntapos.api.service.MasterProductService
import com.zyntasolutions.zyntapos.api.service.UpdateMasterProductRequest
import com.zyntasolutions.zyntapos.api.service.AdminAuthService
import com.zyntasolutions.zyntapos.common.ErrorResponse
import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.koin.ktor.ext.inject

fun Route.adminMasterProductRoutes() {
    val service: MasterProductService by inject()
    val authService: AdminAuthService by inject()

    route("/admin/master-products") {

        // GET /admin/master-products — paginated list with search
        get {
            resolveAdminUser(call, authService) ?: return@get
            val page   = call.request.queryParameters["page"]?.toIntOrNull()?.coerceAtLeast(0) ?: 0
            val size   = call.request.queryParameters["size"]?.toIntOrNull()?.coerceIn(1, 100) ?: 20
            val search = call.request.queryParameters["search"]
            call.respond(HttpStatusCode.OK, service.list(page, size, search))
        }

        // POST /admin/master-products — create new master product
        post {
            resolveAdminUser(call, authService) ?: return@post
            val body = call.receive<CreateMasterProductRequest>()
            if (body.name.isBlank()) {
                return@post call.respond(
                    HttpStatusCode.BadRequest,
                    ErrorResponse("VALIDATION_ERROR", "Product name is required")
                )
            }
            val created = service.create(body)
            call.respond(HttpStatusCode.Created, created)
        }

        // GET /admin/master-products/{id} — single product with store assignments
        get("/{id}") {
            resolveAdminUser(call, authService) ?: return@get
            val id = call.parameters["id"] ?: return@get call.respond(
                HttpStatusCode.BadRequest, ErrorResponse("MISSING_ID", "Product ID required")
            )
            val product = service.getById(id)
                ?: return@get call.respond(HttpStatusCode.NotFound, ErrorResponse("NOT_FOUND", "Master product not found"))
            call.respond(HttpStatusCode.OK, product)
        }

        // PUT /admin/master-products/{id} — update master product
        put("/{id}") {
            resolveAdminUser(call, authService) ?: return@put
            val id = call.parameters["id"] ?: return@put call.respond(
                HttpStatusCode.BadRequest, ErrorResponse("MISSING_ID", "Product ID required")
            )
            val body = call.receive<UpdateMasterProductRequest>()
            val updated = service.update(id, body)
                ?: return@put call.respond(HttpStatusCode.NotFound, ErrorResponse("NOT_FOUND", "Master product not found"))
            call.respond(HttpStatusCode.OK, updated)
        }

        // DELETE /admin/master-products/{id} — soft-delete
        delete("/{id}") {
            resolveAdminUser(call, authService) ?: return@delete
            val id = call.parameters["id"] ?: return@delete call.respond(
                HttpStatusCode.BadRequest, ErrorResponse("MISSING_ID", "Product ID required")
            )
            val deleted = service.delete(id)
            if (!deleted) return@delete call.respond(HttpStatusCode.NotFound, ErrorResponse("NOT_FOUND", "Master product not found"))
            call.respond(HttpStatusCode.OK, mapOf("deleted" to true))
        }

        // GET /admin/master-products/{id}/stores — list store assignments
        get("/{id}/stores") {
            resolveAdminUser(call, authService) ?: return@get
            val id = call.parameters["id"] ?: return@get call.respond(
                HttpStatusCode.BadRequest, ErrorResponse("MISSING_ID", "Product ID required")
            )
            val assignments = service.getStoreAssignments(id)
            call.respond(HttpStatusCode.OK, assignments)
        }

        // POST /admin/master-products/{id}/stores/{storeId} — assign to store
        post("/{id}/stores/{storeId}") {
            resolveAdminUser(call, authService) ?: return@post
            val id = call.parameters["id"] ?: return@post call.respond(
                HttpStatusCode.BadRequest, ErrorResponse("MISSING_ID", "Product ID required")
            )
            val storeId = call.parameters["storeId"] ?: return@post call.respond(
                HttpStatusCode.BadRequest, ErrorResponse("MISSING_ID", "Store ID required")
            )
            val body = call.receiveOrNull<AssignToStoreRequest>() ?: AssignToStoreRequest()
            val assigned = service.assignToStore(id, storeId, body)
            if (!assigned) return@post call.respond(HttpStatusCode.NotFound, ErrorResponse("NOT_FOUND", "Master product not found"))
            call.respond(HttpStatusCode.Created, mapOf("assigned" to true))
        }

        // DELETE /admin/master-products/{id}/stores/{storeId} — remove from store
        delete("/{id}/stores/{storeId}") {
            resolveAdminUser(call, authService) ?: return@delete
            val id = call.parameters["id"] ?: return@delete call.respond(
                HttpStatusCode.BadRequest, ErrorResponse("MISSING_ID", "Product ID required")
            )
            val storeId = call.parameters["storeId"] ?: return@delete call.respond(
                HttpStatusCode.BadRequest, ErrorResponse("MISSING_ID", "Store ID required")
            )
            val removed = service.removeFromStore(id, storeId)
            if (!removed) return@delete call.respond(HttpStatusCode.NotFound, ErrorResponse("NOT_FOUND", "Assignment not found"))
            call.respond(HttpStatusCode.OK, mapOf("removed" to true))
        }

        // PUT /admin/master-products/{id}/stores/{storeId} — update store overrides
        put("/{id}/stores/{storeId}") {
            resolveAdminUser(call, authService) ?: return@put
            val id = call.parameters["id"] ?: return@put call.respond(
                HttpStatusCode.BadRequest, ErrorResponse("MISSING_ID", "Product ID required")
            )
            val storeId = call.parameters["storeId"] ?: return@put call.respond(
                HttpStatusCode.BadRequest, ErrorResponse("MISSING_ID", "Store ID required")
            )
            val body = call.receive<AssignToStoreRequest>()
            val updated = service.updateStoreOverride(id, storeId, body)
            if (!updated) return@put call.respond(HttpStatusCode.NotFound, ErrorResponse("NOT_FOUND", "Assignment not found"))
            call.respond(HttpStatusCode.OK, mapOf("updated" to true))
        }

        // POST /admin/master-products/{id}/bulk-assign — assign to multiple stores
        post("/{id}/bulk-assign") {
            resolveAdminUser(call, authService) ?: return@post
            val id = call.parameters["id"] ?: return@post call.respond(
                HttpStatusCode.BadRequest, ErrorResponse("MISSING_ID", "Product ID required")
            )
            val body = call.receive<BulkAssignRequest>()
            if (body.storeIds.isEmpty()) {
                return@post call.respond(
                    HttpStatusCode.BadRequest,
                    ErrorResponse("VALIDATION_ERROR", "At least one store ID required")
                )
            }
            val count = service.bulkAssign(id, body)
            if (count == 0) return@post call.respond(HttpStatusCode.NotFound, ErrorResponse("NOT_FOUND", "Master product not found"))
            call.respond(HttpStatusCode.OK, mapOf("assigned_count" to count))
        }
    }
}
