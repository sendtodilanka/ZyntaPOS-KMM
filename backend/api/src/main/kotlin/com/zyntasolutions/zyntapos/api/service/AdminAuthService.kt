package com.zyntasolutions.zyntapos.api.service

import at.favre.lib.crypto.bcrypt.BCrypt
import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.zyntasolutions.zyntapos.api.auth.AdminRole
import com.zyntasolutions.zyntapos.api.config.AppConfig
import com.zyntasolutions.zyntapos.api.models.AdminPagedResponse
import com.zyntasolutions.zyntapos.api.models.AdminUserResponse
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.isNull
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import org.slf4j.LoggerFactory
import java.security.MessageDigest
import java.security.SecureRandom
import java.time.Instant
import java.util.*

private val logger = LoggerFactory.getLogger(AdminAuthService::class.java)

// ── Exposed table objects ──────────────────────────────────────────────────

object AdminUsers : Table("admin_users") {
    val id           = uuid("id")
    val email        = text("email")
    val name         = text("name")
    val role         = text("role")
    val passwordHash = text("password_hash").nullable()
    val googleSub    = text("google_sub").nullable()
    val mfaSecret    = text("mfa_secret").nullable()
    val mfaEnabled   = bool("mfa_enabled")
    val failedAttempts = integer("failed_attempts")
    val lockedUntil  = long("locked_until").nullable()   // epoch-ms
    val lastLoginAt  = long("last_login_at").nullable()  // epoch-ms
    val lastLoginIp  = text("last_login_ip").nullable()
    val isActive     = bool("is_active")
    val createdAt    = long("created_at")
    override val primaryKey = PrimaryKey(id)
}

object AdminSessions : Table("admin_sessions") {
    val id          = uuid("id")
    val userId      = uuid("user_id")
    val tokenHash   = text("token_hash")
    val userAgent   = text("user_agent").nullable()
    val ipAddress   = text("ip_address").nullable()
    val createdAt   = long("created_at")
    val expiresAt   = long("expires_at")   // epoch-ms
    val revokedAt   = long("revoked_at").nullable()
    override val primaryKey = PrimaryKey(id)
}

object PasswordResetTokens : Table("password_reset_tokens") {
    val id           = uuid("id")
    val adminUserId  = uuid("admin_user_id")
    val tokenHash    = varchar("token_hash", 64)
    val expiresAt    = long("expires_at")   // epoch-ms
    val usedAt       = long("used_at").nullable()  // epoch-ms
    val createdAt    = long("created_at")   // epoch-ms
    override val primaryKey = PrimaryKey(id)
}

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
    private val auditService: AdminAuditService
) {

    private val bcryptVerifier = BCrypt.verifyer()

    // ── Login ────────────────────────────────────────────────────────────────

    suspend fun login(email: String, password: String, ip: String?, userAgent: String?): AdminAuthResult =
        newSuspendedTransaction {
            // G2: Reject oversized passwords before bcrypt (bcrypt silently truncates at 72 bytes)
            if (password.length > MAX_PASSWORD_LENGTH) {
                // Still run a dummy verify for constant-time behaviour
                bcryptVerifier.verify(password.toCharArray(), DUMMY_HASH)
                return@newSuspendedTransaction AdminAuthResult.InvalidCredentials
            }

            val row = AdminUsers.selectAll()
                .where { AdminUsers.email eq email.lowercase().trim() }
                .singleOrNull()
                ?.toAdminUserRow()
                ?: run {
                    // Constant-time dummy verify to prevent timing attacks on unknown emails
                    bcryptVerifier.verify(password.toCharArray(), DUMMY_HASH)
                    return@newSuspendedTransaction AdminAuthResult.InvalidCredentials
                }

            if (!row.isActive) return@newSuspendedTransaction AdminAuthResult.AccountInactive

            val lockedUntilMs = AdminUsers.selectAll()
                .where { AdminUsers.id eq row.id }
                .single()[AdminUsers.lockedUntil]
            if (lockedUntilMs != null && System.currentTimeMillis() < lockedUntilMs) {
                return@newSuspendedTransaction AdminAuthResult.AccountLocked
            }

            if (row.passwordHash == null) {
                return@newSuspendedTransaction AdminAuthResult.InvalidCredentials
            }

            val verified = bcryptVerifier.verify(password.toCharArray(), row.passwordHash.toCharArray())
            if (!verified.verified) {
                incrementFailedAttempts(row.id, row.email, ip, userAgent)
                return@newSuspendedTransaction AdminAuthResult.InvalidCredentials
            }

            // Reset lockout state on successful login
            val now = System.currentTimeMillis()
            AdminUsers.update({ AdminUsers.id eq row.id }) {
                it[failedAttempts] = 0
                it[lockedUntil] = null
                it[lastLoginAt] = now
                it[lastLoginIp] = ip
            }

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
                return@newSuspendedTransaction AdminAuthResult.MfaRequired(row, pendingToken)
            }

            val (accessToken, refreshToken) = issueTokens(row, ip, userAgent)
            AdminAuthResult.Success(row, accessToken, refreshToken)
        }

    // ── Token refresh ────────────────────────────────────────────────────────

    suspend fun refresh(rawRefreshToken: String, ip: String?, userAgent: String?): Pair<String, String>? =
        newSuspendedTransaction {
            val hash = sha256Hex(rawRefreshToken)
            val session = AdminSessions.selectAll()
                .where {
                    (AdminSessions.tokenHash eq hash) and
                    (AdminSessions.revokedAt.isNull()) and
                    (AdminSessions.expiresAt greater System.currentTimeMillis())
                }
                .singleOrNull() ?: return@newSuspendedTransaction null

            // Revoke the old session (single-use rotation)
            AdminSessions.update({ AdminSessions.id eq session[AdminSessions.id] }) {
                it[revokedAt] = System.currentTimeMillis()
            }

            val userId = session[AdminSessions.userId]
            val row = AdminUsers.selectAll()
                .where { (AdminUsers.id eq userId) and (AdminUsers.isActive eq true) }
                .singleOrNull()?.toAdminUserRow() ?: return@newSuspendedTransaction null

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

            issueTokens(row, ip, userAgent)
        }

    // ── Logout ───────────────────────────────────────────────────────────────

    suspend fun logout(rawRefreshToken: String, adminId: UUID? = null, adminName: String? = null, ip: String? = null) =
        newSuspendedTransaction {
            val hash = sha256Hex(rawRefreshToken)
            AdminSessions.update({ AdminSessions.tokenHash eq hash }) {
                it[revokedAt] = System.currentTimeMillis()
            }
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

    suspend fun findById(userId: UUID): AdminUserRow? = newSuspendedTransaction {
        AdminUsers.selectAll()
            .where { (AdminUsers.id eq userId) and (AdminUsers.isActive eq true) }
            .singleOrNull()?.toAdminUserRow()
    }

    suspend fun emailExists(email: String): Boolean = newSuspendedTransaction {
        AdminUsers.selectAll()
            .where { AdminUsers.email eq email.lowercase().trim() }
            .count() > 0
    }

    // ── Bootstrap (first-run only) ───────────────────────────────────────────

    suspend fun needsBootstrap(): Boolean = newSuspendedTransaction {
        AdminUsers.selectAll().count() == 0L
    }

    /**
     * Creates the first ADMIN user. Returns null if an admin already exists
     * (the route converts this to 409 Conflict).
     */
    suspend fun bootstrap(email: String, name: String, password: String): AdminUserRow? =
        newSuspendedTransaction {
            if (AdminUsers.selectAll().count() != 0L) return@newSuspendedTransaction null
            val hash = BCrypt.withDefaults().hashToString(12, password.toCharArray())
            val now = System.currentTimeMillis()
            AdminUsers.insert {
                it[this.email] = email.lowercase().trim()
                it[this.name] = name
                it[this.role] = AdminRole.ADMIN.name
                it[passwordHash] = hash
                it[mfaEnabled] = false
                it[failedAttempts] = 0
                it[isActive] = true
                it[createdAt] = now
            }
            AdminUsers.selectAll()
                .where { AdminUsers.email eq email.lowercase().trim() }
                .single().toAdminUserRow()
        }

    // ── Admin user management ────────────────────────────────────────────────

    suspend fun createUser(email: String, name: String, role: AdminRole, password: String): AdminUserRow =
        newSuspendedTransaction {
            val hash = BCrypt.withDefaults().hashToString(12, password.toCharArray())
            val now = System.currentTimeMillis()
            AdminUsers.insert {
                it[this.email] = email.lowercase().trim()
                it[this.name] = name
                it[this.role] = role.name
                it[passwordHash] = hash
                it[mfaEnabled] = false
                it[failedAttempts] = 0
                it[isActive] = true
                it[createdAt] = now
            }
            AdminUsers.selectAll()
                .where { AdminUsers.email eq email.lowercase().trim() }
                .single().toAdminUserRow()
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
        newSuspendedTransaction {
            AdminUsers.update({ AdminUsers.id eq id }) { stmt ->
                name?.let { stmt[AdminUsers.name] = it }
                role?.let { stmt[AdminUsers.role] = it.name }
                isActive?.let { stmt[AdminUsers.isActive] = it }
            }
            AdminUsers.selectAll().where { AdminUsers.id eq id }.singleOrNull()?.toAdminUserRow()
        }

    suspend fun revokeAllSessions(userId: UUID) = newSuspendedTransaction {
        AdminSessions.update({ AdminSessions.userId eq userId }) {
            it[revokedAt] = System.currentTimeMillis()
        }
    }

    /** Lists active (non-revoked, non-expired) sessions for a user. */
    suspend fun listActiveSessions(userId: UUID): List<AdminSessionRow> = newSuspendedTransaction {
        AdminSessions.selectAll()
            .where {
                (AdminSessions.userId eq userId) and
                (AdminSessions.revokedAt.isNull()) and
                (AdminSessions.expiresAt greater System.currentTimeMillis())
            }
            .orderBy(AdminSessions.createdAt, SortOrder.DESC)
            .map {
                AdminSessionRow(
                    id        = it[AdminSessions.id],
                    userId    = it[AdminSessions.userId],
                    userAgent = it[AdminSessions.userAgent],
                    ipAddress = it[AdminSessions.ipAddress],
                    createdAt = it[AdminSessions.createdAt],
                    expiresAt = it[AdminSessions.expiresAt],
                    revokedAt = it[AdminSessions.revokedAt]
                )
            }
    }

    /** Changes password after verifying the current password. Returns false on wrong current password. */
    suspend fun changePassword(userId: UUID, currentPassword: String, newPassword: String): Boolean =
        newSuspendedTransaction {
            val row = AdminUsers.selectAll().where { AdminUsers.id eq userId }.singleOrNull()
                ?: return@newSuspendedTransaction false

            val currentHash = row[AdminUsers.passwordHash] ?: return@newSuspendedTransaction false
            val verified = bcryptVerifier.verify(currentPassword.toCharArray(), currentHash.toCharArray())
            if (!verified.verified) return@newSuspendedTransaction false

            val newHash = BCrypt.withDefaults().hashToString(12, newPassword.toCharArray())
            AdminUsers.update({ AdminUsers.id eq userId }) {
                it[passwordHash] = newHash
            }

            // Revoke all existing sessions on password change (force re-login)
            AdminSessions.update({ AdminSessions.userId eq userId }) {
                it[revokedAt] = System.currentTimeMillis()
            }

            true
        }

    // ── Token issuance for external callers (Google OAuth) ───────────────────

    /** Issues access + refresh tokens for a pre-authenticated user (e.g., after Google OAuth). */
    suspend fun issueTokensForUser(user: AdminUserRow, ip: String?, userAgent: String?): Pair<String, String> =
        newSuspendedTransaction { issueTokens(user, ip, userAgent) }

    // ── MFA completion ───────────────────────────────────────────────────────

    /** Called after MFA code is verified — issues full tokens from a pending token. */
    suspend fun completeMfaLogin(pendingToken: String, ip: String?, userAgent: String?): Pair<AdminUserRow, Pair<String, String>>? {
        val userId = verifyMfaPendingToken(pendingToken) ?: return null
        val row = newSuspendedTransaction {
            AdminUsers.selectAll()
                .where { (AdminUsers.id eq userId) and (AdminUsers.isActive eq true) }
                .singleOrNull()?.toAdminUserRow()
        } ?: return null
        val tokens = newSuspendedTransaction { issueTokens(row, ip, userAgent) }
        return row to tokens
    }

    fun issueMfaPendingToken(user: AdminUserRow): String {
        val algorithm = Algorithm.HMAC256(config.adminJwtSecret)
        val now = System.currentTimeMillis()
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
            val algorithm = Algorithm.HMAC256(config.adminJwtSecret)
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
            val algorithm = Algorithm.HMAC256(config.adminJwtSecret)
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

    private fun issueTokens(user: AdminUserRow, ip: String?, userAgent: String?): Pair<String, String> {
        val algorithm = Algorithm.HMAC256(config.adminJwtSecret)
        val now = System.currentTimeMillis()

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

        AdminSessions.insert {
            it[userId] = user.id
            it[tokenHash] = refreshHash
            it[this.userAgent] = userAgent
            it[ipAddress] = ip
            it[createdAt] = now
            it[expiresAt] = refreshExpiresAt
        }

        return accessToken to rawRefreshToken
    }

    private suspend fun incrementFailedAttempts(userId: UUID, email: String, ip: String?, userAgent: String?) {
        newSuspendedTransaction {
            val current = AdminUsers.selectAll()
                .where { AdminUsers.id eq userId }
                .single()[AdminUsers.failedAttempts]
            val newCount = current + 1
            val lockedUntil = if (newCount >= MAX_FAILED_ATTEMPTS) {
                System.currentTimeMillis() + LOCKOUT_DURATION_MS
            } else null

            AdminUsers.update({ AdminUsers.id eq userId }) {
                it[failedAttempts] = newCount
                it[this.lockedUntil] = lockedUntil
            }

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
    }

    // ── Password Reset ────────────────────────────────────────────────────────

    /**
     * Generates a one-time password reset token for the given email.
     * Returns the raw token (to embed in email link) or null if the user doesn't exist.
     * The token hash is stored in the DB; the raw token never touches the DB.
     */
    suspend fun generatePasswordResetToken(email: String): String? = newSuspendedTransaction {
        val user = AdminUsers.selectAll()
            .where { AdminUsers.email eq email.lowercase() }
            .firstOrNull() ?: return@newSuspendedTransaction null

        // Invalidate any existing unused tokens for this user
        PasswordResetTokens.deleteWhere {
            (PasswordResetTokens.adminUserId eq user[AdminUsers.id]) and (PasswordResetTokens.usedAt.isNull())
        }

        val rawToken  = generateSecureToken()
        val tokenHash = sha256Hex(rawToken)
        val now       = System.currentTimeMillis()

        PasswordResetTokens.insert {
            it[id]          = UUID.randomUUID()
            it[adminUserId] = user[AdminUsers.id]
            it[PasswordResetTokens.tokenHash] = tokenHash
            it[expiresAt]   = now + RESET_TOKEN_TTL_MS
            it[createdAt]   = now
        }
        rawToken
    }

    /**
     * Validates the reset token, updates the password, and marks the token as used.
     * Returns true on success, false if token is invalid, expired, or already used.
     */
    suspend fun resetPassword(rawToken: String, newPassword: String): Boolean = newSuspendedTransaction {
        val tokenHash = sha256Hex(rawToken)
        val now       = System.currentTimeMillis()

        val tokenRow = PasswordResetTokens.selectAll()
            .where { PasswordResetTokens.tokenHash eq tokenHash }
            .firstOrNull() ?: return@newSuspendedTransaction false

        if (tokenRow[PasswordResetTokens.usedAt] != null) return@newSuspendedTransaction false
        if (tokenRow[PasswordResetTokens.expiresAt] < now) return@newSuspendedTransaction false

        val userId      = tokenRow[PasswordResetTokens.adminUserId]
        val newHash     = BCrypt.withDefaults().hashToString(12, newPassword.toCharArray())

        AdminUsers.update({ AdminUsers.id eq userId }) {
            it[passwordHash] = newHash
        }
        // Revoke all active sessions for this user
        AdminSessions.update({
            (AdminSessions.userId eq userId) and AdminSessions.revokedAt.isNull()
        }) {
            it[revokedAt] = now
        }
        // Mark token as used
        PasswordResetTokens.update({ PasswordResetTokens.tokenHash eq tokenHash }) {
            it[usedAt] = now
        }
        true
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
