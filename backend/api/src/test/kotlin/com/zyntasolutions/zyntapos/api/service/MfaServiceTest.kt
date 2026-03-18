package com.zyntasolutions.zyntapos.api.service

import com.eatthepath.otp.TimeBasedOneTimePasswordGenerator
import org.apache.commons.codec.binary.Base32
import java.time.Duration
import java.time.Instant
import javax.crypto.spec.SecretKeySpec
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * Unit tests for MfaService pure logic — TOTP generation, verification, and setup URI.
 * Database-dependent methods (generateBackupCodes, enableMfa, etc.) require integration tests.
 */
class MfaServiceTest {

    private val service = MfaService()

    // ── generateSetup ────────────────────────────────────────────────────

    @Test
    fun `generateSetup returns Base32-encoded secret`() {
        val setup = service.generateSetup("user@example.com")
        // Base32 uses A-Z and 2-7
        assertTrue(setup.secret.all { it in "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567=" })
        assertTrue(setup.secret.length >= 16, "Secret should be at least 16 Base32 chars")
    }

    @Test
    fun `generateSetup returns valid otpauth URI`() {
        val setup = service.generateSetup("admin@zyntapos.com")
        assertTrue(setup.qrCodeUrl.startsWith("otpauth://totp/ZyntaPOS:"))
        assertTrue("secret=${setup.secret}" in setup.qrCodeUrl)
        assertTrue("issuer=ZyntaPOS" in setup.qrCodeUrl)
        assertTrue("algorithm=SHA1" in setup.qrCodeUrl)
        assertTrue("digits=6" in setup.qrCodeUrl)
        assertTrue("period=30" in setup.qrCodeUrl)
    }

    @Test
    fun `generateSetup URL-encodes email with special characters`() {
        val setup = service.generateSetup("user+tag@example.com")
        assertTrue("user%2Btag%40example.com" in setup.qrCodeUrl)
    }

    @Test
    fun `generateSetup produces unique secrets on each call`() {
        val s1 = service.generateSetup("user@example.com")
        val s2 = service.generateSetup("user@example.com")
        assertNotEquals(s1.secret, s2.secret, "Each setup should generate a unique secret")
    }

    // ── verifyTotp ───────────────────────────────────────────────────────

    @Test
    fun `verifyTotp accepts current window code`() {
        val setup = service.generateSetup("test@example.com")
        val code = generateTotpCode(setup.secret, Instant.now())
        assertTrue(service.verifyTotp(setup.secret, code))
    }

    @Test
    fun `verifyTotp accepts code from previous window (minus 30s)`() {
        val setup = service.generateSetup("test@example.com")
        val code = generateTotpCode(setup.secret, Instant.now().minusSeconds(30))
        assertTrue(service.verifyTotp(setup.secret, code))
    }

    @Test
    fun `verifyTotp accepts code from next window (plus 30s)`() {
        val setup = service.generateSetup("test@example.com")
        val code = generateTotpCode(setup.secret, Instant.now().plusSeconds(30))
        assertTrue(service.verifyTotp(setup.secret, code))
    }

    @Test
    fun `verifyTotp rejects code from distant future`() {
        val setup = service.generateSetup("test@example.com")
        val code = generateTotpCode(setup.secret, Instant.now().plusSeconds(300))
        assertFalse(service.verifyTotp(setup.secret, code))
    }

    @Test
    fun `verifyTotp rejects wrong code`() {
        val setup = service.generateSetup("test@example.com")
        assertFalse(service.verifyTotp(setup.secret, "000000"))
    }

    @Test
    fun `verifyTotp rejects invalid secret gracefully`() {
        assertFalse(service.verifyTotp("not-valid-base32!!!", "123456"))
    }

    @Test
    fun `verifyTotp handles whitespace in code`() {
        val setup = service.generateSetup("test@example.com")
        val code = generateTotpCode(setup.secret, Instant.now())
        // verifyTotp trims the code
        assertTrue(service.verifyTotp(setup.secret, " $code "))
    }

    // ── generateRandomCode (private, tested via generateSetup uniqueness) ─

    @Test
    fun `generated setup secrets are 160-bit keys (20 bytes Base32-encoded)`() {
        val setup = service.generateSetup("test@example.com")
        val decoded = Base32().decode(setup.secret)
        assertEquals(20, decoded.size, "TOTP key should be 20 bytes (160-bit)")
    }

    // ── Helper ───────────────────────────────────────────────────────────

    private fun generateTotpCode(secret: String, instant: Instant): String {
        val totp = TimeBasedOneTimePasswordGenerator(Duration.ofSeconds(30), 6)
        val keyBytes = Base32().decode(secret)
        val key = SecretKeySpec(keyBytes, totp.algorithm)
        return totp.generateOneTimePasswordString(key, instant)
    }
}
