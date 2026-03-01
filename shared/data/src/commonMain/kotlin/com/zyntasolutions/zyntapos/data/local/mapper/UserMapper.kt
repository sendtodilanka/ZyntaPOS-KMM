package com.zyntasolutions.zyntapos.data.local.mapper

import com.zyntasolutions.zyntapos.db.Users
import com.zyntasolutions.zyntapos.domain.model.Role
import com.zyntasolutions.zyntapos.domain.model.User
import kotlinx.datetime.Instant

/**
 * Maps between the SQLDelight-generated [Users] entity and the domain [User] model.
 */
object UserMapper {

    fun toDomain(row: Users): User = User(
        id            = row.id,
        name          = row.name,
        email         = row.email,
        role          = Role.valueOf(row.role),
        storeId       = row.store_id,
        isActive      = row.is_active == 1L,
        pinHash       = row.pin_hash,
        customRoleId  = row.custom_role_id,
        isSystemAdmin = row.is_system_admin == 1L,
        createdAt     = Instant.fromEpochMilliseconds(row.created_at),
        updatedAt     = Instant.fromEpochMilliseconds(row.updated_at),
    )

    fun toInsertParams(u: User, passwordHash: String, syncStatus: String = "PENDING") = InsertParams(
        id            = u.id,
        name          = u.name,
        email         = u.email,
        passwordHash  = passwordHash,
        role          = u.role.name,
        pinHash       = u.pinHash,
        storeId       = u.storeId,
        isActive      = if (u.isActive) 1L else 0L,
        customRoleId  = u.customRoleId,
        isSystemAdmin = if (u.isSystemAdmin) 1L else 0L,
        createdAt     = u.createdAt.toEpochMilliseconds(),
        updatedAt     = u.updatedAt.toEpochMilliseconds(),
        syncStatus    = syncStatus,
    )

    data class InsertParams(
        val id: String,
        val name: String,
        val email: String,
        val passwordHash: String,
        val role: String,
        val pinHash: String?,
        val storeId: String,
        val isActive: Long,
        val customRoleId: String?,
        val isSystemAdmin: Long,
        val createdAt: Long,
        val updatedAt: Long,
        val syncStatus: String,
    )
}
