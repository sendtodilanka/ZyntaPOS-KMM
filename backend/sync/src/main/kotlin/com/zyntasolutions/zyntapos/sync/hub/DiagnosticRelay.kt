package com.zyntasolutions.zyntapos.sync.hub

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.auth0.jwt.exceptions.JWTVerificationException
import com.auth0.jwt.interfaces.DecodedJWT
import io.ktor.server.websocket.DefaultWebSocketServerSession
import io.ktor.websocket.Frame
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.slf4j.LoggerFactory
import java.security.PublicKey
import java.security.interfaces.RSAPublicKey
import java.util.concurrent.ConcurrentHashMap

/**
 * Manages diagnostic WebSocket connections for TODO-006 (Remote Diagnostic Access).
 *
 * The relay bridges two endpoints:
 * - **Technician** (via admin panel) connects to `/v1/diagnostic/ws?sessionId=...`
 * - **POS device** connects to `/v1/diagnostic/device?sessionId=...&token=...`
 *   OR sends diagnostic responses via the sync channel (`diag_response` messages)
 *
 * Messages flow:  Technician → DiagnosticRelay → POS device (via [WebSocketHub] broadcast
 *                 OR direct device session if connected to the dedicated endpoint)
 *                 POS device → DiagnosticRelay → Technician (via direct session send)
 *
 * Session lifecycle:
 * 1. Admin panel creates a diagnostic session via API (POST /admin/diagnostic/sessions)
 * 2. API returns a JIT token (RS256 JWT with session_id, store_id, technician_id, scope)
 * 3. Technician connects to `/v1/diagnostic/ws` with their admin JWT
 * 4. POS device receives SESSION_STARTED event and connects to `/v1/diagnostic/device`
 *    with the JIT token (presented via consent UI)
 * 5. Messages flow bidirectionally until session expires or is revoked
 *
 * Security:
 * - JIT tokens are RS256 JWTs validated against the same public key used for admin auth
 * - Session IDs are cross-checked between token claims and connection parameters
 * - Sessions auto-expire based on the `exp` claim in the JIT token
 * - All commands logged for audit trail
 */
class DiagnosticRelay(
    private val hub: WebSocketHub,
    private val jwtPublicKey: PublicKey,
    private val jwtIssuer: String,
) {

    private val logger = LoggerFactory.getLogger(DiagnosticRelay::class.java)

    // sessionId → technician WebSocket session
    private val technicianSessions = ConcurrentHashMap<String, DefaultWebSocketServerSession>()

    // sessionId → storeId mapping (for routing responses)
    private val sessionStoreMap = ConcurrentHashMap<String, String>()

    // sessionId → device WebSocket session (dedicated diagnostic device connections)
    private val deviceSessions = ConcurrentHashMap<String, DefaultWebSocketServerSession>()

    // sessionId → expiry timestamp (epoch ms) for TTL enforcement
    private val sessionExpiry = ConcurrentHashMap<String, Long>()

    private val job = SupervisorJob()
    private val scope = CoroutineScope(Dispatchers.IO + job)

    init {
        // Launch periodic cleanup of expired sessions
        scope.launch {
            while (isActive) {
                delay(SESSION_CLEANUP_INTERVAL_MS)
                cleanupExpiredSessions()
            }
        }
    }

    // ── JIT Token Validation ──────────────────────────────────────────────────

    /**
     * Validates a JIT diagnostic token (RS256 JWT issued by the API service).
     *
     * Expected claims:
     * - `session_id` — must match the requested sessionId
     * - `store_id` — the store this session is scoped to
     * - `technician_id` — the technician who requested the session
     * - `scope` — data access scope (READ_ONLY_DIAGNOSTICS, FULL_READ_ONLY)
     * - `exp` — expiry timestamp (seconds since epoch)
     *
     * @return Decoded JWT if valid, or null if verification fails.
     */
    fun validateJitToken(token: String, expectedSessionId: String): DecodedJWT? {
        return try {
            val verifier = JWT.require(Algorithm.RSA256(jwtPublicKey as RSAPublicKey, null))
                .withIssuer(jwtIssuer)
                .build()
            val decoded = verifier.verify(token)

            val tokenSessionId = decoded.getClaim("session_id")?.asString()
            if (tokenSessionId != expectedSessionId) {
                logger.warn(
                    "JIT token session_id mismatch: expected=$expectedSessionId got=$tokenSessionId"
                )
                return null
            }

            val storeId = decoded.getClaim("store_id")?.asString()
            if (storeId.isNullOrBlank()) {
                logger.warn("JIT token missing store_id claim for session=$expectedSessionId")
                return null
            }

            decoded
        } catch (e: JWTVerificationException) {
            logger.warn("JIT token verification failed for session=$expectedSessionId: ${e.message}")
            null
        }
    }

    // ── Technician Session Management ─────────────────────────────────────────

    /**
     * Register a technician's WebSocket connection for a diagnostic session.
     */
    fun registerTechnician(
        sessionId: String,
        storeId: String,
        session: DefaultWebSocketServerSession,
        expiresAtMs: Long = 0L,
    ) {
        technicianSessions[sessionId] = session
        sessionStoreMap[sessionId] = storeId
        if (expiresAtMs > 0) {
            sessionExpiry[sessionId] = expiresAtMs
        }
        logger.info("Diagnostic technician connected: sessionId=$sessionId storeId=$storeId")
    }

    /**
     * Unregister a technician's WebSocket connection.
     */
    fun unregisterTechnician(sessionId: String) {
        technicianSessions.remove(sessionId)
        sessionStoreMap.remove(sessionId)
        sessionExpiry.remove(sessionId)
        logger.info("Diagnostic technician disconnected: sessionId=$sessionId")
    }

    // ── Device Session Management ─────────────────────────────────────────────

    /**
     * Register a POS device's WebSocket connection for a diagnostic session.
     * The device connects via the dedicated `/v1/diagnostic/device` endpoint
     * after the store operator grants consent.
     */
    fun registerDevice(sessionId: String, storeId: String, session: DefaultWebSocketServerSession) {
        deviceSessions[sessionId] = session
        // Ensure store mapping is set (may already exist from technician registration)
        sessionStoreMap[sessionId] = storeId
        logger.info("Diagnostic device connected: sessionId=$sessionId storeId=$storeId")
    }

    /**
     * Unregister a POS device's WebSocket connection.
     */
    fun unregisterDevice(sessionId: String) {
        deviceSessions.remove(sessionId)
        logger.info("Diagnostic device disconnected: sessionId=$sessionId")
    }

    // ── Message Relay ─────────────────────────────────────────────────────────

    /**
     * Forward a diagnostic command from the technician to the POS device.
     *
     * Routing priority:
     * 1. If a device is connected via the dedicated diagnostic endpoint, send directly
     * 2. Otherwise, broadcast to all store devices via [WebSocketHub] (legacy path)
     */
    fun relayCommandToStore(sessionId: String, message: String) {
        val storeId = sessionStoreMap[sessionId] ?: run {
            logger.warn("Diagnostic relay failed: no store mapping for sessionId=$sessionId")
            return
        }

        // Prefer dedicated device connection if available
        val deviceSession = deviceSessions[sessionId]
        if (deviceSession != null) {
            scope.launch {
                try {
                    deviceSession.send(Frame.Text(message))
                    logger.debug("Diagnostic command sent directly to device: sessionId=$sessionId")
                } catch (e: Exception) {
                    logger.warn("Diagnostic direct device send failed: sessionId=$sessionId: ${e.message}")
                    unregisterDevice(sessionId)
                    // Fall back to hub broadcast
                    hub.broadcast(storeId, message)
                }
            }
        } else {
            // Fallback: broadcast to all devices in the store via sync hub
            hub.broadcast(storeId, message)
            logger.debug("Diagnostic command broadcast to store=$storeId sessionId=$sessionId")
        }
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

    // ── Session Queries ───────────────────────────────────────────────────────

    /**
     * Check if a technician is currently connected for the given session.
     */
    fun isTechnicianConnected(sessionId: String): Boolean = technicianSessions.containsKey(sessionId)

    /**
     * Check if a POS device is connected via the dedicated diagnostic endpoint.
     */
    fun isDeviceConnected(sessionId: String): Boolean = deviceSessions.containsKey(sessionId)

    /**
     * Get the store ID for a diagnostic session.
     */
    fun getStoreId(sessionId: String): String? = sessionStoreMap[sessionId]

    /**
     * Check if a session has expired based on its TTL.
     */
    fun isSessionExpired(sessionId: String): Boolean {
        val expiry = sessionExpiry[sessionId] ?: return false
        return System.currentTimeMillis() > expiry
    }

    fun activeSessionCount(): Int = technicianSessions.size
    fun activeDeviceCount(): Int = deviceSessions.size

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    fun close() {
        job.cancel()
        logger.info(
            "DiagnosticRelay closed — " +
                "${technicianSessions.size} technician + ${deviceSessions.size} device sessions terminated"
        )
        technicianSessions.clear()
        deviceSessions.clear()
        sessionStoreMap.clear()
        sessionExpiry.clear()
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    /**
     * Periodic cleanup: close and remove sessions whose JIT token TTL has elapsed.
     */
    private fun cleanupExpiredSessions() {
        val now = System.currentTimeMillis()
        val expired = sessionExpiry.entries.filter { now > it.value }.map { it.key }
        for (sessionId in expired) {
            logger.info("Diagnostic session expired, cleaning up: sessionId=$sessionId")

            // Close technician WebSocket
            technicianSessions.remove(sessionId)?.let { session ->
                scope.launch {
                    try {
                        session.send(Frame.Text(
                            """{"type":"diag_session_event","sessionId":"$sessionId","event":"SESSION_EXPIRED"}"""
                        ))
                    } catch (_: Exception) { /* session may already be closed */ }
                }
            }

            // Close device WebSocket
            deviceSessions.remove(sessionId)?.let { session ->
                scope.launch {
                    try {
                        session.send(Frame.Text(
                            """{"type":"diag_session_event","sessionId":"$sessionId","event":"SESSION_EXPIRED"}"""
                        ))
                    } catch (_: Exception) { /* session may already be closed */ }
                }
            }

            sessionStoreMap.remove(sessionId)
            sessionExpiry.remove(sessionId)
        }
    }

    companion object {
        /** How often to check for expired sessions (every 30 seconds). */
        private const val SESSION_CLEANUP_INTERVAL_MS = 30_000L
    }
}
