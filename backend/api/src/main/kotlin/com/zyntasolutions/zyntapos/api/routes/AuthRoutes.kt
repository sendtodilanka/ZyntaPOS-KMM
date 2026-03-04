package com.zyntasolutions.zyntapos.api.routes

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.zyntasolutions.zyntapos.api.config.AppConfig
import com.zyntasolutions.zyntapos.api.models.ErrorResponse
import com.zyntasolutions.zyntapos.api.models.LoginRequest
import com.zyntasolutions.zyntapos.api.models.LoginResponse
import com.zyntasolutions.zyntapos.api.models.RefreshRequest
import com.zyntasolutions.zyntapos.api.service.UserService
import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import org.koin.ktor.ext.inject
import java.security.interfaces.RSAPrivateKey
import java.security.interfaces.RSAPublicKey
import java.util.Date

fun Route.authRoutes() {
    val config: AppConfig by inject()
    val userService: UserService by inject()

    route("/auth") {
        post("/login") {
            val request = call.receive<LoginRequest>()

            // Input validation
            require(request.licenseKey.isNotBlank()) { "License key is required" }
            require(request.deviceId.isNotBlank()) { "Device ID is required" }
            require(request.username.isNotBlank()) { "Username is required" }
            require(request.password.isNotBlank()) { "Password is required" }

            val user = userService.authenticate(request.licenseKey, request.deviceId, request.username, request.password)
                ?: run {
                    call.respond(
                        HttpStatusCode.Unauthorized,
                        ErrorResponse(code = "INVALID_CREDENTIALS", message = "Invalid username or password")
                    )
                    return@post
                }

            val now = System.currentTimeMillis()
            val accessExpiry = Date(now + config.accessTokenTtlMs)
            val refreshExpiry = Date(now + config.refreshTokenTtlMs)

            val algorithm = Algorithm.RSA256(
                config.jwtPublicKey as RSAPublicKey,
                config.jwtPrivateKey as RSAPrivateKey
            )

            val accessToken = JWT.create()
                .withIssuer(config.jwtIssuer)
                .withAudience(config.jwtAudience)
                .withSubject(user.id)
                .withClaim("role", user.role)
                .withClaim("storeId", user.storeId)
                .withClaim("type", "access")
                .withExpiresAt(accessExpiry)
                .sign(algorithm)

            val refreshToken = JWT.create()
                .withIssuer(config.jwtIssuer)
                .withAudience(config.jwtAudience)
                .withSubject(user.id)
                .withClaim("type", "refresh")
                .withExpiresAt(refreshExpiry)
                .sign(algorithm)

            call.respond(
                HttpStatusCode.OK,
                LoginResponse(
                    accessToken = accessToken,
                    refreshToken = refreshToken,
                    expiresIn = config.accessTokenTtlMs / 1000,
                    tokenType = "Bearer",
                    userId = user.id,
                    role = user.role,
                    storeId = user.storeId
                )
            )
        }

        post("/refresh") {
            val request = call.receive<RefreshRequest>()
            require(request.refreshToken.isNotBlank()) { "Refresh token is required" }

            val newTokens = userService.refreshTokens(request.refreshToken, config)
                ?: run {
                    call.respond(
                        HttpStatusCode.Unauthorized,
                        ErrorResponse(code = "INVALID_TOKEN", message = "Refresh token is invalid or expired")
                    )
                    return@post
                }

            call.respond(HttpStatusCode.OK, newTokens)
        }
    }
}
