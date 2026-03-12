package com.zyntasolutions.zyntapos.api.repository

import com.zyntasolutions.zyntapos.api.db.PosSessions
import com.zyntasolutions.zyntapos.api.db.Stores
import com.zyntasolutions.zyntapos.api.db.Users
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.isNull
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import java.time.OffsetDateTime
import java.util.UUID

/**
 * Exposed-backed implementation of [PosUserRepository] (S3-15).
 */
class PosUserRepositoryImpl : PosUserRepository {

    // ── Store lookup ────────────────────────────────────────────────────────

    override suspend fun findStoreByLicenseKey(licenseKey: String): String? = newSuspendedTransaction {
        Stores.selectAll()
            .where { (Stores.licenseKey eq licenseKey) and (Stores.isActive eq true) }
            .singleOrNull()?.get(Stores.id)
    }

    // ── User queries ────────────────────────────────────────────────────────

    override suspend fun findActiveUsersByStore(storeId: String?): List<PosUserRow> = newSuspendedTransaction {
        val query = if (storeId != null) {
            Users.selectAll().where { (Users.isActive eq true) and (Users.storeId eq storeId) }
        } else {
            Users.selectAll().where { Users.isActive eq true }
        }
        query.map { it.toPosUserRow() }
    }

    override suspend fun findActiveUserById(userId: String): PosUserRow? = newSuspendedTransaction {
        Users.selectAll()
            .where { (Users.id eq userId) and (Users.isActive eq true) }
            .singleOrNull()?.toPosUserRow()
    }

    // ── User mutations ──────────────────────────────────────────────────────

    override suspend fun updatePasswordHash(userId: String, hash: String) {
        newSuspendedTransaction {
            Users.update({ Users.id eq userId }) {
                it[passwordHash] = hash
            }
        }
    }

    override suspend fun updateFailedAttempts(userId: String, count: Int, lockedUntil: OffsetDateTime?) {
        newSuspendedTransaction {
            Users.update({ Users.id eq userId }) {
                it[failedAttempts] = count
                it[Users.lockedUntil] = lockedUntil
            }
        }
    }

    override suspend fun resetFailedAttempts(userId: String) {
        newSuspendedTransaction {
            Users.update({ Users.id eq userId }) {
                it[failedAttempts] = 0
                it[Users.lockedUntil] = null
            }
        }
    }

    // ── POS session management ──────────────────────────────────────────────

    override suspend fun insertPosSession(
        userId: String,
        storeId: String,
        tokenHash: String,
        deviceId: String?,
        userAgent: String?,
        ip: String?,
        expiresAt: OffsetDateTime,
    ) {
        newSuspendedTransaction {
            PosSessions.insert {
                it[PosSessions.userId] = userId
                it[PosSessions.storeId] = storeId
                it[PosSessions.tokenHash] = tokenHash
                it[PosSessions.deviceId] = deviceId
                it[PosSessions.userAgent] = userAgent
                it[ipAddress] = ip
                it[PosSessions.expiresAt] = expiresAt
            }
        }
    }

    override suspend fun findPosSessionByTokenHash(tokenHash: String, now: OffsetDateTime): PosSessionRow? =
        newSuspendedTransaction {
            PosSessions.selectAll()
                .where {
                    (PosSessions.tokenHash eq tokenHash) and
                    (PosSessions.revokedAt.isNull()) and
                    (PosSessions.expiresAt greater now)
                }
                .singleOrNull()?.let {
                    PosSessionRow(
                        id = it[PosSessions.id],
                        userId = it[PosSessions.userId],
                        storeId = it[PosSessions.storeId],
                        tokenHash = it[PosSessions.tokenHash],
                        deviceId = it[PosSessions.deviceId],
                        userAgent = it[PosSessions.userAgent],
                        ipAddress = it[PosSessions.ipAddress],
                    )
                }
        }

    override suspend fun revokePosSession(sessionId: UUID, revokedAt: OffsetDateTime) {
        newSuspendedTransaction {
            PosSessions.update({ PosSessions.id eq sessionId }) {
                it[PosSessions.revokedAt] = revokedAt
            }
        }
    }

    override suspend fun revokeAllPosSessions(userId: String, revokedAt: OffsetDateTime) {
        newSuspendedTransaction {
            PosSessions.update({ (PosSessions.userId eq userId) and PosSessions.revokedAt.isNull() }) {
                it[PosSessions.revokedAt] = revokedAt
            }
        }
    }

    // ── Mapper ──────────────────────────────────────────────────────────────

    private fun ResultRow.toPosUserRow() = PosUserRow(
        id = this[Users.id],
        storeId = this[Users.storeId],
        username = this[Users.username],
        email = this[Users.email],
        name = this[Users.name],
        passwordHash = this[Users.passwordHash],
        role = this[Users.role],
        isActive = this[Users.isActive],
        failedAttempts = this[Users.failedAttempts],
        lockedUntil = this[Users.lockedUntil],
        createdAt = this[Users.createdAt],
        updatedAt = this[Users.updatedAt],
    )
}
