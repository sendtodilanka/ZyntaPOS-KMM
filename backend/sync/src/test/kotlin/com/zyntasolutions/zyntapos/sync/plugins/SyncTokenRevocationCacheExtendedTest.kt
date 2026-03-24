package com.zyntasolutions.zyntapos.sync.plugins

import io.lettuce.core.api.StatefulRedisConnection
import io.lettuce.core.api.sync.RedisCommands
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * C7: Extended tests for [SyncTokenRevocationCache] — covers Redis-backed
 * revocation checks, caching behavior, and graceful degradation.
 */
class SyncTokenRevocationCacheExtendedTest {

    private fun mockRedis(revokedJtis: Set<String>): Pair<StatefulRedisConnection<String, String>, RedisCommands<String, String>> {
        val commands = mockk<RedisCommands<String, String>>()
        every { commands.sismember("revoked_jtis", any()) } answers {
            val jti = arg<String>(1)
            jti in revokedJtis
        }
        val connection = mockk<StatefulRedisConnection<String, String>>()
        every { connection.sync() } returns commands
        return connection to commands
    }

    // ── Redis-backed checks ───────────────────────────────────────────────

    @Test
    fun `returns true for revoked JTI when Redis available`() {
        val (redis, _) = mockRedis(setOf("revoked-jti-1"))
        val result = SyncTokenRevocationCache.isRevoked("revoked-jti-1", redis)
        assertTrue(result)
    }

    @Test
    fun `returns false for non-revoked JTI when Redis available`() {
        val (redis, _) = mockRedis(emptySet())
        val jti = "not-revoked-${System.nanoTime()}"
        val result = SyncTokenRevocationCache.isRevoked(jti, redis)
        assertFalse(result)
    }

    @Test
    fun `queries Redis when checking revocation`() {
        val (redis, commands) = mockRedis(emptySet())
        val jti = "check-${System.nanoTime()}"
        SyncTokenRevocationCache.isRevoked(jti, redis)
        verify { commands.sismember("revoked_jtis", jti) }
    }

    // ── Graceful degradation ──────────────────────────────────────────────

    @Test
    fun `returns false when Redis throws exception`() {
        val commands = mockk<RedisCommands<String, String>>()
        every { commands.sismember(any(), any()) } throws RuntimeException("Redis down")
        val redis = mockk<StatefulRedisConnection<String, String>>()
        every { redis.sync() } returns commands

        val jti = "redis-error-${System.nanoTime()}"
        val result = SyncTokenRevocationCache.isRevoked(jti, redis)
        assertFalse(result, "Should default to not-revoked when Redis is unavailable")
    }

    @Test
    fun `returns false when Redis connection is null`() {
        val jti = "null-redis-${System.nanoTime()}"
        val result = SyncTokenRevocationCache.isRevoked(jti, null)
        assertFalse(result)
    }

    // ── evictStale ────────────────────────────────────────────────────────

    @Test
    fun `evictStale does not throw when called multiple times`() {
        SyncTokenRevocationCache.evictStale()
        SyncTokenRevocationCache.evictStale()
        // Should not throw
    }

    @Test
    fun `evictStale followed by check still works`() {
        SyncTokenRevocationCache.evictStale()
        val jti = "after-evict-${System.nanoTime()}"
        val result = SyncTokenRevocationCache.isRevoked(jti, null)
        assertFalse(result)
    }
}
