package com.zyntasolutions.zyntapos.domain.model

import kotlinx.datetime.Instant

/**
 * Represents a user's access grant to a specific store.
 *
 * In a multi-store deployment, a single [User] can be granted access to
 * multiple stores with potentially different roles at each. The [roleAtStore]
 * field overrides the user's default [User.role] when operating at that store.
 *
 * A user's primary store remains [User.storeId] (backward-compatible).
 * Additional store access is tracked via this junction model.
 *
 * @property id          Unique identifier (UUID v4).
 * @property userId      FK → [User.id].
 * @property storeId     FK → store to which access is granted.
 * @property roleAtStore Optional per-store role override. If null, the user's
 *                       default [User.role] applies at this store.
 * @property isActive    Whether this access grant is currently active.
 * @property grantedBy   User ID of the admin who granted this access.
 * @property createdAt   UTC timestamp when access was granted.
 * @property updatedAt   UTC timestamp of last modification.
 */
data class UserStoreAccess(
    val id: String,
    val userId: String,
    val storeId: String,
    val roleAtStore: Role? = null,
    val isActive: Boolean = true,
    val grantedBy: String? = null,
    val createdAt: Instant,
    val updatedAt: Instant,
)
