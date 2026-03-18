package com.zyntasolutions.zyntapos.license.routes

import com.zyntasolutions.zyntapos.common.validation.validateOr422
import com.zyntasolutions.zyntapos.license.models.ActivateRequest
import com.zyntasolutions.zyntapos.license.models.ErrorResponse
import com.zyntasolutions.zyntapos.license.models.HeartbeatRequest
import com.zyntasolutions.zyntapos.license.service.LicenseService
import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import org.koin.ktor.ext.inject

private val LICENSE_KEY_PATTERN = Regex("^[A-Za-z0-9\\-]{1,128}$")

fun Route.licenseRoutes() {
    val licenseService: LicenseService by inject()

    route("/license") {
        post("/activate") {
            val request = call.receive<ActivateRequest>()

            if (!call.validateOr422 {
                requireNotBlank("licenseKey", request.licenseKey)
                requireMaxLength("licenseKey", request.licenseKey, 128)
                requirePattern("licenseKey", request.licenseKey, LICENSE_KEY_PATTERN, "Invalid license key format")
                requireNotBlank("deviceId", request.deviceId)
                requireMaxLength("deviceId", request.deviceId, 256)
                requireNotBlank("appVersion", request.appVersion)
                requireMaxLength("appVersion", request.appVersion, 64)
            }) return@post

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

        post("/heartbeat") {
            val request = call.receive<HeartbeatRequest>()

            if (!call.validateOr422 {
                requireNotBlank("licenseKey", request.licenseKey)
                requireMaxLength("licenseKey", request.licenseKey, 128)
                requirePattern("licenseKey", request.licenseKey, LICENSE_KEY_PATTERN, "Invalid license key format")
                requireNotBlank("deviceId", request.deviceId)
                requireMaxLength("deviceId", request.deviceId, 256)
                requireNonNegative("dbSizeBytes", request.dbSizeBytes)
                requireNonNegative("syncQueueDepth", request.syncQueueDepth)
                requireNonNegative("lastErrorCount", request.lastErrorCount)
                requireNonNegative("uptimeHours", request.uptimeHours)
                if (request.nonce != null) requireMaxLength("nonce", request.nonce, 128)
            }) return@post

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

        get("/{key}") {
            val key = call.parameters["key"] ?: run {
                call.respond(HttpStatusCode.BadRequest, ErrorResponse("MISSING_KEY", "License key required"))
                return@get
            }

            if (!call.validateOr422 {
                requireMaxLength("key", key, 128)
                requirePattern("key", key, LICENSE_KEY_PATTERN, "Invalid license key format")
            }) return@get

            val status = licenseService.getStatus(key)
            if (status == null) {
                call.respond(HttpStatusCode.NotFound, ErrorResponse("LICENSE_NOT_FOUND", "License key not found"))
            } else {
                call.respond(HttpStatusCode.OK, status)
            }
        }
    }
}
