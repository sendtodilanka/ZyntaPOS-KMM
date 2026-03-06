package com.zyntasolutions.zyntapos.api.plugins

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCallPipeline
import io.ktor.server.application.call
import io.ktor.server.response.respond

/**
 * Enforces request body size limits to prevent memory exhaustion attacks (TODO-009 1d).
 *
 * - Sync routes (`/api/v1/sync`): 1 MB (batched operations)
 * - All other routes: 512 KB
 */
fun Application.configureContentLengthLimit() {
    intercept(ApplicationCallPipeline.Plugins) {
        val contentLength = call.request.headers["Content-Length"]?.toLongOrNull() ?: return@intercept
        val path = call.request.local.uri
        val maxBytes = when {
            path.startsWith("/api/v1/sync") || path.startsWith("/sync") -> 1L * 1024 * 1024  // 1 MB
            else -> 512L * 1024  // 512 KB
        }
        if (contentLength > maxBytes) {
            call.respond(HttpStatusCode.PayloadTooLarge, mapOf("error" to "Request body too large (max ${maxBytes / 1024} KB)"))
            finish()
        }
    }
}
