package com.zyntasolutions.zyntapos.sync.session

import io.ktor.server.websocket.DefaultWebSocketServerSession
import io.ktor.websocket.Frame
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.slf4j.LoggerFactory
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArraySet

/**
 * Manages active WebSocket sessions per store.
 * Allows server-side broadcast of sync notifications to all
 * devices connected for the same store.
 */
class SyncSessionManager {
    private val logger = LoggerFactory.getLogger(SyncSessionManager::class.java)
    private val sessions = ConcurrentHashMap<String, CopyOnWriteArraySet<DefaultWebSocketServerSession>>()
    private val scope = CoroutineScope(Dispatchers.IO)

    fun register(storeId: String, session: DefaultWebSocketServerSession) {
        sessions.getOrPut(storeId) { CopyOnWriteArraySet() }.add(session)
        logger.debug("Session registered: storeId=$storeId activeCount=${sessions[storeId]?.size}")
    }

    fun unregister(storeId: String, session: DefaultWebSocketServerSession) {
        sessions[storeId]?.remove(session)
        if (sessions[storeId]?.isEmpty() == true) {
            sessions.remove(storeId)
        }
    }

    /** Broadcast a JSON message to all connected devices for a given store. */
    fun broadcast(storeId: String, message: String) {
        val storeSessions = sessions[storeId] ?: return
        scope.launch {
            storeSessions.forEach { session ->
                try {
                    session.send(Frame.Text(message))
                } catch (e: Exception) {
                    logger.warn("Failed to send to session in storeId=$storeId, removing dead session: ${e.message}")
                    storeSessions.remove(session)
                    if (storeSessions.isEmpty()) sessions.remove(storeId)
                }
            }
        }
    }

    fun activeStoreCount(): Int = sessions.size
    fun activeSessionCount(): Int = sessions.values.sumOf { it.size }
}
