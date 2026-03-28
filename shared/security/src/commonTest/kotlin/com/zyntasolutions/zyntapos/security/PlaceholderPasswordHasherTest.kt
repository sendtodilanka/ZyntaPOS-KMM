package com.zyntasolutions.zyntapos.security

import com.zyntasolutions.zyntapos.security.auth.PlaceholderPasswordHasher
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * ZyntaPOS — PlaceholderPasswordHasherTest Unit Tests (commonTest)
 *
 * Validates the non-secure test/debug hasher in [PlaceholderPasswordHasher].
 *
 * Coverage:
 *  A. hashPassword prefixes plaintext with "PLAIN:"
 *  B. verifyPassword returns true for PLAIN: prefixed hash matching plain
 *  C. verifyPassword returns false for PLAIN: prefixed hash not matching plain
 *  D. verifyPassword accepts raw plaintext as hash (seed compatibility)
 *  E. verifyPassword rejects wrong plaintext against raw hash
 *  F. empty string is hashed correctly
 *  G. hashPassword is deterministic (same plain → same hash)
 *  H. different plaintexts produce different hashes
 */
class PlaceholderPasswordHasherTest {

    private val hasher = PlaceholderPasswordHasher()

    @Test
    fun `A - hashPassword prefixes plaintext with PLAIN colon`() {
        val hash = hasher.hashPassword("secret")
        assertEquals("PLAIN:secret", hash)
    }

    @Test
    fun `B - verifyPassword returns true for PLAIN-prefixed hash matching plain`() {
        val hash = hasher.hashPassword("mypassword")
        assertTrue(hasher.verifyPassword("mypassword", hash))
    }

    @Test
    fun `C - verifyPassword returns false for PLAIN-prefixed hash with wrong plain`() {
        val hash = hasher.hashPassword("correct")
        assertFalse(hasher.verifyPassword("wrong", hash))
    }

    @Test
    fun `D - verifyPassword accepts raw plaintext as hash for seed compatibility`() {
        // DB seeded with plain string "admin123" rather than "PLAIN:admin123"
        assertTrue(hasher.verifyPassword("admin123", "admin123"))
    }

    @Test
    fun `E - verifyPassword rejects wrong plaintext against raw plaintext hash`() {
        assertFalse(hasher.verifyPassword("wrong", "admin123"))
    }

    @Test
    fun `F - empty string is hashed to PLAIN colon empty`() {
        val hash = hasher.hashPassword("")
        assertEquals("PLAIN:", hash)
        assertTrue(hasher.verifyPassword("", hash))
    }

    @Test
    fun `G - hashPassword is deterministic same plain produces same hash`() {
        val first = hasher.hashPassword("reproducible")
        val second = hasher.hashPassword("reproducible")
        assertEquals(first, second)
    }

    @Test
    fun `H - different plaintexts produce different hashes`() {
        val hash1 = hasher.hashPassword("alpha")
        val hash2 = hasher.hashPassword("beta")
        assertTrue(hash1 != hash2)
    }
}
