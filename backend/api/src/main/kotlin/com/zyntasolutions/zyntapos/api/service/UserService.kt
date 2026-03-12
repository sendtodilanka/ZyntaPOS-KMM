package com.zyntasolutions.zyntapos.api.service

import at.favre.lib.crypto.bcrypt.BCrypt
import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.zyntasolutions.zyntapos.api.config.AppConfig
import com.zyntasolutions.zyntapos.api.db.PosSessions
import com.zyntasolutions.zyntapos.api.db.Stores
import com.zyntasolutions.zyntapos.api.db.Users
import com.zyntasolutions.zyntapos.api.models.RefreshResponse
import com.zyntasolutions.zyntapos.api.models.UserInfo
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.isNull
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.insert
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

// S2-5: Table objects moved to db/Tables.kt (Stores, Users, PosSessions)

class UserService {
    private val logger = LoggerFactory.getLogger(UserService::class.java)

    companion object {
        private const val MAX_FAILED_ATTEMPTS = 5
        private const val LOCKOUT_DURATION_MS = 15 * 60 * 1000L // 15 minutes
        private const val BCRYPT_COST = 12
    }

    private val bcryptVerifier = BCrypt.verifyer()

    /**
     * S2-6: Dual-hash password verification — supports both formats:
     * - bcrypt (`$2a$...` or `$2b$...`) — new format, GPU-resistant
     * - SHA-256 (`<base64url-salt>:<hex-hash>`) — legacy PinManager format
     *
     * On successful SHA-256 match, the password is re-hashed to bcrypt
     * (rolling migration — no forced password resets, no downtime).
     */
    private fun verifyPasswordHash(password: String, storedHash: String, userId: String? = null): Boolean {
        // Try bcrypt first (new format)
        if (storedHash.startsWith("\$2")) {
            return bcryptVerifier.verify(password.toCharArray(), storedHash.toCharArray()).verified
        }

        // Fallback to SHA-256 (legacy PinManager format: base64url-salt:hex-sha256)
        val sha256Match = verifySha256Hash(password, storedHash)
        if (sha256Match && userId != null) {
            // Rolling upgrade: re-hash to bcrypt on successful login
            upgradeHashToBcrypt(userId, password)
        }
        return sha256Match
    }

    private fun verifySha256Hash(password: String, storedHash: String): Boolean = runCatching {
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

    /** S2-6: Silently upgrade SHA-256 hash to bcrypt on next successful login. */
    private fun upgradeHashToBcrypt(userId: String, plainPassword: String) {
        try {
            val bcryptHash = BCrypt.withDefaults().hashToString(BCRYPT_COST, plainPassword.toCharArray())
            Users.update({ Users.id eq userId }) {
                it[passwordHash] = bcryptHash
            }
            logger.info("POS password upgraded to bcrypt for userId=$userId")
        } catch (e: Exception) {
            // Non-fatal — login still succeeds with SHA-256; upgrade will retry next login
            logger.warn("Failed to upgrade password hash for userId=$userId: ${e.message}")
        }
    }

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
                Stores.selectAll()
                    .where { (Stores.licenseKey eq licenseKey) and (Stores.isActive eq true) }
                    .singleOrNull()
                    ?.get(Stores.id)
                    ?: run {
                        logger.warn("Auth failed: license key not found or inactive")
                        return@newSuspendedTransaction null
                    }
            } else null

            val candidates = if (storeId != null) {
                Users.selectAll()
                    .where { (Users.isActive eq true) and (Users.storeId eq storeId) }
            } else {
                Users.selectAll()
                    .where { Users.isActive eq true }
            }.toList()

            val user = candidates.firstOrNull { row ->
                val storedEmail    = row[Users.email]
                val storedUsername = row[Users.username]
                if (storedEmail != null) storedEmail == email else storedUsername == email
            } ?: run {
                logger.warn("Auth failed: user not found for email=$maskedEmail")
                return@newSuspendedTransaction null
            }

            // A1: Check account lockout
            val lockedUntil = user[Users.lockedUntil]
            if (lockedUntil != null && OffsetDateTime.now(ZoneOffset.UTC).isBefore(lockedUntil)) {
                logger.warn("Auth failed: account locked for email=$maskedEmail")
                return@newSuspendedTransaction null
            }

            // S2-6: Dual-hash verification with rolling bcrypt upgrade
            if (!verifyPasswordHash(password, user[Users.passwordHash], user[Users.id])) {
                incrementFailedAttempts(user[Users.id], maskedEmail)
                logger.warn("Auth failed: invalid password for email=$maskedEmail")
                return@newSuspendedTransaction null
            }

            // Reset failed attempts on successful login
            if (user[Users.failedAttempts] > 0) {
                Users.update({ Users.id eq user[Users.id] }) {
                    it[failedAttempts] = 0
                    it[Users.lockedUntil] = null
                }
            }

            val resolvedEmail = user[Users.email] ?: user[Users.username]
            val resolvedName  = user[Users.name]  ?: resolvedEmail.substringBefore("@")

            logger.info("Auth success: email=$maskedEmail role=${user[Users.role]} storeId=${user[Users.storeId]}")
            UserInfo(
                id        = user[Users.id],
                email     = resolvedEmail,
                name      = resolvedName,
                role      = user[Users.role],
                storeId   = user[Users.storeId],
                isActive  = user[Users.isActive],
                createdAt = user[Users.createdAt].toInstant().toEpochMilli(),
                updatedAt = user[Users.updatedAt].toInstant().toEpochMilli(),
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
        val jti = UUID.randomUUID().toString()
        val algorithm = Algorithm.RSA256(
            config.jwtPublicKey as RSAPublicKey,
            config.jwtPrivateKey as RSAPrivateKey
        )

        val accessToken = JWT.create()
            .withIssuer(config.jwtIssuer)
            .withAudience(config.jwtAudience)
            .withSubject(user.id)
            .withJWTId(jti)
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

            val user = Users.selectAll()
                .where { (Users.id eq userId) and (Users.isActive eq true) }
                .singleOrNull() ?: return@newSuspendedTransaction null

            val jti = UUID.randomUUID().toString()
            val algorithm = Algorithm.RSA256(
                config.jwtPublicKey as RSAPublicKey,
                config.jwtPrivateKey as RSAPrivateKey
            )
            val nowMs = System.currentTimeMillis()

            val newAccessToken = JWT.create()
                .withIssuer(config.jwtIssuer)
                .withAudience(config.jwtAudience)
                .withSubject(userId)
                .withJWTId(jti)
                .withClaim("role", user[Users.role])
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
        val current = Users.selectAll()
            .where { Users.id eq userId }
            .single()[Users.failedAttempts]
        val newCount = current + 1
        val lockoutTime = if (newCount >= MAX_FAILED_ATTEMPTS) {
            OffsetDateTime.now(ZoneOffset.UTC).plusSeconds(LOCKOUT_DURATION_MS / 1000)
        } else null

        Users.update({ Users.id eq userId }) {
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
