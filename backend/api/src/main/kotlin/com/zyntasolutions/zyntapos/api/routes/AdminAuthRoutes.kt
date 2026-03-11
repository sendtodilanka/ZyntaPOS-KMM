package com.zyntasolutions.zyntapos.api.routes

import com.zyntasolutions.zyntapos.api.auth.AdminPermissions
import com.zyntasolutions.zyntapos.api.auth.AdminRole
import com.zyntasolutions.zyntapos.api.models.*
import com.zyntasolutions.zyntapos.api.service.AdminAuthResult
import com.zyntasolutions.zyntapos.api.service.AdminAuthService
import com.zyntasolutions.zyntapos.api.service.AdminAuditService
import com.zyntasolutions.zyntapos.api.service.AdminUserRow
import com.zyntasolutions.zyntapos.api.service.EmailService
import com.zyntasolutions.zyntapos.api.service.GoogleOAuthService
import com.zyntasolutions.zyntapos.api.service.MfaService
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
    val mfaService: MfaService by inject()
    val googleOAuth: GoogleOAuthService by inject()
    val auditService: AdminAuditService by inject()
    val emailService: EmailService by inject()
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
                requireLength("password", body.password, 8, AdminAuthService.MAX_PASSWORD_LENGTH)
            }) return@post

            val created = service.bootstrap(body.email, body.name, body.password)
            if (created == null) {
                call.respond(
                    HttpStatusCode.Conflict,
                    ErrorResponse("ALREADY_BOOTSTRAPPED", "An admin account already exists. Use the login page.")
                )
                return@post
            }
            emailService.sendWelcomeAdmin(body.email, body.name)
            call.respond(HttpStatusCode.Created, created.toResponse())
        }

        // POST /admin/auth/login
        post("/login") {
            val body = call.receive<AdminLoginRequest>()

            if (!call.validateOr422 {
                requireNotBlank("email", body.email)
                requireMaxLength("email", body.email, 254)
                requireNotBlank("password", body.password)
                requireMaxLength("password", body.password, AdminAuthService.MAX_PASSWORD_LENGTH)
            }) return@post

            val ip        = call.request.headers["X-Forwarded-For"]?.split(",")?.firstOrNull()?.trim()
                         ?: call.request.local.remoteHost
            val userAgent = call.request.headers[HttpHeaders.UserAgent]

            when (val result = service.login(body.email, body.password, ip, userAgent)) {
                is AdminAuthResult.InvalidCredentials -> {
                    auditService.log(
                        adminId    = null,
                        adminName  = body.email,
                        eventType  = "ADMIN_LOGIN_FAILED",
                        category   = "AUTH",
                        entityType = "admin_user",
                        entityId   = body.email,
                        ipAddress  = ip,
                        userAgent  = userAgent,
                        success    = false,
                        errorMessage = "Invalid credentials",
                    )
                    call.respond(HttpStatusCode.Unauthorized,
                        ErrorResponse("INVALID_CREDENTIALS", "Invalid email or password"))
                }
                is AdminAuthResult.AccountLocked -> {
                    auditService.log(
                        adminId    = null,
                        adminName  = body.email,
                        eventType  = "ADMIN_LOGIN_FAILED",
                        category   = "AUTH",
                        entityType = "admin_user",
                        entityId   = body.email,
                        ipAddress  = ip,
                        userAgent  = userAgent,
                        success    = false,
                        errorMessage = "Account locked",
                    )
                    call.respond(HttpStatusCode.TooManyRequests,
                        ErrorResponse("ACCOUNT_LOCKED", "Account temporarily locked due to too many failed attempts"))
                }
                is AdminAuthResult.AccountInactive -> call.respond(
                    HttpStatusCode.Forbidden,
                    ErrorResponse("ACCOUNT_INACTIVE", "Account is deactivated")
                )
                is AdminAuthResult.MfaRequired -> {
                    call.respond(HttpStatusCode.OK, MfaPendingResponse(pendingToken = result.pendingToken))
                }
                is AdminAuthResult.Success -> {
                    auditService.log(
                        adminId    = result.user.id,
                        adminName  = result.user.name,
                        eventType  = "ADMIN_LOGIN",
                        category   = "AUTH",
                        entityType = "admin_user",
                        entityId   = result.user.id.toString(),
                        newValues  = mapOf("role" to result.user.role.name),
                        ipAddress  = ip,
                        userAgent  = userAgent,
                        success    = true,
                    )
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
            val user = runCatching { resolveAdminUser(call, service) }.getOrNull()
            val rawRefresh = call.request.cookies[REFRESH_COOKIE]
            if (rawRefresh != null) service.logout(rawRefresh)
            clearAuthCookies(call)
            if (user != null) {
                auditService.log(
                    adminId    = user.id,
                    adminName  = user.name,
                    eventType  = "ADMIN_LOGOUT",
                    category   = "AUTH",
                    entityType = "admin_user",
                    entityId   = user.id.toString(),
                    ipAddress  = call.request.headers["X-Forwarded-For"]?.split(",")?.firstOrNull()?.trim()
                                  ?: call.request.local.remoteHost,
                    success    = true,
                )
            }
            call.respond(HttpStatusCode.NoContent)
        }

        // GET /admin/auth/me — returns current user from access token cookie
        get("/me") {
            val user = resolveAdminUser(call, service) ?: return@get
            call.respond(HttpStatusCode.OK, user.toResponse())
        }

        // ── MFA endpoints ────────────────────────────────────────────────────

        // POST /admin/auth/mfa/setup — generate TOTP secret + QR code + backup codes
        post("/mfa/setup") {
            val user = resolveAdminUser(call, service) ?: return@post
            val setup = mfaService.generateSetup(user.email)
            val backupCodes = mfaService.generateBackupCodes(user.id)
            call.respond(HttpStatusCode.OK, MfaSetupResponse(
                secret      = setup.secret,
                qrCodeUrl   = setup.qrCodeUrl,
                backupCodes = backupCodes
            ))
        }

        // POST /admin/auth/mfa/enable — verify code then persist secret + enable MFA
        post("/mfa/enable") {
            val user = resolveAdminUser(call, service) ?: return@post
            val body = call.receive<MfaEnableRequest>()

            if (!mfaService.verifyTotp(body.secret, body.code)) {
                call.respond(HttpStatusCode.UnprocessableEntity,
                    ErrorResponse("INVALID_TOTP", "Invalid TOTP code"))
                return@post
            }

            mfaService.enableMfa(user.id, body.secret)
            auditService.log(
                adminId    = user.id,
                adminName  = user.name,
                eventType  = "ADMIN_MFA_ENABLED",
                category   = "AUTH",
                entityType = "admin_user",
                entityId   = user.id.toString(),
                ipAddress  = call.request.headers["X-Forwarded-For"]?.split(",")?.firstOrNull()?.trim()
                              ?: call.request.local.remoteHost,
                success    = true,
            )
            call.respond(HttpStatusCode.OK, user.copy(/* reflect mfaEnabled=true */).toResponse().copy(mfaEnabled = true))
        }

        // POST /admin/auth/mfa/disable — verify code then disable MFA
        post("/mfa/disable") {
            val user = resolveAdminUser(call, service) ?: return@post
            val body = call.receive<MfaDisableRequest>()

            val secret = mfaService.getMfaSecret(user.id)
            val valid = if (secret != null) mfaService.verifyTotp(secret, body.code) else false

            if (!valid) {
                call.respond(HttpStatusCode.UnprocessableEntity,
                    ErrorResponse("INVALID_TOTP", "Invalid TOTP code"))
                return@post
            }

            mfaService.disableMfa(user.id)
            auditService.log(
                adminId    = user.id,
                adminName  = user.name,
                eventType  = "ADMIN_MFA_DISABLED",
                category   = "AUTH",
                entityType = "admin_user",
                entityId   = user.id.toString(),
                ipAddress  = call.request.headers["X-Forwarded-For"]?.split(",")?.firstOrNull()?.trim()
                              ?: call.request.local.remoteHost,
                success    = true,
            )
            call.respond(HttpStatusCode.NoContent)
        }

        // POST /admin/auth/mfa/verify — complete login after MFA check
        post("/mfa/verify") {
            val body = call.receive<MfaVerifyRequest>()

            val userId = service.verifyMfaPendingToken(body.pendingToken) ?: run {
                call.respond(HttpStatusCode.Unauthorized,
                    ErrorResponse("INVALID_PENDING_TOKEN", "MFA session expired or invalid"))
                return@post
            }

            val user = service.findById(userId) ?: run {
                call.respond(HttpStatusCode.Unauthorized,
                    ErrorResponse("USER_NOT_FOUND", "User not found"))
                return@post
            }

            val secret = mfaService.getMfaSecret(userId)
            val validTotp   = secret != null && mfaService.verifyTotp(secret, body.code)
            val validBackup = !validTotp && mfaService.verifyBackupCode(userId, body.code)

            if (!validTotp && !validBackup) {
                call.respond(HttpStatusCode.UnprocessableEntity,
                    ErrorResponse("INVALID_TOTP", "Invalid TOTP or backup code"))
                return@post
            }

            val ip        = call.request.headers["X-Forwarded-For"]?.split(",")?.firstOrNull()?.trim()
                         ?: call.request.local.remoteHost
            val userAgent = call.request.headers[HttpHeaders.UserAgent]

            val (_, tokens) = service.completeMfaLogin(body.pendingToken, ip, userAgent)
                ?: run {
                    call.respond(HttpStatusCode.Unauthorized, ErrorResponse("SESSION_ERROR", "Could not complete login"))
                    return@post
                }

            val accessTtlSec  = config.adminAccessTokenTtlMs / 1000
            val refreshTtlSec = config.adminRefreshTokenTtlDays * 86_400L
            setAuthCookies(call, tokens.first, tokens.second, accessTtlSec, refreshTtlSec)
            call.respond(HttpStatusCode.OK, AdminLoginResponse(user = user.toResponse(), expiresIn = accessTtlSec))
        }

        // ── Google OAuth endpoints ───────────────────────────────────────────

        // GET /admin/auth/google — redirect to Google consent screen
        get("/google") {
            if (config.googleClientId.isBlank()) {
                call.respond(HttpStatusCode.NotImplemented, ErrorResponse("OAUTH_NOT_CONFIGURED", "Google OAuth is not configured"))
                return@get
            }
            val state = UUID.randomUUID().toString()
            // In production, store state in Redis with 5-min TTL for CSRF validation
            call.respondRedirect(googleOAuth.buildAuthUrl(state))
        }

        // GET /admin/auth/google/callback?code=&state=
        get("/google/callback") {
            val panelUrl = config.adminPanelUrl

            val code = call.request.queryParameters["code"]
                ?: run {
                    call.respondRedirect("$panelUrl/login?error=google_auth_failed")
                    return@get
                }

            val userInfo = googleOAuth.exchangeCodeForUser(code)
                ?: run {
                    call.respondRedirect("$panelUrl/login?error=google_auth_failed")
                    return@get
                }

            val adminUser = googleOAuth.findOrCreateUser(userInfo)
                ?: run {
                    call.respondRedirect("$panelUrl/login?error=domain_not_allowed")
                    return@get
                }

            if (!adminUser.isActive) {
                call.respondRedirect("$panelUrl/login?error=account_inactive")
                return@get
            }

            val ip        = call.request.headers["X-Forwarded-For"]?.split(",")?.firstOrNull()?.trim()
                         ?: call.request.local.remoteHost
            val userAgent = call.request.headers[HttpHeaders.UserAgent]

            // If MFA is enabled, issue pending token and redirect to login MFA step
            if (adminUser.mfaEnabled) {
                val pendingToken = service.issueMfaPendingToken(adminUser)
                call.response.cookies.append(Cookie(
                    name     = "admin_mfa_pending",
                    value    = pendingToken,
                    maxAge   = 120,
                    path     = "/",
                    httpOnly = true,
                    secure   = true,
                    extensions = mapOf("SameSite" to "Lax")
                ))
                call.respondRedirect("$panelUrl/login?mfa=required")
                return@get
            }

            val tokens = service.issueTokensForUser(adminUser, ip, userAgent)
            val accessTtlSec  = config.adminAccessTokenTtlMs / 1000
            val refreshTtlSec = config.adminRefreshTokenTtlDays * 86_400L
            setAuthCookies(call, tokens.first, tokens.second, accessTtlSec, refreshTtlSec)
            call.respondRedirect("$panelUrl/")
        }

        // POST /admin/auth/forgot-password — request password reset email
        // Always returns 202 to prevent email enumeration
        post("/forgot-password") {
            val body = call.receive<AdminForgotPasswordRequest>()
            if (!call.validateOr422 {
                requireNotBlank("email", body.email)
                requireMaxLength("email", body.email, 254)
            }) return@post

            val resetToken = service.generatePasswordResetToken(body.email)
            if (resetToken != null) {
                val resetLink = "${config.adminPanelUrl}/reset-password?token=$resetToken"
                emailService.sendPasswordReset(body.email, resetLink)
            }
            // Always 202 — never reveal whether the email exists
            call.respond(HttpStatusCode.Accepted)
        }

        // POST /admin/auth/reset-password — consume token and set new password
        post("/reset-password") {
            val body = call.receive<AdminResetPasswordRequest>()
            if (!call.validateOr422 {
                requireNotBlank("token", body.token)
                requireNotBlank("newPassword", body.newPassword)
                requireLength("newPassword", body.newPassword, 8, AdminAuthService.MAX_PASSWORD_LENGTH)
            }) return@post

            val success = service.resetPassword(body.token, body.newPassword)
            if (!success) {
                call.respond(
                    HttpStatusCode.UnprocessableEntity,
                    ErrorResponse("INVALID_OR_EXPIRED_TOKEN", "Password reset token is invalid or has expired")
                )
                return@post
            }
            call.respond(HttpStatusCode.NoContent)
        }
    }

    // ── Admin user management (ADMIN role only) ────────────────────────────
    route("/admin/users") {

        // GET /admin/users?page=&size=&search=&role=&isActive=
        get {
            val user = resolveAdminUser(call, service) ?: return@get
            AdminPermissions.requirePermission(user.role, "users:read")

            val page     = call.request.queryParameters["page"]?.toIntOrNull()?.coerceAtLeast(1) ?: 1
            val size     = call.request.queryParameters["size"]?.toIntOrNull()?.coerceIn(1, 100) ?: 20
            val search   = call.request.queryParameters["search"]?.takeIf { it.isNotBlank() }
            val roleParam = call.request.queryParameters["role"]?.let { AdminRole.fromString(it) }
            val isActive = call.request.queryParameters["isActive"]?.toBooleanStrictOrNull()

            call.respond(HttpStatusCode.OK, service.listUsers(page, size, search, roleParam, isActive))
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
                requireLength("password", body.password, 8, AdminAuthService.MAX_PASSWORD_LENGTH)
                requireNotBlank("role", body.role)
            }) return@post

            val role = AdminRole.fromString(body.role) ?: run {
                call.respond(HttpStatusCode.UnprocessableEntity, ErrorResponse("INVALID_ROLE", "Unknown role: ${body.role}"))
                return@post
            }

            if (service.emailExists(body.email)) {
                call.respond(HttpStatusCode.Conflict, ErrorResponse("EMAIL_TAKEN", "An account with this email already exists"))
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

        // GET /admin/users/{id}/sessions — list active sessions for a user
        get("/{id}/sessions") {
            val user = resolveAdminUser(call, service) ?: return@get
            AdminPermissions.requirePermission(user.role, "users:read")

            val targetId = call.parameters["id"]
                ?.let { runCatching { UUID.fromString(it) }.getOrNull() }
                ?: run {
                    call.respond(HttpStatusCode.BadRequest, ErrorResponse("INVALID_ID", "Invalid user ID"))
                    return@get
                }
            val sessions = service.listActiveSessions(targetId).map { s ->
                AdminSessionResponse(
                    id        = s.id.toString(),
                    userAgent = s.userAgent,
                    ipAddress = s.ipAddress,
                    createdAt = s.createdAt,
                    expiresAt = s.expiresAt
                )
            }
            call.respond(HttpStatusCode.OK, sessions)
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

            // G3: Audit session revocation
            auditService.log(
                adminId    = user.id,
                adminName  = user.name,
                eventType  = "ADMIN_SESSIONS_REVOKED",
                category   = "AUTH",
                entityType = "admin_user",
                entityId   = targetId.toString(),
                ipAddress  = call.request.headers["X-Forwarded-For"]?.split(",")?.firstOrNull()?.trim()
                              ?: call.request.local.remoteHost,
                success    = true
            )
            call.respond(HttpStatusCode.NoContent)
        }
    }

    // POST /admin/auth/change-password — change own password
    route("/admin/auth") {
        post("/change-password") {
            val user = resolveAdminUser(call, service) ?: return@post
            val body = call.receive<AdminChangePasswordRequest>()

            if (!call.validateOr422 {
                requireNotBlank("currentPassword", body.currentPassword)
                requireNotBlank("newPassword", body.newPassword)
                requireLength("newPassword", body.newPassword, 8, AdminAuthService.MAX_PASSWORD_LENGTH)
            }) return@post

            val changed = service.changePassword(user.id, body.currentPassword, body.newPassword)
            if (!changed) {
                call.respond(
                    HttpStatusCode.UnprocessableEntity,
                    ErrorResponse("INVALID_CURRENT_PASSWORD", "Current password is incorrect")
                )
                return@post
            }

            // G3: Audit password change
            val ip = call.request.headers["X-Forwarded-For"]?.split(",")?.firstOrNull()?.trim()
                     ?: call.request.local.remoteHost
            auditService.log(
                adminId    = user.id,
                adminName  = user.name,
                eventType  = "ADMIN_PASSWORD_CHANGED",
                category   = "AUTH",
                entityType = "admin_user",
                entityId   = user.id.toString(),
                ipAddress  = ip,
                success    = true
            )
            call.respond(HttpStatusCode.NoContent)
        }
    }
}

// ── Private helpers ──────────────────────────────────────────────────────────

internal suspend fun resolveAdminUser(call: ApplicationCall, service: AdminAuthService): AdminUserRow? {
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

internal fun clearAuthCookies(call: ApplicationCall) {
    call.response.cookies.append(Cookie(
        name       = ACCESS_COOKIE,
        value      = "",
        maxAge     = 0,
        path       = "/",
        httpOnly   = true,
        secure     = true,
        extensions = mapOf("SameSite" to "Strict"),
    ))
    call.response.cookies.append(Cookie(
        name       = REFRESH_COOKIE,
        value      = "",
        maxAge     = 0,
        path       = "/admin/auth",
        httpOnly   = true,
        secure     = true,
        extensions = mapOf("SameSite" to "Strict"),
    ))
}
