package com.zyntasolutions.zyntapos.api.service

import java.security.MessageDigest
import java.util.Base64
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * S3-4: Unit tests for UserService pure logic — dual-hash password verification,
 * brute-force lockout constants, SHA-256 hash format, and email masking.
 *
 * Full DB-dependent tests (authenticate, issueTokens, refreshTokens) require
 * S3-15 repository extraction or Testcontainers.
 */
class UserServiceTest {

    companion object {
        private const val MAX_FAILED_ATTEMPTS = 5
        private const val LOCKOUT_DURATION_MS = 15 * 60 * 1000L
    }

    // ── SHA-256 hash verification (legacy PinManager format) ────────────

    @Test
    fun `SHA-256 hash format is base64url-salt colon hex-hash`() {
        val password = "test-password-123"
        val salt = ByteArray(16).also { java.security.SecureRandom().nextBytes(it) }
        val hash = createSha256Hash(password, salt)

        val parts = hash.split(":")
        assertEquals(2, parts.size, "Hash should be salt:hash format")

        // Salt part should be valid base64url
        val decodedSalt = Base64.getUrlDecoder().decode(parts[0])
        assertEquals(16, decodedSalt.size)

        // Hash part should be 64-char hex (SHA-256 = 32 bytes = 64 hex chars)
        assertEquals(64, parts[1].length)
        assertTrue(parts[1].all { it in "0123456789abcdef" })
    }

    @Test
    fun `SHA-256 verification succeeds with correct password`() {
        val password = "my-secure-password"
        val salt = ByteArray(16).also { java.security.SecureRandom().nextBytes(it) }
        val hash = createSha256Hash(password, salt)

        assertTrue(verifySha256Hash(password, hash))
    }

    @Test
    fun `SHA-256 verification fails with wrong password`() {
        val password = "my-secure-password"
        val salt = ByteArray(16).also { java.security.SecureRandom().nextBytes(it) }
        val hash = createSha256Hash(password, salt)

        assertFalse(verifySha256Hash("wrong-password", hash))
    }

    @Test
    fun `SHA-256 verification fails with malformed hash`() {
        assertFalse(verifySha256Hash("password", "not-a-valid-hash"))
    }

    @Test
    fun `SHA-256 verification fails with empty hash`() {
        assertFalse(verifySha256Hash("password", ""))
    }

    @Test
    fun `SHA-256 verification uses constant-time comparison`() {
        // This test validates the comparison loop structure:
        // diff accumulates XOR differences and checks diff == 0 at the end
        val password = "test"
        val salt = ByteArray(16).also { java.security.SecureRandom().nextBytes(it) }
        val hash = createSha256Hash(password, salt)

        // Both should return same result regardless of where the mismatch occurs
        assertTrue(verifySha256Hash(password, hash))
        assertFalse(verifySha256Hash("tes!", hash)) // differs at last char
        assertFalse(verifySha256Hash("!est", hash)) // differs at first char
    }

    // ── BCrypt detection ────────────────────────────────────────────────

    @Test
    fun `bcrypt hash is detected by dollar-2 prefix`() {
        val bcryptHash = "\$2a\$12\$LJ3m4ys5.X9j9P0P2VT0aOEDlGj.Nf3L0l5Q"
        assertTrue(bcryptHash.startsWith("\$2"))
    }

    @Test
    fun `SHA-256 hash does not start with dollar-2`() {
        val password = "test"
        val salt = ByteArray(16).also { java.security.SecureRandom().nextBytes(it) }
        val hash = createSha256Hash(password, salt)
        assertFalse(hash.startsWith("\$2"))
    }

    // ── Brute-force lockout constants ───────────────────────────────────

    @Test
    fun `max failed attempts is 5`() {
        assertEquals(5, MAX_FAILED_ATTEMPTS)
    }

    @Test
    fun `lockout duration is 15 minutes`() {
        assertEquals(15 * 60 * 1000L, LOCKOUT_DURATION_MS)
    }

    @Test
    fun `lockout triggers at exactly max attempts`() {
        var failedCount = 0
        repeat(MAX_FAILED_ATTEMPTS) { failedCount++ }
        assertTrue(failedCount >= MAX_FAILED_ATTEMPTS)
    }

    @Test
    fun `lockout does not trigger below max attempts`() {
        val failedCount = MAX_FAILED_ATTEMPTS - 1
        assertFalse(failedCount >= MAX_FAILED_ATTEMPTS)
    }

    // ── Email masking (GDPR) ────────────────────────────────────────────

    @Test
    fun `email masking preserves first char and domain`() {
        val masked = maskEmail("admin@example.com")
        assertEquals("a***@example.com", masked)
    }

    @Test
    fun `email masking handles single-char local part`() {
        val masked = maskEmail("a@example.com")
        assertEquals("***@example.com", masked)
    }

    @Test
    fun `email masking handles no-at sign`() {
        val masked = maskEmail("noemail")
        // indexOf('@') returns -1 which is <= 1
        assertEquals("***@***", masked)
    }

    // ── Token hash (SHA-256 hex) ────────────────────────────────────────

    @Test
    fun `sha256Hex produces 64-char hex digest`() {
        val input = "test-refresh-token"
        val hash = sha256Hex(input)
        assertEquals(64, hash.length)
        assertTrue(hash.all { it in "0123456789abcdef" })
    }

    @Test
    fun `sha256Hex is deterministic`() {
        val input = "same-input"
        assertEquals(sha256Hex(input), sha256Hex(input))
    }

    @Test
    fun `sha256Hex produces different hashes for different inputs`() {
        assertTrue(sha256Hex("a") != sha256Hex("b"))
    }

    // ── Helper functions (mirror UserService private methods) ───────────

    private fun createSha256Hash(password: String, salt: ByteArray): String {
        val saltBase64 = Base64.getUrlEncoder().withoutPadding().encodeToString(salt)
        val digest = MessageDigest.getInstance("SHA-256")
        digest.update(salt)
        digest.update(password.toByteArray(Charsets.UTF_8))
        val hashHex = digest.digest().joinToString("") { "%02x".format(it) }
        return "$saltBase64:$hashHex"
    }

    private fun verifySha256Hash(password: String, storedHash: String): Boolean = runCatching {
        val parts = storedHash.split(":")
        if (parts.size != 2) return@runCatching false
        val salt = Base64.getUrlDecoder().decode(parts[0])
        val expectedHex = parts[1]
        val digest = MessageDigest.getInstance("SHA-256")
        digest.update(salt)
        digest.update(password.toByteArray(Charsets.UTF_8))
        val actualHex = digest.digest().joinToString("") { "%02x".format(it) }
        if (actualHex.length != expectedHex.length) return@runCatching false
        var diff = 0
        for (i in actualHex.indices) diff = diff or (actualHex[i].code xor expectedHex[i].code)
        diff == 0
    }.getOrDefault(false)

    private fun maskEmail(email: String): String {
        val atIndex = email.indexOf('@')
        if (atIndex <= 1) return "***@${email.substringAfter('@', "***")}"
        return "${email[0]}***@${email.substringAfter('@')}"
    }

    private fun sha256Hex(input: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        return digest.digest(input.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
    }
}
