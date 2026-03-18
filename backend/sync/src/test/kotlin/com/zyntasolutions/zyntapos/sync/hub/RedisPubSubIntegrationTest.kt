package com.zyntasolutions.zyntapos.sync.hub

import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import com.zyntasolutions.zyntapos.sync.models.SyncNotification

/**
 * A1.1 — Integration tests for [RedisPubSubListener] with a real Redis container.
 *
 * Tests verify that when a [SyncNotification] JSON is published to the
 * `sync:delta:{storeId}` channel, [WebSocketHub.broadcast] is called for
 * the correct storeId with the expected message content and excludeDeviceId.
 *
 * Extends [AbstractRedisIntegrationTest] which starts a singleton Redis 7 container.
 */
class RedisPubSubIntegrationTest : AbstractRedisIntegrationTest() {

    private val json = Json { ignoreUnknownKeys = true }
    private var listener: RedisPubSubListener? = null

    @AfterEach
    fun stopListener() {
        listener?.stop()
        listener = null
    }

    @Test
    fun `delta notification is broadcast to matching store`() = runTest {
        val hub = mockk<WebSocketHub>(relaxed = true)
        val testStoreId = "store-redis-${System.nanoTime()}"
        val senderDevice = "sender-device-1"

        listener = RedisPubSubListener(redisUrl(), hub)
        val job = launch { listener!!.start() }

        // Allow time for psubscribe to complete before publishing
        delay(500)

        val notification = SyncNotification(
            storeId = testStoreId,
            senderDeviceId = senderDevice,
            operationCount = 3,
            latestSeq = 42L,
            entityTypes = listOf("PRODUCT"),
        )
        redisConnection.sync().publish("sync:delta:$testStoreId", json.encodeToString(notification))

        // Allow time for message delivery from Redis
        delay(300)

        verify {
            hub.broadcast(
                eq(testStoreId),
                any(),
                excludeDeviceId = eq(senderDevice),
            )
        }

        job.cancel()
    }

    @Test
    fun `force_sync command is broadcast to correct store`() = runTest {
        val hub = mockk<WebSocketHub>(relaxed = true)
        val testStoreId = "store-cmd-${System.nanoTime()}"

        listener = RedisPubSubListener(redisUrl(), hub)
        val job = launch { listener!!.start() }

        delay(500)

        val command = """{"type":"force_sync","storeId":"$testStoreId"}"""
        redisConnection.sync().publish("sync:commands", command)

        delay(300)

        verify {
            hub.broadcast(eq(testStoreId), eq(command))
        }

        job.cancel()
    }

    @Test
    fun `delta message to unknown store is still broadcast (hub handles unknown store)`() = runTest {
        val hub = mockk<WebSocketHub>(relaxed = true)
        val unknownStore = "store-unknown-${System.nanoTime()}"

        listener = RedisPubSubListener(redisUrl(), hub)
        val job = launch { listener!!.start() }

        delay(500)

        val notification = SyncNotification(
            storeId = unknownStore,
            senderDeviceId = "dev-x",
            operationCount = 1,
            latestSeq = 1L,
        )
        redisConnection.sync().publish("sync:delta:$unknownStore", json.encodeToString(notification))

        delay(300)

        // broadcast is called; WebSocketHub.broadcast for unknown store is a no-op internally
        verify {
            hub.broadcast(eq(unknownStore), any(), excludeDeviceId = any())
        }

        job.cancel()
    }
}
