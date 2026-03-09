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
 * ### Signature verification policy
 *
 * **Online path** — the backend verifies every JWT on each authenticated API call.
 * The client does not need to verify signatures while connected.
 *
 * **Offline RBAC gating** — the client MUST verify the RS256 signature before
 * trusting the `role` claim for navigation route gating. Without verification, an
 * attacker with physical device access could modify the stored JWT in SecurePreferences
 * to claim a higher-privilege role and unlock admin UI while offline. Use
 * [verifyOfflineRole] for this path — it returns `null` when the signature is
 * invalid, forcing the caller to fall back to the lowest-privilege role ([Role.CASHIER]).
 *
 * For all other purposes (expiry checking, userId display) signature verification is
 * not required and [parseJwt] / [extractRole] may be used directly.
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
     * Extracts the [Role] from [token] **without** verifying the RS256 signature.
     * Falls back to [Role.CASHIER] if the role string does not match any known role.
     *
     * **Only use this for non-sensitive display purposes** (e.g., showing the role
     * label in the UI while online). For RBAC route gating use [verifyOfflineRole].
     *
     * @throws IllegalArgumentException if the token is malformed.
     */
    fun extractRole(token: String): Role {
        val roleName = parseJwt(token).role
        return Role.entries.firstOrNull { it.name.equals(roleName, ignoreCase = true) }
            ?: Role.CASHIER
    }

    /**
     * Cryptographically verifies the RS256 signature of [token] and returns its [Role]
     * claim, or `null` if the signature is invalid or the token is malformed.
     *
     * **Use this for offline RBAC route gating** to prevent privilege escalation via
     * JWT tampering in [SecurePreferences]. Falls back to `null` on any failure so
     * callers can degrade safely to [Role.CASHIER].
     *
     * @param token            Raw JWT string (three base64url segments separated by `.`).
     * @param publicKeyDerBase64 Base64-encoded DER bytes of the RSA public key
     *                           (SubjectPublicKeyInfo format — same as `ZYNTA_RS256_PUBLIC_KEY`
     *                           in `local.properties`). Standard Base64, not URL-safe.
     * @return The [Role] from the `role` claim if the signature is valid;
     *         `null` if verification fails or the role string is unrecognised.
     */
    @OptIn(ExperimentalEncodingApi::class)
    fun verifyOfflineRole(token: String, publicKeyDerBase64: String): Role? = runCatching {
        val parts = token.split(".")
        if (parts.size != 3) return@runCatching null

        // Signed data is the raw ASCII bytes of "header.payload"
        val signedData = "${parts[0]}.${parts[1]}".encodeToByteArray()
        val signatureBytes = decodeBase64Url(parts[2])
        val publicKeyDer = Base64.decode(publicKeyDerBase64)

        if (!verifyRs256Signature(signedData, signatureBytes, publicKeyDer)) return@runCatching null

        val payloadJson = decodeBase64Url(parts[1]).decodeToString()
        val claims = json.decodeFromString<JwtClaims>(payloadJson)
        Role.entries.firstOrNull { it.name.equals(claims.role, ignoreCase = true) }
    }.getOrDefault(null)

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
