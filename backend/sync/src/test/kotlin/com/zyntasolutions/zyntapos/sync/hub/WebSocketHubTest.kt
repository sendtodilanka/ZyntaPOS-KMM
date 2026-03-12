package com.zyntasolutions.zyntapos.sync.hub

import io.ktor.server.application.ApplicationCall
import io.ktor.server.websocket.DefaultWebSocketServerSession
import io.ktor.utils.io.InternalAPI
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
 * S3-7: Expanded unit tests for WebSocketHub — connection tracking lifecycle.
 *
 * Tests cover:
 * - Registration / unregistration state tracking
 * - Multi-device multi-store scenarios
 * - Session replacement on reconnect
 * - Store cleanup after last device disconnects
 * - Broadcast to unknown stores (no-op)
 * - Hub close lifecycle
 * - Edge cases: unregister non-existent device, unregister from unknown store
 */
@OptIn(InternalAPI::class)
class WebSocketHubTest {

    /** Minimal stub — hub stores references but never calls methods in these tests. */
    private fun stubSession(): DefaultWebSocketServerSession =
        object : DefaultWebSocketServerSession {
            override val incoming: ReceiveChannel<Frame> get() = error("stub")
            override val outgoing: SendChannel<Frame> get() = error("stub")
            override val extensions: List<WebSocketExtension<*>> get() = emptyList()
            override var maxFrameSize: Long = Long.MAX_VALUE
            override var pingIntervalMillis: Long = 0L
            override var timeoutMillis: Long = 0L
            override var masking: Boolean = false
            override val call: ApplicationCall get() = error("stub")
            override val closeReason = CompletableDeferred<CloseReason?>()
            override suspend fun flush() {}
            override suspend fun send(frame: Frame) {}
            override val coroutineContext = EmptyCoroutineContext
            override fun start(negotiatedExtensions: List<WebSocketExtension<*>>) {}
            @Deprecated("Deprecated in interface")
            override fun terminate() {}
        }

    // ── Initial state ───────────────────────────────────────────────────

    @Test
    fun `initial state has no connections`() {
        val hub = WebSocketHub()
        assertEquals(0, hub.totalConnections())
        assertEquals(0, hub.activeStoreCount())
    }

    // ── Registration ────────────────────────────────────────────────────

    @Test
    fun `registering increments connection count`() {
        val hub = WebSocketHub()
        hub.register("store-A", "device-1", stubSession())
        assertEquals(1, hub.connectionCount("store-A"))
        assertEquals(1, hub.totalConnections())
    }

    @Test
    fun `registering in different stores tracked independently`() {
        val hub = WebSocketHub()
        hub.register("store-A", "device-1", stubSession())
        hub.register("store-B", "device-1", stubSession())
        assertEquals(1, hub.connectionCount("store-A"))
        assertEquals(1, hub.connectionCount("store-B"))
        assertEquals(2, hub.totalConnections())
        assertEquals(2, hub.activeStoreCount())
    }

    // ── Unregistration ──────────────────────────────────────────────────

    @Test
    fun `unregistering decrements connection count`() {
        val hub = WebSocketHub()
        hub.register("store-A", "device-1", stubSession())
        hub.unregister("store-A", "device-1")
        assertEquals(0, hub.connectionCount("store-A"))
        assertEquals(0, hub.activeStoreCount())
    }

    @Test
    fun `unregistering non-existent device is no-op`() {
        val hub = WebSocketHub()
        hub.register("store-A", "device-1", stubSession())
        hub.unregister("store-A", "device-99") // doesn't exist
        assertEquals(1, hub.connectionCount("store-A"))
    }

    @Test
    fun `unregistering from unknown store is no-op`() {
        val hub = WebSocketHub()
        hub.unregister("no-such-store", "device-1") // should not throw
        assertEquals(0, hub.totalConnections())
    }

    // ── Multi-device scenarios ──────────────────────────────────────────

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
    fun `unregistering one device of many preserves others`() {
        val hub = WebSocketHub()
        hub.register("store-A", "device-1", stubSession())
        hub.register("store-A", "device-2", stubSession())
        hub.register("store-A", "device-3", stubSession())
        hub.unregister("store-A", "device-2")
        assertEquals(2, hub.connectionCount("store-A"))
        assertEquals(1, hub.activeStoreCount())
    }

    // ── Reconnect ───────────────────────────────────────────────────────

    @Test
    fun `reconnect replaces existing session for same device`() {
        val hub = WebSocketHub()
        hub.register("store-A", "device-1", stubSession())
        hub.register("store-A", "device-1", stubSession()) // replacement
        assertEquals(1, hub.connectionCount("store-A"))
    }

    // ── Store cleanup ───────────────────────────────────────────────────

    @Test
    fun `unregistering last device removes store entry`() {
        val hub = WebSocketHub()
        hub.register("store-A", "device-1", stubSession())
        hub.unregister("store-A", "device-1")
        assertEquals(0, hub.activeStoreCount())
    }

    @Test
    fun `store entry persists while devices remain`() {
        val hub = WebSocketHub()
        hub.register("store-A", "device-1", stubSession())
        hub.register("store-A", "device-2", stubSession())
        hub.unregister("store-A", "device-1")
        assertEquals(1, hub.activeStoreCount())
        assertEquals(1, hub.connectionCount("store-A"))
    }

    // ── connectionCount ─────────────────────────────────────────────────

    @Test
    fun `connectionCount returns 0 for unknown store`() {
        val hub = WebSocketHub()
        assertEquals(0, hub.connectionCount("no-such-store"))
    }

    // ── Broadcast ───────────────────────────────────────────────────────

    @Test
    fun `broadcast to unknown store is no-op`() {
        val hub = WebSocketHub()
        hub.broadcast("no-such-store", """{"type":"ping"}""")
        // Should not throw
    }

    @Test
    fun `broadcast with excludeDeviceId does not throw`() {
        val hub = WebSocketHub()
        hub.register("store-A", "device-1", stubSession())
        hub.broadcast("store-A", """{"type":"delta"}""", excludeDeviceId = "device-1")
        // No exception expected; device-1 is excluded
    }

    // ── Lifecycle ───────────────────────────────────────────────────────

    @Test
    fun `close does not throw`() {
        val hub = WebSocketHub()
        hub.register("store-A", "device-1", stubSession())
        hub.close()
    }

    @Test
    fun `close on empty hub does not throw`() {
        val hub = WebSocketHub()
        hub.close()
    }

    // ── Large scale ─────────────────────────────────────────────────────

    @Test
    fun `handles 100 devices across 10 stores`() {
        val hub = WebSocketHub()
        repeat(10) { storeIdx ->
            repeat(10) { deviceIdx ->
                hub.register("store-$storeIdx", "device-$deviceIdx", stubSession())
            }
        }
        assertEquals(100, hub.totalConnections())
        assertEquals(10, hub.activeStoreCount())
        assertEquals(10, hub.connectionCount("store-0"))
    }
}
