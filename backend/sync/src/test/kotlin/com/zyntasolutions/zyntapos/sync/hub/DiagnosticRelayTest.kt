package com.zyntasolutions.zyntapos.sync.hub

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Unit tests for DiagnosticRelay — technician session tracking, store mapping,
 * and relay routing logic. WebSocket send/receive is mocked.
 */
class DiagnosticRelayTest {

    private val hub = mockk<WebSocketHub>(relaxed = true)
    private val relay = DiagnosticRelay(hub)

    // ── Registration / Unregistration ────────────────────────────────────

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
    fun `relayCommandToStore broadcasts to correct store via hub`() {
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

    // ── isTechnicianConnected / getStoreId ───────────────────────────────

    @Test
    fun `isTechnicianConnected returns false for unknown session`() {
        assertFalse(relay.isTechnicianConnected("nope"))
    }

    @Test
    fun `getStoreId returns null for unknown session`() {
        assertNull(relay.getStoreId("nope"))
    }

    // ── close ────────────────────────────────────────────────────────────

    @Test
    fun `close clears all sessions`() {
        val s1 = mockk<io.ktor.server.websocket.DefaultWebSocketServerSession>(relaxed = true)
        relay.registerTechnician("sess-1", "store-A", s1)

        relay.close()

        assertEquals(0, relay.activeSessionCount())
        assertFalse(relay.isTechnicianConnected("sess-1"))
    }
}
