package com.zyntasolutions.zyntapos.api.service

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.zyntasolutions.zyntapos.api.config.AppConfig
import com.zyntasolutions.zyntapos.api.models.LoginResponse
import com.zyntasolutions.zyntapos.api.models.UserInfo
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import org.slf4j.LoggerFactory
import java.security.MessageDigest
import java.security.interfaces.RSAPrivateKey
import java.security.interfaces.RSAPublicKey
import java.util.Base64
import java.util.Date

// ── Exposed table (mirrors V1 schema — users & stores) ───────────────────────

private object AppUsers : Table("users") {
    val id           = text("id")
    val storeId      = text("store_id")
    val username     = text("username")
    val passwordHash = text("password_hash")
    val role         = text("role")
    val isActive     = bool("is_active")
    override val primaryKey = PrimaryKey(id)
}

private object AppStores : Table("stores") {
    val id         = text("id")
    val licenseKey = text("license_key")
    val isActive   = bool("is_active")
    override val primaryKey = PrimaryKey(id)
}

class UserService {
    private val logger = LoggerFactory.getLogger(UserService::class.java)

    /**
     * Verifies [password] against a stored hash produced by PinManager on the KMP client.
     * Format: `<base64url-salt>:<hex-sha256>` where hash = SHA-256(salt || password.toByteArray()).
     * Uses constant-time comparison to prevent timing side-channel attacks.
     */
    private fun verifyPasswordHash(password: String, storedHash: String): Boolean = runCatching {
        val parts = storedHash.split(":")
        if (parts.size != 2) return@runCatching false
        val salt = Base64.getUrlDecoder().decode(parts[0])
        val expectedHex = parts[1]
        val digest = MessageDigest.getInstance("SHA-256")
        digest.update(salt)
        digest.update(password.toByteArray(Charsets.UTF_8))
        val actualHex = digest.digest().joinToString("") { "%02x".format(it) }
        // Constant-time comparison
        if (actualHex.length != expectedHex.length) return@runCatching false
        var diff = 0
        for (i in actualHex.indices) diff = diff or (actualHex[i].code xor expectedHex[i].code)
        diff == 0
    }.getOrDefault(false)

    suspend fun authenticate(licenseKey: String, deviceId: String, username: String, password: String): UserInfo? =
        newSuspendedTransaction {
            logger.info("Auth attempt: user=$username device=$deviceId")

            // 1. Resolve store by license key (must be active)
            val store = AppStores.selectAll()
                .where { (AppStores.licenseKey eq licenseKey) and (AppStores.isActive eq true) }
                .singleOrNull()
                ?: run {
                    logger.warn("Auth failed: license key not found or inactive licenseKey=****${licenseKey.takeLast(4)}")
                    return@newSuspendedTransaction null
                }

            val storeId = store[AppStores.id]

            // 2. Look up active user by username within that store
            val user = AppUsers.selectAll()
                .where {
                    (AppUsers.storeId eq storeId) and
                    (AppUsers.username eq username) and
                    (AppUsers.isActive eq true)
                }
                .singleOrNull()
                ?: run {
                    logger.warn("Auth failed: user not found user=$username storeId=$storeId")
                    return@newSuspendedTransaction null
                }

            // 3. Verify password (SHA-256 + salt — matches PinManager format)
            if (!verifyPasswordHash(password, user[AppUsers.passwordHash])) {
                logger.warn("Auth failed: invalid password user=$username storeId=$storeId")
                return@newSuspendedTransaction null
            }

            logger.info("Auth success: user=$username role=${user[AppUsers.role]} storeId=$storeId")
            UserInfo(
                id       = user[AppUsers.id],
                username = user[AppUsers.username],
                role     = user[AppUsers.role],
                storeId  = storeId
            )
        }

    fun refreshTokens(refreshToken: String, config: AppConfig): LoginResponse? {
        return try {
            val algorithm = Algorithm.RSA256(
                config.jwtPublicKey as RSAPublicKey,
                config.jwtPrivateKey as RSAPrivateKey
            )
            val verifier = JWT.require(algorithm)
                .withIssuer(config.jwtIssuer)
                .withClaim("type", "refresh")
                .build()
            val decoded = verifier.verify(refreshToken)
            val userId = decoded.subject ?: return null
            val role = decoded.getClaim("role")?.asString() ?: return null
            val storeId = decoded.getClaim("storeId")?.asString() ?: return null

            val now = System.currentTimeMillis()
            val newAccessToken = JWT.create()
                .withIssuer(config.jwtIssuer)
                .withAudience(config.jwtAudience)
                .withSubject(userId)
                .withClaim("role", role)
                .withClaim("storeId", storeId)
                .withClaim("type", "access")
                .withExpiresAt(Date(now + config.accessTokenTtlMs))
                .sign(algorithm)

            LoginResponse(
                accessToken = newAccessToken,
                refreshToken = refreshToken, // Extend refresh token validity
                expiresIn = config.accessTokenTtlMs / 1000,
                tokenType = "Bearer",
                userId = userId,
                role = role,
                storeId = storeId
            )
        } catch (e: Exception) {
            logger.warn("Refresh token validation failed: ${e.message}")
            null
        }
    }
}
