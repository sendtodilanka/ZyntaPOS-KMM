package com.zyntasolutions.zyntapos.api.repository

import java.time.OffsetDateTime
import java.util.UUID

/**
 * Repository interface for POS user (users/stores/pos_sessions) table access (S3-15).
 *
 * Extracts direct Exposed DB operations from UserService into a testable boundary.
 */
interface PosUserRepository {

    // ── Store lookup ────────────────────────────────────────────────────────

    suspend fun findStoreByLicenseKey(licenseKey: String): String?

    // ── User queries ────────────────────────────────────────────────────────

    suspend fun findActiveUsersByStore(storeId: String?): List<PosUserRow>

    suspend fun findActiveUserById(userId: String): PosUserRow?

    // ── User mutations ──────────────────────────────────────────────────────

    suspend fun updatePasswordHash(userId: String, hash: String)

    suspend fun updateFailedAttempts(userId: String, count: Int, lockedUntil: OffsetDateTime?)

    suspend fun resetFailedAttempts(userId: String)

    // ── POS session management ──────────────────────────────────────────────

    suspend fun insertPosSession(
        userId: String,
        storeId: String,
        tokenHash: String,
        deviceId: String?,
        userAgent: String?,
        ip: String?,
        expiresAt: OffsetDateTime,
    )

    suspend fun findPosSessionByTokenHash(tokenHash: String, now: OffsetDateTime): PosSessionRow?

    suspend fun revokePosSession(sessionId: UUID, revokedAt: OffsetDateTime)

    suspend fun revokeAllPosSessions(userId: String, revokedAt: OffsetDateTime)
}

data class PosUserRow(
    val id: String,
    val storeId: String,
    val username: String,
    val email: String?,
    val name: String?,
    val passwordHash: String,
    val role: String,
    val isActive: Boolean,
    val failedAttempts: Int,
    val lockedUntil: OffsetDateTime?,
    val createdAt: OffsetDateTime,
    val updatedAt: OffsetDateTime,
)

data class PosSessionRow(
    val id: UUID,
    val userId: String,
    val storeId: String,
    val tokenHash: String,
    val deviceId: String?,
    val userAgent: String?,
    val ipAddress: String?,
)
