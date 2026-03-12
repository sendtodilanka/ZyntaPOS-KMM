package com.zyntasolutions.zyntapos.api.repository

import com.zyntasolutions.zyntapos.api.auth.AdminRole
import com.zyntasolutions.zyntapos.api.service.AdminUserRow
import java.util.UUID

/**
 * Repository interface for admin_users and admin_sessions table access (S3-15).
 *
 * Extracts direct Exposed DB operations from AdminAuthService into a testable
 * boundary. Service classes inject this interface; tests mock it.
 */
interface AdminUserRepository {

    // ── Queries ─────────────────────────────────────────────────────────────

    suspend fun findByEmail(email: String): AdminUserRow?

    suspend fun findById(id: UUID): AdminUserRow?

    suspend fun count(): Long

    suspend fun getLockedUntil(id: UUID): Long?

    suspend fun getFailedAttempts(id: UUID): Int

    // ── Mutations ───────────────────────────────────────────────────────────

    suspend fun createUser(email: String, name: String, role: AdminRole, passwordHash: String): AdminUserRow

    suspend fun updateLoginSuccess(id: UUID, timestampMs: Long, ip: String?)

    suspend fun updateFailedAttempts(id: UUID, count: Int, lockedUntilMs: Long?)

    suspend fun updatePassword(id: UUID, passwordHash: String)

    suspend fun updateUser(id: UUID, name: String?, role: AdminRole?, isActive: Boolean?): AdminUserRow?

    // ── Session management ──────────────────────────────────────────────────

    suspend fun insertSession(userId: UUID, tokenHash: String, ip: String?, userAgent: String?, createdAt: Long, expiresAt: Long)

    suspend fun findSessionByTokenHash(tokenHash: String, nowMs: Long): SessionRow?

    suspend fun revokeSession(sessionId: UUID, revokedAtMs: Long)

    suspend fun revokeAllSessions(userId: UUID, revokedAtMs: Long)

    // ── Password reset ──────────────────────────────────────────────────────

    suspend fun deleteUnusedResetTokens(userId: UUID)

    suspend fun insertResetToken(id: UUID, userId: UUID, tokenHash: String, expiresAt: Long, createdAt: Long)

    suspend fun findResetToken(tokenHash: String): ResetTokenRow?

    suspend fun markResetTokenUsed(tokenHash: String, usedAt: Long)
}

data class SessionRow(
    val id: UUID,
    val userId: UUID,
    val userAgent: String?,
    val ipAddress: String?,
    val createdAt: Long,
    val expiresAt: Long,
    val revokedAt: Long?,
)

data class ResetTokenRow(
    val id: UUID,
    val adminUserId: UUID,
    val tokenHash: String,
    val expiresAt: Long,
    val usedAt: Long?,
    val createdAt: Long,
)
