package com.zyntasolutions.zyntapos.api.plugins

import io.ktor.http.HttpMethod
import io.ktor.server.application.ApplicationCallPipeline
import io.ktor.server.application.application
import io.ktor.server.application.call
import io.ktor.server.application.createRouteScopedPlugin
import io.ktor.server.application.install
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.RouteSelector
import io.ktor.server.routing.RouteSelectorEvaluation
import io.ktor.server.routing.RoutingResolveContext
import io.ktor.http.Cookie
import io.ktor.http.HttpStatusCode
import io.ktor.server.request.httpMethod
import io.ktor.server.request.path
import io.ktor.server.response.header
import com.zyntasolutions.zyntapos.api.models.ErrorResponse
import java.util.UUID

/**
 * CSRF protection via the double-submit cookie pattern (TODO-007f Day 3).
 *
 * How it works:
 *   1. On every response, backend sets a readable `XSRF-TOKEN` cookie (NOT httpOnly).
 *   2. Frontend reads `XSRF-TOKEN` and sends it as `X-XSRF-Token` request header.
 *   3. Backend validates that header == cookie value on all state-changing methods.
 *
 * This adds a second layer on top of SameSite=Strict cookies (which already block
 * CSRF for modern browsers). Together, they protect against both classic CSRF and
 * misconfigured/legacy browser scenarios.
 *
 * Excluded paths (pre-auth — no XSRF cookie exists yet):
 *   - GET requests (read-only)
 *   - /admin/auth/login
 *   - /admin/auth/bootstrap
 *   - /admin/auth/refresh (uses httpOnly refresh cookie, not XSRF)
 */

private val STATE_CHANGING_METHODS = setOf(
    HttpMethod.Post, HttpMethod.Put, HttpMethod.Patch, HttpMethod.Delete
)

/** Paths excluded from CSRF validation (pre-auth or server-redirect flows). */
private val CSRF_EXCLUDED_PATHS = setOf(
    "/admin/auth/login",
    "/admin/auth/bootstrap",
    "/admin/auth/refresh"
)

private const val XSRF_COOKIE  = "XSRF-TOKEN"
private const val XSRF_HEADER  = "X-XSRF-Token"

/**
 * Ktor plugin that enforces CSRF double-submit cookie validation on all
 * state-changing admin endpoints (POST/PUT/PATCH/DELETE), except excluded paths.
 *
 * Install once in Application.module() via configureCsrf().
 */
fun Route.withCsrfProtection(build: Route.() -> Unit): Route {
    val route = createChild(CsrfRouteSelector())
    route.install(CsrfPlugin)
    route.build()
    return route
}

private class CsrfRouteSelector : RouteSelector() {
    override suspend fun evaluate(context: RoutingResolveContext, segmentIndex: Int) =
        RouteSelectorEvaluation.Transparent
}

val CsrfPlugin = createRouteScopedPlugin("CsrfPlugin") {
    onCall { call ->
        // Ensure XSRF-TOKEN cookie is always present on the response
        val existingCsrf = call.request.cookies[XSRF_COOKIE]
        val csrfToken = if (existingCsrf.isNullOrBlank()) {
            UUID.randomUUID().toString().replace("-", "")
        } else {
            existingCsrf
        }

        // Set readable XSRF cookie (not httpOnly — intentionally readable by JS)
        call.response.cookies.append(
            Cookie(
                name     = XSRF_COOKIE,
                value    = csrfToken,
                maxAge   = 86_400,       // 24h — refresh on each response
                path     = "/",
                httpOnly = false,        // MUST be false so JS can read it
                secure   = true,
                extensions = mapOf("SameSite" to "Strict")
            )
        )

        val method = call.request.httpMethod
        val path   = call.request.path()

        // Only validate state-changing methods on non-excluded paths
        if (method in STATE_CHANGING_METHODS && path !in CSRF_EXCLUDED_PATHS) {
            val headerToken = call.request.headers[XSRF_HEADER]
            if (headerToken.isNullOrBlank() || headerToken != csrfToken) {
                call.respond(
                    HttpStatusCode.Forbidden,
                    ErrorResponse("CSRF_INVALID", "CSRF token missing or invalid")
                )
                call.application.environment.log.warn("CSRF validation failed: path=$path method=$method")
                return@onCall
            }
        }
    }
}
