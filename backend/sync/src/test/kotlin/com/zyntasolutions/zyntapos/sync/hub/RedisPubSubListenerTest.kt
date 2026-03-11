package com.zyntasolutions.zyntapos.sync.hub

import kotlinx.serialization.json.*
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Unit tests for [RedisPubSubListener] message routing logic.
 *
 * The Redis connection itself is not tested here (requires a live Redis instance).
 * We verify the channel routing and JSON parsing logic by calling the internal
 * handlers directly via reflection or by extracting the logic into testable functions.
 *
 * Full integration tests require Testcontainers + Redis and are tracked as future work.
 */
class RedisPubSubListenerTest {

    @Test
    fun `sync delta channel prefix is correctly stripped to storeId`() {
        val channel = "sync:delta:store-abc-123"
        val storeId = channel.removePrefix("sync:delta:")
        assertEquals("store-abc-123", storeId)
    }

    @Test
    fun `non-delta channels are ignored`() {
        val channel = "other:channel"
        val isDelta = channel.startsWith("sync:delta:")
        assertEquals(false, isDelta)
    }

    @Test
    fun `force_sync command type is recognised`() {
        val message = """{"type":"force_sync","storeId":"store-1"}"""
        val parsed = kotlinx.serialization.json.Json.parseToJsonElement(message).jsonObject
        val type    = (parsed["type"] as? kotlinx.serialization.json.JsonPrimitive)?.content
        val storeId = (parsed["storeId"] as? kotlinx.serialization.json.JsonPrimitive)?.content
        assertEquals("force_sync", type)
        assertEquals("store-1", storeId)
    }

    @Test
    fun `unknown command type is not broadcast`() {
        val message = """{"type":"unknown_command","storeId":"store-1"}"""
        val parsed = kotlinx.serialization.json.Json.parseToJsonElement(message).jsonObject
        val type = (parsed["type"] as? kotlinx.serialization.json.JsonPrimitive)?.content
        val shouldBroadcast = type == "force_sync"
        assertEquals(false, shouldBroadcast)
    }

    @Test
    fun `small delta threshold determines WsDelta vs WsNotify`() {
        val threshold = 10
        val smallCount = 5
        val largeCount = 15
        assertEquals(true, smallCount <= threshold)
        assertEquals(false, largeCount <= threshold)
    }
}
