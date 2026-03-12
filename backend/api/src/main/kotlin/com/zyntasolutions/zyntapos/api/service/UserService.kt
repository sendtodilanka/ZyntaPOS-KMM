package com.zyntasolutions.zyntapos.api.service

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.zyntasolutions.zyntapos.api.config.AppConfig
import com.zyntasolutions.zyntapos.api.models.RefreshResponse
import com.zyntasolutions.zyntapos.api.models.UserInfo
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.isNull
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.javatime.timestampWithTimeZone
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import org.jetbrains.exposed.sql.update
import org.slf4j.LoggerFactory
import java.security.MessageDigest
import java.security.interfaces.RSAPrivateKey
import java.security.interfaces.RSAPublicKey
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.Base64
import java.util.Date
import java.util.UUID

// ── Exposed tables (mirrors V1 + V9 + V11 schema) ────────────────────────────

private object AppUsers : Table("users") {
    val id             = text("id")
    val storeId        = text("store_id")
    val username       = text("username")
    val email          = text("email").nullable()
    val name           = text("name").nullable()
    val passwordHash   = text("password_hash")
    val role           = text("role")
    val isActive       = bool("is_active")
    val failedAttempts = integer("failed_attempts")
    val lockedUntil    = timestampWithTimeZone("locked_until").nullable()
    val createdAt      = timestampWithTimeZone("created_at")
    val updatedAt      = timestampWithTimeZone("updated_at")
    override val primaryKey = PrimaryKey(id)
}

private object AppStores : Table("stores") {
    val id         = text("id")
    val licenseKey = text("license_key")
    val isActive   = bool("is_active")
    override val primaryKey = PrimaryKey(id)
}

private object PosSessions : Table("pos_sessions") {
    val id         = uuid("id")
    val userId     = text("user_id")
    val storeId    = text("store_id")
    val tokenHash  = text("token_hash")
    val deviceId   = text("device_id").nullable()
    val userAgent  = text("user_agent").nullable()
    val ipAddress  = text("ip_address").nullable()
    val createdAt  = timestampWithTimeZone("created_at")
    val expiresAt  = timestampWithTimeZone("expires_at")
    val revokedAt  = timestampWithTimeZone("revoked_at").nullable()
    override val primaryKey = PrimaryKey(id)
}

class UserService {
    private val logger = LoggerFactory.getLogger(UserService::class.java)

    companion object {
        private const val MAX_FAILED_ATTEMPTS = 5
        private const val LOCKOUT_DURATION_MS = 15 * 60 * 1000L // 15 minutes
    }

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
     * Implements brute-force protection (A1): accounts are locked after [MAX_FAILED_ATTEMPTS]
     * failed attempts for [LOCKOUT_DURATION_MS].
     *
     * [licenseKey] scopes the search to a single store when provided.
     */
    suspend fun authenticate(email: String, password: String, licenseKey: String?): UserInfo? =
        newSuspendedTransaction {
            val maskedEmail = maskEmail(email)
            logger.info("Auth attempt: email=$maskedEmail")

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

            val user = candidates.firstOrNull { row ->
                val storedEmail    = row[AppUsers.email]
                val storedUsername = row[AppUsers.username]
                if (storedEmail != null) storedEmail == email else storedUsername == email
            } ?: run {
                logger.warn("Auth failed: user not found for email=$maskedEmail")
                return@newSuspendedTransaction null
            }

            // A1: Check account lockout
            val lockedUntil = user[AppUsers.lockedUntil]
            if (lockedUntil != null && OffsetDateTime.now(ZoneOffset.UTC).isBefore(lockedUntil)) {
                logger.warn("Auth failed: account locked for email=$maskedEmail")
                return@newSuspendedTransaction null
            }

            if (!verifyPasswordHash(password, user[AppUsers.passwordHash])) {
                incrementFailedAttempts(user[AppUsers.id], maskedEmail)
                logger.warn("Auth failed: invalid password for email=$maskedEmail")
                return@newSuspendedTransaction null
            }

            // Reset failed attempts on successful login
            if (user[AppUsers.failedAttempts] > 0) {
                AppUsers.update({ AppUsers.id eq user[AppUsers.id] }) {
                    it[failedAttempts] = 0
                    it[AppUsers.lockedUntil] = null
                }
            }

            val resolvedEmail = user[AppUsers.email] ?: user[AppUsers.username]
            val resolvedName  = user[AppUsers.name]  ?: resolvedEmail.substringBefore("@")

            logger.info("Auth success: email=$maskedEmail role=${user[AppUsers.role]} storeId=${user[AppUsers.storeId]}")
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

    /**
     * Issues JWT access token + stores an opaque refresh token in pos_sessions (A3).
     * Returns (accessToken, refreshToken, expiresInSeconds).
     */
    suspend fun issueTokens(
        user: UserInfo,
        config: AppConfig,
        deviceId: String?,
        ip: String?,
        userAgent: String?
    ): Triple<String, String, Long> = newSuspendedTransaction {
        val now = System.currentTimeMillis()
        val algorithm = Algorithm.RSA256(
            config.jwtPublicKey as RSAPublicKey,
            config.jwtPrivateKey as RSAPrivateKey
        )

        val accessToken = JWT.create()
            .withIssuer(config.jwtIssuer)
            .withAudience(config.jwtAudience)
            .withSubject(user.id)
            .withClaim("role", user.role)
            .withClaim("storeId", user.storeId)
            .withClaim("type", "access")
            .withExpiresAt(Date(now + config.accessTokenTtlMs))
            .sign(algorithm)

        // Opaque refresh token stored server-side (not a JWT)
        val rawRefreshToken = UUID.randomUUID().toString() + UUID.randomUUID().toString()
        val refreshHash = sha256Hex(rawRefreshToken)
        val refreshExpiresAt = OffsetDateTime.now(ZoneOffset.UTC)
            .plusSeconds(config.refreshTokenTtlMs / 1000)

        PosSessions.insert {
            it[userId] = user.id
            it[PosSessions.storeId] = user.storeId
            it[tokenHash] = refreshHash
            it[PosSessions.deviceId] = deviceId
            it[PosSessions.userAgent] = userAgent
            it[ipAddress] = ip
            it[expiresAt] = refreshExpiresAt
        }

        Triple(accessToken, rawRefreshToken, config.accessTokenTtlMs / 1000)
    }

    /**
     * Refreshes POS tokens using an opaque refresh token with single-use rotation (A3).
     * The old refresh token is revoked and a new pair is issued.
     */
    suspend fun refreshTokens(rawRefreshToken: String, config: AppConfig, ip: String?, userAgent: String?): RefreshResponse? =
        newSuspendedTransaction {
            val hash = sha256Hex(rawRefreshToken)
            val now = OffsetDateTime.now(ZoneOffset.UTC)

            val session = PosSessions.selectAll()
                .where {
                    (PosSessions.tokenHash eq hash) and
                    (PosSessions.revokedAt.isNull()) and
                    (PosSessions.expiresAt greater now)
                }
                .singleOrNull() ?: return@newSuspendedTransaction null

            // Revoke old session (single-use rotation)
            PosSessions.update({ PosSessions.id eq session[PosSessions.id] }) {
                it[revokedAt] = now
            }

            val userId = session[PosSessions.userId]
            val storeId = session[PosSessions.storeId]

            val user = AppUsers.selectAll()
                .where { (AppUsers.id eq userId) and (AppUsers.isActive eq true) }
                .singleOrNull() ?: return@newSuspendedTransaction null

            val algorithm = Algorithm.RSA256(
                config.jwtPublicKey as RSAPublicKey,
                config.jwtPrivateKey as RSAPrivateKey
            )
            val nowMs = System.currentTimeMillis()

            val newAccessToken = JWT.create()
                .withIssuer(config.jwtIssuer)
                .withAudience(config.jwtAudience)
                .withSubject(userId)
                .withClaim("role", user[AppUsers.role])
                .withClaim("storeId", storeId)
                .withClaim("type", "access")
                .withExpiresAt(Date(nowMs + config.accessTokenTtlMs))
                .sign(algorithm)

            // Issue new opaque refresh token
            val newRawRefresh = UUID.randomUUID().toString() + UUID.randomUUID().toString()
            val newRefreshHash = sha256Hex(newRawRefresh)
            val refreshExpiresAt = now.plusSeconds(config.refreshTokenTtlMs / 1000)

            PosSessions.insert {
                it[PosSessions.userId] = userId
                it[PosSessions.storeId] = storeId
                it[tokenHash] = newRefreshHash
                it[deviceId] = session[PosSessions.deviceId]
                it[PosSessions.userAgent] = userAgent
                it[ipAddress] = ip
                it[expiresAt] = refreshExpiresAt
            }

            RefreshResponse(
                accessToken  = newAccessToken,
                refreshToken = newRawRefresh,
                expiresIn    = config.accessTokenTtlMs / 1000,
            )
        }

    /**
     * Revokes all POS sessions for a user (e.g., on password change or deactivation).
     */
    suspend fun revokeAllSessions(userId: String) = newSuspendedTransaction {
        val now = OffsetDateTime.now(ZoneOffset.UTC)
        PosSessions.update({ (PosSessions.userId eq userId) and PosSessions.revokedAt.isNull() }) {
            it[revokedAt] = now
        }
    }

    // ── Private helpers ────────────────────────────────────────────────────────

    private fun incrementFailedAttempts(userId: String, maskedEmail: String) {
        val current = AppUsers.selectAll()
            .where { AppUsers.id eq userId }
            .single()[AppUsers.failedAttempts]
        val newCount = current + 1
        val lockoutTime = if (newCount >= MAX_FAILED_ATTEMPTS) {
            OffsetDateTime.now(ZoneOffset.UTC).plusSeconds(LOCKOUT_DURATION_MS / 1000)
        } else null

        AppUsers.update({ AppUsers.id eq userId }) {
            it[failedAttempts] = newCount
            it[lockedUntil] = lockoutTime
        }

        if (lockoutTime != null) {
            logger.warn("POS account locked after $newCount failed attempts: email=$maskedEmail")
        }
    }

    private fun sha256Hex(input: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        return digest.digest(input.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
    }

    /** Masks email for GDPR-compliant logging (§2.12). */
    private fun maskEmail(email: String): String {
        val atIndex = email.indexOf('@')
        if (atIndex <= 1) return "***@${email.substringAfter('@', "***")}"
        return "${email[0]}***@${email.substringAfter('@')}"
    }
}
