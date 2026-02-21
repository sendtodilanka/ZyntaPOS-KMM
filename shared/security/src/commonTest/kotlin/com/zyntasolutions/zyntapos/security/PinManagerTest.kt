package com.zyntasolutions.zyntapos.security

import com.zyntasolutions.zyntapos.domain.model.Permission
import com.zyntasolutions.zyntapos.domain.model.Role
import com.zyntasolutions.zyntapos.security.auth.JwtManager
import com.zyntasolutions.zyntapos.security.auth.PasswordHasher
import com.zyntasolutions.zyntapos.security.auth.PinManager
import com.zyntasolutions.zyntapos.security.rbac.RbacEngine
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

// ─────────────────────────────────────────────────────────────────────────────
// PinManager Tests
// ─────────────────────────────────────────────────────────────────────────────

class PinManagerTest {

    @Test
    fun `validatePinFormat accepts 4-digit pin`() {
        assertTrue(PinManager.validatePinFormat("1234"))
    }

    @Test
    fun `validatePinFormat accepts 6-digit pin`() {
        assertTrue(PinManager.validatePinFormat("123456"))
    }

    @Test
    fun `validatePinFormat rejects 3-digit pin`() {
        assertFalse(PinManager.validatePinFormat("123"))
    }

    @Test
    fun `validatePinFormat rejects 7-digit pin`() {
        assertFalse(PinManager.validatePinFormat("1234567"))
    }

    @Test
    fun `validatePinFormat rejects non-digits`() {
        assertFalse(PinManager.validatePinFormat("12ab"))
        assertFalse(PinManager.validatePinFormat(""))
        assertFalse(PinManager.validatePinFormat("12 34"))
    }

    @Test
    fun `hashPin returns non-empty string in salt-hash format`() {
        val hash = PinManager.hashPin("1234")
        assertTrue(hash.contains(":"))
        val parts = hash.split(":")
        assertEquals(2, parts.size)
        assertTrue(parts[0].isNotEmpty())
        assertTrue(parts[1].isNotEmpty())
    }

    @Test
    fun `two hashes of same pin are different (random salt)`() {
        val hash1 = PinManager.hashPin("1234")
        val hash2 = PinManager.hashPin("1234")
        assertNotEquals(hash1, hash2)
    }

    @Test
    fun `verifyPin returns true for correct pin`() {
        val pin = "9876"
        val hash = PinManager.hashPin(pin)
        assertTrue(PinManager.verifyPin(pin, hash))
    }

    @Test
    fun `verifyPin returns false for wrong pin`() {
        val hash = PinManager.hashPin("1234")
        assertFalse(PinManager.verifyPin("9999", hash))
    }

    @Test
    fun `verifyPin returns false for malformed hash`() {
        assertFalse(PinManager.verifyPin("1234", "not-a-valid-hash"))
        assertFalse(PinManager.verifyPin("1234", ""))
    }

    @Test
    fun `hashPin throws for invalid pin`() {
        assertFailsWith<IllegalArgumentException> { PinManager.hashPin("12") }
        assertFailsWith<IllegalArgumentException> { PinManager.hashPin("abcd") }
    }
}
