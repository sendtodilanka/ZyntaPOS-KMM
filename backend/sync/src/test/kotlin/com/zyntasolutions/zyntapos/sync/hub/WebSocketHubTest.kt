package com.zyntasolutions.zyntapos.sync.hub

import io.ktor.server.websocket.DefaultWebSocketServerSession
import io.ktor.websocket.CloseReason
import io.ktor.websocket.Frame
import io.ktor.websocket.WebSocketExtension
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.channels.SendChannel
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Unit tests for WebSocketHub — verifies connection tracking state.
 * Broadcast I/O is not tested here (requires Ktor testApplication).
 */
class WebSocketHubTest {

    /** Minimal stub — hub stores references but never calls methods in these tests. */
    private fun stubSession(): DefaultWebSocketServerSession =
        object : DefaultWebSocketServerSession {
            override val incoming: ReceiveChannel<Frame> get() = error("stub")
            override val outgoing: SendChannel<Frame> get() = error("stub")
            override val extensions: List<WebSocketExtension<*>> get() = emptyList()
            override var maxFrameSize: Long = Long.MAX_VALUE
            override val closeReason = CompletableDeferred<CloseReason?>()
            override suspend fun flush() {}
            override suspend fun send(frame: Frame) {}
            override val coroutineContext = EmptyCoroutineContext
            override suspend fun start(negotiatedExtensions: List<WebSocketExtension<*>>) {}
        }

    @Test
    fun `initial state has no connections`() {
        val hub = WebSocketHub()
        assertEquals(0, hub.totalConnections())
        assertEquals(0, hub.activeStoreCount())
    }

    @Test
    fun `registering increments connection count`() {
        val hub = WebSocketHub()
        hub.register("store-A", "device-1", stubSession())
        assertEquals(1, hub.connectionCount("store-A"))
        assertEquals(1, hub.totalConnections())
    }

    @Test
    fun `unregistering decrements connection count`() {
        val hub = WebSocketHub()
        hub.register("store-A", "device-1", stubSession())
        hub.unregister("store-A", "device-1")
        assertEquals(0, hub.connectionCount("store-A"))
        assertEquals(0, hub.activeStoreCount())
    }

    @Test
    fun `multiple devices in same store tracked separately`() {
        val hub = WebSocketHub()
        hub.register("store-A", "device-1", stubSession())
        hub.register("store-A", "device-2", stubSession())
        hub.register("store-B", "device-3", stubSession())
        assertEquals(2, hub.connectionCount("store-A"))
        assertEquals(1, hub.connectionCount("store-B"))
        assertEquals(3, hub.totalConnections())
        assertEquals(2, hub.activeStoreCount())
    }

    @Test
    fun `reconnect replaces existing session for same device`() {
        val hub = WebSocketHub()
        hub.register("store-A", "device-1", stubSession())
        hub.register("store-A", "device-1", stubSession())
        assertEquals(1, hub.connectionCount("store-A"))
    }

    @Test
    fun `unregistering last device removes store entry`() {
        val hub = WebSocketHub()
        hub.register("store-A", "device-1", stubSession())
        hub.unregister("store-A", "device-1")
        assertEquals(0, hub.activeStoreCount())
    }

    @Test
    fun `connectionCount returns 0 for unknown store`() {
        val hub = WebSocketHub()
        assertEquals(0, hub.connectionCount("no-such-store"))
    }

    @Test
    fun `broadcast to unknown store is no-op`() {
        val hub = WebSocketHub()
        hub.broadcast("no-such-store", """{"type":"ping"}""")
        // Should not throw
    }

    @Test
    fun `close does not throw`() {
        val hub = WebSocketHub()
        hub.register("store-A", "device-1", stubSession())
        hub.close()  // Should cancel scope without throwing
    }
}
