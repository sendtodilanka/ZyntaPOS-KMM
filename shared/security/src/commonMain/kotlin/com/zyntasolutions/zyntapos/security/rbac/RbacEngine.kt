package com.zyntasolutions.zyntapos.security.rbac

import com.zyntasolutions.zyntapos.domain.model.CustomRole
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

    // ── Store-scoped RBAC (C3.2) ──────────────────────────────────────────────

    /**
     * Returns `true` if [user] holds [permission] at the specified [storeId].
     *
     * The effective role is resolved as follows:
     * 1. If [storeId] == [user.storeId] → use user's default role.
     * 2. If [roleAtStore] is provided (from a [UserStoreAccess] grant) → use that role.
     * 3. Otherwise → deny access.
     *
     * @param user        The authenticated user.
     * @param permission  The required permission.
     * @param storeId     The store where the action is being performed.
     * @param roleAtStore Optional per-store role override from a UserStoreAccess grant.
     */
    fun hasPermissionAtStore(
        user: User,
        permission: Permission,
        storeId: String,
        roleAtStore: Role? = null,
    ): Boolean {
        // Primary store — use default role
        if (storeId == user.storeId) {
            return hasPermission(user, permission)
        }
        // Multi-store access — use override role or user's default role
        val effectiveRole = roleAtStore ?: user.role
        return hasPermission(effectiveRole, permission)
    }

    // ── Dynamic RBAC overloads ────────────────────────────────────────────────

    /**
     * Returns `true` if [user] is granted [permission] after honouring admin-configured
     * built-in role overrides and any custom role assignment.
     *
     * Priority:
     * 1. If [user.customRoleId] is set → look up in [customRoles] and use that permission set.
     * 2. Else if [user.role] has an entry in [builtInOverrides] → use the override set.
     * 3. Else fall back to the static [Permission.rolePermissions] defaults.
     *
     * @param user             The authenticated user.
     * @param permission       The permission to check.
     * @param builtInOverrides Map of admin-configured overrides for built-in roles (non-ADMIN only).
     * @param customRoles      All custom role definitions (from DB).
     */
    fun hasPermission(
        user: User,
        permission: Permission,
        builtInOverrides: Map<Role, Set<Permission>>,
        customRoles: List<CustomRole>,
    ): Boolean = permission in resolvePermissions(user, builtInOverrides, customRoles)

    /**
     * Resolves the effective [Set] of [Permission]s for [user], honouring dynamic overrides.
     *
     * @see hasPermission for priority rules.
     */
    fun resolvePermissions(
        user: User,
        builtInOverrides: Map<Role, Set<Permission>>,
        customRoles: List<CustomRole>,
    ): Set<Permission> = when {
        // ADMIN always retains all permissions — defense-in-depth; no override can restrict ADMIN.
        user.role == Role.ADMIN        -> getPermissions(Role.ADMIN)
        user.customRoleId != null      -> customRoles.find { it.id == user.customRoleId }?.permissions ?: emptySet()
        else                           -> builtInOverrides[user.role] ?: getPermissions(user.role)
    }
}
