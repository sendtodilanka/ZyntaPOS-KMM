package com.zyntasolutions.zyntapos.api.service

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.zyntasolutions.zyntapos.api.config.AppConfig
import com.zyntasolutions.zyntapos.api.models.RefreshResponse
import com.zyntasolutions.zyntapos.api.models.UserInfo
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.javatime.timestampWithTimeZone
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import org.slf4j.LoggerFactory
import java.security.MessageDigest
import java.security.interfaces.RSAPrivateKey
import java.security.interfaces.RSAPublicKey
import java.util.Base64
import java.util.Date

// ── Exposed table (mirrors V1 + V9 schema — users & stores) ──────────────────

private object AppUsers : Table("users") {
    val id           = text("id")
    val storeId      = text("store_id")
    val username     = text("username")
    val email        = text("email").nullable()  // added in V9; falls back to username
    val name         = text("name").nullable()   // added in V9
    val passwordHash = text("password_hash")
    val role         = text("role")
    val isActive     = bool("is_active")
    val createdAt    = timestampWithTimeZone("created_at")
    val updatedAt    = timestampWithTimeZone("updated_at")
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
     * Verifies [password] against the stored PinManager hash.
     * Format: `<base64url-salt>:<hex-sha256>` — constant-time comparison.
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
        if (actualHex.length != expectedHex.length) return@runCatching false
        var diff = 0
        for (i in actualHex.indices) diff = diff or (actualHex[i].code xor expectedHex[i].code)
        diff == 0
    }.getOrDefault(false)

    /**
     * Authenticates a POS user by email and password.
     *
     * [licenseKey] scopes the search to a single store when provided.
     * When absent, matches email globally (suitable for single-store installs).
     */
    suspend fun authenticate(email: String, password: String, licenseKey: String?): UserInfo? =
        newSuspendedTransaction {
            logger.info("Auth attempt: email=$email")

            val storeId: String? = if (licenseKey != null) {
                AppStores.selectAll()
                    .where { (AppStores.licenseKey eq licenseKey) and (AppStores.isActive eq true) }
                    .singleOrNull()
                    ?.get(AppStores.id)
                    ?: run {
                        logger.warn("Auth failed: license key not found or inactive")
                        return@newSuspendedTransaction null
                    }
            } else null

            val candidates = if (storeId != null) {
                AppUsers.selectAll()
                    .where { (AppUsers.isActive eq true) and (AppUsers.storeId eq storeId) }
            } else {
                AppUsers.selectAll()
                    .where { AppUsers.isActive eq true }
            }.toList()

            // Match on email column (V9+) or fall back to username for legacy rows
            val user = candidates.firstOrNull { row ->
                val storedEmail    = row[AppUsers.email]
                val storedUsername = row[AppUsers.username]
                if (storedEmail != null) storedEmail == email else storedUsername == email
            } ?: run {
                logger.warn("Auth failed: user not found for email=$email")
                return@newSuspendedTransaction null
            }

            if (!verifyPasswordHash(password, user[AppUsers.passwordHash])) {
                logger.warn("Auth failed: invalid password for email=$email")
                return@newSuspendedTransaction null
            }

            val resolvedEmail = user[AppUsers.email] ?: user[AppUsers.username]
            val resolvedName  = user[AppUsers.name]  ?: resolvedEmail.substringBefore("@")

            logger.info("Auth success: email=$email role=${user[AppUsers.role]} storeId=${user[AppUsers.storeId]}")
            UserInfo(
                id        = user[AppUsers.id],
                email     = resolvedEmail,
                name      = resolvedName,
                role      = user[AppUsers.role],
                storeId   = user[AppUsers.storeId],
                isActive  = user[AppUsers.isActive],
                createdAt = user[AppUsers.createdAt].toInstant().toEpochMilli(),
                updatedAt = user[AppUsers.updatedAt].toInstant().toEpochMilli(),
            )
        }

    fun refreshTokens(refreshToken: String, config: AppConfig): RefreshResponse? {
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
            decoded.subject ?: return null

            val now = System.currentTimeMillis()
            val newAccessToken = JWT.create()
                .withIssuer(config.jwtIssuer)
                .withAudience(config.jwtAudience)
                .withSubject(decoded.subject)
                .withClaim("role", decoded.getClaim("role").asString())
                .withClaim("storeId", decoded.getClaim("storeId").asString())
                .withClaim("type", "access")
                .withExpiresAt(Date(now + config.accessTokenTtlMs))
                .sign(algorithm)

            RefreshResponse(
                accessToken = newAccessToken,
                expiresIn   = config.accessTokenTtlMs / 1000,
            )
        } catch (e: Exception) {
            logger.warn("Refresh token validation failed: ${e.message}")
            null
        }
    }
}
