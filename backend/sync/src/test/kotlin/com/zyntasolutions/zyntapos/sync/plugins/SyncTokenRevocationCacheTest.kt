package com.zyntasolutions.zyntapos.sync.plugins

import kotlin.test.Test
import kotlin.test.assertFalse

/**
 * Unit tests for [SyncTokenRevocationCache] — Redis-backed JWT revocation cache
 * used by the sync service.
 *
 * Without a real Redis connection, these tests verify the graceful degradation:
 * when Redis is unavailable, the cache returns false (not revoked) to avoid
 * blocking legitimate connections.
 */
class SyncTokenRevocationCacheTest {

    @Test
    fun `returns false for unknown JTI when Redis is null`() {
        val revoked = SyncTokenRevocationCache.isRevoked("unknown-jti-${System.nanoTime()}", null)
        assertFalse(revoked, "Should not be revoked when Redis unavailable and no cache entry")
    }

    @Test
    fun `returns false for different JTIs when Redis is null`() {
        val jti1 = "jti-1-${System.nanoTime()}"
        val jti2 = "jti-2-${System.nanoTime()}"
        assertFalse(SyncTokenRevocationCache.isRevoked(jti1, null))
        assertFalse(SyncTokenRevocationCache.isRevoked(jti2, null))
    }

    @Test
    fun `evictStale does not throw on empty cache`() {
        SyncTokenRevocationCache.evictStale()
        // Should not throw
    }
}
