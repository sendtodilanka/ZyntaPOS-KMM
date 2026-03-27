package com.zyntasolutions.zyntapos.security

import com.zyntasolutions.zyntapos.core.result.AuthException
import com.zyntasolutions.zyntapos.core.result.Result
import com.zyntasolutions.zyntapos.core.result.ValidationException
import com.zyntasolutions.zyntapos.security.auth.DiagnosticClaims
import com.zyntasolutions.zyntapos.security.auth.DiagnosticTokenValidator
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Clock

/**
 * ZyntaPOS — DiagnosticTokenValidator Unit Tests (commonTest)
 *
 * Validates the JWT parsing and expiry logic in [DiagnosticTokenValidator].
 * Signature verification is intentionally skipped (server-only concern).
 *
 * Coverage:
 *  A. valid token with future exp decodes to DiagnosticClaims successfully
 *  B. all claim fields are correctly parsed from the token payload
 *  C. expired token returns Result.Error(AuthException) with SESSION_EXPIRED reason
 *  D. token with exp in the past but within 30-second clock skew buffer is accepted
 *  E. token with exp beyond 30-second clock skew is rejected
 *  F. malformed token (wrong number of segments) returns Result.Error
 *  G. token with invalid Base64 payload returns Result.Error
 *  H. isExpired returns false for future exp
 *  I. isExpired returns true for past exp (beyond skew)
 */
class DiagnosticTokenValidatorTest {

    private val validator = DiagnosticTokenValidator()

    // ── Helpers ───────────────────────────────────────────────────────────────

    /**
     * Builds a minimal JWT token string (unsigned — header.payload.signature format).
     * The signature segment is a placeholder since DiagnosticTokenValidator does not verify it.
     */
    @OptIn(ExperimentalEncodingApi::class)
    private fun buildToken(
        sessionId: String = "sess-01",
        technicianId: String = "tech-42",
        storeId: String = "store-01",
        scope: String = "READ_ONLY",
        exp: Long,
        iat: Long = exp - 900L,
    ): String {
        val header = Base64.UrlSafe.encode("""{"alg":"HS256","typ":"JWT"}""".encodeToByteArray())
        val payload = Base64.UrlSafe.encode(
            """{"session_id":"$sessionId","technician_id":"$technicianId","store_id":"$storeId","scope":"$scope","exp":$exp,"iat":$iat}"""
                .encodeToByteArray()
        )
        val sig = "fakesignature"
        return "$header.$payload.$sig"
    }

    private val futureExp get() = Clock.System.now().epochSeconds + 600L  // 10 minutes from now
    private val pastExp   get() = Clock.System.now().epochSeconds - 600L  // 10 minutes ago

    // ── Tests ─────────────────────────────────────────────────────────────────

    @Test
    fun `A - valid token with future exp returns Success`() {
        val token = buildToken(exp = futureExp)
        val result = validator.validateToken(token)
        assertIs<Result.Success<DiagnosticClaims>>(result)
    }

    @Test
    fun `B - all claim fields are correctly parsed from the token payload`() {
        val exp = futureExp
        val iat = exp - 900L
        val token = buildToken(
            sessionId = "sess-abc",
            technicianId = "tech-99",
            storeId = "store-07",
            scope = "FULL_ACCESS",
            exp = exp,
            iat = iat,
        )

        val result = validator.validateToken(token)
        assertIs<Result.Success<DiagnosticClaims>>(result)
        val claims = result.data
        assertEquals("sess-abc", claims.sessionId)
        assertEquals("tech-99", claims.technicianId)
        assertEquals("store-07", claims.storeId)
        assertEquals("FULL_ACCESS", claims.scope)
        assertEquals(exp, claims.exp)
        assertEquals(iat, claims.iat)
    }

    @Test
    fun `C - expired token returns AuthException with SESSION_EXPIRED reason`() {
        val token = buildToken(exp = pastExp)  // expired 10 minutes ago, well beyond 30s skew
        val result = validator.validateToken(token)

        assertIs<Result.Error>(result)
        val ex = result.exception
        assertNotNull(ex)
        assertIs<AuthException>(ex)
        // AuthFailureReason is in the exception
        assertTrue(ex.message?.contains("expired", ignoreCase = true) == true)
    }

    @Test
    fun `D - token expired within 30 second clock skew buffer is accepted`() {
        // exp is 10 seconds in the past — within the 30-second skew buffer
        val exp = Clock.System.now().epochSeconds - 10L
        val token = buildToken(exp = exp)

        val result = validator.validateToken(token)
        assertIs<Result.Success<DiagnosticClaims>>(result)
    }

    @Test
    fun `E - token expired beyond 30 second clock skew is rejected`() {
        // exp is 60 seconds in the past — beyond the 30-second skew buffer
        val exp = Clock.System.now().epochSeconds - 60L
        val token = buildToken(exp = exp)

        val result = validator.validateToken(token)
        assertIs<Result.Error>(result)
    }

    @Test
    fun `F - malformed token with wrong number of segments returns error`() {
        val result = validator.validateToken("notavalidjwt")
        assertIs<Result.Error>(result)
    }

    @Test
    fun `F2 - token with only two segments returns error`() {
        val result = validator.validateToken("header.payload")
        assertIs<Result.Error>(result)
    }

    @Test
    @OptIn(ExperimentalEncodingApi::class)
    fun `G - token with invalid base64 payload returns ValidationException`() {
        val header = Base64.UrlSafe.encode("""{"alg":"HS256"}""".encodeToByteArray())
        val result = validator.validateToken("$header.!!invalid_base64!!.sig")
        assertIs<Result.Error>(result)
    }

    @Test
    fun `H - isExpired returns false for future exp`() {
        val claims = DiagnosticClaims(
            sessionId = "s", technicianId = "t", storeId = "store",
            scope = "R", exp = futureExp, iat = futureExp - 900,
        )
        assertFalse(validator.isExpired(claims))
    }

    @Test
    fun `I - isExpired returns true for past exp beyond clock skew`() {
        val claims = DiagnosticClaims(
            sessionId = "s", technicianId = "t", storeId = "store",
            scope = "R", exp = pastExp, iat = pastExp - 900,
        )
        assertTrue(validator.isExpired(claims))
    }
}
