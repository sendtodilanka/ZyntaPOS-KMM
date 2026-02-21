package com.zyntasolutions.zyntapos.security

import com.zyntasolutions.zyntapos.security.crypto.EncryptionManager
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

// ─────────────────────────────────────────────────────────────────────────────
// EncryptionManager Tests
//
// These are round-trip tests exercising the expect/actual EncryptionManager.
// Both Android and Desktop actuals use AES-256-GCM — the same test suite covers both.
// ─────────────────────────────────────────────────────────────────────────────

class EncryptionManagerTest {

    private val manager = EncryptionManager()

    @Test
    fun `encrypt and decrypt round-trip returns original plaintext`() {
        val original = "hello, ZyntaPOS!"
        val encrypted = manager.encrypt(original)
        val decrypted = manager.decrypt(encrypted)
        assertEquals(original, decrypted)
    }

    @Test
    fun `encrypt returns non-empty ciphertext and iv and tag`() {
        val encrypted = manager.encrypt("test-value")
        assertTrue(encrypted.ciphertext.isNotEmpty(), "ciphertext must not be empty")
        assertTrue(encrypted.iv.isNotEmpty(), "iv must not be empty")
        assertTrue(encrypted.tag.isNotEmpty(), "tag must not be empty")
    }

    @Test
    fun `iv is 12 bytes (AES-GCM standard)`() {
        val encrypted = manager.encrypt("test")
        assertEquals(12, encrypted.iv.size)
    }

    @Test
    fun `tag is 16 bytes (128-bit GCM tag)`() {
        val encrypted = manager.encrypt("test")
        assertEquals(16, encrypted.tag.size)
    }

    @Test
    fun `two encryptions of same plaintext produce different ciphertext (random IV)`() {
        val enc1 = manager.encrypt("same-plaintext")
        val enc2 = manager.encrypt("same-plaintext")
        // IVs must differ (randomized encryption)
        assertFalse(enc1.iv.contentEquals(enc2.iv), "IVs should be unique per encryption call")
        // Ciphertexts should also differ because IVs differ
        assertFalse(enc1.ciphertext.contentEquals(enc2.ciphertext), "ciphertexts should differ for different IVs")
    }

    @Test
    fun `encrypt and decrypt works for empty string`() {
        val encrypted = manager.encrypt("")
        assertEquals("", manager.decrypt(encrypted))
    }

    @Test
    fun `encrypt and decrypt works for unicode content`() {
        val unicode = "ශ්‍රී ලංකා — රුපියල් 💰"
        val encrypted = manager.encrypt(unicode)
        assertEquals(unicode, manager.decrypt(encrypted))
    }

    @Test
    fun `encrypt and decrypt works for long payload`() {
        val longText = "A".repeat(10_000)
        val encrypted = manager.encrypt(longText)
        assertEquals(longText, manager.decrypt(encrypted))
    }

    @Test
    fun `EncryptedData equality is content-based`() {
        val enc1 = manager.encrypt("value")
        val enc2 = manager.encrypt("value")
        // They are different encryptions — not equal
        assertNotEquals(enc1, enc2)
    }
}
