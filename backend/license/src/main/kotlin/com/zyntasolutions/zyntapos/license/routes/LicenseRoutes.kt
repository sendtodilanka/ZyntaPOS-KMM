package com.zyntasolutions.zyntapos.license.routes

import com.zyntasolutions.zyntapos.license.models.ActivateRequest
import com.zyntasolutions.zyntapos.license.models.ActivateResponse
import com.zyntasolutions.zyntapos.license.models.ErrorResponse
import com.zyntasolutions.zyntapos.license.models.HeartbeatRequest
import com.zyntasolutions.zyntapos.license.models.HeartbeatResponse
import com.zyntasolutions.zyntapos.license.models.LicenseStatusResponse
import com.zyntasolutions.zyntapos.license.service.LicenseService
import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import org.koin.ktor.ext.inject

fun Route.licenseRoutes() {
    val licenseService: LicenseService by inject()

    route("/license") {
        // Activate a new device under a license key
        post("/activate") {
            val request = call.receive<ActivateRequest>()
            require(request.licenseKey.isNotBlank()) { "License key is required" }
            require(request.deviceId.isNotBlank()) { "Device ID is required" }
            require(request.appVersion.isNotBlank()) { "App version is required" }

            val result = licenseService.activate(request)
            when {
                result == null -> call.respond(
                    HttpStatusCode.NotFound,
                    ErrorResponse("LICENSE_NOT_FOUND", "License key not found")
                )
                !result.isValid -> call.respond(
                    HttpStatusCode.Forbidden,
                    ErrorResponse(result.errorCode ?: "LICENSE_INVALID", result.message ?: "License is not valid")
                )
                else -> call.respond(HttpStatusCode.OK, result)
            }
        }

        // Periodic heartbeat — POS app calls this every 24 hours
        post("/heartbeat") {
            val request = call.receive<HeartbeatRequest>()
            require(request.licenseKey.isNotBlank()) { "License key is required" }
            require(request.deviceId.isNotBlank()) { "Device ID is required" }

            val result = licenseService.heartbeat(request)
            if (result == null) {
                call.respond(
                    HttpStatusCode.Unauthorized,
                    ErrorResponse("LICENSE_INVALID", "License key or device not registered")
                )
            } else {
                call.respond(HttpStatusCode.OK, result)
            }
        }

        // Query license status (used by panel and admin)
        get("/{key}") {
            val key = call.parameters["key"] ?: run {
                call.respond(HttpStatusCode.BadRequest, ErrorResponse("MISSING_KEY", "License key required"))
                return@get
            }
            val status = licenseService.getStatus(key)
            if (status == null) {
                call.respond(HttpStatusCode.NotFound, ErrorResponse("LICENSE_NOT_FOUND", "License key not found"))
            } else {
                call.respond(HttpStatusCode.OK, status)
            }
        }
    }
}
