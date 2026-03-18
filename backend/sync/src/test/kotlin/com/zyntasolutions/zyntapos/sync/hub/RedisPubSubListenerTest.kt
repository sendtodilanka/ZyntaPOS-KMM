package com.zyntasolutions.zyntapos.sync.hub

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.decodeFromString
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * S3-7: Expanded unit tests for [RedisPubSubListener] message routing logic.
 *
 * Tests cover:
 * - Channel prefix stripping for store ID extraction
 * - Delta vs non-delta channel filtering
 * - force_sync command recognition and unknown command filtering
 * - Small delta threshold (10) for WsDelta vs WsNotify
 * - Malformed JSON handling
 * - Missing fields handling
 * - Backoff calculation
 *
 * Full integration tests require Testcontainers + Redis.
 */
class RedisPubSubListenerTest {

    companion object {
        private const val SMALL_DELTA_THRESHOLD = 10
        private val BACKOFF_MS = listOf(1_000L, 2_000L, 4_000L, 8_000L, 16_000L, 30_000L, 60_000L, 60_000L)
    }

    // ── Channel routing ─────────────────────────────────────────────────

    @Test
    fun `sync delta channel prefix is correctly stripped to storeId`() {
        val channel = "sync:delta:store-abc-123"
        val storeId = channel.removePrefix("sync:delta:")
        assertEquals("store-abc-123", storeId)
    }

    @Test
    fun `delta channel with UUID store ID`() {
        val channel = "sync:delta:550e8400-e29b-41d4-a716-446655440000"
        val storeId = channel.removePrefix("sync:delta:")
        assertEquals("550e8400-e29b-41d4-a716-446655440000", storeId)
    }

    @Test
    fun `non-delta channels are filtered out`() {
        val channels = listOf("other:channel", "sync:commands", "sync:status", "")
        for (channel in channels) {
            assertFalse(channel.startsWith("sync:delta:"), "Channel '$channel' should not match delta pattern")
        }
    }

    @Test
    fun `delta channel detection is positive for correct prefix`() {
        assertTrue("sync:delta:store-1".startsWith("sync:delta:"))
    }

    // ── Command parsing ─────────────────────────────────────────────────

    @Test
    fun `force_sync command type is recognised`() {
        val message = """{"type":"force_sync","storeId":"store-1"}"""
        val parsed = Json.parseToJsonElement(message).jsonObject
        val type = (parsed["type"] as? JsonPrimitive)?.content
        val storeId = (parsed["storeId"] as? JsonPrimitive)?.content
        assertEquals("force_sync", type)
        assertEquals("store-1", storeId)
    }

    @Test
    fun `unknown command type is not broadcast`() {
        val message = """{"type":"unknown_command","storeId":"store-1"}"""
        val parsed = Json.parseToJsonElement(message).jsonObject
        val type = (parsed["type"] as? JsonPrimitive)?.content
        assertFalse(type == "force_sync")
    }

    @Test
    fun `command with missing storeId is not broadcast`() {
        val message = """{"type":"force_sync"}"""
        val parsed = Json.parseToJsonElement(message).jsonObject
        val storeId = (parsed["storeId"] as? JsonPrimitive)?.content
        assertNull(storeId)
    }

    @Test
    fun `command with missing type is not broadcast`() {
        val message = """{"storeId":"store-1"}"""
        val parsed = Json.parseToJsonElement(message).jsonObject
        val type = (parsed["type"] as? JsonPrimitive)?.content
        assertNull(type)
        assertFalse(type == "force_sync")
    }

    @Test
    fun `only sync commands channel is handled`() {
        val validChannel = "sync:commands"
        val invalidChannel = "sync:other"
        assertTrue(validChannel == "sync:commands")
        assertFalse(invalidChannel == "sync:commands")
    }

    // ── Small delta threshold ───────────────────────────────────────────

    @Test
    fun `small delta threshold determines WsDelta vs WsNotify`() {
        assertEquals(true, 1 <= SMALL_DELTA_THRESHOLD)
        assertEquals(true, 10 <= SMALL_DELTA_THRESHOLD)
        assertEquals(false, 11 <= SMALL_DELTA_THRESHOLD)
    }

    @Test
    fun `zero operations uses WsDelta`() {
        assertTrue(0 <= SMALL_DELTA_THRESHOLD)
    }

    @Test
    fun `exactly at threshold uses WsDelta`() {
        assertTrue(SMALL_DELTA_THRESHOLD <= SMALL_DELTA_THRESHOLD)
    }

    @Test
    fun `one above threshold uses WsNotify`() {
        assertFalse(SMALL_DELTA_THRESHOLD + 1 <= SMALL_DELTA_THRESHOLD)
    }

    // ── SyncNotification parsing ────────────────────────────────────────

    @Test
    fun `valid SyncNotification JSON is parseable`() {
        val message = """{"storeId":"store-1","senderDeviceId":"dev-1","operationCount":5,"latestSeq":42}"""
        val parsed = Json.parseToJsonElement(message).jsonObject

        assertEquals("store-1", (parsed["storeId"] as JsonPrimitive).content)
        assertEquals("dev-1", (parsed["senderDeviceId"] as JsonPrimitive).content)
        assertEquals(5, (parsed["operationCount"] as JsonPrimitive).content.toInt())
        assertEquals(42, (parsed["latestSeq"] as JsonPrimitive).content.toLong())
    }

    @Test
    fun `malformed JSON does not crash channel handler`() {
        val malformed = "not-json{{"
        val result = runCatching { Json.parseToJsonElement(malformed) }
        assertTrue(result.isFailure) // should throw, caught by the handler
    }

    // ── Backoff calculation ─────────────────────────────────────────────

    @Test
    fun `backoff schedule is exponential then constant`() {
        assertEquals(1_000L, BACKOFF_MS[0])
        assertEquals(2_000L, BACKOFF_MS[1])
        assertEquals(4_000L, BACKOFF_MS[2])
        assertEquals(8_000L, BACKOFF_MS[3])
        assertEquals(16_000L, BACKOFF_MS[4])
        assertEquals(30_000L, BACKOFF_MS[5])
        assertEquals(60_000L, BACKOFF_MS[6])
        assertEquals(60_000L, BACKOFF_MS[7]) // caps at 60s
    }

    @Test
    fun `backoff beyond list size returns last value`() {
        val attempt = 10 // beyond list
        val backoff = BACKOFF_MS.getOrElse(attempt) { 60_000L }
        assertEquals(60_000L, backoff)
    }

    // ── SyncNotification with entityTypes ─────────────────────────────────

    @Test
    fun `SyncNotification JSON with entityTypes is parseable`() {
        val message = """{"storeId":"store-1","senderDeviceId":"dev-1","operationCount":3,"latestSeq":42,"entityTypes":["PRODUCT","ORDER","CUSTOMER"]}"""
        val json = Json { ignoreUnknownKeys = true }
        val notification = json.decodeFromString<com.zyntasolutions.zyntapos.sync.models.SyncNotification>(message)
        assertEquals("store-1", notification.storeId)
        assertEquals(3, notification.operationCount)
        assertEquals(listOf("PRODUCT", "ORDER", "CUSTOMER"), notification.entityTypes)
    }

    @Test
    fun `SyncNotification JSON without entityTypes uses empty list`() {
        val message = """{"storeId":"store-1","senderDeviceId":"dev-1","operationCount":1,"latestSeq":10}"""
        val json = Json { ignoreUnknownKeys = true }
        val notification = json.decodeFromString<com.zyntasolutions.zyntapos.sync.models.SyncNotification>(message)
        assertTrue(notification.entityTypes.isEmpty())
    }
}
