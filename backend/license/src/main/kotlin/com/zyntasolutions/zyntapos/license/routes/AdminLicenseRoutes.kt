package com.zyntasolutions.zyntapos.license.routes

import com.zyntasolutions.zyntapos.common.validation.validateOr422
import com.zyntasolutions.zyntapos.license.auth.AdminJwtValidator
import com.zyntasolutions.zyntapos.license.models.AdminCreateLicenseRequest
import com.zyntasolutions.zyntapos.license.models.AdminUpdateLicenseRequest
import com.zyntasolutions.zyntapos.license.models.ErrorResponse
import com.zyntasolutions.zyntapos.license.service.AdminLicenseService
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.koin.ktor.ext.inject

private const val ACCESS_COOKIE = "admin_access_token"

fun Route.adminLicenseRoutes() {
    val service: AdminLicenseService by inject()
    val validator: AdminJwtValidator by inject()

    route("/admin/licenses") {

        // GET /admin/licenses — paginated list
        get {
            call.requireAdmin(validator) ?: return@get
            val page   = call.request.queryParameters["page"]?.toIntOrNull()?.coerceAtLeast(0) ?: 0
            val size   = call.request.queryParameters["size"]?.toIntOrNull()?.coerceIn(1, 100) ?: 20
            val status = call.request.queryParameters["status"]
            val edition= call.request.queryParameters["edition"]
            val search = call.request.queryParameters["search"]

            call.respond(HttpStatusCode.OK, service.listLicenses(page, size, status, edition, search))
        }

        // GET /admin/licenses/stats
        get("/stats") {
            call.requireAdmin(validator) ?: return@get
            call.respond(HttpStatusCode.OK, service.getStats())
        }

        // POST /admin/licenses — create new license
        post {
            call.requireAdmin(validator) ?: return@post
            val body = call.receive<AdminCreateLicenseRequest>()

            if (!call.validateOr422 {
                requireNotBlank("customerId", body.customerId)
                requireMaxLength("customerId", body.customerId, 256)
                requireNotBlank("edition", body.edition)
                requireInRange("maxDevices", body.maxDevices, 1, 100)
            }) return@post

            val validEditions = setOf("STARTER", "PROFESSIONAL", "ENTERPRISE")
            if (body.edition.uppercase() !in validEditions) {
                call.respond(
                    HttpStatusCode.UnprocessableEntity,
                    ErrorResponse("INVALID_EDITION", "Edition must be one of: ${validEditions.joinToString()}")
                )
                return@post
            }

            val license = service.createLicense(body)
            call.respond(HttpStatusCode.Created, license)
        }

        // GET /admin/licenses/{key} — single license with devices
        get("/{key}") {
            call.requireAdmin(validator) ?: return@get
            val key = call.parameters["key"] ?: return@get call.respond(
                HttpStatusCode.BadRequest, ErrorResponse("MISSING_KEY", "License key required")
            )
            val result = service.getLicense(key)
                ?: return@get call.respond(HttpStatusCode.NotFound, ErrorResponse("NOT_FOUND", "License not found"))
            call.respond(HttpStatusCode.OK, result)
        }

        // PUT /admin/licenses/{key} — update (extend, change edition, change status)
        put("/{key}") {
            call.requireAdmin(validator) ?: return@put
            val key = call.parameters["key"] ?: return@put call.respond(
                HttpStatusCode.BadRequest, ErrorResponse("MISSING_KEY", "License key required")
            )
            val body = call.receive<AdminUpdateLicenseRequest>()
            body.maxDevices?.let {
                if (it < 1) {
                    call.respond(HttpStatusCode.UnprocessableEntity, ErrorResponse("INVALID_MAX_DEVICES", "maxDevices must be >= 1"))
                    return@put
                }
            }

            val updated = service.updateLicense(key, body)
                ?: return@put call.respond(HttpStatusCode.NotFound, ErrorResponse("NOT_FOUND", "License not found"))
            call.respond(HttpStatusCode.OK, updated)
        }

        // DELETE /admin/licenses/{key} — revoke (soft delete)
        delete("/{key}") {
            call.requireAdmin(validator) ?: return@delete
            val key = call.parameters["key"] ?: return@delete call.respond(
                HttpStatusCode.BadRequest, ErrorResponse("MISSING_KEY", "License key required")
            )
            val revoked = service.revokeLicense(key)
            if (!revoked) {
                call.respond(HttpStatusCode.NotFound, ErrorResponse("NOT_FOUND", "License not found"))
            } else {
                call.respond(HttpStatusCode.NoContent)
            }
        }

        // GET /admin/licenses/{key}/devices
        get("/{key}/devices") {
            call.requireAdmin(validator) ?: return@get
            val key = call.parameters["key"] ?: return@get call.respond(
                HttpStatusCode.BadRequest, ErrorResponse("MISSING_KEY", "License key required")
            )
            val devices = service.getDevices(key)
                ?: return@get call.respond(HttpStatusCode.NotFound, ErrorResponse("NOT_FOUND", "License not found"))
            call.respond(HttpStatusCode.OK, devices)
        }

        // DELETE /admin/licenses/{key}/devices/{deviceId} — deregister
        delete("/{key}/devices/{deviceId}") {
            call.requireAdmin(validator) ?: return@delete
            val key = call.parameters["key"] ?: return@delete call.respond(
                HttpStatusCode.BadRequest, ErrorResponse("MISSING_KEY", "License key required")
            )
            val deviceId = call.parameters["deviceId"] ?: return@delete call.respond(
                HttpStatusCode.BadRequest, ErrorResponse("MISSING_DEVICE_ID", "Device ID required")
            )
            val removed = service.deregisterDevice(key, deviceId)
            if (!removed) {
                call.respond(HttpStatusCode.NotFound, ErrorResponse("NOT_FOUND", "Device not found"))
            } else {
                call.respond(HttpStatusCode.NoContent)
            }
        }
    }
}

// ── Private helpers ──────────────────────────────────────────────────────────

private suspend fun ApplicationCall.requireAdmin(validator: AdminJwtValidator): Unit? {
    val token = request.cookies[ACCESS_COOKIE] ?: run {
        respond(HttpStatusCode.Unauthorized, ErrorResponse("NOT_AUTHENTICATED", "Not authenticated"))
        return null
    }
    validator.verify(token) ?: run {
        respond(HttpStatusCode.Unauthorized, ErrorResponse("INVALID_TOKEN", "Token expired or invalid"))
        return null
    }
    return Unit
}

