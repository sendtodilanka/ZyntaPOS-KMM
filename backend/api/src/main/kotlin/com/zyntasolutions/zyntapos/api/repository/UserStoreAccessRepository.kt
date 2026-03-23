package com.zyntasolutions.zyntapos.api.repository

import com.zyntasolutions.zyntapos.api.db.UserStoreAccessTable
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID

/**
 * Backend repository for user-store access grants (C3.2).
 *
 * Manages the `user_store_access` junction table that tracks
 * which stores a user can access beyond their primary store.
 */
class UserStoreAccessRepository {

    data class UserStoreAccessRow(
        val id: UUID,
        val userId: String,
        val storeId: String,
        val roleAtStore: String?,
        val isActive: Boolean,
        val grantedBy: String?,
        val createdAt: OffsetDateTime,
        val updatedAt: OffsetDateTime,
    )

    suspend fun getAccessibleStores(userId: String): List<UserStoreAccessRow> =
        newSuspendedTransaction {
            UserStoreAccessTable.selectAll()
                .where { (UserStoreAccessTable.userId eq userId) and (UserStoreAccessTable.isActive eq true) }
                .map { it.toRow() }
        }

    suspend fun getUsersForStore(storeId: String): List<UserStoreAccessRow> =
        newSuspendedTransaction {
            UserStoreAccessTable.selectAll()
                .where { (UserStoreAccessTable.storeId eq storeId) and (UserStoreAccessTable.isActive eq true) }
                .map { it.toRow() }
        }

    suspend fun getByUserAndStore(userId: String, storeId: String): UserStoreAccessRow? =
        newSuspendedTransaction {
            UserStoreAccessTable.selectAll()
                .where { (UserStoreAccessTable.userId eq userId) and (UserStoreAccessTable.storeId eq storeId) }
                .firstOrNull()?.toRow()
        }

    suspend fun hasAccess(userId: String, storeId: String): Boolean =
        newSuspendedTransaction {
            UserStoreAccessTable.selectAll()
                .where {
                    (UserStoreAccessTable.userId eq userId) and
                    (UserStoreAccessTable.storeId eq storeId) and
                    (UserStoreAccessTable.isActive eq true)
                }
                .count() > 0
        }

    suspend fun grantAccess(
        userId: String,
        storeId: String,
        roleAtStore: String? = null,
        grantedBy: String? = null,
    ): UserStoreAccessRow = newSuspendedTransaction {
        val now = OffsetDateTime.now(ZoneOffset.UTC)

        // Check if entry exists — reactivate if so
        val existing = UserStoreAccessTable.selectAll()
            .where { (UserStoreAccessTable.userId eq userId) and (UserStoreAccessTable.storeId eq storeId) }
            .firstOrNull()

        if (existing != null) {
            UserStoreAccessTable.update({
                (UserStoreAccessTable.userId eq userId) and (UserStoreAccessTable.storeId eq storeId)
            }) {
                it[UserStoreAccessTable.roleAtStore] = roleAtStore
                it[isActive] = true
                it[UserStoreAccessTable.grantedBy] = grantedBy
                it[updatedAt] = now
            }
            UserStoreAccessTable.selectAll()
                .where { UserStoreAccessTable.id eq existing[UserStoreAccessTable.id] }
                .first().toRow()
        } else {
            val id = UUID.randomUUID()
            UserStoreAccessTable.insert {
                it[UserStoreAccessTable.id] = id
                it[UserStoreAccessTable.userId] = userId
                it[UserStoreAccessTable.storeId] = storeId
                it[UserStoreAccessTable.roleAtStore] = roleAtStore
                it[isActive] = true
                it[UserStoreAccessTable.grantedBy] = grantedBy
                it[createdAt] = now
                it[updatedAt] = now
            }
            UserStoreAccessRow(id, userId, storeId, roleAtStore, true, grantedBy, now, now)
        }
    }

    suspend fun revokeAccess(userId: String, storeId: String): Boolean =
        newSuspendedTransaction {
            val updated = UserStoreAccessTable.update({
                (UserStoreAccessTable.userId eq userId) and (UserStoreAccessTable.storeId eq storeId)
            }) {
                it[isActive] = false
                it[updatedAt] = OffsetDateTime.now(ZoneOffset.UTC)
            }
            updated > 0
        }

    suspend fun upsertFromSync(
        id: String,
        userId: String,
        storeId: String,
        roleAtStore: String?,
        isActive: Boolean,
        grantedBy: String?,
        updatedAt: OffsetDateTime,
    ) = newSuspendedTransaction {
        val existing = UserStoreAccessTable.selectAll()
            .where { UserStoreAccessTable.id eq UUID.fromString(id) }
            .firstOrNull()

        if (existing != null) {
            UserStoreAccessTable.update({
                UserStoreAccessTable.id eq UUID.fromString(id)
            }) {
                it[UserStoreAccessTable.roleAtStore] = roleAtStore
                it[UserStoreAccessTable.isActive] = isActive
                it[UserStoreAccessTable.grantedBy] = grantedBy
                it[UserStoreAccessTable.updatedAt] = updatedAt
            }
        } else {
            UserStoreAccessTable.insert {
                it[UserStoreAccessTable.id] = UUID.fromString(id)
                it[UserStoreAccessTable.userId] = userId
                it[UserStoreAccessTable.storeId] = storeId
                it[UserStoreAccessTable.roleAtStore] = roleAtStore
                it[UserStoreAccessTable.isActive] = isActive
                it[UserStoreAccessTable.grantedBy] = grantedBy
                it[createdAt] = updatedAt
                it[UserStoreAccessTable.updatedAt] = updatedAt
            }
        }
    }

    private fun ResultRow.toRow() = UserStoreAccessRow(
        id = this[UserStoreAccessTable.id],
        userId = this[UserStoreAccessTable.userId],
        storeId = this[UserStoreAccessTable.storeId],
        roleAtStore = this[UserStoreAccessTable.roleAtStore],
        isActive = this[UserStoreAccessTable.isActive],
        grantedBy = this[UserStoreAccessTable.grantedBy],
        createdAt = this[UserStoreAccessTable.createdAt],
        updatedAt = this[UserStoreAccessTable.updatedAt],
    )
}
