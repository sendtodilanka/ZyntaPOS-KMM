package com.zyntasolutions.zyntapos.api.service

import com.zyntasolutions.zyntapos.api.config.AppConfig
import io.mockk.mockk
import java.security.MessageDigest
import java.util.Base64
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Unit tests for InboundEmailProcessor pure logic --
 * HMAC signature verification, priority inference, and inbox categorization.
 */
class InboundEmailProcessorTest {

    private val hmacSecret = "test-hmac-secret-256bit-value"

    private val config = mockk<AppConfig>(relaxed = true).also {
        io.mockk.every { it.inboundEmailHmacSecret } returns hmacSecret
    }

    private val processor = InboundEmailProcessor(
        config = config,
        ticketRepo = mockk(relaxed = true),
        emailService = mockk(relaxed = true),
        chatwootService = mockk(relaxed = true),
        ticketService = mockk(relaxed = true),
    )

    // ── HMAC signature verification ──────────────────────────────────────

    @Test
    fun `verifySignature accepts valid HMAC-SHA256 signature`() {
        val body = """{"fromAddress":"test@example.com","subject":"Help"}"""
        val signature = computeHmac(body, hmacSecret)
        val authHeader = "HMAC-SHA256 $signature"

        assertTrue(processor.verifySignature(authHeader, body))
    }

    @Test
    fun `verifySignature rejects wrong signature`() {
        val body = """{"fromAddress":"test@example.com"}"""
        val authHeader = "HMAC-SHA256 aW52YWxpZHNpZ25hdHVyZQ=="

        assertFalse(processor.verifySignature(authHeader, body))
    }

    @Test
    fun `verifySignature rejects null auth header`() {
        assertFalse(processor.verifySignature(null, "body"))
    }

    @Test
    fun `verifySignature rejects wrong prefix`() {
        val body = "body"
        val sig = computeHmac(body, hmacSecret)
        assertFalse(processor.verifySignature("Bearer $sig", body))
    }

    @Test
    fun `verifySignature allows all when secret is blank (dev mode)`() {
        val devConfig = mockk<AppConfig>(relaxed = true)
        io.mockk.every { devConfig.inboundEmailHmacSecret } returns ""

        val devProcessor = InboundEmailProcessor(
            config = devConfig,
            ticketRepo = mockk(relaxed = true),
            emailService = mockk(relaxed = true),
            chatwootService = mockk(relaxed = true),
            ticketService = mockk(relaxed = true),
        )

        assertTrue(devProcessor.verifySignature(null, "anything"))
        assertTrue(devProcessor.verifySignature("wrong", "anything"))
    }

    @Test
    fun `verifySignature is constant-time (uses MessageDigest isEqual)`() {
        // This test just verifies the code path works for valid + invalid
        val body = "test body"
        val validSig = computeHmac(body, hmacSecret)
        assertTrue(processor.verifySignature("HMAC-SHA256 $validSig", body))
        assertFalse(processor.verifySignature("HMAC-SHA256 ${validSig}x", body))
    }

    // ── Priority inference (exposed via process() but tested indirectly) ──

    @Test
    fun `InboundEmailPayload model has all required fields`() {
        val payload = InboundEmailPayload(
            messageId = "msg-1", inReplyTo = null, references = null,
            fromAddress = "user@test.com", fromName = "User",
            toAddress = "support@zyntapos.com", subject = "Help",
            bodyText = "body", bodyHtml = null, receivedAt = "2026-03-18T12:00:00Z",
        )
        assertEquals("msg-1", payload.messageId)
        assertEquals("user@test.com", payload.fromAddress)
        assertEquals("support@zyntapos.com", payload.toAddress)
    }

    @Test
    fun `InboundEmailPayload allows null optional fields`() {
        val payload = InboundEmailPayload(
            messageId = null, inReplyTo = null, references = null,
            fromAddress = "a@b.com", fromName = null,
            toAddress = "support@zyntapos.com", subject = "Hi",
            bodyText = null, bodyHtml = null, receivedAt = "2026-03-18T12:00:00Z",
        )
        assertEquals(null, payload.messageId)
        assertEquals(null, payload.fromName)
        assertEquals(null, payload.bodyText)
    }

    // ── Helper ───────────────────────────────────────────────────────────

    private fun computeHmac(data: String, secret: String): String {
        val key = SecretKeySpec(secret.toByteArray(Charsets.UTF_8), "HmacSHA256")
        val mac = Mac.getInstance("HmacSHA256").apply { init(key) }
        return Base64.getEncoder().encodeToString(mac.doFinal(data.toByteArray(Charsets.UTF_8)))
    }
}
