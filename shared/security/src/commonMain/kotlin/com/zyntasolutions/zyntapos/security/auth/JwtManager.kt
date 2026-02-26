package com.zyntasolutions.zyntapos.security.auth

import com.zyntasolutions.zyntapos.domain.model.Role
import com.zyntasolutions.zyntapos.security.prefs.SecurePreferencesKeys
import com.zyntasolutions.zyntapos.security.prefs.TokenStorage
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.time.Clock
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Raw decoded claims from a ZyntaPOS JWT.
 *
 * The server signs JWTs with RS256; the client **does not** verify the signature.
 * Signature verification is deferred to the server on every authenticated API call.
 * The client only reads claims to drive UX (role gating, token-expiry refresh).
 *
 * @property sub        Subject — the user's UUID.
 * @property role       The user's [Role] as a string matching [Role.name].
 * @property storeId    The store the user belongs to.
 * @property exp        Unix timestamp (seconds) at which the token expires.
 * @property iat        Unix timestamp (seconds) at which the token was issued.
 */
@Serializable
data class JwtClaims(
    val sub: String,
    val role: String,
    @SerialName("store_id") val storeId: String = "",
    val exp: Long,
    val iat: Long,
)

/**
 * Client-side JWT manager: decodes and inspects ZyntaPOS access/refresh tokens.
 *
 * **Security note:** This class does NOT cryptographically verify JWT signatures.
 * Signature verification is the responsibility of the backend on each API call.
 * The client parses tokens only to extract display-relevant claims (userId, role)
 * and to detect client-side token expiry for proactive refresh.
 *
 * Tokens are persisted in [SecurePreferences] under the well-known key constants.
 *
 * @param prefs [TokenStorage] instance used for token persistence.
 */
class JwtManager(private val prefs: TokenStorage) {

    private val json = Json { ignoreUnknownKeys = true }

    // ── Token storage ─────────────────────────────────────────────────────────

    /**
     * Saves the [accessToken] and [refreshToken] to [SecurePreferences].
     *
     * @param accessToken  Short-lived JWT (e.g., 15-min expiry).
     * @param refreshToken Long-lived JWT used to obtain a new access token.
     */
    fun saveTokens(accessToken: String, refreshToken: String) {
        prefs.put(SecurePreferencesKeys.KEY_ACCESS_TOKEN, accessToken)
        prefs.put(SecurePreferencesKeys.KEY_REFRESH_TOKEN, refreshToken)
    }

    /** Returns the persisted access token, or `null` if none is stored. */
    fun getAccessToken(): String? = prefs.get(SecurePreferencesKeys.KEY_ACCESS_TOKEN)

    /** Returns the persisted refresh token, or `null` if none is stored. */
    fun getRefreshToken(): String? = prefs.get(SecurePreferencesKeys.KEY_REFRESH_TOKEN)

    /** Clears both access and refresh tokens from storage (logout). */
    fun clearTokens() {
        prefs.remove(SecurePreferencesKeys.KEY_ACCESS_TOKEN)
        prefs.remove(SecurePreferencesKeys.KEY_REFRESH_TOKEN)
    }

    // ── Token inspection ──────────────────────────────────────────────────────

    /**
     * Decodes the payload of [token] (base64url) and returns its [JwtClaims].
     *
     * @throws IllegalArgumentException if [token] is not a valid 3-part JWT.
     * @throws kotlinx.serialization.SerializationException if the payload JSON is malformed.
     */
    fun parseJwt(token: String): JwtClaims {
        val parts = token.split(".")
        require(parts.size == 3) { "Invalid JWT: expected 3 parts, got ${parts.size}" }
        val payloadJson = decodeBase64Url(parts[1]).decodeToString()
        return json.decodeFromString<JwtClaims>(payloadJson)
    }

    /**
     * Returns `true` if [token] is expired (or cannot be parsed).
     * Adds a 30-second clock-skew buffer to handle minor time drift between client and server.
     *
     * @param token JWT string to evaluate.
     */
    fun isTokenExpired(token: String): Boolean {
        return try {
            val claims = parseJwt(token)
            val nowSeconds = Clock.System.now().epochSeconds
            claims.exp <= nowSeconds + 30
        } catch (_: Exception) {
            true // treat unparseable tokens as expired
        }
    }

    /**
     * Extracts the user ID (`sub` claim) from [token].
     *
     * @throws IllegalArgumentException if the token is malformed.
     */
    fun extractUserId(token: String): String = parseJwt(token).sub

    /**
     * Extracts the [Role] from [token].
     * Falls back to [Role.CASHIER] if the role string does not match any known role.
     *
     * @throws IllegalArgumentException if the token is malformed.
     */
    fun extractRole(token: String): Role {
        val roleName = parseJwt(token).role
        return Role.entries.firstOrNull { it.name.equals(roleName, ignoreCase = true) }
            ?: Role.CASHIER
    }

    // ── Internal ──────────────────────────────────────────────────────────────

    @OptIn(ExperimentalEncodingApi::class)
    private fun decodeBase64Url(input: String): ByteArray {
        // Pad to a multiple of 4 and replace URL-safe chars back to standard
        val standard = input.replace('-', '+').replace('_', '/')
        val padded = when (standard.length % 4) {
            2 -> "$standard=="
            3 -> "$standard="
            else -> standard
        }
        return Base64.decode(padded)
    }
}
