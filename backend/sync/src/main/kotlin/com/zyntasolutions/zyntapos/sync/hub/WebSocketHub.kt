package com.zyntasolutions.zyntapos.sync.hub

import io.ktor.server.websocket.DefaultWebSocketServerSession
import io.ktor.websocket.Frame
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.slf4j.LoggerFactory
import java.util.concurrent.ConcurrentHashMap

/**
 * Manages active WebSocket connections per store, tracked at the device level.
 *
 * Key improvement over the old [SyncSessionManager]: connections are keyed by
 * (storeId, deviceId) so that push notifications can exclude the sender device
 * and avoid echo. Each device may have at most one active session (reconnect
 * replaces the previous session).
 */
class WebSocketHub {
    private val logger = LoggerFactory.getLogger(WebSocketHub::class.java)

    // storeId → { deviceId → session }
    private val connections = ConcurrentHashMap<String, ConcurrentHashMap<String, DefaultWebSocketServerSession>>()
    private val scope = CoroutineScope(Dispatchers.IO)

    fun register(storeId: String, deviceId: String, session: DefaultWebSocketServerSession) {
        connections.getOrPut(storeId) { ConcurrentHashMap() }[deviceId] = session
        logger.info("WS registered: store=$storeId device=$deviceId (store total: ${connectionCount(storeId)})")
    }

    fun unregister(storeId: String, deviceId: String) {
        connections[storeId]?.remove(deviceId)
        if (connections[storeId]?.isEmpty() == true) {
            connections.remove(storeId)
        }
        logger.info("WS unregistered: store=$storeId device=$deviceId")
    }

    /**
     * Broadcast [message] to all connected devices of [storeId], optionally
     * excluding [excludeDeviceId] (the sender, to prevent echo).
     */
    fun broadcast(storeId: String, message: String, excludeDeviceId: String? = null) {
        val storeConns = connections[storeId] ?: return
        scope.launch {
            for ((deviceId, session) in storeConns) {
                if (deviceId == excludeDeviceId) continue
                try {
                    session.send(Frame.Text(message))
                } catch (e: Exception) {
                    logger.warn("WS send failed to device=$deviceId store=$storeId: ${e.message}")
                    unregister(storeId, deviceId)
                }
            }
        }
    }

    fun connectionCount(storeId: String): Int = connections[storeId]?.size ?: 0
    fun totalConnections(): Int = connections.values.sumOf { it.size }
    fun activeStoreCount(): Int = connections.size
}
