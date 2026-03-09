package com.zyntasolutions.zyntapos.security

import com.zyntasolutions.zyntapos.domain.model.Role
import com.zyntasolutions.zyntapos.security.auth.JwtManager
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

// ─────────────────────────────────────────────────────────────────────────────
// JwtManager Tests (commonTest — claim parsing, expiry, role extraction)
// ─────────────────────────────────────────────────────────────────────────────
//
// These tests cover parseJwt / isTokenExpired / extractRole / extractUserId.
// They use hand-crafted tokens with a dummy signature segment because signature
// verification is not the concern of these paths.
//
// RS256 signature verification (verifyOfflineRole) is tested separately in
// jvmTest/VerifyOfflineRoleTest.kt, which generates a real RSA key pair.
// ─────────────────────────────────────────────────────────────────────────────

class JwtManagerTest {

    @OptIn(ExperimentalEncodingApi::class)
    private fun buildToken(payloadJson: String): String {
        val header = Base64.UrlSafe.encode("""{"alg":"RS256","typ":"JWT"}""".encodeToByteArray()).trimEnd('=')
        val payload = Base64.UrlSafe.encode(payloadJson.encodeToByteArray()).trimEnd('=')
        return "$header.$payload.sig"
    }

    // ── Token with far-future expiry (year 9999) ──────────────────────────────
    private val validToken = buildToken(
        """{"sub":"user-abc","role":"ADMIN","store_id":"store-1","exp":253402300800,"iat":1700000000}""",
    )

    // ── Token that expired in the distant past ────────────────────────────────
    private val expiredToken = buildToken(
        """{"sub":"user-xyz","role":"CASHIER","store_id":"store-2","exp":1000000000,"iat":999999000}""",
    )

    // ── Token with unknown role ───────────────────────────────────────────────
    private val unknownRoleToken = buildToken(
        """{"sub":"user-unk","role":"SUPERUSER","store_id":"store-3","exp":253402300800,"iat":1700000000}""",
    )

    @Test
    fun `parseJwt extracts sub claim correctly`() {
        val claims = JwtManager(FakeSecurePreferences()).parseJwt(validToken)
        assertEquals("user-abc", claims.sub)
    }

    @Test
    fun `parseJwt extracts storeId correctly`() {
        val claims = JwtManager(FakeSecurePreferences()).parseJwt(validToken)
        assertEquals("store-1", claims.storeId)
    }

    @Test
    fun `isTokenExpired returns false for far-future token`() {
        assertFalse(JwtManager(FakeSecurePreferences()).isTokenExpired(validToken))
    }

    @Test
    fun `isTokenExpired returns true for expired token`() {
        assertTrue(JwtManager(FakeSecurePreferences()).isTokenExpired(expiredToken))
    }

    @Test
    fun `isTokenExpired returns true for malformed token`() {
        assertTrue(JwtManager(FakeSecurePreferences()).isTokenExpired("not.a.token.at.all"))
    }

    @Test
    fun `extractUserId returns sub claim`() {
        assertEquals("user-abc", JwtManager(FakeSecurePreferences()).extractUserId(validToken))
    }

    @Test
    fun `extractRole returns correct role for known role string`() {
        assertEquals(Role.ADMIN, JwtManager(FakeSecurePreferences()).extractRole(validToken))
    }

    @Test
    fun `extractRole returns CASHIER for unknown role string`() {
        assertEquals(Role.CASHIER, JwtManager(FakeSecurePreferences()).extractRole(unknownRoleToken))
    }

    @Test
    fun `parseJwt throws for token with wrong number of parts`() {
        assertFailsWith<IllegalArgumentException> {
            JwtManager(FakeSecurePreferences()).parseJwt("only.two")
        }
        assertFailsWith<IllegalArgumentException> {
            JwtManager(FakeSecurePreferences()).parseJwt("too.many.parts.here")
        }
    }

    @Test
    fun `extractRole is case-insensitive`() {
        val token = buildToken(
            """{"sub":"u","role":"cashier","store_id":"s","exp":253402300800,"iat":0}""",
        )
        assertEquals(Role.CASHIER, JwtManager(FakeSecurePreferences()).extractRole(token))
    }
}
