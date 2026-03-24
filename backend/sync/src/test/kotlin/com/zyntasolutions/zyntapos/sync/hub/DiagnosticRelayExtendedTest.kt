package com.zyntasolutions.zyntapos.sync.hub

import io.ktor.server.websocket.DefaultWebSocketServerSession
import io.ktor.websocket.Frame
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import java.security.KeyPairGenerator
import java.security.interfaces.RSAPublicKey
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * C7: Extended tests for [DiagnosticRelay] — covers relayResponseToTechnician,
 * failed send cleanup, and concurrency scenarios.
 */
class DiagnosticRelayExtendedTest {

    companion object {
        private const val TEST_ISSUER = "zyntapos-api"
        private val publicKey = KeyPairGenerator.getInstance("RSA")
            .apply { initialize(2048) }.generateKeyPair().public as RSAPublicKey
    }

    private val hub = mockk<WebSocketHub>(relaxed = true)
    private val relay = DiagnosticRelay(hub, publicKey, TEST_ISSUER)

    // ── relayResponseToTechnician ──────────────────────────────────────────

    @Test
    fun `relayResponseToTechnician sends frame to technician session`() = runBlocking {
        val session = mockk<DefaultWebSocketServerSession>(relaxed = true)
        relay.registerTechnician("sess-1", "store-A", session)

        relay.relayResponseToTechnician("sess-1", """{"type":"diag_response","result":"OK"}""")

        delay(200) // allow scope.launch to execute

        coVerify { session.send(match { it is Frame.Text }) }
    }

    @Test
    fun `relayResponseToTechnician to unknown session is no-op`() = runBlocking {
        relay.relayResponseToTechnician("unknown-sess", """{"type":"diag_response"}""")
        delay(100)
        // Should not throw and no sessions should be affected
        assertEquals(0, relay.activeSessionCount())
    }

    @Test
    fun `relayResponseToTechnician removes session on send failure`() = runBlocking {
        val failingSession = mockk<DefaultWebSocketServerSession>(relaxed = true)
        coEvery { failingSession.send(any()) } throws RuntimeException("Connection closed")

        relay.registerTechnician("sess-fail", "store-A", failingSession)
        assertTrue(relay.isTechnicianConnected("sess-fail"))

        relay.relayResponseToTechnician("sess-fail", """{"type":"diag_response"}""")

        delay(300) // allow scope.launch to execute and cleanup

        assertFalse(relay.isTechnicianConnected("sess-fail"))
        assertEquals(0, relay.activeSessionCount())
    }

    // ── relayCommandToStore with multiple sessions ────────────────────────

    @Test
    fun `relayCommandToStore routes to correct store per session`() {
        val s1 = mockk<DefaultWebSocketServerSession>(relaxed = true)
        val s2 = mockk<DefaultWebSocketServerSession>(relaxed = true)

        relay.registerTechnician("sess-1", "store-A", s1)
        relay.registerTechnician("sess-2", "store-B", s2)

        relay.relayCommandToStore("sess-1", """{"cmd":"get_logs"}""")
        verify(exactly = 1) { hub.broadcast("store-A", """{"cmd":"get_logs"}""") }
        verify(exactly = 0) { hub.broadcast("store-B", any()) }
    }

    // ── close with active response relays ─────────────────────────────────

    @Test
    fun `close clears all sessions and store mappings`() {
        val s1 = mockk<DefaultWebSocketServerSession>(relaxed = true)
        val s2 = mockk<DefaultWebSocketServerSession>(relaxed = true)

        relay.registerTechnician("sess-1", "store-A", s1)
        relay.registerTechnician("sess-2", "store-B", s2)

        relay.close()

        assertEquals(0, relay.activeSessionCount())
        assertFalse(relay.isTechnicianConnected("sess-1"))
        assertFalse(relay.isTechnicianConnected("sess-2"))
    }
}
