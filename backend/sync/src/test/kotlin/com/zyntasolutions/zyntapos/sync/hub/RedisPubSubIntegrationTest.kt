package com.zyntasolutions.zyntapos.sync.hub

import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import com.zyntasolutions.zyntapos.sync.models.SyncNotification

/**
 * A1.1 — Integration tests for [RedisPubSubListener] with a real Redis container.
 *
 * Tests verify that when a [SyncNotification] JSON is published to the
 * `sync:delta:{storeId}` channel, [WebSocketHub.broadcast] is called for
 * the correct storeId with the expected message content and excludeDeviceId.
 *
 * Uses [runBlocking] (not runTest) since real I/O requires wall-clock time — virtual
 * time from TestCoroutineScheduler does not advance Redis network operations.
 *
 * Extends [AbstractRedisIntegrationTest] which lazily starts a singleton Redis 7 container.
 * A warmup PING is issued before each test to ensure the Redis TCP connection is fully
 * established on CI — this eliminates flakiness from cold-start network overhead.
 */
class RedisPubSubIntegrationTest : AbstractRedisIntegrationTest() {

    private val json = Json { ignoreUnknownKeys = true }
    private var listener: RedisPubSubListener? = null

    /** Warm up the shared connection before each test to avoid TCP cold-start delays on CI. */
    @BeforeTest
    fun warmUpRedis() {
        redisConnection.sync().ping()
    }

    @AfterTest
    fun stopListener() {
        listener?.stop()
        listener = null
    }

    @Test
    fun `delta notification is broadcast to matching store`() = runBlocking {
        val hub = mockk<WebSocketHub>(relaxed = true)
        val testStoreId = "store-redis-${System.nanoTime()}"
        val senderDevice = "sender-device-1"

        listener = RedisPubSubListener(redisUrl(), hub)
        launch(Dispatchers.IO) { listener!!.start() }

        // Allow time for psubscribe handshake to complete (real wall-clock time needed).
        // 1500ms is sufficient even on heavily loaded CI runners.
        Thread.sleep(1500)

        val notification = SyncNotification(
            storeId = testStoreId,
            senderDeviceId = senderDevice,
            operationCount = 3,
            latestSeq = 42L,
            entityTypes = listOf("PRODUCT"),
        )
        redisConnection.sync().publish("sync:delta:$testStoreId", json.encodeToString(notification))

        // Allow time for message delivery through Redis → Lettuce callback → hub.broadcast
        Thread.sleep(800)

        verify {
            hub.broadcast(
                eq(testStoreId),
                any(),
                excludeDeviceId = eq(senderDevice),
            )
        }
    }

    @Test
    fun `force_sync command is broadcast to correct store`() = runBlocking {
        val hub = mockk<WebSocketHub>(relaxed = true)
        val testStoreId = "store-cmd-${System.nanoTime()}"

        listener = RedisPubSubListener(redisUrl(), hub)
        launch(Dispatchers.IO) { listener!!.start() }

        Thread.sleep(1500)

        val command = """{"type":"force_sync","storeId":"$testStoreId"}"""
        redisConnection.sync().publish("sync:commands", command)

        Thread.sleep(800)

        verify {
            hub.broadcast(eq(testStoreId), eq(command))
        }
    }

    @Test
    fun `delta message to unknown store is still broadcast to hub`() = runBlocking {
        val hub = mockk<WebSocketHub>(relaxed = true)
        val unknownStore = "store-unknown-${System.nanoTime()}"

        listener = RedisPubSubListener(redisUrl(), hub)
        launch(Dispatchers.IO) { listener!!.start() }

        Thread.sleep(1500)

        val notification = SyncNotification(
            storeId = unknownStore,
            senderDeviceId = "dev-x",
            operationCount = 1,
            latestSeq = 1L,
        )
        redisConnection.sync().publish("sync:delta:$unknownStore", json.encodeToString(notification))

        Thread.sleep(800)

        // broadcast is called; WebSocketHub.broadcast for unknown store is a no-op internally
        verify {
            hub.broadcast(eq(unknownStore), any(), excludeDeviceId = any())
        }
    }
}
