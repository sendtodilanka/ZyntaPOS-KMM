package com.zyntasolutions.zyntapos.api.service

import at.favre.lib.crypto.bcrypt.BCrypt
import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.zyntasolutions.zyntapos.api.config.AppConfig
import com.zyntasolutions.zyntapos.api.models.RefreshResponse
import com.zyntasolutions.zyntapos.api.models.UserInfo
import com.zyntasolutions.zyntapos.api.repository.PosUserRepository
import com.zyntasolutions.zyntapos.api.repository.PosUserRow
import org.slf4j.LoggerFactory
import java.security.MessageDigest
import java.security.interfaces.RSAPrivateKey
import java.security.interfaces.RSAPublicKey
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.Base64
import java.util.Date
import java.util.UUID

// S2-5: Table objects moved to db/Tables.kt (Stores, Users, PosSessions)

class UserService(
    private val posUserRepo: PosUserRepository,
) {
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
    private suspend fun verifyPasswordHash(password: String, storedHash: String, userId: String? = null): Boolean {
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
    private suspend fun upgradeHashToBcrypt(userId: String, plainPassword: String) {
        try {
            val bcryptHash = BCrypt.withDefaults().hashToString(BCRYPT_COST, plainPassword.toCharArray())
            posUserRepo.updatePasswordHash(userId, bcryptHash)
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
    suspend fun authenticate(email: String, password: String, licenseKey: String?): UserInfo? {
        val maskedEmail = maskEmail(email)
        logger.info("Auth attempt: email=$maskedEmail")

        val storeId: String? = if (licenseKey != null) {
            posUserRepo.findStoreByLicenseKey(licenseKey) ?: run {
                logger.warn("Auth failed: license key not found or inactive")
                return null
            }
        } else null

        val candidates = posUserRepo.findActiveUsersByStore(storeId)

        val user = candidates.firstOrNull { row ->
            if (row.email != null) row.email == email else row.username == email
        } ?: run {
            logger.warn("Auth failed: user not found for email=$maskedEmail")
            return null
        }

        // A1: Check account lockout
        if (user.lockedUntil != null && OffsetDateTime.now(ZoneOffset.UTC).isBefore(user.lockedUntil)) {
            logger.warn("Auth failed: account locked for email=$maskedEmail")
            return null
        }

        // S2-6: Dual-hash verification with rolling bcrypt upgrade
        if (!verifyPasswordHash(password, user.passwordHash, user.id)) {
            incrementFailedAttempts(user.id, maskedEmail)
            logger.warn("Auth failed: invalid password for email=$maskedEmail")
            return null
        }

        // Reset failed attempts on successful login
        if (user.failedAttempts > 0) {
            posUserRepo.resetFailedAttempts(user.id)
        }

        val resolvedEmail = user.email ?: user.username
        val resolvedName = user.name ?: resolvedEmail.substringBefore("@")

        logger.info("Auth success: email=$maskedEmail role=${user.role} storeId=${user.storeId}")
        return UserInfo(
            id = user.id,
            email = resolvedEmail,
            name = resolvedName,
            role = user.role,
            storeId = user.storeId,
            isActive = user.isActive,
            createdAt = user.createdAt.toInstant().toEpochMilli(),
            updatedAt = user.updatedAt.toInstant().toEpochMilli(),
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
    ): Triple<String, String, Long> {
        val now = Instant.now().toEpochMilli()
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

        posUserRepo.insertPosSession(user.id, user.storeId, refreshHash, deviceId, userAgent, ip, refreshExpiresAt)

        return Triple(accessToken, rawRefreshToken, config.accessTokenTtlMs / 1000)
    }

    /**
     * Refreshes POS tokens using an opaque refresh token with single-use rotation (A3).
     * The old refresh token is revoked and a new pair is issued.
     */
    suspend fun refreshTokens(rawRefreshToken: String, config: AppConfig, ip: String?, userAgent: String?): RefreshResponse? {
        val hash = sha256Hex(rawRefreshToken)
        val now = OffsetDateTime.now(ZoneOffset.UTC)

        val session = posUserRepo.findPosSessionByTokenHash(hash, now) ?: return null

        // Revoke old session (single-use rotation)
        posUserRepo.revokePosSession(session.id, now)

        val user = posUserRepo.findActiveUserById(session.userId) ?: return null

        val jti = UUID.randomUUID().toString()
        val algorithm = Algorithm.RSA256(
            config.jwtPublicKey as RSAPublicKey,
            config.jwtPrivateKey as RSAPrivateKey
        )
        val nowMs = Instant.now().toEpochMilli()

        val newAccessToken = JWT.create()
            .withIssuer(config.jwtIssuer)
            .withAudience(config.jwtAudience)
            .withSubject(session.userId)
            .withJWTId(jti)
            .withClaim("role", user.role)
            .withClaim("storeId", session.storeId)
            .withClaim("type", "access")
            .withExpiresAt(Date(nowMs + config.accessTokenTtlMs))
            .sign(algorithm)

        // Issue new opaque refresh token
        val newRawRefresh = UUID.randomUUID().toString() + UUID.randomUUID().toString()
        val newRefreshHash = sha256Hex(newRawRefresh)
        val refreshExpiresAt = now.plusSeconds(config.refreshTokenTtlMs / 1000)

        posUserRepo.insertPosSession(
            session.userId, session.storeId, newRefreshHash,
            session.deviceId, userAgent, ip, refreshExpiresAt
        )

        return RefreshResponse(
            accessToken = newAccessToken,
            refreshToken = newRawRefresh,
            expiresIn = config.accessTokenTtlMs / 1000,
        )
    }

    /**
     * Revokes all POS sessions for a user (e.g., on password change or deactivation).
     */
    suspend fun revokeAllSessions(userId: String) {
        posUserRepo.revokeAllPosSessions(userId, OffsetDateTime.now(ZoneOffset.UTC))
    }

    // ── Private helpers ────────────────────────────────────────────────────────

    private suspend fun incrementFailedAttempts(userId: String, maskedEmail: String) {
        val candidates = posUserRepo.findActiveUsersByStore(null)
        val current = candidates.firstOrNull { it.id == userId }?.failedAttempts ?: 0
        val newCount = current + 1
        val lockoutTime = if (newCount >= MAX_FAILED_ATTEMPTS) {
            OffsetDateTime.now(ZoneOffset.UTC).plusSeconds(LOCKOUT_DURATION_MS / 1000)
        } else null

        posUserRepo.updateFailedAttempts(userId, newCount, lockoutTime)

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
