package com.zyntasolutions.zyntapos.sync.routes

import com.zyntasolutions.zyntapos.sync.session.SyncSessionManager
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.auth.principal
import io.ktor.server.routing.Route
import io.ktor.server.websocket.webSocket
import io.ktor.websocket.CloseReason
import io.ktor.websocket.Frame
import io.ktor.websocket.close
import io.ktor.websocket.readText
import kotlinx.serialization.json.Json
import org.koin.ktor.ext.inject
import org.slf4j.LoggerFactory

private val logger = LoggerFactory.getLogger("SyncWebSocket")

fun Route.syncWebSocketRoutes() {
    val sessionManager: SyncSessionManager by inject()

    // Real-time sync WebSocket endpoint
    // POS app connects here for push notifications and live sync
    webSocket("/v1/sync/ws") {
        val principal = call.principal<JWTPrincipal>()
        val storeId = principal?.payload?.getClaim("storeId")?.asString()
        val userId = principal?.payload?.subject

        if (storeId == null || userId == null) {
            close(CloseReason(CloseReason.Codes.VIOLATED_POLICY, "Missing claims"))
            return@webSocket
        }

        val deviceId = call.request.queryParameters["deviceId"]
        logger.info("WebSocket connected: storeId=$storeId userId=$userId deviceId=$deviceId")

        sessionManager.register(storeId, this)

        try {
            for (frame in incoming) {
                if (frame is Frame.Text) {
                    val text = frame.readText()
                    logger.debug("WS message from $storeId: ${text.take(100)}")
                    // Echo back confirmation (real implementation would process sync ops)
                    send(Frame.Text("""{"type":"ack","storeId":"$storeId"}"""))
                }
            }
        } catch (e: Exception) {
            logger.warn("WebSocket error for storeId=$storeId: ${e.message}")
        } finally {
            sessionManager.unregister(storeId, this)
            logger.info("WebSocket disconnected: storeId=$storeId")
        }
    }
}
