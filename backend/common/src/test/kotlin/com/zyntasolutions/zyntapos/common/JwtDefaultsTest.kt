package com.zyntasolutions.zyntapos.common

import java.security.KeyPairGenerator
import java.util.Base64
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * S3-9: Tests for [JwtDefaults] — JWT constants and PEM/key parsing utilities.
 */
class JwtDefaultsTest {

    // ── Constants ───────────────────────────────────────────────────────

    @Test
    fun `POS_ISSUER is correct`() {
        assertEquals("https://api.zyntapos.com", JwtDefaults.POS_ISSUER)
    }

    @Test
    fun `POS_AUDIENCE is correct`() {
        assertEquals("zyntapos-app", JwtDefaults.POS_AUDIENCE)
    }

    @Test
    fun `ADMIN_ISSUER is correct`() {
        assertEquals("https://panel.zyntapos.com", JwtDefaults.ADMIN_ISSUER)
    }

    @Test
    fun `POS access token TTL is 60 minutes`() {
        assertEquals(60L, JwtDefaults.POS_ACCESS_TOKEN_TTL_MINUTES)
    }

    @Test
    fun `POS refresh token TTL is 30 days`() {
        assertEquals(30L, JwtDefaults.POS_REFRESH_TOKEN_TTL_DAYS)
    }

    @Test
    fun `admin access token TTL is 15 minutes`() {
        assertEquals(15L, JwtDefaults.ADMIN_ACCESS_TOKEN_TTL_MINUTES)
    }

    @Test
    fun `admin refresh token TTL is 7 days`() {
        assertEquals(7L, JwtDefaults.ADMIN_REFRESH_TOKEN_TTL_DAYS)
    }

    // ── stripPemHeaders ─────────────────────────────────────────────────

    @Test
    fun `stripPemHeaders removes public key headers`() {
        val pem = """
            -----BEGIN PUBLIC KEY-----
            MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEA
            -----END PUBLIC KEY-----
        """.trimIndent()
        val stripped = JwtDefaults.stripPemHeaders(pem)
        assertTrue(!stripped.contains("-----"))
        assertTrue(!stripped.contains("\n"))
        assertTrue(stripped.isNotBlank())
    }

    @Test
    fun `stripPemHeaders removes RSA private key headers`() {
        val pem = """
            -----BEGIN RSA PRIVATE KEY-----
            MIIEowIBAAKCAQEA
            -----END RSA PRIVATE KEY-----
        """.trimIndent()
        val stripped = JwtDefaults.stripPemHeaders(pem)
        assertTrue(!stripped.contains("-----"))
        assertEquals("MIIEowIBAAKCAQEA", stripped)
    }

    @Test
    fun `stripPemHeaders handles raw base64 input`() {
        val raw = "MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8A"
        assertEquals(raw, JwtDefaults.stripPemHeaders(raw))
    }

    // ── parseRsaPublicKey ───────────────────────────────────────────────

    @Test
    fun `parseRsaPublicKey parses a valid RSA public key PEM`() {
        val kpg = KeyPairGenerator.getInstance("RSA")
        kpg.initialize(2048)
        val kp = kpg.generateKeyPair()

        val pubBase64 = Base64.getEncoder().encodeToString(kp.public.encoded)
        val pem = "-----BEGIN PUBLIC KEY-----\n$pubBase64\n-----END PUBLIC KEY-----"

        val parsed = JwtDefaults.parseRsaPublicKey(pem)
        assertNotNull(parsed)
        assertEquals("RSA", parsed.algorithm)
    }

    @Test
    fun `parseRsaPublicKey handles raw base64 without headers`() {
        val kpg = KeyPairGenerator.getInstance("RSA")
        kpg.initialize(2048)
        val kp = kpg.generateKeyPair()

        val pubBase64 = Base64.getEncoder().encodeToString(kp.public.encoded)
        val parsed = JwtDefaults.parseRsaPublicKey(pubBase64)
        assertNotNull(parsed)
        assertEquals("RSA", parsed.algorithm)
    }

    // ── readKeyFile / readSecret ────────────────────────────────────────

    @Test
    fun `readKeyFile returns null for unset env var`() {
        // Use an env var that definitely doesn't exist
        val result = JwtDefaults.readKeyFile("ZYNTA_TEST_NONEXISTENT_VAR_12345")
        assertEquals(null, result)
    }

    @Test
    fun `readSecret returns null for unset env var`() {
        val result = JwtDefaults.readSecret("ZYNTA_TEST_NONEXISTENT_VAR_12345")
        assertEquals(null, result)
    }
}
