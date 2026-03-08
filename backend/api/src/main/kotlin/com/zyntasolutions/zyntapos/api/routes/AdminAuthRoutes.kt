package com.zyntasolutions.zyntapos.api.routes

import com.zyntasolutions.zyntapos.api.auth.AdminPermissions
import com.zyntasolutions.zyntapos.api.auth.AdminRole
import com.zyntasolutions.zyntapos.api.models.*
import com.zyntasolutions.zyntapos.api.service.AdminAuthResult
import com.zyntasolutions.zyntapos.api.service.AdminAuthService
import com.zyntasolutions.zyntapos.api.service.AdminUserRow
import com.zyntasolutions.zyntapos.api.service.toResponse
import com.zyntasolutions.zyntapos.api.config.AppConfig
import com.zyntasolutions.zyntapos.common.validation.validateOr422
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.koin.ktor.ext.inject
import java.util.UUID

private const val ACCESS_COOKIE  = "admin_access_token"
private const val REFRESH_COOKIE = "admin_refresh_token"

fun Route.adminAuthRoutes() {
    val service: AdminAuthService by inject()
    val config: AppConfig by inject()

    route("/admin/auth") {

        // GET /admin/auth/status — returns whether first-run bootstrap is needed
        get("/status") {
            call.respond(HttpStatusCode.OK, AdminStatusResponse(needsBootstrap = service.needsBootstrap()))
        }

        // POST /admin/auth/bootstrap — creates first ADMIN; 409 if any admin already exists
        post("/bootstrap") {
            val body = call.receive<AdminBootstrapRequest>()

            if (!call.validateOr422 {
                requireNotBlank("email", body.email)
                requireMaxLength("email", body.email, 254)
                requireNotBlank("name", body.name)
                requireNotBlank("password", body.password)
                requireLength("password", body.password, 8, 256)
            }) return@post

            val created = service.bootstrap(body.email, body.name, body.password)
            if (created == null) {
                call.respond(
                    HttpStatusCode.Conflict,
                    ErrorResponse("ALREADY_BOOTSTRAPPED", "An admin account already exists. Use the login page.")
                )
                return@post
            }
            call.respond(HttpStatusCode.Created, created.toResponse())
        }

        // POST /admin/auth/login
        post("/login") {
            val body = call.receive<AdminLoginRequest>()

            if (!call.validateOr422 {
                requireNotBlank("email", body.email)
                requireMaxLength("email", body.email, 254)
                requireNotBlank("password", body.password)
                requireMaxLength("password", body.password, 256)
            }) return@post

            val ip        = call.request.headers["X-Forwarded-For"]?.split(",")?.firstOrNull()?.trim()
                         ?: call.request.local.remoteHost
            val userAgent = call.request.headers[HttpHeaders.UserAgent]

            when (val result = service.login(body.email, body.password, ip, userAgent)) {
                is AdminAuthResult.InvalidCredentials -> call.respond(
                    HttpStatusCode.Unauthorized,
                    ErrorResponse("INVALID_CREDENTIALS", "Invalid email or password")
                )
                is AdminAuthResult.AccountLocked -> call.respond(
                    HttpStatusCode.TooManyRequests,
                    ErrorResponse("ACCOUNT_LOCKED", "Account temporarily locked due to too many failed attempts")
                )
                is AdminAuthResult.AccountInactive -> call.respond(
                    HttpStatusCode.Forbidden,
                    ErrorResponse("ACCOUNT_INACTIVE", "Account is deactivated")
                )
                is AdminAuthResult.Success -> {
                    val accessTtlSec  = config.adminAccessTokenTtlMs / 1000
                    val refreshTtlSec = config.adminRefreshTokenTtlDays * 86_400L
                    setAuthCookies(call, result.accessToken, result.refreshToken, accessTtlSec, refreshTtlSec)
                    call.respond(
                        HttpStatusCode.OK,
                        AdminLoginResponse(
                            user      = result.user.toResponse(),
                            expiresIn = accessTtlSec
                        )
                    )
                }
            }
        }

        // POST /admin/auth/refresh
        post("/refresh") {
            val rawRefresh = call.request.cookies[REFRESH_COOKIE]
                ?: run {
                    call.respond(HttpStatusCode.Unauthorized, ErrorResponse("NO_REFRESH_TOKEN", "No refresh token"))
                    return@post
                }

            val ip        = call.request.headers["X-Forwarded-For"]?.split(",")?.firstOrNull()?.trim()
                         ?: call.request.local.remoteHost
            val userAgent = call.request.headers[HttpHeaders.UserAgent]

            val tokens = service.refresh(rawRefresh, ip, userAgent)
                ?: run {
                    clearAuthCookies(call)
                    call.respond(HttpStatusCode.Unauthorized, ErrorResponse("INVALID_REFRESH_TOKEN", "Refresh token is invalid or expired"))
                    return@post
                }

            val accessTtlSec  = config.adminAccessTokenTtlMs / 1000
            val refreshTtlSec = config.adminRefreshTokenTtlDays * 86_400L
            setAuthCookies(call, tokens.first, tokens.second, accessTtlSec, refreshTtlSec)
            call.respond(HttpStatusCode.NoContent)
        }

        // POST /admin/auth/logout
        post("/logout") {
            val rawRefresh = call.request.cookies[REFRESH_COOKIE]
            if (rawRefresh != null) service.logout(rawRefresh)
            clearAuthCookies(call)
            call.respond(HttpStatusCode.NoContent)
        }

        // GET /admin/auth/me — returns current user from access token cookie
        get("/me") {
            val user = resolveAdminUser(call, service) ?: return@get
            call.respond(HttpStatusCode.OK, user.toResponse())
        }
    }

    // ── Admin user management (ADMIN role only) ────────────────────────────
    route("/admin/users") {

        // GET /admin/users
        get {
            val user = resolveAdminUser(call, service) ?: return@get
            AdminPermissions.requirePermission(user.role, "users:read")
            call.respond(HttpStatusCode.OK, service.listUsers().map { it.toResponse() })
        }

        // POST /admin/users
        post {
            val user = resolveAdminUser(call, service) ?: return@post
            AdminPermissions.requirePermission(user.role, "users:write")

            val body = call.receive<AdminCreateUserRequest>()
            if (!call.validateOr422 {
                requireNotBlank("email", body.email)
                requireMaxLength("email", body.email, 254)
                requireNotBlank("name", body.name)
                requireNotBlank("password", body.password)
                requireLength("password", body.password, 8, 256)
                requireNotBlank("role", body.role)
            }) return@post

            val role = AdminRole.fromString(body.role) ?: run {
                call.respond(HttpStatusCode.UnprocessableEntity, ErrorResponse("INVALID_ROLE", "Unknown role: ${body.role}"))
                return@post
            }

            val created = service.createUser(body.email, body.name, role, body.password)
            call.respond(HttpStatusCode.Created, created.toResponse())
        }

        // PATCH /admin/users/{id}
        patch("/{id}") {
            val user = resolveAdminUser(call, service) ?: return@patch
            AdminPermissions.requirePermission(user.role, "users:write")

            val targetId = call.parameters["id"]
                ?.let { runCatching { UUID.fromString(it) }.getOrNull() }
                ?: run {
                    call.respond(HttpStatusCode.BadRequest, ErrorResponse("INVALID_ID", "Invalid user ID"))
                    return@patch
                }

            val body = call.receive<AdminUpdateUserRequest>()
            val role = body.role?.let {
                AdminRole.fromString(it) ?: run {
                    call.respond(HttpStatusCode.UnprocessableEntity, ErrorResponse("INVALID_ROLE", "Unknown role: $it"))
                    return@patch
                }
            }

            val updated = service.updateUser(targetId, body.name, role, body.isActive) ?: run {
                call.respond(HttpStatusCode.NotFound, ErrorResponse("NOT_FOUND", "User not found"))
                return@patch
            }
            call.respond(HttpStatusCode.OK, updated.toResponse())
        }

        // DELETE /admin/users/{id}/sessions
        delete("/{id}/sessions") {
            val user = resolveAdminUser(call, service) ?: return@delete
            AdminPermissions.requirePermission(user.role, "users:sessions:revoke")

            val targetId = call.parameters["id"]
                ?.let { runCatching { UUID.fromString(it) }.getOrNull() }
                ?: run {
                    call.respond(HttpStatusCode.BadRequest, ErrorResponse("INVALID_ID", "Invalid user ID"))
                    return@delete
                }
            service.revokeAllSessions(targetId)
            call.respond(HttpStatusCode.NoContent)
        }
    }
}

// ── Private helpers ──────────────────────────────────────────────────────────

private suspend fun resolveAdminUser(call: ApplicationCall, service: AdminAuthService): AdminUserRow? {
    val token = call.request.cookies[ACCESS_COOKIE] ?: run {
        call.respond(HttpStatusCode.Unauthorized, ErrorResponse("NOT_AUTHENTICATED", "Not authenticated"))
        return null
    }
    val userId = service.verifyAccessToken(token) ?: run {
        clearAuthCookies(call)
        call.respond(HttpStatusCode.Unauthorized, ErrorResponse("INVALID_TOKEN", "Token expired or invalid"))
        return null
    }
    return service.findById(userId) ?: run {
        clearAuthCookies(call)
        call.respond(HttpStatusCode.Unauthorized, ErrorResponse("USER_NOT_FOUND", "User no longer exists"))
        null
    }
}

private fun setAuthCookies(
    call: ApplicationCall,
    accessToken: String,
    refreshToken: String,
    accessTtlSec: Long,
    refreshTtlSec: Long
) {
    call.response.cookies.append(
        Cookie(
            name       = ACCESS_COOKIE,
            value      = accessToken,
            maxAge     = accessTtlSec.toInt(),
            path       = "/",
            httpOnly   = true,
            secure     = true,
            extensions = mapOf("SameSite" to "Strict")
        )
    )
    call.response.cookies.append(
        Cookie(
            name       = REFRESH_COOKIE,
            value      = refreshToken,
            maxAge     = refreshTtlSec.toInt(),
            path       = "/admin/auth",  // Narrow path — only sent to refresh endpoint
            httpOnly   = true,
            secure     = true,
            extensions = mapOf("SameSite" to "Strict")
        )
    )
}

private fun clearAuthCookies(call: ApplicationCall) {
    call.response.cookies.append(Cookie(ACCESS_COOKIE,  "", maxAge = 0, path = "/"))
    call.response.cookies.append(Cookie(REFRESH_COOKIE, "", maxAge = 0, path = "/admin/auth"))
}
