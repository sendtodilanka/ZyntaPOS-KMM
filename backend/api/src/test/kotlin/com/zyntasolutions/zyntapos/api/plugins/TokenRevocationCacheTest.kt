package com.zyntasolutions.zyntapos.api.plugins

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Unit tests for [TokenRevocationCache] — in-memory JWT revocation cache.
 *
 * Tests cover:
 * - Cache miss returns null (not-cached)
 * - Cache hit returns correct revocation status
 * - markRevoked immediately marks JTI as revoked
 * - Different JTIs are independent
 */
class TokenRevocationCacheTest {

    @Test
    fun `cache miss returns null for unknown JTI`() {
        assertNull(TokenRevocationCache.isRevoked("unknown-jti-${System.nanoTime()}"))
    }

    @Test
    fun `put and retrieve non-revoked token`() {
        val jti = "valid-jti-${System.nanoTime()}"
        TokenRevocationCache.put(jti, false)
        assertEquals(false, TokenRevocationCache.isRevoked(jti))
    }

    @Test
    fun `put and retrieve revoked token`() {
        val jti = "revoked-jti-${System.nanoTime()}"
        TokenRevocationCache.put(jti, true)
        assertEquals(true, TokenRevocationCache.isRevoked(jti))
    }

    @Test
    fun `markRevoked sets JTI as revoked`() {
        val jti = "mark-revoked-jti-${System.nanoTime()}"
        TokenRevocationCache.markRevoked(jti)
        assertEquals(true, TokenRevocationCache.isRevoked(jti))
    }

    @Test
    fun `markRevoked overrides previously non-revoked status`() {
        val jti = "override-jti-${System.nanoTime()}"
        TokenRevocationCache.put(jti, false)
        assertEquals(false, TokenRevocationCache.isRevoked(jti))
        TokenRevocationCache.markRevoked(jti)
        assertEquals(true, TokenRevocationCache.isRevoked(jti))
    }

    @Test
    fun `different JTIs are independent`() {
        val jti1 = "jti-a-${System.nanoTime()}"
        val jti2 = "jti-b-${System.nanoTime()}"
        TokenRevocationCache.put(jti1, true)
        TokenRevocationCache.put(jti2, false)
        assertEquals(true, TokenRevocationCache.isRevoked(jti1))
        assertEquals(false, TokenRevocationCache.isRevoked(jti2))
    }
}
