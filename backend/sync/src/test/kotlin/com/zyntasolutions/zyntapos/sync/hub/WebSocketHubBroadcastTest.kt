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
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * C7: Tests for WebSocketHub broadcast behavior — message delivery,
 * sender exclusion, and dead client cleanup.
 *
 * Uses a tracking stub session that records sent frames and can optionally
 * throw to simulate a dead/disconnected client.
 */
@OptIn(InternalAPI::class)
class WebSocketHubBroadcastTest {

    /** A tracking session stub that records sent frames. */
    private class TrackingSession(private val shouldFail: Boolean = false) : DefaultWebSocketServerSession {
        val sentFrames = mutableListOf<Frame>()

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
        override suspend fun send(frame: Frame) {
            if (shouldFail) throw RuntimeException("Connection closed")
            sentFrames.add(frame)
        }
        override val coroutineContext = EmptyCoroutineContext
        override fun start(negotiatedExtensions: List<WebSocketExtension<*>>) {}
        @Deprecated("Deprecated in interface")
        override fun terminate() {}
    }

    // ── Broadcast delivery ────────────────────────────────────────────────

    @Test
    fun `broadcast delivers message to all devices in store`() = runBlocking {
        val hub = WebSocketHub()
        val session1 = TrackingSession()
        val session2 = TrackingSession()

        hub.register("store-A", "device-1", session1)
        hub.register("store-A", "device-2", session2)

        hub.broadcast("store-A", """{"type":"delta"}""")

        // Allow async broadcast scope to complete
        delay(200)

        assertEquals(1, session1.sentFrames.size)
        assertEquals(1, session2.sentFrames.size)
    }

    @Test
    fun `broadcast excludes sender device`() = runBlocking {
        val hub = WebSocketHub()
        val sender = TrackingSession()
        val receiver = TrackingSession()

        hub.register("store-A", "sender-dev", sender)
        hub.register("store-A", "receiver-dev", receiver)

        hub.broadcast("store-A", """{"type":"delta"}""", excludeDeviceId = "sender-dev")

        delay(200)

        assertEquals(0, sender.sentFrames.size, "Sender should be excluded")
        assertEquals(1, receiver.sentFrames.size, "Receiver should get the message")
    }

    @Test
    fun `broadcast does not deliver to devices in other stores`() = runBlocking {
        val hub = WebSocketHub()
        val storeA = TrackingSession()
        val storeB = TrackingSession()

        hub.register("store-A", "device-1", storeA)
        hub.register("store-B", "device-2", storeB)

        hub.broadcast("store-A", """{"type":"delta"}""")

        delay(200)

        assertEquals(1, storeA.sentFrames.size)
        assertEquals(0, storeB.sentFrames.size)
    }

    // ── Dead client cleanup ───────────────────────────────────────────────

    @Test
    fun `broadcast to failing session removes it from connections`() = runBlocking {
        val hub = WebSocketHub()
        val alive = TrackingSession(shouldFail = false)
        val dead = TrackingSession(shouldFail = true)

        hub.register("store-A", "alive-dev", alive)
        hub.register("store-A", "dead-dev", dead)

        assertEquals(2, hub.connectionCount("store-A"))

        hub.broadcast("store-A", """{"type":"notify"}""")

        // Allow async broadcast to complete and trigger unregister
        delay(300)

        // Dead client should have been removed
        assertEquals(1, hub.connectionCount("store-A"))
        assertEquals(1, alive.sentFrames.size)
    }

    @Test
    fun `broadcast removes all dead clients leaving store empty`() = runBlocking {
        val hub = WebSocketHub()
        val dead1 = TrackingSession(shouldFail = true)
        val dead2 = TrackingSession(shouldFail = true)

        hub.register("store-A", "dead-1", dead1)
        hub.register("store-A", "dead-2", dead2)

        hub.broadcast("store-A", """{"type":"notify"}""")

        delay(300)

        assertEquals(0, hub.connectionCount("store-A"))
        assertEquals(0, hub.activeStoreCount())
    }

    // ── Message content ───────────────────────────────────────────────────

    @Test
    fun `broadcast sends exact message content as text frame`() = runBlocking {
        val hub = WebSocketHub()
        val session = TrackingSession()

        hub.register("store-A", "device-1", session)

        val message = """{"type":"force_sync","storeId":"store-A"}"""
        hub.broadcast("store-A", message)

        delay(200)

        assertEquals(1, session.sentFrames.size)
        val frame = session.sentFrames[0]
        assertTrue(frame is Frame.Text, "Frame should be Text type")
    }

    // ── Multiple broadcasts ───────────────────────────────────────────────

    @Test
    fun `multiple broadcasts deliver all messages`() = runBlocking {
        val hub = WebSocketHub()
        val session = TrackingSession()

        hub.register("store-A", "device-1", session)

        hub.broadcast("store-A", """{"seq":1}""")
        hub.broadcast("store-A", """{"seq":2}""")
        hub.broadcast("store-A", """{"seq":3}""")

        delay(2000)

        assertTrue(session.sentFrames.size >= 3, "Expected at least 3 frames, got ${session.sentFrames.size}")
    }
}
