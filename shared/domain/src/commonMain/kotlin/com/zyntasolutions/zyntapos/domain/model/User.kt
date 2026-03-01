package com.zyntasolutions.zyntapos.domain.model

import kotlinx.datetime.Instant

/**
 * Represents an authenticated operator within the ZyntaPOS system.
 *
 * @property id             Unique identifier (UUID v4).
 * @property name           Full display name.
 * @property email          Login credential — must be unique across the system.
 * @property role           The built-in [Role] assigned to this user. Always populated.
 * @property storeId        Foreign key to the store this user is assigned to.
 * @property isActive       Whether the account is enabled. Inactive users cannot log in.
 * @property pinHash        Hash of the user's 4–6 digit quick-switch PIN. Null if not set.
 * @property customRoleId   If non-null, points to a [CustomRole] whose permission set
 *                          overrides [role]'s default permissions for RBAC checks.
 *                          [role] is retained for display and nav-gating fallback.
 * @property isSystemAdmin  When `true`, this is the designated system admin — the single
 *                          superuser created during onboarding. At most one [User] per
 *                          installation may have this flag set. System admin cannot be
 *                          deactivated without first transferring the designation via
 *                          [com.zyntasolutions.zyntapos.domain.usecase.user.TransferSystemAdminUseCase].
 * @property createdAt      UTC timestamp of account creation.
 * @property updatedAt      UTC timestamp of the last account modification.
 */
data class User(
    val id: String,
    val name: String,
    val email: String,
    val role: Role,
    val storeId: String,
    val isActive: Boolean = true,
    val pinHash: String? = null,
    val customRoleId: String? = null,
    val isSystemAdmin: Boolean = false,
    val createdAt: Instant,
    val updatedAt: Instant,
)
