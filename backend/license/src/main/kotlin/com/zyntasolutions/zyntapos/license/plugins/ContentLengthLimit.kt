package com.zyntasolutions.zyntapos.license.plugins

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCallPipeline
import io.ktor.server.application.call
import io.ktor.server.response.respond

/**
 * Enforces a strict 4 KB body size limit for all license service endpoints (TODO-009 1d).
 *
 * License payloads (activate, heartbeat) are tiny — 4 KB is more than sufficient.
 */
fun Application.configureContentLengthLimit() {
    intercept(ApplicationCallPipeline.Plugins) {
        val contentLength = call.request.headers["Content-Length"]?.toLongOrNull() ?: return@intercept
        val maxBytes = 4L * 1024  // 4 KB
        if (contentLength > maxBytes) {
            call.respond(HttpStatusCode.PayloadTooLarge, mapOf("error" to "Request body too large (max 4 KB)"))
            finish()
        }
    }
}
