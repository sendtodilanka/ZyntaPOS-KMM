package com.zyntasolutions.zyntapos.sync.models

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * C7: Tests for WebSocket message serialization/deserialization.
 *
 * Covers: WsAck, WsDelta, WsNotify, WsForceSync, WsPing, WsPong,
 * WsDiagCommand, WsDiagResponse, WsDiagSessionEvent, SyncNotification.
 */
class WebSocketMessagesTest {

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    // ── WsAck ─────────────────────────────────────────────────────────────

    @Test
    fun `WsAck serializes with correct type`() {
        val ack: WsMessage = WsAck(storeId = "store-1", deviceId = "dev-1", connectedAt = 1710000000000L)
        val encoded = json.encodeToString(ack)
        assertTrue(encoded.contains("\"type\":\"ack\""))
        assertTrue(encoded.contains("\"storeId\":\"store-1\""))
        assertTrue(encoded.contains("\"deviceId\":\"dev-1\""))
        assertTrue(encoded.contains("\"connectedAt\":1710000000000"))
    }

    @Test
    fun `WsAck discriminator is ack`() {
        val ack: WsMessage = WsAck(storeId = "s", deviceId = "d", connectedAt = 0L)
        val encoded = json.encodeToString(ack)
        assertTrue(encoded.contains("\"type\":\"ack\""))
    }

    @Test
    fun `WsAck round-trip serialization`() {
        val original = WsAck(storeId = "store-A", deviceId = "dev-X", connectedAt = 999L)
        val encoded = json.encodeToString(original)
        val decoded = json.decodeFromString<WsAck>(encoded)
        assertEquals(original, decoded)
    }

    // ── WsDelta ───────────────────────────────────────────────────────────

    @Test
    fun `WsDelta serializes with correct type`() {
        val delta: WsMessage = WsDelta(storeId = "store-1", operationCount = 5, latestSeq = 42L)
        val encoded = json.encodeToString(delta)
        assertTrue(encoded.contains("\"type\":\"delta\""))
        assertTrue(encoded.contains("\"operationCount\":5"))
        assertTrue(encoded.contains("\"latestSeq\":42"))
    }

    @Test
    fun `WsDelta with entityTypes serializes correctly`() {
        val delta = WsDelta(
            storeId = "store-1",
            operationCount = 3,
            latestSeq = 10L,
            entityTypes = listOf("PRODUCT", "ORDER"),
        )
        val encoded = json.encodeToString(delta)
        assertTrue(encoded.contains("PRODUCT"))
        assertTrue(encoded.contains("ORDER"))
    }

    @Test
    fun `WsDelta default entityTypes is empty`() {
        val delta = WsDelta(storeId = "s", operationCount = 1, latestSeq = 1L)
        assertTrue(delta.entityTypes.isEmpty())
    }

    @Test
    fun `WsDelta round-trip serialization`() {
        val original = WsDelta(
            storeId = "store-B",
            operationCount = 7,
            latestSeq = 100L,
            entityTypes = listOf("CUSTOMER"),
        )
        val encoded = json.encodeToString(original)
        val decoded = json.decodeFromString<WsDelta>(encoded)
        assertEquals(original, decoded)
    }

    // ── WsNotify ──────────────────────────────────────────────────────────

    @Test
    fun `WsNotify serializes with correct type`() {
        val notify: WsMessage = WsNotify(storeId = "store-1", latestSeq = 99L)
        val encoded = json.encodeToString(notify)
        assertTrue(encoded.contains("\"type\":\"notify\""))
        assertTrue(encoded.contains("\"message\":\"sync_available\""))
    }

    @Test
    fun `WsNotify default message is sync_available`() {
        val notify = WsNotify(storeId = "s", latestSeq = 1L)
        assertEquals("sync_available", notify.message)
    }

    @Test
    fun `WsNotify with custom entityTypes`() {
        val notify = WsNotify(
            storeId = "store-1",
            latestSeq = 50L,
            entityTypes = listOf("PRODUCT", "CATEGORY", "SUPPLIER"),
        )
        assertEquals(3, notify.entityTypes.size)
    }

    @Test
    fun `WsNotify round-trip serialization`() {
        val original = WsNotify(
            storeId = "store-C",
            message = "sync_available",
            latestSeq = 200L,
            entityTypes = listOf("ORDER"),
        )
        val encoded = json.encodeToString(original)
        val decoded = json.decodeFromString<WsNotify>(encoded)
        assertEquals(original, decoded)
    }

    // ── WsForceSync ───────────────────────────────────────────────────────

    @Test
    fun `WsForceSync serializes with correct type`() {
        val forceSync: WsMessage = WsForceSync(storeId = "store-1")
        val encoded = json.encodeToString(forceSync)
        assertTrue(encoded.contains("\"type\":\"force_sync\""))
        assertTrue(encoded.contains("\"storeId\":\"store-1\""))
    }

    @Test
    fun `WsForceSync round-trip serialization`() {
        val original = WsForceSync(storeId = "store-X")
        val encoded = json.encodeToString(original)
        val decoded = json.decodeFromString<WsForceSync>(encoded)
        assertEquals(original, decoded)
    }

    // ── WsPing / WsPong ───────────────────────────────────────────────────

    @Test
    fun `WsPing serializes with correct type`() {
        val ping: WsMessage = WsPing()
        val encoded = json.encodeToString(ping)
        assertTrue(encoded.contains("\"type\":\"ping\""))
    }

    @Test
    fun `WsPong serializes with correct type`() {
        val pong: WsMessage = WsPong()
        val encoded = json.encodeToString(pong)
        assertTrue(encoded.contains("\"type\":\"pong\""))
    }

    @Test
    fun `WsPing round-trip serialization`() {
        val original = WsPing()
        val encoded = json.encodeToString(original)
        val decoded = json.decodeFromString<WsPing>(encoded)
        assertEquals(original, decoded)
    }

    @Test
    fun `WsPong round-trip serialization`() {
        val original = WsPong()
        val encoded = json.encodeToString(original)
        val decoded = json.decodeFromString<WsPong>(encoded)
        assertEquals(original, decoded)
    }

    // ── WsDiagCommand ─────────────────────────────────────────────────────

    @Test
    fun `WsDiagCommand serializes with correct type`() {
        val cmd: WsMessage = WsDiagCommand(sessionId = "sess-1", command = "get_logs")
        val encoded = json.encodeToString(cmd)
        assertTrue(encoded.contains("\"type\":\"diag_command\""))
        assertTrue(encoded.contains("\"sessionId\":\"sess-1\""))
        assertTrue(encoded.contains("\"command\":\"get_logs\""))
    }

    @Test
    fun `WsDiagCommand default payload is empty JSON object`() {
        val cmd = WsDiagCommand(sessionId = "s", command = "c")
        assertEquals("{}", cmd.payload)
    }

    @Test
    fun `WsDiagCommand with custom payload`() {
        val cmd = WsDiagCommand(
            sessionId = "sess-1",
            command = "query",
            payload = """{"table":"products","limit":10}""",
        )
        val encoded = json.encodeToString(cmd)
        assertTrue(encoded.contains("products"))
    }

    @Test
    fun `WsDiagCommand round-trip serialization`() {
        val original = WsDiagCommand(sessionId = "sess-X", command = "get_info", payload = """{"key":"value"}""")
        val encoded = json.encodeToString(original)
        val decoded = json.decodeFromString<WsDiagCommand>(encoded)
        assertEquals(original, decoded)
    }

    // ── WsDiagResponse ────────────────────────────────────────────────────

    @Test
    fun `WsDiagResponse serializes with correct type`() {
        val resp: WsMessage = WsDiagResponse(sessionId = "sess-1", command = "get_logs", result = "OK")
        val encoded = json.encodeToString(resp)
        assertTrue(encoded.contains("\"type\":\"diag_response\""))
    }

    @Test
    fun `WsDiagResponse default success is true`() {
        val resp = WsDiagResponse(sessionId = "s", command = "c", result = "r")
        assertTrue(resp.success)
    }

    @Test
    fun `WsDiagResponse with failure`() {
        val resp = WsDiagResponse(sessionId = "s", command = "c", result = "error", success = false)
        assertEquals(false, resp.success)
    }

    @Test
    fun `WsDiagResponse round-trip serialization`() {
        val original = WsDiagResponse(sessionId = "sess-Y", command = "get_db_size", result = "1024", success = true)
        val encoded = json.encodeToString(original)
        val decoded = json.decodeFromString<WsDiagResponse>(encoded)
        assertEquals(original, decoded)
    }

    // ── WsDiagSessionEvent ────────────────────────────────────────────────

    @Test
    fun `WsDiagSessionEvent serializes with correct type`() {
        val event: WsMessage = WsDiagSessionEvent(sessionId = "sess-1", storeId = "store-1", event = "SESSION_STARTED")
        val encoded = json.encodeToString(event)
        assertTrue(encoded.contains("\"type\":\"diag_session_event\""))
        assertTrue(encoded.contains("\"event\":\"SESSION_STARTED\""))
    }

    @Test
    fun `WsDiagSessionEvent round-trip serialization`() {
        val original = WsDiagSessionEvent(sessionId = "sess-Z", storeId = "store-Z", event = "SESSION_ENDED")
        val encoded = json.encodeToString(original)
        val decoded = json.decodeFromString<WsDiagSessionEvent>(encoded)
        assertEquals(original, decoded)
    }

    // ── SyncNotification ──────────────────────────────────────────────────

    @Test
    fun `SyncNotification serializes correctly`() {
        val notif = SyncNotification(
            storeId = "store-1",
            senderDeviceId = "dev-1",
            operationCount = 5,
            latestSeq = 42L,
        )
        val encoded = json.encodeToString(notif)
        assertTrue(encoded.contains("\"storeId\":\"store-1\""))
        assertTrue(encoded.contains("\"senderDeviceId\":\"dev-1\""))
        assertTrue(encoded.contains("\"operationCount\":5"))
        assertTrue(encoded.contains("\"latestSeq\":42"))
    }

    @Test
    fun `SyncNotification default entityTypes is empty`() {
        val notif = SyncNotification(storeId = "s", senderDeviceId = "d", operationCount = 1, latestSeq = 1L)
        assertTrue(notif.entityTypes.isEmpty())
    }

    @Test
    fun `SyncNotification with entityTypes round-trip`() {
        val original = SyncNotification(
            storeId = "store-1",
            senderDeviceId = "dev-1",
            operationCount = 3,
            latestSeq = 10L,
            entityTypes = listOf("PRODUCT", "ORDER"),
        )
        val encoded = json.encodeToString(original)
        val decoded = json.decodeFromString<SyncNotification>(encoded)
        assertEquals(original, decoded)
    }

    // ── Cross-type discrimination ─────────────────────────────────────────

    @Test
    fun `different message types have different type discriminators`() {
        val messages: List<WsMessage> = listOf(
            WsAck(storeId = "s", deviceId = "d", connectedAt = 0L),
            WsDelta(storeId = "s", operationCount = 0, latestSeq = 0L),
            WsNotify(storeId = "s", latestSeq = 0L),
            WsForceSync(storeId = "s"),
            WsPing(),
            WsPong(),
            WsDiagCommand(sessionId = "s", command = "c"),
            WsDiagResponse(sessionId = "s", command = "c", result = "r"),
            WsDiagSessionEvent(sessionId = "s", storeId = "s", event = "e"),
        )
        val types = messages.map { msg ->
            val encoded = json.encodeToString(msg)
            Regex("\"type\":\"([^\"]+)\"").find(encoded)!!.groupValues[1]
        }
        // All type discriminators should be unique
        assertEquals(types.size, types.toSet().size)
    }
}
