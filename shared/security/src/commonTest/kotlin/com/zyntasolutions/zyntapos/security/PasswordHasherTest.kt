package com.zyntasolutions.zyntapos.security

import com.zyntasolutions.zyntapos.security.auth.PasswordHasher
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

// ─────────────────────────────────────────────────────────────────────────────
// PasswordHasher Tests
// ─────────────────────────────────────────────────────────────────────────────

class PasswordHasherTest {

    @Test
    fun `hashPassword returns non-empty BCrypt string`() {
        val hash = PasswordHasher.hashPassword("secret")
        assertTrue(hash.isNotEmpty())
        // BCrypt hashes always start with $2a$ or $2b$
        assertTrue(hash.startsWith("\$2"))
    }

    @Test
    fun `two hashes of same password differ (unique salt)`() {
        val h1 = PasswordHasher.hashPassword("mypassword")
        val h2 = PasswordHasher.hashPassword("mypassword")
        assertNotEquals(h1, h2)
    }

    @Test
    fun `verifyPassword returns true for matching password`() {
        val password = "P@ssw0rd123"
        val hash = PasswordHasher.hashPassword(password)
        assertTrue(PasswordHasher.verifyPassword(password, hash))
    }

    @Test
    fun `verifyPassword returns false for wrong password`() {
        val hash = PasswordHasher.hashPassword("correct")
        assertFalse(PasswordHasher.verifyPassword("wrong", hash))
    }

    @Test
    fun `verifyPassword returns false for malformed hash`() {
        assertFalse(PasswordHasher.verifyPassword("any", "not-a-hash"))
        assertFalse(PasswordHasher.verifyPassword("any", ""))
    }

    @Test
    fun `hashPassword handles empty string`() {
        val hash = PasswordHasher.hashPassword("")
        assertTrue(PasswordHasher.verifyPassword("", hash))
        assertFalse(PasswordHasher.verifyPassword("notempty", hash))
    }
}
