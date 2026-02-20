package com.zyntasolutions.zyntapos.domain.model

import kotlinx.datetime.Instant

/**
 * Represents an authenticated operator within the ZyntaPOS system.
 *
 * @property id Unique identifier (UUID v4).
 * @property name Full display name.
 * @property email Login credential — must be unique across the system.
 * @property role The [Role] that determines this user's permission set.
 * @property storeId Foreign key to the store this user is assigned to.
 * @property isActive Whether the account is enabled. Inactive users cannot log in.
 * @property pinHash SHA-256 + salt hash of the user's 4–6 digit quick-switch PIN.
 *                   Stored as "salt:hash". Null if PIN has not been configured.
 * @property createdAt UTC timestamp of account creation.
 * @property updatedAt UTC timestamp of the last account modification.
 */
data class User(
    val id: String,
    val name: String,
    val email: String,
    val role: Role,
    val storeId: String,
    val isActive: Boolean = true,
    val pinHash: String? = null,
    val createdAt: Instant,
    val updatedAt: Instant,
)
