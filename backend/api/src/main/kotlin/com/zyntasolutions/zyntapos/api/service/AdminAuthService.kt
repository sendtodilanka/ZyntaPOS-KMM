package com.zyntasolutions.zyntapos.api.service

import at.favre.lib.crypto.bcrypt.BCrypt
import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.zyntasolutions.zyntapos.api.auth.AdminRole
import com.zyntasolutions.zyntapos.api.config.AppConfig
import com.zyntasolutions.zyntapos.api.models.AdminUserResponse
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import org.slf4j.LoggerFactory
import java.security.MessageDigest
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

sealed class AdminAuthResult {
    data class Success(val user: AdminUserRow, val accessToken: String, val refreshToken: String) : AdminAuthResult()
    data object InvalidCredentials : AdminAuthResult()
    data object AccountLocked : AdminAuthResult()
    data object AccountInactive : AdminAuthResult()
}

// ── Service ─────────────────────────────────────────────────────────────────

class AdminAuthService(private val config: AppConfig) {

    private val bcryptVerifier = BCrypt.verifyer()

    // ── Login ────────────────────────────────────────────────────────────────

    suspend fun login(email: String, password: String, ip: String?, userAgent: String?): AdminAuthResult =
        newSuspendedTransaction {
            val row = AdminUsers.selectAll()
                .where { AdminUsers.email eq email.lowercase().trim() }
                .singleOrNull()
                ?.toAdminUserRow()
                ?: run {
                    // Constant-time dummy verify to prevent timing attacks
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
                incrementFailedAttempts(row.id)
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

            issueTokens(row, ip, userAgent)
        }

    // ── Logout ───────────────────────────────────────────────────────────────

    suspend fun logout(rawRefreshToken: String) = newSuspendedTransaction {
        val hash = sha256Hex(rawRefreshToken)
        AdminSessions.update({ AdminSessions.tokenHash eq hash }) {
            it[revokedAt] = System.currentTimeMillis()
        }
    }

    // ── Get user by JWT subject ───────────────────────────────────────────────

    suspend fun findById(userId: UUID): AdminUserRow? = newSuspendedTransaction {
        AdminUsers.selectAll()
            .where { (AdminUsers.id eq userId) and (AdminUsers.isActive eq true) }
            .singleOrNull()?.toAdminUserRow()
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

    suspend fun listUsers(): List<AdminUserRow> = newSuspendedTransaction {
        AdminUsers.selectAll().orderBy(AdminUsers.createdAt).map { it.toAdminUserRow() }
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

    private fun incrementFailedAttempts(userId: UUID) {
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

        if (lockedUntil != null) {
            logger.warn("Admin account locked after $newCount failed attempts: userId=$userId")
        }
    }

    private fun sha256Hex(input: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        return digest.digest(input.toByteArray()).joinToString("") { "%02x".format(it) }
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
