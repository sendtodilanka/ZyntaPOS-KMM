package com.zyntasolutions.zyntapos.api.service

import at.favre.lib.crypto.bcrypt.BCrypt
import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.zyntasolutions.zyntapos.api.auth.AdminRole
import com.zyntasolutions.zyntapos.api.config.AppConfig
import com.zyntasolutions.zyntapos.api.models.AdminPagedResponse
import com.zyntasolutions.zyntapos.api.models.AdminUserResponse
import com.zyntasolutions.zyntapos.api.db.AdminSessions
import com.zyntasolutions.zyntapos.api.db.AdminUsers
import com.zyntasolutions.zyntapos.api.db.PasswordResetTokens
import com.zyntasolutions.zyntapos.api.repository.AdminUserRepository
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.isNull
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import org.slf4j.LoggerFactory
import java.security.MessageDigest
import java.security.SecureRandom
import java.security.interfaces.RSAPrivateKey
import java.security.interfaces.RSAPublicKey
import java.time.Instant
import java.util.*

private val logger = LoggerFactory.getLogger(AdminAuthService::class.java)

// S2-5: Table objects moved to db/Tables.kt

// ── Data classes ────────────────────────────────────────────────────────────

data class AdminUserRow(
    val id: UUID,
    val email: String,
    val name: String,
    val role: AdminRole,
    val passwordHash: String?,
    val mfaEnabled: Boolean,
    val isActive: Boolean,
    val lastLoginAt: Long?,
    val createdAt: Long
)

data class AdminSessionRow(
    val id: UUID,
    val userId: UUID,
    val userAgent: String?,
    val ipAddress: String?,
    val createdAt: Long,
    val expiresAt: Long,
    val revokedAt: Long?
)

sealed class AdminAuthResult {
    data class Success(val user: AdminUserRow, val accessToken: String, val refreshToken: String) : AdminAuthResult()
    data class MfaRequired(val user: AdminUserRow, val pendingToken: String) : AdminAuthResult()
    data object InvalidCredentials : AdminAuthResult()
    data object AccountLocked : AdminAuthResult()
    data object AccountInactive : AdminAuthResult()
}

// ── Service ─────────────────────────────────────────────────────────────────

class AdminAuthService(
    private val config: AppConfig,
    private val auditService: AdminAuditService,
    private val adminUserRepo: AdminUserRepository,
) {

    private val bcryptVerifier = BCrypt.verifyer()

    // ── Login ────────────────────────────────────────────────────────────────

    suspend fun login(email: String, password: String, ip: String?, userAgent: String?): AdminAuthResult {
        // G2: Reject oversized passwords before bcrypt (bcrypt truncates at 72 bytes).
        // Return directly — no timing-attack concern for clearly oversized input.
        if (password.length > MAX_PASSWORD_LENGTH) {
            return AdminAuthResult.InvalidCredentials
        }

        val row = adminUserRepo.findByEmail(email) ?: run {
            bcryptVerifier.verify(password.toCharArray(), DUMMY_HASH)
            return AdminAuthResult.InvalidCredentials
        }

        if (!row.isActive) return AdminAuthResult.AccountInactive

        val lockedUntilMs = adminUserRepo.getLockedUntil(row.id)
        if (lockedUntilMs != null && Instant.now().toEpochMilli() < lockedUntilMs) {
            return AdminAuthResult.AccountLocked
        }

        if (row.passwordHash == null) {
            return AdminAuthResult.InvalidCredentials
        }

        val verified = bcryptVerifier.verify(password.toCharArray(), row.passwordHash.toCharArray())
        if (!verified.verified) {
            incrementFailedAttempts(row.id, row.email, ip, userAgent)
            return AdminAuthResult.InvalidCredentials
        }

        // Reset lockout state on successful login
        val now = Instant.now().toEpochMilli()
        adminUserRepo.updateLoginSuccess(row.id, now, ip)

        // G3: Audit successful login
        auditService.log(
            adminId    = row.id,
            adminName  = row.name,
            eventType  = "ADMIN_LOGIN_SUCCESS",
            category   = "AUTH",
            ipAddress  = ip,
            userAgent  = userAgent,
            success    = true
        )

        // If MFA is enabled, issue a short-lived pending token instead of full access
        if (row.mfaEnabled) {
            val pendingToken = issueMfaPendingToken(row)
            return AdminAuthResult.MfaRequired(row, pendingToken)
        }

        val (accessToken, refreshToken) = issueTokens(row, ip, userAgent)
        return AdminAuthResult.Success(row, accessToken, refreshToken)
    }

    // ── Token refresh ────────────────────────────────────────────────────────

    suspend fun refresh(rawRefreshToken: String, ip: String?, userAgent: String?): Pair<String, String>? {
        val hash = sha256Hex(rawRefreshToken)
        val now = Instant.now().toEpochMilli()
        val session = adminUserRepo.findSessionByTokenHash(hash, now) ?: return null

        // Revoke the old session (single-use rotation)
        adminUserRepo.revokeSession(session.id, now)

        val row = adminUserRepo.findById(session.userId) ?: return null

        // G3: Audit token refresh
        auditService.log(
            adminId   = row.id,
            adminName = row.name,
            eventType = "ADMIN_TOKEN_REFRESHED",
            category  = "AUTH",
            ipAddress = ip,
            userAgent = userAgent,
            success   = true
        )

        return issueTokens(row, ip, userAgent)
    }

    // ── Logout ───────────────────────────────────────────────────────────────

    suspend fun logout(rawRefreshToken: String, adminId: UUID? = null, adminName: String? = null, ip: String? = null) {
        val hash = sha256Hex(rawRefreshToken)
        val now = Instant.now().toEpochMilli()
        adminUserRepo.revokeSessionByTokenHash(hash, now)
        // G3: Audit logout
        auditService.log(
            adminId   = adminId,
            adminName = adminName,
            eventType = "ADMIN_LOGOUT",
            category  = "AUTH",
            ipAddress = ip,
            success   = true
        )
    }

    // ── Get user by JWT subject ───────────────────────────────────────────────

    suspend fun findById(userId: UUID): AdminUserRow? = adminUserRepo.findById(userId)

    suspend fun emailExists(email: String): Boolean = adminUserRepo.findByEmail(email) != null

    // ── Bootstrap (first-run only) ───────────────────────────────────────────

    suspend fun needsBootstrap(): Boolean = adminUserRepo.count() == 0L

    suspend fun bootstrap(email: String, name: String, password: String): AdminUserRow? {
        if (adminUserRepo.count() != 0L) return null
        val hash = BCrypt.withDefaults().hashToString(12, password.toCharArray())
        return adminUserRepo.createUser(email, name, AdminRole.ADMIN, hash)
    }

    // ── Admin user management ────────────────────────────────────────────────

    suspend fun createUser(email: String, name: String, role: AdminRole, password: String): AdminUserRow {
        val hash = BCrypt.withDefaults().hashToString(12, password.toCharArray())
        return adminUserRepo.createUser(email, name, role, hash)
    }

    /** G7: Paginated user list with optional filters. */
    suspend fun listUsers(
        page: Int = 1,
        size: Int = 20,
        search: String? = null,
        role: AdminRole? = null,
        isActive: Boolean? = null
    ): AdminPagedResponse<AdminUserResponse> = newSuspendedTransaction {
        val offset = ((page - 1) * size).toLong()

        val query = AdminUsers.selectAll().apply {
            if (!search.isNullOrBlank()) {
                val term = "%${search.lowercase()}%"
                andWhere {
                    (AdminUsers.email.lowerCase() like term) or
                    (AdminUsers.name.lowerCase() like term)
                }
            }
            role?.let { andWhere { AdminUsers.role eq it.name } }
            isActive?.let { andWhere { AdminUsers.isActive eq it } }
        }

        val total = query.count().toInt()
        val items = query
            .orderBy(AdminUsers.createdAt, SortOrder.DESC)
            .limit(size).offset(offset)
            .map { it.toAdminUserRow().toResponse() }

        AdminPagedResponse(
            data       = items,
            page       = page,
            size       = size,
            total      = total,
            totalPages = if (total == 0) 0 else ((total - 1) / size) + 1
        )
    }

    suspend fun updateUser(id: UUID, name: String?, role: AdminRole?, isActive: Boolean?): AdminUserRow? =
        adminUserRepo.updateUser(id, name, role, isActive)

    suspend fun revokeAllSessions(userId: UUID) =
        adminUserRepo.revokeAllSessions(userId, Instant.now().toEpochMilli())

    /** Lists active (non-revoked, non-expired) sessions for a user. */
    suspend fun listActiveSessions(userId: UUID): List<AdminSessionRow> =
        adminUserRepo.listActiveSessions(userId, Instant.now().toEpochMilli())

    /** Changes password after verifying the current password. Returns false on wrong current password. */
    suspend fun changePassword(userId: UUID, currentPassword: String, newPassword: String): Boolean {
        val row = adminUserRepo.findByIdWithPassword(userId) ?: return false

        val currentHash = row.passwordHash ?: return false
        val verified = bcryptVerifier.verify(currentPassword.toCharArray(), currentHash.toCharArray())
        if (!verified.verified) return false

        val newHash = BCrypt.withDefaults().hashToString(12, newPassword.toCharArray())
        adminUserRepo.updatePassword(userId, newHash)
        adminUserRepo.revokeAllSessions(userId, Instant.now().toEpochMilli())
        return true
    }

    // ── Token issuance for external callers (Google OAuth) ───────────────────

    suspend fun issueTokensForUser(user: AdminUserRow, ip: String?, userAgent: String?): Pair<String, String> =
        issueTokens(user, ip, userAgent)

    // ── MFA completion ───────────────────────────────────────────────────────

    suspend fun completeMfaLogin(pendingToken: String, ip: String?, userAgent: String?): Pair<AdminUserRow, Pair<String, String>>? {
        val userId = verifyMfaPendingToken(pendingToken) ?: return null
        val row = adminUserRepo.findById(userId) ?: return null
        val tokens = issueTokens(row, ip, userAgent)
        return row to tokens
    }

    fun issueMfaPendingToken(user: AdminUserRow): String {
        val algorithm = Algorithm.RSA256(config.adminJwtPublicKey as RSAPublicKey, config.adminJwtPrivateKey as RSAPrivateKey)
        val now = Instant.now().toEpochMilli()
        return JWT.create()
            .withIssuer(config.adminJwtIssuer)
            .withSubject(user.id.toString())
            .withClaim("type", "admin_mfa_pending")
            .withIssuedAt(Date(now))
            .withExpiresAt(Date(now + 2 * 60 * 1000L)) // 2 minutes
            .sign(algorithm)
    }

    fun verifyMfaPendingToken(token: String): UUID? {
        return try {
            val algorithm = Algorithm.RSA256(config.adminJwtPublicKey as RSAPublicKey, null)
            val verifier = JWT.require(algorithm)
                .withIssuer(config.adminJwtIssuer)
                .withClaim("type", "admin_mfa_pending")
                .build()
            val decoded = verifier.verify(token)
            UUID.fromString(decoded.subject)
        } catch (e: Exception) {
            logger.debug("MFA pending token verification failed: ${e.message}")
            null
        }
    }

    // ── JWT ─────────────────────────────────────────────────────────────────

    fun verifyAccessToken(token: String): UUID? {
        return try {
            val algorithm = Algorithm.RSA256(config.adminJwtPublicKey as RSAPublicKey, null)
            val verifier = JWT.require(algorithm)
                .withIssuer(config.adminJwtIssuer)
                .withClaim("type", "admin_access")
                .build()
            val decoded = verifier.verify(token)
            UUID.fromString(decoded.subject)
        } catch (e: Exception) {
            logger.debug("Admin JWT verification failed: ${e.message}")
            null
        }
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private suspend fun issueTokens(user: AdminUserRow, ip: String?, userAgent: String?): Pair<String, String> {
        val algorithm = Algorithm.RSA256(config.adminJwtPublicKey as RSAPublicKey, config.adminJwtPrivateKey as RSAPrivateKey)
        val now = Instant.now().toEpochMilli()

        val accessToken = JWT.create()
            .withIssuer(config.adminJwtIssuer)
            .withSubject(user.id.toString())
            .withClaim("email", user.email)
            .withClaim("name", user.name)
            .withClaim("role", user.role.name)
            .withClaim("mfa", user.mfaEnabled)
            .withClaim("type", "admin_access")
            .withIssuedAt(Date(now))
            .withExpiresAt(Date(now + config.adminAccessTokenTtlMs))
            .sign(algorithm)

        val rawRefreshToken = UUID.randomUUID().toString() + UUID.randomUUID().toString()
        val refreshHash = sha256Hex(rawRefreshToken)
        val refreshExpiresAt = now + config.adminRefreshTokenTtlDays * 86_400_000L

        adminUserRepo.insertSession(user.id, refreshHash, ip, userAgent, now, refreshExpiresAt)

        return accessToken to rawRefreshToken
    }

    private suspend fun incrementFailedAttempts(userId: UUID, email: String, ip: String?, userAgent: String?) {
        val current = adminUserRepo.getFailedAttempts(userId)
        val newCount = current + 1
        val lockedUntil = if (newCount >= MAX_FAILED_ATTEMPTS) {
            Instant.now().toEpochMilli() + LOCKOUT_DURATION_MS
        } else null

        adminUserRepo.updateFailedAttempts(userId, newCount, lockedUntil)

        // G3: Audit login failure
        auditService.log(
            adminId      = userId,
            adminName    = null,
            eventType    = if (lockedUntil != null) "ADMIN_LOGIN_LOCKOUT" else "ADMIN_LOGIN_FAILURE",
            category     = "AUTH",
            entityType   = "admin_user",
            entityId     = userId.toString(),
            newValues    = mapOf("failedAttempts" to newCount.toString()),
            ipAddress    = ip,
            userAgent    = userAgent,
            success      = false,
            errorMessage = if (lockedUntil != null) "Account locked after $newCount failed attempts" else "Invalid credentials"
        )

        if (lockedUntil != null) {
            logger.warn("Admin account locked after $newCount failed attempts: userId=$userId email=$email")
        }
    }

    // ── Password Reset ────────────────────────────────────────────────────────

    suspend fun generatePasswordResetToken(email: String): String? {
        val user = adminUserRepo.findByEmail(email) ?: return null

        adminUserRepo.deleteUnusedResetTokens(user.id)

        val rawToken = generateSecureToken()
        val tokenHash = sha256Hex(rawToken)
        val now = Instant.now().toEpochMilli()

        adminUserRepo.insertResetToken(UUID.randomUUID(), user.id, tokenHash, now + RESET_TOKEN_TTL_MS, now)
        return rawToken
    }

    suspend fun resetPassword(rawToken: String, newPassword: String): Boolean {
        val tokenHash = sha256Hex(rawToken)
        val now = Instant.now().toEpochMilli()

        val tokenRow = adminUserRepo.findResetToken(tokenHash) ?: return false
        if (tokenRow.usedAt != null) return false
        if (tokenRow.expiresAt < now) return false

        val newHash = BCrypt.withDefaults().hashToString(12, newPassword.toCharArray())
        adminUserRepo.updatePassword(tokenRow.adminUserId, newHash)
        adminUserRepo.revokeAllSessions(tokenRow.adminUserId, now)
        adminUserRepo.markResetTokenUsed(tokenHash, now)
        return true
    }

    private fun generateSecureToken(): String {
        val bytes = ByteArray(32)
        SecureRandom().nextBytes(bytes)
        return bytes.joinToString("") { "%02x".format(it) }
    }

    private fun sha256Hex(input: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        return digest.digest(input.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
    }

    private fun ResultRow.toAdminUserRow() = AdminUserRow(
        id           = this[AdminUsers.id],
        email        = this[AdminUsers.email],
        name         = this[AdminUsers.name],
        role         = AdminRole.fromString(this[AdminUsers.role]) ?: AdminRole.AUDITOR,
        passwordHash = this[AdminUsers.passwordHash],
        mfaEnabled   = this[AdminUsers.mfaEnabled],
        isActive     = this[AdminUsers.isActive],
        lastLoginAt  = this[AdminUsers.lastLoginAt],
        createdAt    = this[AdminUsers.createdAt]
    )

    companion object {
        private const val MAX_FAILED_ATTEMPTS = 5
        private const val LOCKOUT_DURATION_MS = 15 * 60 * 1000L  // 15 minutes
        private const val RESET_TOKEN_TTL_MS  = 60 * 60 * 1000L  // 1 hour

        /** G2: bcrypt silently truncates at 72 bytes — reject anything over 128 chars to prevent DoS. */
        const val MAX_PASSWORD_LENGTH = 128

        // Constant-time dummy hash to prevent timing attacks on unknown emails
        private val DUMMY_HASH = BCrypt.withDefaults().hashToChar(12, "dummy-prevent-timing-attack".toCharArray())
    }
}

// ── Extension for response serialization ────────────────────────────────────

fun AdminUserRow.toResponse() = AdminUserResponse(
    id          = id.toString(),
    email       = email,
    name        = name,
    role        = role.name,
    mfaEnabled  = mfaEnabled,
    isActive    = isActive,
    lastLoginAt = lastLoginAt,
    createdAt   = createdAt
)
