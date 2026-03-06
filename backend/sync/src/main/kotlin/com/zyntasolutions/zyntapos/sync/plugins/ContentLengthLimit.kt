package com.zyntasolutions.zyntapos.sync.plugins

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCallPipeline
import io.ktor.server.application.call
import io.ktor.server.response.respond

/**
 * Enforces a 1 MB body size limit for sync service HTTP endpoints (TODO-009 1d).
 *
 * WebSocket frame size is separately limited in WebSockets.kt (maxFrameSize = 1 MB).
 */
fun Application.configureContentLengthLimit() {
    intercept(ApplicationCallPipeline.Plugins) {
        val contentLength = call.request.headers["Content-Length"]?.toLongOrNull() ?: return@intercept
        val maxBytes = 1L * 1024 * 1024  // 1 MB
        if (contentLength > maxBytes) {
            call.respond(HttpStatusCode.PayloadTooLarge, mapOf("error" to "Request body too large (max 1 MB)"))
            finish()
        }
    }
}
