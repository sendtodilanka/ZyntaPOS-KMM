package com.zyntasolutions.zyntapos.sync.hub

import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * C7: Unit tests for [SyncForwarder] — verifies error handling and response
 * envelope formatting when the API service is unreachable.
 *
 * These tests exercise the failure path (no API service running) which is the
 * most critical path to test — success paths require a live API service.
 */
class SyncForwarderTest {

    // Use a non-routable address to ensure connection failure
    private val forwarder = SyncForwarder("http://127.0.0.1:1")

    // ── Push failure handling ─────────────────────────────────────────────

    @Test
    fun `forwardPush returns error envelope when API unreachable`() = runBlocking {
        val result = forwarder.forwardPush("""{"type":"sync_push","payload":{}}""", "fake-token")
        assertTrue(result.contains("\"type\":\"sync_push_response\""))
        assertTrue(result.contains("\"status\":502"))
        assertTrue(result.contains("\"error\":"))
    }

    @Test
    fun `forwardPush returns error envelope when bearer token is null`() = runBlocking {
        val result = forwarder.forwardPush("""{"type":"sync_push","payload":{}}""", null)
        assertTrue(result.contains("\"type\":\"sync_push_response\""))
        assertTrue(result.contains("\"status\":502"))
    }

    @Test
    fun `forwardPush handles malformed JSON gracefully`() = runBlocking {
        val result = forwarder.forwardPush("not valid json", "token")
        assertTrue(result.contains("\"type\":\"sync_push_response\""))
        assertTrue(result.contains("\"status\":502"))
    }

    // ── Pull failure handling ─────────────────────────────────────────────

    @Test
    fun `forwardPull returns error envelope when API unreachable`() = runBlocking {
        val result = forwarder.forwardPull("store-1", "device-1", 0L, 50, "fake-token")
        assertTrue(result.contains("\"type\":\"sync_pull_response\""))
        assertTrue(result.contains("\"status\":502"))
        assertTrue(result.contains("\"error\":"))
    }

    @Test
    fun `forwardPull returns error envelope when bearer token is null`() = runBlocking {
        val result = forwarder.forwardPull("store-1", "device-1", 0L, 50, null)
        assertTrue(result.contains("\"type\":\"sync_pull_response\""))
        assertTrue(result.contains("\"status\":502"))
    }

    // ── Response envelope structure ───────────────────────────────────────

    @Test
    fun `push error response does not contain unescaped quotes in error message`() = runBlocking {
        val result = forwarder.forwardPush("""{"type":"sync_push"}""", "token")
        // The error message should have quotes replaced with single quotes
        val afterError = result.substringAfter("\"error\":\"")
        val errorContent = afterError.substringBefore("\"}")
        assertTrue(!errorContent.contains("\""), "Error message should not contain unescaped double quotes")
    }

    // ── close ─────────────────────────────────────────────────────────────

    @Test
    fun `close does not throw`() {
        val f = SyncForwarder("http://127.0.0.1:1")
        f.close() // Should not throw
    }
}
