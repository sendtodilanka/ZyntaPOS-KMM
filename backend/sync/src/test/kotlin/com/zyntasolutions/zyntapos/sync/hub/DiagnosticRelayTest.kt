package com.zyntasolutions.zyntapos.sync.hub

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.security.KeyPairGenerator
import java.security.interfaces.RSAPrivateKey
import java.security.interfaces.RSAPublicKey
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Unit tests for DiagnosticRelay — technician/device session tracking, JIT token
 * validation, store mapping, and relay routing logic. WebSocket send/receive is mocked.
 */
class DiagnosticRelayTest {

    companion object {
        private const val TEST_ISSUER = "https://panel.zyntapos.com"
        private val keyPair = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
        private val publicKey = keyPair.public as RSAPublicKey
        private val privateKey = keyPair.private as RSAPrivateKey
    }

    private val hub = mockk<WebSocketHub>(relaxed = true)
    private val relay = DiagnosticRelay(hub, publicKey, TEST_ISSUER)

    // ── Technician Registration / Unregistration ──────────────────────────

    @Test
    fun `registerTechnician tracks session and store mapping`() {
        val session = mockk<io.ktor.server.websocket.DefaultWebSocketServerSession>(relaxed = true)
        relay.registerTechnician("sess-1", "store-A", session)

        assertTrue(relay.isTechnicianConnected("sess-1"))
        assertEquals("store-A", relay.getStoreId("sess-1"))
        assertEquals(1, relay.activeSessionCount())
    }

    @Test
    fun `unregisterTechnician removes session and store mapping`() {
        val session = mockk<io.ktor.server.websocket.DefaultWebSocketServerSession>(relaxed = true)
        relay.registerTechnician("sess-1", "store-A", session)
        relay.unregisterTechnician("sess-1")

        assertFalse(relay.isTechnicianConnected("sess-1"))
        assertNull(relay.getStoreId("sess-1"))
        assertEquals(0, relay.activeSessionCount())
    }

    @Test
    fun `unregister non-existent session is a no-op`() {
        relay.unregisterTechnician("does-not-exist")
        assertEquals(0, relay.activeSessionCount())
    }

    // ── Device Registration / Unregistration ──────────────────────────────

    @Test
    fun `registerDevice tracks device session`() {
        val session = mockk<io.ktor.server.websocket.DefaultWebSocketServerSession>(relaxed = true)
        relay.registerDevice("sess-1", "store-A", session)

        assertTrue(relay.isDeviceConnected("sess-1"))
        assertEquals("store-A", relay.getStoreId("sess-1"))
        assertEquals(1, relay.activeDeviceCount())
    }

    @Test
    fun `unregisterDevice removes device session`() {
        val session = mockk<io.ktor.server.websocket.DefaultWebSocketServerSession>(relaxed = true)
        relay.registerDevice("sess-1", "store-A", session)
        relay.unregisterDevice("sess-1")

        assertFalse(relay.isDeviceConnected("sess-1"))
        assertEquals(0, relay.activeDeviceCount())
    }

    @Test
    fun `unregisterDevice for non-existent session is a no-op`() {
        relay.unregisterDevice("does-not-exist")
        assertEquals(0, relay.activeDeviceCount())
    }

    // ── Multiple sessions ────────────────────────────────────────────────

    @Test
    fun `multiple technicians can be registered simultaneously`() {
        val s1 = mockk<io.ktor.server.websocket.DefaultWebSocketServerSession>(relaxed = true)
        val s2 = mockk<io.ktor.server.websocket.DefaultWebSocketServerSession>(relaxed = true)

        relay.registerTechnician("sess-1", "store-A", s1)
        relay.registerTechnician("sess-2", "store-B", s2)

        assertEquals(2, relay.activeSessionCount())
        assertTrue(relay.isTechnicianConnected("sess-1"))
        assertTrue(relay.isTechnicianConnected("sess-2"))
        assertEquals("store-A", relay.getStoreId("sess-1"))
        assertEquals("store-B", relay.getStoreId("sess-2"))
    }

    @Test
    fun `replacing a session updates the mapping`() {
        val s1 = mockk<io.ktor.server.websocket.DefaultWebSocketServerSession>(relaxed = true)
        val s2 = mockk<io.ktor.server.websocket.DefaultWebSocketServerSession>(relaxed = true)

        relay.registerTechnician("sess-1", "store-A", s1)
        relay.registerTechnician("sess-1", "store-B", s2)

        assertEquals(1, relay.activeSessionCount())
        assertEquals("store-B", relay.getStoreId("sess-1"))
    }

    // ── relayCommandToStore ──────────────────────────────────────────────

    @Test
    fun `relayCommandToStore broadcasts to correct store via hub when no device connected`() {
        val session = mockk<io.ktor.server.websocket.DefaultWebSocketServerSession>(relaxed = true)
        relay.registerTechnician("sess-1", "store-A", session)

        relay.relayCommandToStore("sess-1", """{"cmd":"get_logs"}""")

        verify(exactly = 1) { hub.broadcast("store-A", """{"cmd":"get_logs"}""") }
    }

    @Test
    fun `relayCommandToStore ignores unknown session`() {
        relay.relayCommandToStore("unknown-sess", """{"cmd":"get_logs"}""")

        verify(exactly = 0) { hub.broadcast(any(), any()) }
    }

    // ── JIT Token Validation ─────────────────────────────────────────────

    @Test
    fun `validateJitToken accepts valid token with matching session_id`() {
        val token = createJitToken("sess-1", "store-A", "tech-1", "READ_ONLY_DIAGNOSTICS")

        val decoded = relay.validateJitToken(token, "sess-1")

        assertNotNull(decoded)
        assertEquals("sess-1", decoded.getClaim("session_id").asString())
        assertEquals("store-A", decoded.getClaim("store_id").asString())
        assertEquals("tech-1", decoded.getClaim("technician_id").asString())
        assertEquals("READ_ONLY_DIAGNOSTICS", decoded.getClaim("scope").asString())
    }

    @Test
    fun `validateJitToken rejects token with mismatched session_id`() {
        val token = createJitToken("sess-1", "store-A", "tech-1", "READ_ONLY_DIAGNOSTICS")

        val decoded = relay.validateJitToken(token, "sess-WRONG")

        assertNull(decoded)
    }

    @Test
    fun `validateJitToken rejects expired token`() {
        val token = createJitToken(
            "sess-1", "store-A", "tech-1", "READ_ONLY_DIAGNOSTICS",
            expiresAtSec = (System.currentTimeMillis() / 1000) - 60 // 60 seconds ago
        )

        val decoded = relay.validateJitToken(token, "sess-1")

        assertNull(decoded)
    }

    @Test
    fun `validateJitToken rejects token with wrong issuer`() {
        val algorithm = Algorithm.RSA256(publicKey, privateKey)
        val token = JWT.create()
            .withIssuer("https://evil.example.com")
            .withClaim("session_id", "sess-1")
            .withClaim("store_id", "store-A")
            .withClaim("technician_id", "tech-1")
            .withClaim("scope", "READ_ONLY_DIAGNOSTICS")
            .withClaim("exp", (System.currentTimeMillis() / 1000) + 900)
            .sign(algorithm)

        val decoded = relay.validateJitToken(token, "sess-1")

        assertNull(decoded)
    }

    @Test
    fun `validateJitToken rejects token missing store_id claim`() {
        val algorithm = Algorithm.RSA256(publicKey, privateKey)
        val token = JWT.create()
            .withIssuer(TEST_ISSUER)
            .withClaim("session_id", "sess-1")
            .withClaim("technician_id", "tech-1")
            .withClaim("scope", "READ_ONLY_DIAGNOSTICS")
            .withClaim("exp", (System.currentTimeMillis() / 1000) + 900)
            .sign(algorithm)

        val decoded = relay.validateJitToken(token, "sess-1")

        assertNull(decoded)
    }

    @Test
    fun `validateJitToken rejects malformed token`() {
        val decoded = relay.validateJitToken("not.a.valid.jwt", "sess-1")

        assertNull(decoded)
    }

    // ── Session Expiry ───────────────────────────────────────────────────

    @Test
    fun `isSessionExpired returns false for non-expired session`() {
        val session = mockk<io.ktor.server.websocket.DefaultWebSocketServerSession>(relaxed = true)
        val futureMs = System.currentTimeMillis() + 900_000L // 15 minutes from now
        relay.registerTechnician("sess-1", "store-A", session, expiresAtMs = futureMs)

        assertFalse(relay.isSessionExpired("sess-1"))
    }

    @Test
    fun `isSessionExpired returns true for expired session`() {
        val session = mockk<io.ktor.server.websocket.DefaultWebSocketServerSession>(relaxed = true)
        val pastMs = System.currentTimeMillis() - 1000L // 1 second ago
        relay.registerTechnician("sess-1", "store-A", session, expiresAtMs = pastMs)

        assertTrue(relay.isSessionExpired("sess-1"))
    }

    @Test
    fun `isSessionExpired returns false for unknown session`() {
        assertFalse(relay.isSessionExpired("unknown"))
    }

    // ── isTechnicianConnected / isDeviceConnected / getStoreId ───────────

    @Test
    fun `isTechnicianConnected returns false for unknown session`() {
        assertFalse(relay.isTechnicianConnected("nope"))
    }

    @Test
    fun `isDeviceConnected returns false for unknown session`() {
        assertFalse(relay.isDeviceConnected("nope"))
    }

    @Test
    fun `getStoreId returns null for unknown session`() {
        assertNull(relay.getStoreId("nope"))
    }

    // ── close ────────────────────────────────────────────────────────────

    @Test
    fun `close clears all sessions`() {
        val s1 = mockk<io.ktor.server.websocket.DefaultWebSocketServerSession>(relaxed = true)
        val s2 = mockk<io.ktor.server.websocket.DefaultWebSocketServerSession>(relaxed = true)
        relay.registerTechnician("sess-1", "store-A", s1)
        relay.registerDevice("sess-1", "store-A", s2)

        relay.close()

        assertEquals(0, relay.activeSessionCount())
        assertEquals(0, relay.activeDeviceCount())
        assertFalse(relay.isTechnicianConnected("sess-1"))
        assertFalse(relay.isDeviceConnected("sess-1"))
    }

    // ── Test helpers ─────────────────────────────────────────────────────

    private fun createJitToken(
        sessionId: String,
        storeId: String,
        technicianId: String,
        scope: String,
        expiresAtSec: Long = (System.currentTimeMillis() / 1000) + 900, // 15 min default
    ): String {
        val algorithm = Algorithm.RSA256(publicKey, privateKey)
        return JWT.create()
            .withIssuer(TEST_ISSUER)
            .withClaim("session_id", sessionId)
            .withClaim("store_id", storeId)
            .withClaim("technician_id", technicianId)
            .withClaim("scope", scope)
            .withClaim("exp", expiresAtSec)
            .withClaim("iat", System.currentTimeMillis() / 1000)
            .sign(algorithm)
    }
}
