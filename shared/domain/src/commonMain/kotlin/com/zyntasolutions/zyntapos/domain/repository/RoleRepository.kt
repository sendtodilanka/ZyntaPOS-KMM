package com.zyntasolutions.zyntapos.domain.repository

import com.zyntasolutions.zyntapos.core.result.Result
import com.zyntasolutions.zyntapos.domain.model.CustomRole
import com.zyntasolutions.zyntapos.domain.model.Permission
import com.zyntasolutions.zyntapos.domain.model.Role
import kotlinx.coroutines.flow.Flow

/**
 * Contract for managing dynamic RBAC role definitions.
 *
 * Provides:
 * 1. **Custom role CRUD** — create, read, update, delete user-defined roles with
 *    explicit permission sets (stored in `custom_roles` table).
 * 2. **Built-in role overrides** — admins can override the default [Permission.rolePermissions]
 *    matrix for any non-ADMIN built-in role. Overrides are persisted in the settings store
 *    and take precedence over the static map at runtime.
 *
 * ### Relationship with [RbacEngine]
 * [com.zyntasolutions.zyntapos.security.rbac.RbacEngine] is stateless and uses
 * [Permission.rolePermissions] by default. When overrides or custom roles exist,
 * pass them explicitly to the dynamic `hasPermission` overload introduced alongside
 * this repository.
 *
 * ### ADMIN role is protected
 * Built-in [Role.ADMIN] always retains all permissions; implementations MUST
 * reject any attempt to override it via [setBuiltInRolePermissions].
 */
interface RoleRepository {

    // ── Custom Roles ─────────────────────────────────────────────────────────

    /**
     * Emits all custom roles ordered alphabetically by name.
     * Re-emits whenever a custom role is created, updated, or deleted.
     */
    fun getAllCustomRoles(): Flow<List<CustomRole>>

    /**
     * Returns the custom role with [id], or [Result.Error] if not found.
     */
    suspend fun getCustomRoleById(id: String): Result<CustomRole>

    /**
     * Persists a new custom role. [CustomRole.id] must be pre-populated by the caller.
     *
     * @return [Result.Error] with [com.zyntasolutions.zyntapos.core.result.ValidationException]
     *         if [CustomRole.name] is blank or conflicts with an existing role.
     */
    suspend fun createCustomRole(role: CustomRole): Result<Unit>

    /**
     * Updates the mutable fields ([CustomRole.name], [CustomRole.description],
     * [CustomRole.permissions]) of an existing custom role.
     */
    suspend fun updateCustomRole(role: CustomRole): Result<Unit>

    /**
     * Permanently deletes the custom role with [id].
     *
     * Users previously assigned this role will retain the [User.role] built-in
     * fallback. Callers should reassign affected users before deletion.
     */
    suspend fun deleteCustomRole(id: String): Result<Unit>

    // ── Built-in Role Permission Overrides ───────────────────────────────────

    /**
     * Returns the admin-configured permission set for [role], or `null` if no override
     * exists (in which case [Permission.rolePermissions] defaults apply).
     *
     * [Role.ADMIN] always returns `null` — its permissions cannot be overridden.
     */
    suspend fun getBuiltInRolePermissions(role: Role): Set<Permission>?

    /**
     * Persists an explicit permission override for [role].
     *
     * Replaces any previously stored override. The override takes precedence over
     * [Permission.rolePermissions] at runtime.
     *
     * @return [Result.Error] if [role] is [Role.ADMIN] (protected) or on storage failure.
     */
    suspend fun setBuiltInRolePermissions(role: Role, permissions: Set<Permission>): Result<Unit>

    /**
     * Removes any stored override for [role], reverting it to [Permission.rolePermissions]
     * defaults.
     */
    suspend fun resetBuiltInRolePermissions(role: Role): Result<Unit>
}
