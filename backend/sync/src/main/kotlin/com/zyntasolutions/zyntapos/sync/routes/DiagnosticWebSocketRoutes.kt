package com.zyntasolutions.zyntapos.sync.routes

import com.zyntasolutions.zyntapos.sync.hub.DiagnosticRelay
import com.zyntasolutions.zyntapos.sync.models.WsAck
import com.zyntasolutions.zyntapos.sync.models.WsDiagSessionEvent
import com.zyntasolutions.zyntapos.sync.models.WsPong
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.auth.principal
import io.ktor.server.routing.Route
import io.ktor.server.websocket.webSocket
import io.ktor.websocket.CloseReason
import io.ktor.websocket.Frame
import io.ktor.websocket.close
import io.ktor.websocket.readText
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.koin.ktor.ext.inject
import org.slf4j.LoggerFactory

private val logger = LoggerFactory.getLogger("DiagnosticWebSocket")
private val json = Json { ignoreUnknownKeys = true }

/**
 * Diagnostic WebSocket endpoints for TODO-006 (Remote Diagnostic Access).
 *
 * Two endpoints are provided:
 *
 * 1. **Technician endpoint** — `WSS /v1/diagnostic/ws?sessionId={sessionId}`
 *    Authenticated via admin JWT (RS256). The technician sends diagnostic commands
 *    and receives responses from the POS device.
 *
 * 2. **Device endpoint** — `WSS /v1/diagnostic/device?sessionId={sessionId}&token={jitToken}`
 *    Authenticated via JIT token (RS256 JWT with session_id claim). The POS device
 *    connects after the store operator grants consent and receives commands from
 *    the technician.
 *
 * The [DiagnosticRelay] manages pairing and bidirectional message routing between
 * the technician and device WebSocket sessions.
 */
fun Route.diagnosticWebSocketRoutes() {
    val relay: DiagnosticRelay by inject()

    // WSS /v1/diagnostic/ws?sessionId=<session>
    // Technicians connect here to send diagnostic commands to POS devices.
    webSocket("/v1/diagnostic/ws") {
        val principal = call.principal<JWTPrincipal>()
        val technicianId = principal?.payload?.subject
        val storeId = principal?.payload?.getClaim("storeId")?.asString()
            ?: principal?.payload?.getClaim("store_id")?.asString()

        if (technicianId == null) {
            close(CloseReason(CloseReason.Codes.VIOLATED_POLICY, "Missing JWT claims"))
            return@webSocket
        }

        val sessionId = call.request.queryParameters["sessionId"]
        if (sessionId.isNullOrBlank()) {
            close(CloseReason(CloseReason.Codes.VIOLATED_POLICY, "Missing sessionId query parameter"))
            return@webSocket
        }

        val resolvedStoreId = storeId ?: run {
            close(CloseReason(CloseReason.Codes.VIOLATED_POLICY, "Missing storeId claim"))
            return@webSocket
        }

        logger.info("Diagnostic WS connected: sessionId=$sessionId technician=$technicianId store=$resolvedStoreId")
        relay.registerTechnician(sessionId, resolvedStoreId, this)

        // Send acknowledgement
        send(Frame.Text(json.encodeToString(WsAck(
            storeId = resolvedStoreId,
            deviceId = "technician-$technicianId",
            connectedAt = java.time.Instant.now().toEpochMilli(),
        ))))

        // Notify store devices that diagnostic session started
        relay.relayCommandToStore(sessionId, json.encodeToString(WsDiagSessionEvent(
            sessionId = sessionId,
            storeId = resolvedStoreId,
            event = "SESSION_STARTED",
        )))

        try {
            for (frame in incoming) {
                when (frame) {
                    is Frame.Text -> {
                        val text = frame.readText()
                        handleTechnicianMessage(text, sessionId, relay)
                    }
                    is Frame.Ping -> send(Frame.Pong(frame.data))
                    else -> {}
                }
            }
        } catch (e: Exception) {
            logger.warn("Diagnostic WS error: sessionId=$sessionId — ${e.message}")
        } finally {
            // Notify store devices that diagnostic session ended
            relay.relayCommandToStore(sessionId, json.encodeToString(WsDiagSessionEvent(
                sessionId = sessionId,
                storeId = resolvedStoreId,
                event = "SESSION_ENDED",
            )))
            relay.unregisterTechnician(sessionId)
            logger.info("Diagnostic WS disconnected: sessionId=$sessionId")
        }
    }
}

/**
 * Device-side diagnostic WebSocket endpoint.
 * Separated from [diagnosticWebSocketRoutes] because this endpoint uses JIT token
 * authentication (query parameter) rather than the standard JWT bearer auth.
 *
 * The route is registered outside the `authenticate("jwt-rs256")` block in Routing.kt.
 */
fun Route.diagnosticDeviceWebSocketRoutes() {
    val relay: DiagnosticRelay by inject()

    // WSS /v1/diagnostic/device?sessionId=<session>&token=<jitToken>
    // POS devices connect here after the store operator grants diagnostic consent.
    webSocket("/v1/diagnostic/device") {
        val sessionId = call.request.queryParameters["sessionId"]
        val token = call.request.queryParameters["token"]

        if (sessionId.isNullOrBlank() || token.isNullOrBlank()) {
            close(CloseReason(CloseReason.Codes.VIOLATED_POLICY, "Missing sessionId or token"))
            return@webSocket
        }

        // Validate JIT token
        val decoded = relay.validateJitToken(token, sessionId)
        if (decoded == null) {
            close(CloseReason(CloseReason.Codes.VIOLATED_POLICY, "Invalid or expired diagnostic token"))
            return@webSocket
        }

        val storeId = decoded.getClaim("store_id").asString()
        val scope = decoded.getClaim("scope")?.asString() ?: "READ_ONLY_DIAGNOSTICS"
        val expiresAtMs = (decoded.getClaim("exp")?.asLong() ?: 0L) * 1000L

        // Check if session has already expired
        if (expiresAtMs > 0 && System.currentTimeMillis() > expiresAtMs) {
            close(CloseReason(CloseReason.Codes.VIOLATED_POLICY, "Diagnostic session has expired"))
            return@webSocket
        }

        logger.info(
            "Diagnostic device WS connected: sessionId=$sessionId storeId=$storeId scope=$scope"
        )
        relay.registerDevice(sessionId, storeId, this)

        // Send acknowledgement to the device
        send(Frame.Text(json.encodeToString(WsAck(
            storeId = storeId,
            deviceId = "diag-device-$sessionId",
            connectedAt = java.time.Instant.now().toEpochMilli(),
        ))))

        // Notify technician that device has connected
        relay.relayResponseToTechnician(sessionId, json.encodeToString(WsDiagSessionEvent(
            sessionId = sessionId,
            storeId = storeId,
            event = "DEVICE_CONNECTED",
        )))

        try {
            for (frame in incoming) {
                when (frame) {
                    is Frame.Text -> {
                        val text = frame.readText()
                        handleDeviceMessage(text, sessionId, relay)
                    }
                    is Frame.Ping -> send(Frame.Pong(frame.data))
                    else -> {}
                }
            }
        } catch (e: Exception) {
            logger.warn("Diagnostic device WS error: sessionId=$sessionId — ${e.message}")
        } finally {
            // Notify technician that device has disconnected
            relay.relayResponseToTechnician(sessionId, json.encodeToString(WsDiagSessionEvent(
                sessionId = sessionId,
                storeId = storeId,
                event = "DEVICE_DISCONNECTED",
            )))
            relay.unregisterDevice(sessionId)
            logger.info("Diagnostic device WS disconnected: sessionId=$sessionId")
        }
    }
}

private fun handleTechnicianMessage(text: String, sessionId: String, relay: DiagnosticRelay) {
    try {
        val element = json.parseToJsonElement(text).jsonObject
        val type = element["type"]?.jsonPrimitive?.content

        when (type) {
            "ping" -> {
                // Handled at frame level, but also support JSON-level ping
            }
            "diag_command" -> {
                // Forward the raw command message to the POS device
                relay.relayCommandToStore(sessionId, text)
                logger.debug("Diagnostic command forwarded: sessionId=$sessionId")
            }
            else -> {
                logger.debug("Diagnostic WS unknown message type: $type sessionId=$sessionId")
            }
        }
    } catch (e: Exception) {
        logger.warn("Diagnostic WS message parse error: sessionId=$sessionId — ${e.message}")
    }
}

private fun handleDeviceMessage(text: String, sessionId: String, relay: DiagnosticRelay) {
    try {
        val element = json.parseToJsonElement(text).jsonObject
        val type = element["type"]?.jsonPrimitive?.content

        when (type) {
            "ping" -> {
                // JSON-level ping — no relay needed
            }
            "diag_response" -> {
                // Forward the diagnostic response to the technician
                relay.relayResponseToTechnician(sessionId, text)
                logger.debug("Diagnostic response forwarded from device: sessionId=$sessionId")
            }
            else -> {
                logger.debug("Diagnostic device WS unknown message type: $type sessionId=$sessionId")
            }
        }
    } catch (e: Exception) {
        logger.warn("Diagnostic device WS message parse error: sessionId=$sessionId — ${e.message}")
    }
}
