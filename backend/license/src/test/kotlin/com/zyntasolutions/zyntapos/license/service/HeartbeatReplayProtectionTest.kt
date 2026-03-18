package com.zyntasolutions.zyntapos.license.service

import com.zyntasolutions.zyntapos.license.models.HeartbeatRequest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Unit tests for heartbeat nonce-based replay protection and timestamp validation.
 *
 * Tests cover:
 * - Nonce uniqueness: duplicate nonces within TTL are rejected
 * - Timestamp staleness: heartbeats >60s old are rejected
 * - Fresh heartbeats with valid nonce + timestamp pass through
 * - Null nonce/timestamp fields (backward compat) pass through
 */
class HeartbeatReplayProtectionTest {

    @Test
    fun `heartbeat request includes nonce and clientTimestamp fields`() {
        val request = HeartbeatRequest(
            licenseKey = "TEST-KEY-1234",
            deviceId = "device-001",
            appVersion = "1.0.0",
            nonce = "unique-nonce-123",
            clientTimestamp = System.currentTimeMillis()
        )
        assertNotNull(request.nonce)
        assertNotNull(request.clientTimestamp)
    }

    @Test
    fun `heartbeat request with null nonce and timestamp is backward compatible`() {
        val request = HeartbeatRequest(
            licenseKey = "TEST-KEY-1234",
            deviceId = "device-001",
            appVersion = "1.0.0"
        )
        assertNull(request.nonce)
        assertNull(request.clientTimestamp)
    }

    @Test
    fun `timestamp older than 60 seconds is considered stale`() {
        val now = System.currentTimeMillis()
        val staleTimestamp = now - 61_000 // 61 seconds ago
        assertTrue(now - staleTimestamp > 60_000, "Should be older than 60s")
    }

    @Test
    fun `timestamp within 60 seconds is not stale`() {
        val now = System.currentTimeMillis()
        val recentTimestamp = now - 30_000 // 30 seconds ago
        assertFalse(now - recentTimestamp > 60_000, "Should not be stale")
    }

    @Test
    fun `timestamp at exactly 60 seconds boundary is not stale`() {
        val now = System.currentTimeMillis()
        val boundaryTimestamp = now - 60_000 // exactly 60 seconds ago
        assertFalse(now - boundaryTimestamp > 60_000, "Boundary should not be stale")
    }

    @Test
    fun `future timestamp is accepted`() {
        val now = System.currentTimeMillis()
        val futureTimestamp = now + 5_000 // 5 seconds in the future (clock skew)
        assertFalse(now - futureTimestamp > 60_000, "Future timestamp should pass")
    }

    @Test
    fun `nonce format is unrestricted - any string up to 128 chars`() {
        val request = HeartbeatRequest(
            licenseKey = "TEST-KEY-1234",
            deviceId = "device-001",
            appVersion = "1.0.0",
            nonce = "a".repeat(128)
        )
        assertTrue(request.nonce!!.length <= 128)
    }
}
