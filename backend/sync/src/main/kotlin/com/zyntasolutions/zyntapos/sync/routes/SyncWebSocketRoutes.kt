package com.zyntasolutions.zyntapos.sync.routes

import com.zyntasolutions.zyntapos.sync.hub.DiagnosticRelay
import com.zyntasolutions.zyntapos.sync.hub.SyncForwarder
import com.zyntasolutions.zyntapos.sync.hub.WebSocketHub
import com.zyntasolutions.zyntapos.sync.models.WsAck
import com.zyntasolutions.zyntapos.sync.models.WsMessage
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

private val logger = LoggerFactory.getLogger("SyncWebSocket")
private val json = Json { ignoreUnknownKeys = true }

fun Route.syncWebSocketRoutes() {
    val hub: WebSocketHub by inject()
    val diagnosticRelay: DiagnosticRelay by inject()
    val syncForwarder: SyncForwarder by inject()

    // WSS /v1/sync/ws?deviceId=<device>
    // POS terminals connect here for real-time delta notifications and sync push.
    webSocket("/v1/sync/ws") {
        val principal = call.principal<JWTPrincipal>()
        val storeId   = principal?.payload?.getClaim("storeId")?.asString()
        val userId    = principal?.payload?.subject
        val rawToken  = call.request.headers["Authorization"]?.removePrefix("Bearer ")

        if (storeId == null || userId == null) {
            close(CloseReason(CloseReason.Codes.VIOLATED_POLICY, "Missing JWT claims"))
            return@webSocket
        }

        val deviceId = call.request.queryParameters["deviceId"] ?: "device-$userId"

        logger.info("WS connected: store=$storeId device=$deviceId")
        hub.register(storeId, deviceId, this)

        // Send acknowledgement as first message
        send(Frame.Text(json.encodeToString<WsMessage>(WsAck(
            storeId     = storeId,
            deviceId    = deviceId,
            connectedAt = java.time.Instant.now().toEpochMilli(),
        ))))

        try {
            for (frame in incoming) {
                when (frame) {
                    is Frame.Text -> {
                        val text = frame.readText()
                        when {
                            text.contains("\"type\":\"ping\"") -> {
                                send(Frame.Text(json.encodeToString<WsMessage>(WsPong())))
                            }
                            text.contains("\"type\":\"sync_push\"") -> {
                                // Forward sync push to API service's /v1/sync/push endpoint
                                val response = syncForwarder.forwardPush(text, rawToken)
                                send(Frame.Text(response))
                            }
                            text.contains("\"type\":\"sync_pull\"") -> {
                                // Forward sync pull request to API service's /v1/sync/pull endpoint
                                val parsed = json.parseToJsonElement(text).jsonObject
                                val since = parsed["since"]?.jsonPrimitive?.content?.toLongOrNull() ?: 0L
                                val limit = parsed["limit"]?.jsonPrimitive?.content?.toIntOrNull() ?: 50
                                val response = syncForwarder.forwardPull(storeId, deviceId, since, limit, rawToken)
                                send(Frame.Text(response))
                            }
                            text.contains("\"type\":\"diag_response\"") -> {
                                // Relay diagnostic response from POS device to technician (TODO-006)
                                val sessionId = extractSessionId(text)
                                if (sessionId != null) {
                                    diagnosticRelay.relayResponseToTechnician(sessionId, text)
                                }
                            }
                        }
                    }
                    is Frame.Ping -> send(Frame.Pong(frame.data))
                    else -> {}
                }
            }
        } catch (e: Exception) {
            logger.warn("WS error: store=$storeId device=$deviceId — ${e.message}")
        } finally {
            hub.unregister(storeId, deviceId)
            logger.info("WS disconnected: store=$storeId device=$deviceId")
        }
    }
}

private fun extractSessionId(text: String): String? = try {
    json.parseToJsonElement(text).jsonObject["sessionId"]?.jsonPrimitive?.content
} catch (_: Exception) {
    null
}
