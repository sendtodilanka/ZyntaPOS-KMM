package com.zyntasolutions.zyntapos.api.plugins

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCallPipeline
import io.ktor.server.application.call
import io.ktor.server.request.ApplicationReceivePipeline
import io.ktor.server.response.respond
import io.ktor.util.AttributeKey
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.readRemaining
import kotlinx.io.readByteArray

/**
 * Enforces request body size limits to prevent memory exhaustion attacks.
 *
 * - Sync routes (`/v1/sync`): 1 MB (batched operations)
 * - All other routes: 512 KB
 *
 * SECURITY: The previous implementation only checked the `Content-Length` header, which is
 * absent for chunked transfer encoding (Transfer-Encoding: chunked). This version adds a
 * second enforcement stage in the receive pipeline that buffers and re-exposes the body only
 * if it fits within the limit, regardless of transfer encoding.
 *
 * Two-stage check:
 *   1. Fast reject if Content-Length header already exceeds the limit (cheap, no I/O).
 *   2. In the receive pipeline: read up to (limit + 1) bytes from the channel. If the body
 *      exceeds the limit, respond 413 before the route handler runs. Otherwise, re-wrap the
 *      buffered bytes as a new ByteReadChannel so downstream handlers read normally.
 */

private val MAX_BODY_SIZE_KEY = AttributeKey<Long>("MaxBodySizeBytes")

private fun pathLimit(path: String): Long = when {
    path.startsWith("/v1/sync") || path.startsWith("/api/v1/sync") -> 1L * 1024 * 1024  // 1 MB
    else -> 512L * 1024  // 512 KB
}

fun Application.configureContentLengthLimit() {

    // ── Stage 1: fast reject on declared Content-Length ──────────────────────
    intercept(ApplicationCallPipeline.Plugins) {
        val path = call.request.local.uri
        val maxBytes = pathLimit(path)

        val declared = call.request.headers["Content-Length"]?.toLongOrNull()
        if (declared != null && declared > maxBytes) {
            call.respond(
                HttpStatusCode.PayloadTooLarge,
                mapOf("error" to "Request body too large (max ${maxBytes / 1024} KB)")
            )
            finish()
            return@intercept
        }

        // Pass the limit to the receive pipeline via call attributes
        call.attributes.put(MAX_BODY_SIZE_KEY, maxBytes)
    }

    // ── Stage 2: enforce on actual channel (catches chunked / no Content-Length) ──
    receivePipeline.intercept(ApplicationReceivePipeline.Before) {
        val maxBytes = call.attributes.getOrNull(MAX_BODY_SIZE_KEY) ?: return@intercept
        // In Ktor 3.x, subject in the receive pipeline IS the ByteReadChannel directly
        val channel = subject as? ByteReadChannel ?: return@intercept

        // Read up to maxBytes + 1 into memory. If more than maxBytes arrived, reject.
        // readRemaining(limit) reads at most `limit` bytes without blocking indefinitely.
        val bytes = channel.readRemaining(maxBytes + 1).readByteArray()

        if (bytes.size.toLong() > maxBytes) {
            call.respond(
                HttpStatusCode.PayloadTooLarge,
                mapOf("error" to "Request body too large (max ${maxBytes / 1024} KB)")
            )
            finish()
            return@intercept
        }

        // Re-wrap the buffered bytes as a new channel for downstream handlers.
        // In Ktor 3.x, proceedWith takes the channel directly (no ApplicationReceiveRequest wrapper).
        proceedWith(ByteReadChannel(bytes))
    }
}
