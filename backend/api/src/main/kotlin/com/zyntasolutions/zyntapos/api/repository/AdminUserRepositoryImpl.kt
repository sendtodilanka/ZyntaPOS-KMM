package com.zyntasolutions.zyntapos.api.repository

import com.zyntasolutions.zyntapos.api.auth.AdminRole
import com.zyntasolutions.zyntapos.api.db.AdminSessions
import com.zyntasolutions.zyntapos.api.db.AdminUsers
import com.zyntasolutions.zyntapos.api.db.PasswordResetTokens
import com.zyntasolutions.zyntapos.api.service.AdminSessionRow
import com.zyntasolutions.zyntapos.api.service.AdminUserRow
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.isNull
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import java.util.UUID

/**
 * Exposed-backed implementation of [AdminUserRepository] (S3-15).
 */
class AdminUserRepositoryImpl : AdminUserRepository {

    // ── Queries ─────────────────────────────────────────────────────────────

    override suspend fun findByEmail(email: String): AdminUserRow? = newSuspendedTransaction {
        AdminUsers.selectAll()
            .where { AdminUsers.email eq email.lowercase().trim() }
            .singleOrNull()?.toAdminUserRow()
    }

    override suspend fun findById(id: UUID): AdminUserRow? = newSuspendedTransaction {
        AdminUsers.selectAll()
            .where { (AdminUsers.id eq id) and (AdminUsers.isActive eq true) }
            .singleOrNull()?.toAdminUserRow()
    }

    override suspend fun count(): Long = newSuspendedTransaction {
        AdminUsers.selectAll().count()
    }

    override suspend fun getLockedUntil(id: UUID): Long? = newSuspendedTransaction {
        AdminUsers.selectAll()
            .where { AdminUsers.id eq id }
            .single()[AdminUsers.lockedUntil]
    }

    override suspend fun getFailedAttempts(id: UUID): Int = newSuspendedTransaction {
        AdminUsers.selectAll()
            .where { AdminUsers.id eq id }
            .single()[AdminUsers.failedAttempts]
    }

    override suspend fun getLastLoginIp(id: UUID): String? = newSuspendedTransaction {
        AdminUsers.selectAll()
            .where { AdminUsers.id eq id }
            .singleOrNull()?.get(AdminUsers.lastLoginIp)
    }

    override suspend fun getPasswordChangedAt(id: UUID): Long? = newSuspendedTransaction {
        AdminUsers.selectAll()
            .where { AdminUsers.id eq id }
            .singleOrNull()?.get(AdminUsers.passwordChangedAt)
    }

    // ── Mutations ───────────────────────────────────────────────────────────

    override suspend fun createUser(email: String, name: String, role: AdminRole, passwordHash: String): AdminUserRow =
        newSuspendedTransaction {
            val now = java.time.Instant.now().toEpochMilli()
            AdminUsers.insert {
                it[AdminUsers.email] = email.lowercase().trim()
                it[AdminUsers.name] = name
                it[AdminUsers.role] = role.name
                it[AdminUsers.passwordHash] = passwordHash
                it[mfaEnabled] = false
                it[failedAttempts] = 0
                it[isActive] = true
                it[createdAt] = now
            }
            AdminUsers.selectAll()
                .where { AdminUsers.email eq email.lowercase().trim() }
                .single().toAdminUserRow()
        }

    override suspend fun updateLoginSuccess(id: UUID, timestampMs: Long, ip: String?) {
        newSuspendedTransaction {
            AdminUsers.update({ AdminUsers.id eq id }) {
                it[failedAttempts] = 0
                it[lockedUntil] = null
                it[lastLoginAt] = timestampMs
                it[lastLoginIp] = ip
            }
        }
    }

    override suspend fun updateFailedAttempts(id: UUID, count: Int, lockedUntilMs: Long?) {
        newSuspendedTransaction {
            AdminUsers.update({ AdminUsers.id eq id }) {
                it[failedAttempts] = count
                it[lockedUntil] = lockedUntilMs
            }
        }
    }

    override suspend fun updatePassword(id: UUID, passwordHash: String) {
        newSuspendedTransaction {
            AdminUsers.update({ AdminUsers.id eq id }) {
                it[AdminUsers.passwordHash] = passwordHash
                it[AdminUsers.passwordChangedAt] = java.time.Instant.now().toEpochMilli()
            }
        }
    }

    override suspend fun updateUser(id: UUID, name: String?, role: AdminRole?, isActive: Boolean?): AdminUserRow? =
        newSuspendedTransaction {
            AdminUsers.update({ AdminUsers.id eq id }) { stmt ->
                name?.let { stmt[AdminUsers.name] = it }
                role?.let { stmt[AdminUsers.role] = it.name }
                isActive?.let { stmt[AdminUsers.isActive] = it }
            }
            AdminUsers.selectAll().where { AdminUsers.id eq id }.singleOrNull()?.toAdminUserRow()
        }

    // ── Session management ──────────────────────────────────────────────────

    override suspend fun insertSession(userId: UUID, tokenHash: String, ip: String?, userAgent: String?, createdAt: Long, expiresAt: Long) {
        newSuspendedTransaction {
            AdminSessions.insert {
                it[AdminSessions.userId] = userId
                it[AdminSessions.tokenHash] = tokenHash
                it[AdminSessions.userAgent] = userAgent
                it[ipAddress] = ip
                it[AdminSessions.createdAt] = createdAt
                it[AdminSessions.expiresAt] = expiresAt
            }
        }
    }

    override suspend fun findSessionByTokenHash(tokenHash: String, nowMs: Long): SessionRow? = newSuspendedTransaction {
        AdminSessions.selectAll()
            .where {
                (AdminSessions.tokenHash eq tokenHash) and
                (AdminSessions.revokedAt.isNull()) and
                (AdminSessions.expiresAt greater nowMs)
            }
            .singleOrNull()?.let {
                SessionRow(
                    id = it[AdminSessions.id],
                    userId = it[AdminSessions.userId],
                    userAgent = it[AdminSessions.userAgent],
                    ipAddress = it[AdminSessions.ipAddress],
                    createdAt = it[AdminSessions.createdAt],
                    expiresAt = it[AdminSessions.expiresAt],
                    revokedAt = it[AdminSessions.revokedAt],
                )
            }
    }

    override suspend fun revokeSession(sessionId: UUID, revokedAtMs: Long) {
        newSuspendedTransaction {
            AdminSessions.update({ AdminSessions.id eq sessionId }) {
                it[revokedAt] = revokedAtMs
            }
        }
    }

    override suspend fun revokeAllSessions(userId: UUID, revokedAtMs: Long) {
        newSuspendedTransaction {
            AdminSessions.update({ AdminSessions.userId eq userId }) {
                it[revokedAt] = revokedAtMs
            }
        }
    }

    override suspend fun revokeSessionByTokenHash(tokenHash: String, revokedAtMs: Long) {
        newSuspendedTransaction {
            AdminSessions.update({ AdminSessions.tokenHash eq tokenHash }) {
                it[revokedAt] = revokedAtMs
            }
        }
    }

    override suspend fun listActiveSessions(userId: UUID, nowMs: Long): List<AdminSessionRow> = newSuspendedTransaction {
        AdminSessions.selectAll()
            .where {
                (AdminSessions.userId eq userId) and
                (AdminSessions.revokedAt.isNull()) and
                (AdminSessions.expiresAt greater nowMs)
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

    override suspend fun findByIdWithPassword(id: UUID): AdminUserRow? = newSuspendedTransaction {
        AdminUsers.selectAll()
            .where { AdminUsers.id eq id }
            .singleOrNull()?.toAdminUserRow()
    }

    // ── Password reset ──────────────────────────────────────────────────────

    override suspend fun deleteUnusedResetTokens(userId: UUID) {
        newSuspendedTransaction {
            PasswordResetTokens.deleteWhere {
                (PasswordResetTokens.adminUserId eq userId) and (PasswordResetTokens.usedAt.isNull())
            }
        }
    }

    override suspend fun insertResetToken(id: UUID, userId: UUID, tokenHash: String, expiresAt: Long, createdAt: Long) {
        newSuspendedTransaction {
            PasswordResetTokens.insert {
                it[PasswordResetTokens.id] = id
                it[adminUserId] = userId
                it[PasswordResetTokens.tokenHash] = tokenHash
                it[PasswordResetTokens.expiresAt] = expiresAt
                it[PasswordResetTokens.createdAt] = createdAt
            }
        }
    }

    override suspend fun findResetToken(tokenHash: String): ResetTokenRow? = newSuspendedTransaction {
        PasswordResetTokens.selectAll()
            .where { PasswordResetTokens.tokenHash eq tokenHash }
            .firstOrNull()?.let {
                ResetTokenRow(
                    id = it[PasswordResetTokens.id],
                    adminUserId = it[PasswordResetTokens.adminUserId],
                    tokenHash = it[PasswordResetTokens.tokenHash],
                    expiresAt = it[PasswordResetTokens.expiresAt],
                    usedAt = it[PasswordResetTokens.usedAt],
                    createdAt = it[PasswordResetTokens.createdAt],
                )
            }
    }

    override suspend fun markResetTokenUsed(tokenHash: String, usedAt: Long) {
        newSuspendedTransaction {
            PasswordResetTokens.update({ PasswordResetTokens.tokenHash eq tokenHash }) {
                it[PasswordResetTokens.usedAt] = usedAt
            }
        }
    }

    // ── Mapper ──────────────────────────────────────────────────────────────

    private fun ResultRow.toAdminUserRow() = AdminUserRow(
        id = this[AdminUsers.id],
        email = this[AdminUsers.email],
        name = this[AdminUsers.name],
        role = AdminRole.fromString(this[AdminUsers.role]) ?: AdminRole.AUDITOR,
        passwordHash = this[AdminUsers.passwordHash],
        mfaEnabled = this[AdminUsers.mfaEnabled],
        isActive = this[AdminUsers.isActive],
        lastLoginAt = this[AdminUsers.lastLoginAt],
        createdAt = this[AdminUsers.createdAt],
    )
}
