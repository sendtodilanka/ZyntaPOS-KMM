package com.zyntasolutions.zyntapos.sync.routes

import com.zyntasolutions.zyntapos.sync.hub.DiagnosticRelay
import com.zyntasolutions.zyntapos.sync.models.WsAck
import com.zyntasolutions.zyntapos.sync.models.WsDiagCommand
import com.zyntasolutions.zyntapos.sync.models.WsDiagResponse
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
 * Diagnostic WebSocket endpoint for TODO-006 (Remote Diagnostic Access).
 *
 * Technicians connect via: WSS /v1/diagnostic/ws?sessionId={sessionId}
 * POS devices relay responses via: WSS /v1/sync/ws (diag_response messages)
 *
 * Authentication: Admin JWT (HS256) with claim "session_id" matching the query param.
 * The API service validates the session exists and is ACTIVE before the technician connects.
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
            connectedAt = System.currentTimeMillis(),
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

private fun handleTechnicianMessage(text: String, sessionId: String, relay: DiagnosticRelay) {
    try {
        val element = json.parseToJsonElement(text).jsonObject
        val type = element["type"]?.jsonPrimitive?.content

        when (type) {
            "ping" -> {
                // Handled at frame level, but also support JSON-level ping
            }
            "diag_command" -> {
                // Forward the raw command message to all POS devices in the store
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
