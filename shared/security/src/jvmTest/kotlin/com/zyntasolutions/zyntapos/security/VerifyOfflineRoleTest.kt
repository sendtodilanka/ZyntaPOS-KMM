package com.zyntasolutions.zyntapos.security

import com.zyntasolutions.zyntapos.domain.model.Role
import com.zyntasolutions.zyntapos.security.auth.JwtManager
import java.security.KeyPairGenerator
import java.security.Signature
import java.security.interfaces.RSAPublicKey
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

// ─────────────────────────────────────────────────────────────────────────────
// verifyOfflineRole — RS256 signature verification tests
//
// JVM-only: test setup uses KeyPairGenerator (java.security) to generate a
// fresh 2048-bit RSA key pair per test class, sign real JWTs, and verify them.
// The commonTest helpers still cover the claim-parsing / expiry paths.
// ─────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalEncodingApi::class)
class VerifyOfflineRoleTest {

    // ── One RSA-2048 key pair shared across all tests in this class ───────────
    private val keyPair = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
    private val privateKey = keyPair.private
    private val publicKey  = keyPair.public as RSAPublicKey

    /** Standard Base64 of the public key DER bytes (SubjectPublicKeyInfo) */
    private val publicKeyDerBase64: String = Base64.encode(publicKey.encoded)

    // ── JWT construction helpers ──────────────────────────────────────────────

    private fun b64url(bytes: ByteArray): String =
        Base64.UrlSafe.encode(bytes).trimEnd('=')

    /**
     * Builds a real RS256-signed JWT using the test private key.
     * [payloadJson] is the raw JSON string for the claims.
     */
    private fun signedToken(payloadJson: String): String {
        val header  = b64url("""{"alg":"RS256","typ":"JWT"}""".encodeToByteArray())
        val payload = b64url(payloadJson.encodeToByteArray())
        val sigInput = "$header.$payload"
        val sig = Signature.getInstance("SHA256withRSA").apply {
            initSign(privateKey)
            update(sigInput.encodeToByteArray())
        }.sign()
        return "$sigInput.${b64url(sig)}"
    }

    // ── Tests ─────────────────────────────────────────────────────────────────

    @Test
    fun `verifyOfflineRole returns correct role for valid signed token`() {
        val token = signedToken(
            """{"sub":"u1","role":"ADMIN","store_id":"s1","exp":253402300800,"iat":0}"""
        )
        val role = JwtManager(FakeSecurePreferences()).verifyOfflineRole(token, publicKeyDerBase64)
        assertEquals(Role.ADMIN, role)
    }

    @Test
    fun `verifyOfflineRole returns MANAGER for manager role`() {
        val token = signedToken(
            """{"sub":"u2","role":"MANAGER","store_id":"s1","exp":253402300800,"iat":0}"""
        )
        val role = JwtManager(FakeSecurePreferences()).verifyOfflineRole(token, publicKeyDerBase64)
        assertEquals(Role.MANAGER, role)
    }

    @Test
    fun `verifyOfflineRole returns null for tampered payload`() {
        // Build a valid token, then swap out the payload for a tampered one
        val legitimateToken = signedToken(
            """{"sub":"u1","role":"CASHIER","store_id":"s1","exp":253402300800,"iat":0}"""
        )
        val (header, _, signature) = legitimateToken.split(".")
        val tamperedPayload = b64url(
            """{"sub":"u1","role":"ADMIN","store_id":"s1","exp":253402300800,"iat":0}""".encodeToByteArray()
        )
        val tamperedToken = "$header.$tamperedPayload.$signature"

        assertNull(JwtManager(FakeSecurePreferences()).verifyOfflineRole(tamperedToken, publicKeyDerBase64))
    }

    @Test
    fun `verifyOfflineRole returns null for token signed with different key`() {
        // Sign with a completely different key pair
        val otherPrivateKey = KeyPairGenerator.getInstance("RSA")
            .apply { initialize(2048) }.generateKeyPair().private
        val header  = b64url("""{"alg":"RS256","typ":"JWT"}""".encodeToByteArray())
        val payload = b64url("""{"sub":"u","role":"ADMIN","store_id":"s","exp":253402300800,"iat":0}""".encodeToByteArray())
        val sig = Signature.getInstance("SHA256withRSA").apply {
            initSign(otherPrivateKey)
            update("$header.$payload".encodeToByteArray())
        }.sign()
        val token = "$header.$payload.${b64url(sig)}"

        assertNull(JwtManager(FakeSecurePreferences()).verifyOfflineRole(token, publicKeyDerBase64))
    }

    @Test
    fun `verifyOfflineRole returns null for malformed token`() {
        assertNull(JwtManager(FakeSecurePreferences()).verifyOfflineRole("not.a.jwt", publicKeyDerBase64))
    }

    @Test
    fun `verifyOfflineRole returns null for garbage public key`() {
        val token = signedToken(
            """{"sub":"u","role":"ADMIN","store_id":"s","exp":253402300800,"iat":0}"""
        )
        assertNull(JwtManager(FakeSecurePreferences()).verifyOfflineRole(token, "bm90YXZhbGlka2V5"))
    }

    @Test
    fun `verifyOfflineRole is case-insensitive for role claim`() {
        val token = signedToken(
            """{"sub":"u","role":"cashier","store_id":"s","exp":253402300800,"iat":0}"""
        )
        assertEquals(Role.CASHIER, JwtManager(FakeSecurePreferences()).verifyOfflineRole(token, publicKeyDerBase64))
    }

    @Test
    fun `verifyOfflineRole returns null for unrecognised role even with valid signature`() {
        val token = signedToken(
            """{"sub":"u","role":"SUPERUSER","store_id":"s","exp":253402300800,"iat":0}"""
        )
        assertNull(JwtManager(FakeSecurePreferences()).verifyOfflineRole(token, publicKeyDerBase64))
    }
}
