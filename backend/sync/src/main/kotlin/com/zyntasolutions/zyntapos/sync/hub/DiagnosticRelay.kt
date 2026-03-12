package com.zyntasolutions.zyntapos.sync.hub

import io.ktor.server.websocket.DefaultWebSocketServerSession
import io.ktor.websocket.Frame
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.slf4j.LoggerFactory
import java.util.concurrent.ConcurrentHashMap

/**
 * Manages diagnostic WebSocket connections for TODO-006 (Remote Diagnostic Access).
 *
 * The relay bridges two endpoints:
 * - **Technician** (via admin panel) connects to `/v1/diagnostic/ws?sessionId=...`
 * - **POS device** (via sync channel) sends diagnostic responses back
 *
 * Messages flow:  Technician → DiagnosticRelay → POS device (via [WebSocketHub] broadcast)
 *                  POS device → DiagnosticRelay → Technician (via direct session send)
 *
 * Security:
 * - Session ID validated against active diagnostic sessions (API service responsibility)
 * - Commands are scoped to the session's dataScope (POS device enforces)
 * - All commands logged for audit trail
 */
class DiagnosticRelay(private val hub: WebSocketHub) {

    private val logger = LoggerFactory.getLogger(DiagnosticRelay::class.java)

    // sessionId → technician WebSocket session
    private val technicianSessions = ConcurrentHashMap<String, DefaultWebSocketServerSession>()

    // sessionId → storeId mapping (for routing responses)
    private val sessionStoreMap = ConcurrentHashMap<String, String>()

    private val job = SupervisorJob()
    private val scope = CoroutineScope(Dispatchers.IO + job)

    /**
     * Register a technician's WebSocket connection for a diagnostic session.
     */
    fun registerTechnician(sessionId: String, storeId: String, session: DefaultWebSocketServerSession) {
        technicianSessions[sessionId] = session
        sessionStoreMap[sessionId] = storeId
        logger.info("Diagnostic technician connected: sessionId=$sessionId storeId=$storeId")
    }

    /**
     * Unregister a technician's WebSocket connection.
     */
    fun unregisterTechnician(sessionId: String) {
        technicianSessions.remove(sessionId)
        sessionStoreMap.remove(sessionId)
        logger.info("Diagnostic technician disconnected: sessionId=$sessionId")
    }

    /**
     * Forward a diagnostic command from the technician to the POS device(s) in the store.
     * Uses the existing [WebSocketHub] broadcast mechanism.
     */
    fun relayCommandToStore(sessionId: String, message: String) {
        val storeId = sessionStoreMap[sessionId] ?: run {
            logger.warn("Diagnostic relay failed: no store mapping for sessionId=$sessionId")
            return
        }
        hub.broadcast(storeId, message)
        logger.debug("Diagnostic command relayed to store=$storeId sessionId=$sessionId")
    }

    /**
     * Forward a diagnostic response from the POS device back to the technician.
     */
    fun relayResponseToTechnician(sessionId: String, message: String) {
        val session = technicianSessions[sessionId] ?: run {
            logger.warn("Diagnostic relay failed: no technician session for sessionId=$sessionId")
            return
        }
        scope.launch {
            try {
                session.send(Frame.Text(message))
                logger.debug("Diagnostic response relayed to technician sessionId=$sessionId")
            } catch (e: Exception) {
                logger.warn("Diagnostic relay send failed: sessionId=$sessionId: ${e.message}")
                unregisterTechnician(sessionId)
            }
        }
    }

    /**
     * Check if a technician is currently connected for the given session.
     */
    fun isTechnicianConnected(sessionId: String): Boolean = technicianSessions.containsKey(sessionId)

    /**
     * Get the store ID for a diagnostic session.
     */
    fun getStoreId(sessionId: String): String? = sessionStoreMap[sessionId]

    fun activeSessionCount(): Int = technicianSessions.size

    fun close() {
        job.cancel()
        logger.info("DiagnosticRelay closed — ${technicianSessions.size} sessions terminated")
        technicianSessions.clear()
        sessionStoreMap.clear()
    }
}
