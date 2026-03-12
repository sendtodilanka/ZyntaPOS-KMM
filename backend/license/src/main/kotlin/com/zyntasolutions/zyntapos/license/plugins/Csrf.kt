package com.zyntasolutions.zyntapos.license.plugins

import com.zyntasolutions.zyntapos.license.models.ErrorResponse
import io.ktor.http.Cookie
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.createRouteScopedPlugin
import io.ktor.server.application.install
import io.ktor.server.request.httpMethod
import io.ktor.server.request.path
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.RouteSelector
import io.ktor.server.routing.RouteSelectorEvaluation
import io.ktor.server.routing.RoutingResolveContext
import java.util.UUID

/**
 * CSRF protection via the double-submit cookie pattern (A4 — ported from API service).
 *
 * How it works:
 *   1. On every response, backend sets a readable `XSRF-TOKEN` cookie (NOT httpOnly).
 *   2. Frontend reads `XSRF-TOKEN` and sends it as `X-XSRF-Token` request header.
 *   3. Backend validates that header == cookie value on all state-changing methods.
 */

private val STATE_CHANGING_METHODS = setOf(
    HttpMethod.Post, HttpMethod.Put, HttpMethod.Patch, HttpMethod.Delete
)

private const val XSRF_COOKIE = "XSRF-TOKEN"
private const val XSRF_HEADER = "X-XSRF-Token"

fun Route.withCsrfProtection(build: Route.() -> Unit): Route {
    val route = createChild(CsrfRouteSelector())
    route.install(LicenseCsrfPlugin)
    route.build()
    return route
}

private class CsrfRouteSelector : RouteSelector() {
    override suspend fun evaluate(context: RoutingResolveContext, segmentIndex: Int) =
        RouteSelectorEvaluation.Transparent
}

val LicenseCsrfPlugin = createRouteScopedPlugin("LicenseCsrfPlugin") {
    onCall { call ->
        val existingCsrf = call.request.cookies[XSRF_COOKIE]
        val csrfToken = if (existingCsrf.isNullOrBlank()) {
            UUID.randomUUID().toString().replace("-", "")
        } else {
            existingCsrf
        }

        call.response.cookies.append(
            Cookie(
                name     = XSRF_COOKIE,
                value    = csrfToken,
                maxAge   = 86_400,
                path     = "/",
                httpOnly = false,
                secure   = true,
                extensions = mapOf("SameSite" to "Strict")
            )
        )

        val method = call.request.httpMethod

        if (method in STATE_CHANGING_METHODS) {
            val headerToken = call.request.headers[XSRF_HEADER]
            if (headerToken.isNullOrBlank() || headerToken != csrfToken) {
                call.respond(
                    HttpStatusCode.Forbidden,
                    ErrorResponse("CSRF_INVALID", "CSRF token missing or invalid")
                )
                call.application.environment.log.warn(
                    "CSRF validation failed: path=${call.request.path()} method=$method"
                )
                return@onCall
            }
        }
    }
}
