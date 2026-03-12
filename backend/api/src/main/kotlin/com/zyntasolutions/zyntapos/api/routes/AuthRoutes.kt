package com.zyntasolutions.zyntapos.api.routes

import com.zyntasolutions.zyntapos.api.config.AppConfig
import com.zyntasolutions.zyntapos.api.models.ErrorResponse
import com.zyntasolutions.zyntapos.api.models.LoginRequest
import com.zyntasolutions.zyntapos.api.models.LoginResponse
import com.zyntasolutions.zyntapos.api.models.RefreshRequest
import com.zyntasolutions.zyntapos.api.models.UserResponseDto
import com.zyntasolutions.zyntapos.api.service.LicenseValidationClient
import com.zyntasolutions.zyntapos.api.service.LicenseValidationResult
import com.zyntasolutions.zyntapos.api.service.UserService
import com.zyntasolutions.zyntapos.common.validation.validateOr422
import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import org.koin.ktor.ext.inject

fun Route.authRoutes() {
    val config: AppConfig by inject()
    val userService: UserService by inject()
    val licenseClient: LicenseValidationClient by inject()

    route("/auth") {

        // POST /v1/auth/login
        post("/login") {
            val request = call.receive<LoginRequest>()

            if (!call.validateOr422 {
                requireNotBlank("email", request.email)
                requireMaxLength("email", request.email, 256)
                requireNotBlank("password", request.password)
                requireLength("password", request.password, 1, 256)
                request.licenseKey?.let { requireMaxLength("license_key", it, 128) }
                request.deviceId?.let { requireMaxLength("device_id", it, 256) }
            }) return@post

            // S2-12: Validate store license before authenticating
            if (request.licenseKey != null) {
                val licenseResult = licenseClient.validate(request.licenseKey)
                if (licenseResult == LicenseValidationResult.INVALID) {
                    call.respond(
                        HttpStatusCode.Forbidden,
                        ErrorResponse(
                            code = "LICENSE_INVALID",
                            message = "Store license is expired, revoked, or not found. Contact support."
                        )
                    )
                    return@post
                }
                // UNAVAILABLE = fail-open (license service unreachable) — allow login
            }

            val user = userService.authenticate(request.email, request.password, request.licenseKey)
                ?: run {
                    call.respond(
                        HttpStatusCode.Unauthorized,
                        ErrorResponse(code = "INVALID_CREDENTIALS", message = "Invalid email or password")
                    )
                    return@post
                }

            // A3: Issue opaque refresh token stored server-side (single-use rotation)
            val ip = call.request.local.remoteAddress
            val ua = call.request.headers["User-Agent"]
            val (accessToken, refreshToken, expiresIn) = userService.issueTokens(
                user, config, request.deviceId, ip, ua
            )

            call.respond(
                HttpStatusCode.OK,
                LoginResponse(
                    accessToken  = accessToken,
                    refreshToken = refreshToken,
                    expiresIn    = expiresIn,
                    user         = UserResponseDto(
                        id        = user.id,
                        name      = user.name,
                        email     = user.email,
                        role      = user.role,
                        storeId   = user.storeId,
                        isActive  = user.isActive,
                        createdAt = user.createdAt,
                        updatedAt = user.updatedAt,
                    )
                )
            )
        }

        // POST /v1/auth/refresh
        post("/refresh") {
            val request = call.receive<RefreshRequest>()

            if (!call.validateOr422 {
                requireNotBlank("refresh_token", request.refreshToken)
                requireMaxLength("refresh_token", request.refreshToken, 4096)
            }) return@post

            val ip = call.request.local.remoteAddress
            val ua = call.request.headers["User-Agent"]
            val response = userService.refreshTokens(request.refreshToken, config, ip, ua)
                ?: run {
                    call.respond(
                        HttpStatusCode.Unauthorized,
                        ErrorResponse(code = "INVALID_TOKEN", message = "Refresh token is invalid or expired")
                    )
                    return@post
                }

            call.respond(HttpStatusCode.OK, response)
        }
    }
}
