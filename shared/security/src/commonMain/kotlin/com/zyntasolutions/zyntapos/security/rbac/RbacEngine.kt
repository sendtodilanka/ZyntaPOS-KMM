package com.zyntasolutions.zyntapos.security.rbac

import com.zyntasolutions.zyntapos.domain.model.Permission
import com.zyntasolutions.zyntapos.domain.model.Role
import com.zyntasolutions.zyntapos.domain.model.User

/**
 * ZyntaPOS Role-Based Access Control engine.
 *
 * Stateless, pure-computation component — no IO, no side effects. All permission
 * decisions are derived from [Permission.rolePermissions], the single source of
 * truth for the RBAC matrix defined in the `:shared:domain` module.
 *
 * ### Usage
 * ```kotlin
 * val rbac = RbacEngine()
 * if (rbac.hasPermission(currentUser, Permission.VOID_ORDER)) {
 *     // proceed with void
 * } else {
 *     auditLogger.logPermissionDenied(currentUser.id, Permission.VOID_ORDER, "CartPanel")
 *     // show error
 * }
 * ```
 *
 * Inject via Koin `securityModule` — the instance is a singleton with no mutable state.
 */
class RbacEngine {

    /**
     * Returns `true` if [user] holds a [Role] that grants [permission].
     *
     * The check is purely based on the static [Permission.rolePermissions] matrix.
     * Dynamic overrides (per-user custom grants) are not supported in Phase 1.
     *
     * @param user       The authenticated [User] whose role is evaluated.
     * @param permission The [Permission] required for the action.
     * @return `true` if the user's role includes [permission]; `false` otherwise.
     */
    fun hasPermission(user: User, permission: Permission): Boolean =
        permission in getPermissions(user.role)

    /**
     * Returns the full set of [Permission]s granted to [role].
     *
     * Falls back to an empty set if [role] has no entry in the matrix
     * (defensive — should not happen with the current enum set).
     *
     * @param role The [Role] to query.
     * @return Immutable [Set] of permissions; never `null`.
     */
    fun getPermissions(role: Role): Set<Permission> =
        Permission.rolePermissions[role] ?: emptySet()

    /**
     * Checks whether [role] grants [permission].
     *
     * Convenience overload for cases where the full [User] object is not available
     * (e.g., during session bootstrap before the user domain model is loaded).
     *
     * @param role       The role to check.
     * @param permission The required permission.
     */
    fun hasPermission(role: Role, permission: Permission): Boolean =
        permission in getPermissions(role)

    /**
     * Returns all [Permission]s that are **not** granted to [role].
     *
     * Useful for building permission-denied error messages.
     */
    fun getDeniedPermissions(role: Role): Set<Permission> =
        Permission.entries.toSet() - getPermissions(role)
}
