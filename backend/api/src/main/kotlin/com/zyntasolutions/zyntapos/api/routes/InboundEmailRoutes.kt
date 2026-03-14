package com.zyntasolutions.zyntapos.api.routes

import com.zyntasolutions.zyntapos.api.service.InboundEmailPayload
import com.zyntasolutions.zyntapos.api.service.InboundEmailProcessor
import io.ktor.http.HttpStatusCode
import io.ktor.server.request.header
import io.ktor.server.request.receive
import io.ktor.server.request.receiveText
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import kotlinx.serialization.json.Json
import org.koin.ktor.ext.inject

/**
 * Internal inbound email endpoint — called by the CF Email Worker (TODO-008a).
 *
 * ## Route
 * `POST /internal/email/inbound`
 *
 * ## Authentication
 * HMAC-SHA256 — not JWT. The CF Worker signs the JSON body with a shared secret
 * (`INBOUND_EMAIL_HMAC_SECRET`) and sends it in `Authorization: HMAC-SHA256 <sig>`.
 * The route is intentionally NOT behind JWT auth — CF Workers cannot obtain JWTs.
 *
 * ## Request body (JSON)
 * ```json
 * {
 *   "messageId": "<msg@example.com>",
 *   "inReplyTo": null,
 *   "references": null,
 *   "fromAddress": "customer@example.com",
 *   "fromName": "Customer Name",
 *   "toAddress": "support@zyntapos.com",
 *   "subject": "Help needed",
 *   "bodyText": "...",
 *   "bodyHtml": null,
 *   "receivedAt": "2026-03-14T10:00:00Z"
 * }
 * ```
 *
 * ## Responses
 * - `200 OK`             — email processed successfully
 * - `401 Unauthorized`   — HMAC signature invalid
 * - `422 Unprocessable`  — payload deserialization failed
 */
fun Route.inboundEmailRoutes() {
    val processor: InboundEmailProcessor by inject()
    val json = Json { ignoreUnknownKeys = true }

    route("/internal/email") {
        post("/inbound") {
            // Read raw body first so we can verify HMAC over exact bytes received
            val rawBody = call.receiveText()

            // Verify HMAC signature — reject unsigned or tampered payloads
            val authHeader = call.request.header("Authorization")
            if (!processor.verifySignature(authHeader, rawBody)) {
                call.respond(HttpStatusCode.Unauthorized, mapOf("error" to "Invalid HMAC signature"))
                return@post
            }

            // Deserialize payload
            val payload = runCatching { json.decodeFromString<InboundEmailPayload>(rawBody) }
                .getOrElse {
                    call.respond(HttpStatusCode.UnprocessableEntity, mapOf("error" to "Invalid payload: ${it.message}"))
                    return@post
                }

            // Process asynchronously — return 200 immediately so the CF Worker doesn't retry
            processor.process(payload)
            call.respond(HttpStatusCode.OK, mapOf("status" to "accepted"))
        }
    }
}
