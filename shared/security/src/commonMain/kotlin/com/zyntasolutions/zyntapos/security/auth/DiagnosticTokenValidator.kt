package com.zyntasolutions.zyntapos.security.auth

import com.zyntasolutions.zyntapos.core.result.Result
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.time.Clock

/**
 * Claims carried in a JIT diagnostic session token.
 *
 * Tokens are short-lived (15-minute TTL) and scoped to a single store.
 * The client reads these claims to display the consent dialog; signature
 * verification is performed server-side at the time of consent acceptance.
 */
@Serializable
data class DiagnosticClaims(
    @SerialName("session_id")    val sessionId: String,
    @SerialName("technician_id") val technicianId: String,
    @SerialName("store_id")      val storeId: String,
    @SerialName("scope")         val scope: String,
    @SerialName("exp")           val exp: Long,   // Unix seconds
    @SerialName("iat")           val iat: Long,   // Unix seconds
)

/**
 * Client-side validator for JIT diagnostic session tokens.
 *
 * **Does not verify the JWT signature** — that is the server's responsibility.
 * This class only decodes claims from the token payload and checks TTL/expiry
 * so the device can show the consent UI and reject stale tokens early.
 *
 * Tokens are issued by the backend `DiagnosticSessionService` and signed with
 * the admin JWT HS256 secret. The 15-minute TTL is enforced via the `exp` claim.
 */
class DiagnosticTokenValidator {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    /**
     * Decodes the payload of a diagnostic token and returns the [DiagnosticClaims].
     * Returns [Result.Error] if the token is malformed, expired, or missing required fields.
     */
    @OptIn(ExperimentalEncodingApi::class)
    fun validateToken(token: String): Result<DiagnosticClaims> {
        return runCatching {
            val parts = token.split(".")
            require(parts.size == 3) { "Invalid token format — expected 3 JWT segments" }

            val payloadJson = Base64.UrlSafe.decode(parts[1]).decodeToString()
            val claims = json.decodeFromString<DiagnosticClaims>(payloadJson)

            if (isExpired(claims)) {
                return Result.Error(
                    IllegalStateException("Diagnostic token has expired (exp=${claims.exp})")
                )
            }
            claims
        }.fold(
            onSuccess = { Result.Success(it) },
            onFailure = { Result.Error(it) },
        )
    }

    /**
     * Returns true if the [DiagnosticClaims.exp] is in the past (with 30-second clock skew buffer).
     */
    fun isExpired(claims: DiagnosticClaims): Boolean {
        val nowSeconds = Clock.System.now().epochSeconds
        return nowSeconds > claims.exp + CLOCK_SKEW_SECONDS
    }

    companion object {
        private const val CLOCK_SKEW_SECONDS = 30L
    }
}
